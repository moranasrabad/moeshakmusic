package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.os.Looper;

import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.td.TdClient;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * فاساد سطح بالای تلگرام: احراز هویت، اسکن عمیق موزیک‌ها، دانلود و چانک‌خوانی استریم.
 * تیم موشک — moeshakteam.ir
 */
public final class Tg implements TdClient.UpdateHandler {

    public enum Auth {LOADING, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, WAIT_QR, READY, LOGGING_OUT, CLOSED, ERROR}

    public interface AuthListener {
        void onAuth(Auth a, String error);

        /** وضعیت اتصال شبکهٔ TDLib: connecting / ready / waiting / updating */
        default void onConnState(String s) {
        }
    }

    public interface ScanListener {
        void onProgress(int found, int chats);

        void onDone(int total);

        void onError(String msg);
    }

    public interface DownloadListener {
        void onProgress(int pct);

        void onDone(String path);

        void onError(String msg);
    }

    public interface AccountListener {
        void onAccount(String name, String phone, String id);

        default void onPhoto(android.graphics.Bitmap bmp) {
        }
    }

    /** اسکن عمیق: حداکثر تعداد چت؛ هر چت بدون سقف تا انتهای تاریخچه خوانده می‌شود */
    public static final int MAX_CHATS = 1500;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TgWorker");
        t.setDaemon(true);
        return t;
    });

    /** ترد جدا برای عملیات پروکسی — پشت صف اسکن گیر نکند */
    private static final ExecutorService PROXY_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TgProxy");
        t.setDaemon(true);
        return t;
    });

    /** ترد جدا برای دانلود — پشت اسکن/چک‌فالو گیر نکند */
    private static final ExecutorService DL_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TgDownload");
        t.setDaemon(true);
        return t;
    });

    private static Tg inst;

    private final Context ctx;
    private final Prefs prefs;
    private final List<AuthListener> authListeners = new CopyOnWriteArrayList<>();
    private final Map<Integer, DownloadTask> downloads = new ConcurrentHashMap<>();

    /** دانلودهای فعال — برای نمایش زنده + لغو در بخش دانلودها */
    public static class ActiveDownload {
        public final int fileId;
        public final String title;
        public final String chatTitle;
        public final long size;
        public volatile int pct = -1; // -1 = در صف/شروع
        public volatile boolean done;
        public volatile boolean error;

        ActiveDownload(int fileId, String title, String chatTitle, long size) {
            this.fileId = fileId;
            this.title = title;
            this.chatTitle = chatTitle;
            this.size = size;
        }
    }

    private final Map<Integer, ActiveDownload> activeDownloads = new ConcurrentHashMap<>();
    /** هوک UI — بعد از هر تغییر در دانلودهای فعال صدا زده می‌شود */
    public volatile Runnable onDownloadsChanged;
    private final List<Runnable> downloadListeners = new CopyOnWriteArrayList<>();

    public void addDownloadsListener(Runnable r) {
        if (r != null && !downloadListeners.contains(r)) downloadListeners.add(r);
    }

    public void removeDownloadsListener(Runnable r) {
        downloadListeners.remove(r);
    }

    private void notifyDownloadsChanged() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Runnable r = onDownloadsChanged;
            if (r != null) {
                try { r.run(); } catch (Throwable ignored) {}
            }
            for (Runnable l : downloadListeners) {
                try { l.run(); } catch (Throwable ignored) {}
            }
        });
    }

    /** لیست دانلودهای فعال (اسنپ‌شات — امن برای UI) */
    public List<ActiveDownload> activeDownloads() {
        return new ArrayList<>(activeDownloads.values());
    }

    public final Map<Long, String> chatTitles = new ConcurrentHashMap<>();
    public final List<Track> library = new CopyOnWriteArrayList<>();
    public volatile String passwordHint = "";
    /** وضعیت اتصال TDLib برای نمایش در UI */
    public volatile String connState = "";
    /** اگه کد ارسال شده و منتظریم کد وارد بشه */
    private volatile boolean codeRequested;
    private volatile long lastPhoneSendMs;
    /** لینک QR لاگین (برای WaitOtherDeviceConfirmation) */
    public volatile String qrLink = "";
    private volatile Auth auth = Auth.LOADING;
    private volatile String authError;
    private volatile boolean started;
    private volatile boolean scanning;
    private volatile boolean logoutRequested;

    private static class DownloadTask {
        final DownloadListener cb;
        volatile boolean done;

        DownloadTask(DownloadListener cb) {
            this.cb = cb;
        }
    }

    public static synchronized Tg get(Context c) {
        if (inst == null) inst = new Tg(c.getApplicationContext());
        return inst;
    }

    private Tg(Context c) {
        ctx = c.getApplicationContext();
        prefs = Prefs.get(ctx);
        // فیوریت‌ها + کتابخانهٔ ذخیره‌شده (آهنگ‌هایی که صریحاً «افزودن به کتابخانه» شده‌اند)
        favsSaved = SavedTracksStore.load(ctx);
        for (Track t : favsSaved) PlayerManager.FAVORITES.add(PlayerManager.key(t));
        if (!favsSaved.isEmpty())
            log("❤️ " + favsSaved.size() + " فیوریت از دیسک بازیابی شد (پس از ورود، fileId تازه گرفته می‌شود)");
        // 📚 کتابخانهٔ ذخیره‌شده — دیگر بعد از ری‌استارت خالی نیست
        List<Track> lib = LibraryPersistence.load(ctx);
        if (!lib.isEmpty()) {
            library.addAll(lib);
            log("📚 " + lib.size() + " تراک کتابخانه از دیسک لود شد");
        }
    }

    /** ذخیرهٔ کتابخانه روی دیسک — بعد از هر تغییر مهم */
    public void persistLibraryNow() {
        LibraryPersistence.save(ctx, new ArrayList<>(library));
    }

    /** نتایج اسکن — جدا از کتابخانه تا قاطی نشوند */
    public final List<Track> scanResults = new CopyOnWriteArrayList<>();
    /** فایل عکس کوچک هر چت (برای تامبنیل) — chatId → fileId */
    public final Map<Long, Integer> chatPhotoFileIds = new ConcurrentHashMap<>();
    /** فیوریت‌های لودشده از دیسک (برای بازیابی بعد از ورود) */
    private final List<Track> favsSaved;
    /** آیا بازیابی بعد از ورود انجام شده؟ */
    private volatile boolean restored;
    /** هوک UI — بعد از تغییر کتابخانه صدا زده می‌شود (چند شنونده؛ رونویسی نشود) */
    public volatile Runnable onLibraryChanged;
    private final List<Runnable> libraryListeners = new CopyOnWriteArrayList<>();

    public void addLibraryListener(Runnable r) {
        if (r != null && !libraryListeners.contains(r)) libraryListeners.add(r);
    }

    public void removeLibraryListener(Runnable r) {
        libraryListeners.remove(r);
    }
    /** شنونده‌های پیشرفت اسکن عمیق (UI) */
    public final List<ScanListener> deepListeners = new CopyOnWriteArrayList<>();

    // ---------- چرخهٔ حیات ----------

    public void addAuthListener(AuthListener l) {
        authListeners.add(l);
        l.onAuth(auth, authError);
    }

    public void removeAuthListener(AuthListener l) {
        authListeners.remove(l);
    }

    public Auth auth() {
        return auth;
    }

    public String authError() {
        return authError;
    }

    public boolean isScanning() {
        return scanning;
    }

    public void start() {
        if (started) return;
        started = true;
        log("🚀 راه‌اندازی TDLib… (apiId=" + prefs.apiId() + ")");
        TdClient.init(this);
        applyProxy();
        setAuth(Auth.LOADING, null);
    }

    /** آخرین پروکسیِ اعمال‌شده (proxyId در TDLib + امضای آدرس) — ضدِ AddProxy تکراری */
    private volatile int appliedProxyId = -1;
    private volatile String appliedProxySig = "";

    /** اعمال پروکسی MTProto (اگه در تنظیمات فعال باشه) — بدون AddProxy تکراری و ری‌استارت اتصال */
    public void applyProxy() {
        PROXY_EXEC.execute(() -> {
            try {
                if (prefs.proxyEnabled() && !prefs.proxyServer().trim().isEmpty()) {
                    String server = prefs.proxyServer().trim();
                    String secret = prefs.proxySecret().trim();
                    int port;
                    try {
                        port = Integer.parseInt(prefs.proxyPort().trim().isEmpty() ? "443" : prefs.proxyPort().trim());
                    } catch (Exception e) {
                        port = 443;
                    }
                    if (server.isEmpty() || secret.isEmpty()) {
                        disableProxy();
                        return;
                    }
                    String sig = server + ":" + port + ":" + secret;
                    if (sig.equals(appliedProxySig) && appliedProxyId > 0) {
                        // همین پروکسی قبلاً اضافه شده — فقط فعالش کن (بدون قطع‌ووصل دوباره)
                        TdClient.send(new TdApi.EnableProxy(appliedProxyId), r -> {});
                        return;
                    }
                    // بگرد ببین قبلاً اضافه شده؟ (جلوگیری از انباشت پروکسی تکراری در TDLib)
                    int existing = (auth == Auth.READY) ? findProxyId(server, port, secret) : 0;
                    if (existing > 0) {
                        appliedProxyId = existing;
                        appliedProxySig = sig;
                        TdClient.send(new TdApi.EnableProxy(existing), r -> {});
                        return;
                    }
                    log("📶 اعمال پروکسی: " + server + ":" + port);
                    TdApi.Proxy p = new TdApi.Proxy(server, port, new TdApi.ProxyTypeMtproto(secret));
                    TdClient.send(new TdApi.AddProxy(p, true, "moeshak"), r -> {
                        if (r instanceof TdApi.Error) {
                            Ui.toast(ctx, ctx.getString(R.string.proxy_bad));
                        } else if (r instanceof TdApi.AddedProxy) {
                            appliedProxyId = ((TdApi.AddedProxy) r).id;
                            appliedProxySig = sig;
                            Ui.toast(ctx, ctx.getString(R.string.proxy_active));
                        }
                    });
                } else {
                    disableProxy();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void disableProxy() {
        appliedProxyId = -1;
        appliedProxySig = "";
        TdClient.send(new TdApi.DisableProxy(), r -> {});
    }

    /** پیدا کردن proxy_id یک پروکسیِ از قبل اضافه‌شده با همین آدرس */
    private int findProxyId(String server, int port, String secret) {
        try {
            TdApi.AddedProxies aps = (TdApi.AddedProxies) TdClient.sync(new TdApi.GetProxies());
            if (aps == null || aps.proxies == null) return 0;
            for (TdApi.AddedProxy ap : aps.proxies) {
                if (ap == null || ap.proxy == null) continue;
                if (!server.equals(ap.proxy.server)) continue;
                if (ap.proxy.port != port) continue;
                if (ap.proxy.type instanceof TdApi.ProxyTypeMtproto) {
                    String sec = ((TdApi.ProxyTypeMtproto) ap.proxy.type).secret;
                    if (secret.equals(sec == null ? "" : sec)) return ap.id;
                }
            }
        } catch (Exception e) {
            // قبل از READY یا هنگام خطا — بی‌خیال، AddProxy تازه می‌زنیم
        }
        return 0;
    }

    // ---------- آپدیت‌های TDLib ----------

    @Override
    public void onUpdate(TdApi.Object u) {
        if (u instanceof TdApi.UpdateAuthorizationState) {
            handleAuth(((TdApi.UpdateAuthorizationState) u).authorizationState);
        } else if (u instanceof TdApi.UpdateFile) {
            onFileUpdate(((TdApi.UpdateFile) u).file);
        } else if (u instanceof TdApi.UpdateConnectionState) {
            TdApi.ConnectionState cs = ((TdApi.UpdateConnectionState) u).state;
            String code = "connecting";
            if (cs instanceof TdApi.ConnectionStateReady) code = "ready";
            else if (cs instanceof TdApi.ConnectionStateWaitingForNetwork) code = "waiting";
            else if (cs instanceof TdApi.ConnectionStateUpdating) code = "updating";
            connState = code;
            log("🌐 اتصال: " + code);
            for (AuthListener l : authListeners) {
                try {
                    l.onConnState(code);
                } catch (Throwable ignored) {
                }
            }
        } else if (u instanceof TdApi.UpdateChatFolders) {
            // لیست پوشه‌های کاربر (برای مرورگر چت‌ها)
            folders.clear();
            for (TdApi.ChatFolderInfo fi : ((TdApi.UpdateChatFolders) u).chatFolders) {
                folders.add(fi);
            }
            log("🗂 " + folders.size() + " پوشه شناسایی شد");
        }
    }

    /** پوشه‌های کاربر (از updateChatFolders) */
    public final List<TdApi.ChatFolderInfo> folders = new CopyOnWriteArrayList<>();

    /** لود چت‌های یک لیست مشخص (اصلی/آرشیو/پوشه) */
    public List<TdApi.Chat> loadChatsOfList(TdApi.ChatList list, int max) throws Exception {
        while (true) {
            try {
                TdClient.sync(new TdApi.LoadChats(list, 100));
            } catch (TdClient.TdError e) {
                break;
            }
        }
        org.json.JSONObject resp = TdClient.syncRaw(new TdApi.GetChats(list, 5000));
        org.json.JSONArray idsArr = resp.optJSONArray("chat_ids");
        List<TdApi.Chat> out = new ArrayList<>();
        if (idsArr == null) return out;
        for (int i = 0; i < idsArr.length(); i++) {
            if (out.size() >= max) break;
            out.add(chatFromRaw(idsArr.optLong(i)));
        }
        log("📂 این لیست: " + out.size() + " چت");
        return out;
    }

    /**
     * اسکن عمیق دستی یک چت (از صفحهٔ مرور چت‌ها) — فیلترها + تاریخچهٔ بلند.
     * تراک‌های جدید به کتابخانه اضافه می‌شن و تعدادشون برمی‌گرده.
     */
    /**
     * ماشین حالت ورود — تنها مرجع تغییر وضعیت UI.
     * ترتیب دقیقاً مثل تلگرام رسمی:
     * WaitTdlibParameters → SetParameters → WaitPhoneNumber → [شماره یا QR] → WaitCode → WaitPassword → Ready
     */
    private void handleAuth(TdApi.AuthorizationState s) {
        if (s == null) return;
        log("⚙️ authState: " + s.getClass().getSimpleName());
        if (s instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            String dir = new File(ctx.getFilesDir(), "tdlib").getAbsolutePath();
            TdApi.SetTdlibParameters p = new TdApi.SetTdlibParameters(
                    false, dir, dir, null, true, true, true, false,
                    prefs.apiId(), prefs.apiHash(), "fa", "Android", "Android", "2.2.0");
            final TdApi.SetTdlibParameters pFinal = p;
            TdClient.send(p, r -> {
                if (r instanceof TdApi.Error) {
                    TdApi.Error e = (TdApi.Error) r;
                    log("⚠️ setTdlibParameters: " + e.message);
                    if (e.message != null && (e.message.contains("lock") || e.message.contains("database"))) {
                        // قفل دیتابیس — نمونهٔ قبلی هنوز داره بسته می‌شه؛ ۳ بار با backoff تلاش می‌کنیم
                        EXEC.execute(() -> {
                            int[] waits = {4000, 8000, 12000};
                            for (int i = 0; i < waits.length; i++) {
                                final int attempt = i + 2;
                                try {
                                    Thread.sleep(waits[i]);
                                    log("🔁 تلاش " + attempt + " برای باز کردن دیتابیس…");
                                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                                    final boolean[] ok = {false};
                                    TdClient.send(pFinal, r2 -> {
                                        if (r2 instanceof TdApi.Error) {
                                            String m2 = ((TdApi.Error) r2).message;
                                            log("⚠️ تلاش " + attempt + ": " + m2);
                                            if (m2 != null && !m2.contains("lock") && !m2.contains("database")) ok[0] = true;
                                        } else {
                                            ok[0] = true;
                                        }
                                        latch.countDown();
                                    });
                                    latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                                    if (ok[0]) break;
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    } else {
                        setAuth(Auth.ERROR, friendly(e));
                    }
                }
            });
        } else if (s instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            codeRequested = false;
            // اگر کاربر QR خواسته و فلوی قبلی ریست شده — خودکار QR ادامه بده
            if (pendingQr) {
                pendingQr = false;
                log("🔳 فلوی تازه آماده شد — درخواست QR…");
                TdClient.send(new TdApi.RequestQrCodeAuthentication(new long[0]), r -> {
                    if (r instanceof TdApi.Error) {
                        TdApi.Error e = (TdApi.Error) r;
                        log("⚠️ QR رد شد: " + e.message);
                        pendingQr = false;
                        setAuth(Auth.WAIT_PHONE, friendly(e));
                    }
                });
                return;
            }
            setAuth(Auth.WAIT_PHONE, null);
        } else if (s instanceof TdApi.AuthorizationStateWaitCode) {
            codeRequested = true;
            setAuth(Auth.WAIT_CODE, null);
        } else if (s instanceof TdApi.AuthorizationStateWaitRegistration) {
            setAuth(Auth.ERROR, ctx.getString(R.string.err_generic, "sign-up not supported"));
        } else if (s instanceof TdApi.AuthorizationStateWaitPassword) {
            TdApi.AuthorizationStateWaitPassword w = (TdApi.AuthorizationStateWaitPassword) s;
            passwordHint = w.passwordHint == null ? "" : w.passwordHint;
            setAuth(Auth.WAIT_PASSWORD, null);
        } else if (s instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            qrLink = ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) s).link == null
                    ? "" : ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) s).link;
            log("🔳 QR آماده است — با تلگرام رسمی اسکن کن");
            setAuth(Auth.WAIT_QR, null);
        } else if (s instanceof TdApi.AuthorizationStateReady) {
            codeRequested = false;
            pendingQr = false;
            log("✅ ورود انجام شد — READY");
            setAuth(Auth.READY, null);
            // بازیابی فیوریت‌ها و پلی‌لیست‌ها با fileId تازه
            restoreSaved();
        } else if (s instanceof TdApi.AuthorizationStateLoggingOut) {
            // خروج — یا دستی (از تنظیمات) یا خاتمهٔ از راه دور (Devices ← Terminate)
            if (!logoutRequested) {
                log("🚪 نشست از راه دور خاتمه یافت (از دستگاه دیگری) — ورود مجدد با QR آماده می‌شود…");
                Ui.toast(ctx, R.string.session_terminated);
                pendingQr = true; // بعد از ساخت کلاینت تازه، خودکار صفحهٔ QR باز شود
            } else {
                log("🚪 خروج دستی…");
            }
            // بستن درگاه — هیچ درخواستی به کلاینتِ در حال مرگ نرود (ضدکرش)
            TdClient.gate(true);
            // توقف پخش/دانلود — استریم روی نشستِ مرده کرش می‌دهد
            try {
                PlayerManager.get(ctx).stopAll();
            } catch (Throwable ignored) {
            }
            setAuth(Auth.LOGGING_OUT, null);
        } else if (s instanceof TdApi.AuthorizationStateClosed) {
            // کلاینت کاملاً بسته شد — حالا امنه کلاینت تازه بسازیم
            library.clear();
            chatTitles.clear();
            downloads.clear();
            TdClient.recreate();
            TdClient.gate(false); // کلاینت تازه آماده است — درگاه باز شود
            if (prefs.proxyEnabled()) applyProxy();
            logoutRequested = false;
            log("🔄 کلاینت تازه ساخته شد — منتظر وضعیت جدید…");
            setAuth(Auth.LOADING, null);
            // شبکهٔ امنیتی: اگر ۸ ثانیه بعد هنوز هیچ state ای نیامد، کلاینت را دوباره از نو بساز
            EXEC.execute(() -> {
                try {
                    Thread.sleep(8000);
                    if (auth == Auth.LOADING && !logoutRequested) {
                        log("🔁 ۸ ثانیه هیچ state ای نیامد — کلاینت دوباره تازه می‌شود…");
                        TdClient.recreate();
                        if (prefs.proxyEnabled()) applyProxy();
                    }
                } catch (InterruptedException ignored) {
                }
            });
        }
    }

    private void setAuth(Auth a, String err) {
        auth = a;
        authError = err;
        for (AuthListener l : authListeners) {
            try {
                l.onAuth(a, err);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onAuthResponse(TdApi.Object r) {
        if (r instanceof TdApi.Error) {
            TdApi.Error e = (TdApi.Error) r;
            android.util.Log.w("Tg", "AUTH ERROR: " + e.code + " " + e.message);
            // اگر خطا «درخواست تکراری» بود و کد قبلاً رفته → برو صفحه کد
            if (e.message != null && e.message.toLowerCase().contains("another authorization")) {
                codeRequested = true;
                setAuth(Auth.WAIT_CODE, null);
                return;
            }
            Auth cur = auth;
            setAuth(cur, friendly(e));
        }
    }

    private String friendly(TdApi.Error e) {
        String m = e.message == null ? "" : e.message;
        if (m.contains("API_ID") || m.contains("API_HASH") || m.contains("api_id")) return ctx.getString(R.string.err_invalid_keys);
        if (m.contains("PHONE_NUMBER_INVALID") || m.contains("PHONE_NUMBER_BANNED")) return ctx.getString(R.string.err_phone);
        if (m.contains("PHONE_CODE_INVALID") || m.contains("PHONE_CODE_EXPIRED")) return ctx.getString(R.string.err_bad_code);
        if (m.contains("PASSWORD_HASH_INVALID")) return ctx.getString(R.string.err_bad_password);
        if (m.contains("FLOOD")) return ctx.getString(R.string.err_flood);
        if (m.toLowerCase().contains("another authorization")) return ctx.getString(R.string.err_code_sent_already);
        if (m.contains("API_ID_PUBLISHED_FLOOD") || m.contains("published")) return ctx.getString(R.string.err_api_flood);
        if (m.toLowerCase().contains("timeout")) return ctx.getString(R.string.err_network);
        if (m.toLowerCase().contains("aborted") || m.toLowerCase().contains("terminated"))
            return "درخواست قطع شد — فلوی ورود ریست شد؛ دوباره تلاش کن";
        return ctx.getString(R.string.err_generic, m);
    }

    // ---------- ورود (API ساده و امن) ----------

    /** پرچم: کاربر QR می‌خواهد — وقتی فلو به WaitPhoneNumber رسید خودکار اجرا می‌شود */
    private volatile boolean pendingQr;

    /** ورود با شماره — فقط در فاز WAIT_PHONE معتبر است */
    public void sendPhone(String phone) {
        if (auth() != Auth.WAIT_PHONE) {
            log("⚠️ sendPhone در فاز اشتباه (" + auth() + ") — فلوی ورود ریست می‌شود، بعد دوباره شماره را بزن");
            Ui.toast(ctx, "فلوی ورود ریست شد — ۳ ثانیه بعد دوباره شماره را بزن");
            pendingQr = false;
            TdClient.send(new TdApi.Close(), r -> {
            });
            setAuth(Auth.LOADING, null);
            return;
        }
        TdApi.PhoneNumberAuthenticationSettings st = new TdApi.PhoneNumberAuthenticationSettings();
        st.allowFlashCall = false;
        st.allowMissedCall = false;
        st.isCurrentPhoneNumber = false;
        st.hasUnknownPhoneNumber = false;
        st.allowSmsRetrieverApi = false;
        setAuth(auth, null);
        TdClient.send(new TdApi.SetAuthenticationPhoneNumber(phone, st), this::onAuthResponse);
    }

    /** ورود با کد — فقط در فاز WAIT_CODE */
    public void sendCode(String code) {
        if (auth() != Auth.WAIT_CODE) {
            log("⚠️ sendCode در فاز اشتباه (" + auth() + ") — ریست فلو…");
            pendingQr = false;
            TdClient.send(new TdApi.Close(), r -> {
            });
            setAuth(Auth.LOADING, null);
            return;
        }
        setAuth(auth, null);
        TdClient.send(new TdApi.CheckAuthenticationCode(code), this::onAuthResponse);
    }

    /** ورود با رمز — فقط در فاز WAIT_PASSWORD */
    public void sendPassword(String pw) {
        if (auth() != Auth.WAIT_PASSWORD) {
            log("⚠️ sendPassword در فاز اشتباه (" + auth() + ") — ریست فلو…");
            pendingQr = false;
            TdClient.send(new TdApi.Close(), r -> {
            });
            setAuth(Auth.LOADING, null);
            return;
        }
        setAuth(auth, null);
        TdClient.send(new TdApi.CheckAuthenticationPassword(pw), this::onAuthResponse);
    }

    /**
     * ورود با QR — مثل تلگرام رسمی:
     * در فاز WAIT_PHONE مستقیم اجرا می‌شود؛
     * در فاز اشتباه → Close امن → Closed → کلاینت تازه → خودکار QR.
     */
    public void requestQr() {
        qrLink = "";
        if (auth() == Auth.WAIT_PHONE) {
            pendingQr = false;
            log("🔳 درخواست QR…");
            TdClient.send(new TdApi.RequestQrCodeAuthentication(new long[0]), r -> {
                if (r instanceof TdApi.Error) {
                    TdApi.Error e = (TdApi.Error) r;
                    log("⚠️ QR رد شد: " + e.message);
                    // نمایش خطا روی UI هم (کاربر ببیند دقیقاً چیست)
                    setAuth(Auth.WAIT_PHONE, "QR: " + e.message);
                }
            });
            return;
        }
        // فاز اشتباه — مستقیماً امتحان می‌کنیم؛ TDLib اگر خطا بدهد هندل می‌شود
        pendingQr = false;
        log("🔳 درخواست QR (فاز: " + auth() + ")…");
        TdClient.send(new TdApi.RequestQrCodeAuthentication(new long[0]), r -> {
            if (r instanceof TdApi.Error) {
                TdApi.Error e = (TdApi.Error) r;
                log("⚠️ QR رد شد: " + e.message);
                setAuth(Auth.WAIT_PHONE, "QR: " + e.message);
            }
        });
    }

    /** ارسال دوباره کد */
    public void resendCode() {
        if (auth() != Auth.WAIT_CODE) {
            Ui.toast(ctx, ctx.getString(R.string.resend_only_after_send));
            return;
        }
        TdClient.send(new TdApi.ResendAuthenticationCode(new TdApi.ResendCodeReasonUserRequest()), r -> {
            if (r instanceof TdApi.Error) {
                TdApi.Error e = (TdApi.Error) r;
                String msg = e.message == null ? "" : e.message;
                if (msg.contains("FLOOD")) Ui.toast(ctx, ctx.getString(R.string.err_flood));
                else if (msg.contains("RESEND") || msg.contains("NOT_ALLOWED") || msg.contains("TIMED_OUT"))
                    Ui.toast(ctx, ctx.getString(R.string.resend_not_allowed));
                else Ui.toast(ctx, ctx.getString(R.string.err_generic, msg));
            } else {
                Ui.toast(ctx, ctx.getString(R.string.code_resent));
            }
        });
    }

    /** ورود دستی به مرحله کد (وقتی UI جا مونده) */
    public void forceWaitCode() {
        codeRequested = true;
        setAuth(Auth.WAIT_CODE, null);
    }

    /** خروج از حساب */
    public void logout() {
        logoutRequested = true;
        pendingQr = false;
        TdClient.send(new TdApi.LogOut(), r -> {
        });
    }

    public void getAccount(AccountListener l) {
        TdClient.sendRaw(new TdApi.GetMe(), jo -> {
            try {
                myUserId = jo.optLong("id");
                String name = (jo.optString("first_name", "") + " " + jo.optString("last_name", "")).trim();
                if (name.isEmpty()) name = jo.optString("username", "کاربر");
                String username = "";
                org.json.JSONArray un = jo.optJSONObject("usernames") != null
                        ? jo.optJSONObject("usernames").optJSONArray("active_usernames") : null;
                if (un != null && un.length() > 0) username = "@" + un.optString(0);
                String phone = jo.optString("phone_number", "—");
                log("👤 اکانت: " + name + (username.isEmpty() ? "" : " (" + username + ")") + " (+" + phone + ") id:" + myUserId);
                l.onAccount(name, phone, String.valueOf(myUserId));
                org.json.JSONObject pp = jo.optJSONObject("profile_photo");
                if (pp != null) {
                    org.json.JSONObject small = pp.optJSONObject("small");
                    if (small != null) {
                        final int pfid = small.optInt("id");
                        new Thread(() -> {
                            android.graphics.Bitmap bmp = downloadPhotoSync(pfid);
                            if (bmp != null) l.onPhoto(bmp);
                        }, "PhotoLoader").start();
                    }
                }
            } catch (Throwable e) {
                log("⚠️ خطای گرفتن اکانت: " + e);
                l.onAccount("—", "—", "—");
            }
        });
    }

    public interface PhotoListener {
        void onPhoto(android.graphics.Bitmap bmp);
    }

    /** فقط عکس پروفایل */
    public void getAccountPhoto(PhotoListener l) {
        if (auth() != Auth.READY) return;
        EXEC.execute(() -> {
            try {
                TdApi.User me = (TdApi.User) TdClient.sync(new TdApi.GetMe());
                if (me.profilePhoto != null && me.profilePhoto.small != null) {
                    android.graphics.Bitmap bmp = downloadPhotoSync(me.profilePhoto.small.id);
                    if (bmp != null) l.onPhoto(bmp);
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** دانلود همگام عکس پروفایل (فقط از thread پس‌زمینه) */
    private android.graphics.Bitmap downloadPhotoSync(int fileId) {
        try {
            TdApi.File f = (TdApi.File) TdClient.sync(new TdApi.GetFile(fileId));
            if (f.local != null && f.local.isDownloadingCompleted && f.local.path != null) {
                return android.graphics.BitmapFactory.decodeFile(f.local.path);
            }
            long size = f.expectedSize > 0 ? f.expectedSize : (f.size > 0 ? f.size : 1);
            TdClient.sync(new TdApi.DownloadFile(fileId, 1, 0L, size, true));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                f = (TdApi.File) TdClient.sync(new TdApi.GetFile(fileId));
                if (f.local != null && f.local.isDownloadingCompleted && f.local.path != null) {
                    return android.graphics.BitmapFactory.decodeFile(f.local.path);
                }
                Thread.sleep(200);
            }
        } catch (Exception e) {
            log("⚠️ دانلود عکس پروفایل ناموفق: " + e.getMessage());
        }
        return null;
    }

    // ---------- اسکن بازنویسی‌شدهٔ v2 — موتور تاریخچهٔ مستقیم ----------
    //
    // فلسفهٔ جدید: به جای SearchChatMessages (که روی برخی اکانت‌ها خالی جواب می‌ده)
    // از GetChatHistory مستقیم استفاده می‌کنیم — همون چیزی که تلگرام رسمی هم برای
    // نمایش چت استفاده می‌کنه و همیشه جواب می‌ده. سرچ فقط به‌عنوان مکمل.

    /** اسکن کامل: همهٔ چت‌ها با تاریخچهٔ مستقیم */
    public void scanLibrary(ScanListener cb) {
        scanRange(0, Integer.MAX_VALUE, cb, false);
    }

    /** اسکن دستی: از چت from به بعد، count تا چت — نتایج به scanResults می‌روند (جدا از کتابخانه) */
    public void scanRange(int from, int count, ScanListener cb) {
        // نتایج باید در «بخش اسکن» دیده شوند و کاربر با «افزودن به کتابخانه» منتقلشان کند
        scanRange(from, count, cb, false);
    }

    /**
     * اسکن با مقصد قابل انتخاب:
     * toLibrary=true → کتابخانهٔ دائمی
     * toLibrary=false → نتایج اسکن (بخش SCAN)
     */
    public void scanRange(int from, int count, ScanListener cb, boolean toLibrary) {
        if (scanning) {
            Ui.toast(ctx, ctx.getString(R.string.scan_already));
            cb.onDone(toLibrary ? library.size() : scanResults.size());
            return;
        }
        scanning = true;
        scanCancel = false;
        final List<Track> target = toLibrary ? library : scanResults;
        final long tStart = System.currentTimeMillis();
        EXEC.execute(() -> {
            int chats = 0;
            int files = 0;
            List<Track> buffer = new ArrayList<>();
            try {
                // همهٔ حالت‌های اسکن، کل تاریخچهٔ هر چت را می‌خوانند (کامل — هیچ سقفی روی پیام‌ها نیست)
                final boolean deep = count >= Integer.MAX_VALUE;
                log("🚀 اسکن شروع شد → " + (toLibrary ? "کتابخانه" : "بخش اسکن")
                        + " (" + (deep ? "همهٔ چت‌ها" : count + " چت") + " — تاریخچهٔ کامل هر چت)");
                List<TdApi.Chat> all = loadAllChats();
                log("📋 " + all.size() + " چت لود شد");
                // Saved Messages همیشه اول
                try {
                    TdApi.User me = (TdApi.User) TdClient.sync(new TdApi.GetMe());
                    myUserId = me.id;
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id == me.id) {
                            TdApi.Chat c = all.remove(i);
                            all.add(0, c);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                int end = (int) Math.min(all.size(), (long) from + count);
                cb.onProgress(target.size(), 0);
                for (int i = from; i < end; i++) {
                    if (scanCancel) {
                        log("⏹ اسکن توسط کاربر لغو شد");
                        break;
                    }
                    TdApi.Chat c = all.get(i);
                    String title = c.title == null || c.title.isEmpty() ? "بدون‌نام" : c.title;
                    // 🔍 لاگ زندهٔ شروع هر چت — معلوم باشد اسکن در جریان است
                    log("🔍 [" + (i + 1) + "/" + end + "] در حال اسکن «" + title + "»…");
                    List<Track> found;
                    try {
                        int[] msgs = new int[1];  // شمارندهٔ مستقل برای هر چت
                        long t0 = System.currentTimeMillis();
                        found = scanChatHistory(c, msgs, deep);
                        long ms = System.currentTimeMillis() - t0;
                        log("✅ [" + (i + 1) + "/" + end + "] «" + title + "» → " + msgs[0] + " پیام، "
                                + found.size() + " فایل صوتی (" + ms + "ms)");
                    } catch (Exception e) {
                        // یک چت خراب نباید کل اسکن را از کار بیندازد
                        log("⚠️ «" + title + "» اسکن نشد: " + e.getMessage());
                        found = new ArrayList<>();
                    }
                    files += found.size();
                    for (Track t : found) {
                        boolean isNew = true;
                        for (Track ex : buffer) {
                            if (ex.chatId == t.chatId && ex.messageId == t.messageId) {
                                isNew = false;
                                break;
                            }
                        }
                        if (isNew) buffer.add(t);
                    }
                    chats++;
                    scannedChats = chats;
                    if (chats == 1) ir.moeshakteam.moeshakmusic.util.NotifHelper.scanProgress(ctx, 0, target.size());
                    else if (chats % 10 == 0) ir.moeshakteam.moeshakmusic.util.NotifHelper.scanProgress(ctx, chats, target.size());
                    if (found.size() > 0) {
                        log("🎵 «" + title + "»: " + found.size() + " موزیک (مجموع: " + (target.size() + buffer.size()) + ")");
                    }
                    // ادغام تدریجی — نتایج زنده در UI دیده شوند
                    if (!buffer.isEmpty()) {
                        mergeNew(buffer, target);
                        buffer.clear();
                    }
                    cb.onProgress(target.size(), chats);
                }
                // ادغام یک‌جای بافر (بدون هزاران کپی COW) — بدون تکرار
                int added = mergeNew(buffer, target);
                sortNewestFirst(target);
                log("🏁 اسکن تمام شد: " + chats + " چت، " + files + " فایل صوتی، " + added + " تراک جدید → "
                        + (toLibrary ? "کتابخانه " + library.size() : "نتایج اسکن " + scanResults.size()));
                if (toLibrary) persistLibraryNow();
                notifyLibraryChanged();
                long dsec = (System.currentTimeMillis() - tStart) / 1000;
                ir.moeshakteam.moeshakmusic.util.NotifHelper.scanDone(ctx, chats, target.size(), (int) dsec);
                cb.onDone(target.size());
            } catch (Exception e) {
                log("⚠️ خطای اسکن: " + e.getMessage());
                cb.onDone(target.size());
            } finally {
                scanning = false;
            }
        });
    }

    /** افزودن تراک‌های بدون تکرار به مقصد — تعداد اضافه‌شده */
    private int mergeNew(List<Track> buffer, List<Track> target) {
        // O(n) با HashSet — قبلاً O(n²) بود و با کتابخانهٔ بزرگ (هزاران تراک) UI/اسکن را قفل می‌کرد
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Track t : library) existing.add(t.chatId + ":" + t.messageId);
        if (target != library) {
            for (Track t : scanResults) existing.add(t.chatId + ":" + t.messageId);
        }
        int added = 0;
        for (Track t : buffer) {
            if (existing.add(t.chatId + ":" + t.messageId)) {
                target.add(t);
                added++;
            }
        }
        return added;
    }

    private void sortNewestFirst(List<Track> list) {
        List<Track> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(b.date, a.date));
        list.clear();
        list.addAll(sorted);
    }

    /** انتقال همهٔ نتایج اسکن به کتابخانهٔ دائمی — تعداد اضافه‌شده */
    public int addScanResultsToLibrary() {
        int added = mergeNew(new ArrayList<>(scanResults), library);
        scanResults.clear();
        if (added > 0) {
            sortNewestFirst(library);
        }
        log("📚 " + added + " تراک از اسکن به کتابخانه اضافه شد (مجموع " + library.size() + ")");
        if (added > 0) persistLibraryNow();
        notifyLibraryChanged();
        return added;
    }

    /** پاک کردن نتایج اسکن (بدون افزودن به کتابخانه) */
    public void clearScanResults() {
        scanResults.clear();
    }

    /** 🗑 پاک کردن کل کتابخانه + نتایج — برای شروع اسکن از اول — تیم موشک */
    public void clearLibrary() {
        library.clear();
        scanResults.clear();
        persistLibraryNow();
        log("🗑 کتابخانه پاک شد — با اسکن از اول ساخته می‌شود");
        notifyLibraryChanged();
    }

    /** هوک UI بعد از تغییر کتابخانه */
    private void notifyLibraryChanged() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Runnable r = onLibraryChanged;
            if (r != null) {
                try { r.run(); } catch (Throwable ignored) {}
            }
            for (Runnable l : libraryListeners) {
                try { l.run(); } catch (Throwable ignored) {}
            }
        });
    }

    /** رفرش کتابخانه از دیسک + لاگ وضعیت */
    public void reloadLibraryFromDisk() {
        log("♻️ رفرش کتابخانه از دیسک… (الان " + library.size() + " موزیک)");
        List<Track> lib = LibraryPersistence.load(ctx);
        if (!lib.isEmpty()) {
            mergeNew(lib, library);
            sortNewestFirst(library);
        }
        log("📚 کتابخانه بعد از رفرش: " + library.size() + " موزیک"
                + (library.isEmpty() ? " — خالی است" : ""));
        notifyLibraryChanged();
    }

    // ---------- ذخیرهٔ دائمی: فقط فیوریت‌ها (+ پلی‌لیست‌ها در PlaylistStore) ----------

    /** ذخیرهٔ فیوریت‌ها از همهٔ منابع (کتابخانه + صف پخش + پلی‌لیست‌ها) */
    public void saveFavorites() {
        EXEC.execute(() -> {
            try {
                List<Track> out = new ArrayList<>();
                java.util.Set<String> keys = PlayerManager.FAVORITES;
                java.util.Set<String> seen = new HashSet<>();
                for (Track t : library) {
                    String k = PlayerManager.key(t);
                    if (keys.contains(k) && seen.add(k)) out.add(t);
                }
                for (Track t : PlayerManager.get(ctx).queue) {
                    String k = PlayerManager.key(t);
                    if (keys.contains(k) && seen.add(k)) out.add(t);
                }
                for (PlaylistStore.Playlist p : PlaylistStore.get(ctx).all()) {
                    for (Track t : p.tracks) {
                        String k = PlayerManager.key(t);
                        if (keys.contains(k) && seen.add(k)) out.add(t);
                    }
                }
                SavedTracksStore.save(ctx, out);
                log("❤️ فیوریت‌ها ذخیره شد (" + out.size() + ")");
            } catch (Exception e) {
                log("⚠️ ذخیرهٔ فیوریت: " + e.getMessage());
            }
        });
    }

    /**
     * بازیابی بعد از ورود (بعد از READY): fileId تلگرام بین ری‌استارت‌ها عوض می‌شود،
     * پس برای هر تراکِ ذخیره‌شده (فیوریت/پلی‌لیست) پیام اصلی دوباره گرفته می‌شود تا fileId تازه باشد.
     * تراک‌های بازیابی‌شده در کتابخانه قرار می‌گیرند — بقیهٔ کتابخانه با اسکن ساخته می‌شود.
     */
    public void restoreSaved() {
        if (restored) return;
        restored = true;
        EXEC.execute(() -> {
            try {
                int favCount = favsSaved.size();
                log("♻️ بازیابی فیوریت‌ها و پلی‌لیست‌ها (fileId تازه)…");
                Set<String> keys = new java.util.LinkedHashSet<>();
                List<Track> resolved = new ArrayList<>();
                // فیوریت‌ها
                for (Track old : favsSaved) {
                    keys.add(PlayerManager.key(old));
                    Track nt = refreshTrackFile(old);
                    if (nt != null) resolved.add(nt);
                    else resolved.add(old); // اگر پیام پاک شده باشد همان نسخهٔ قدیمی می‌ماند
                }
                PlayerManager.FAVORITES.clear();
                PlayerManager.FAVORITES.addAll(keys);
                // پلی‌لیست‌ها
                PlaylistStore ps = PlaylistStore.get(ctx);
                for (PlaylistStore.Playlist p : ps.all()) {
                    boolean changed = false;
                    for (int i = 0; i < p.tracks.size(); i++) {
                        Track nt = refreshTrackFile(p.tracks.get(i));
                        if (nt != null) {
                            p.tracks.set(i, nt);
                            changed = true;
                            resolved.add(nt);
                        }
                    }
                    if (changed) ps.save(p);
                }
                int added = mergeNew(resolved, library);
                sortNewestFirst(library);
                log("♻️ " + favCount + " فیوریت + پلی‌لیست‌ها بازیابی شد (" + added + " تراک در کتابخانه) — بقیه با اسکن 🔍");
                notifyLibraryChanged();
            } catch (Exception e) {
                log("⚠️ خطای بازیابی: " + e.getMessage());
            }
        });
    }

    /** گرفتن fileId تازهٔ یک تراک از پیام اصلی‌اش — null اگر پیام دیگر نباشد */
    private Track refreshTrackFile(Track old) {
        try {
            org.json.JSONObject msg = TdClient.syncRaw(new TdApi.GetMessage(old.chatId, old.messageId));
            if (msg == null || msg.optLong("id") == 0) return null;
            org.json.JSONObject content = msg.optJSONObject("content");
            if (content == null) return null;
            String type = content.optString("@type");
            // ویس — جدا ساخته می‌شود (اسکن عادی ویس نمی‌گیرد ولی فیوریت/پلی‌لیست ممکن است ویس باشد)
            if ("messageVoiceNote".equals(type)) {
                org.json.JSONObject vn = content.optJSONObject("voice_note");
                org.json.JSONObject vf = vn == null ? null : vn.optJSONObject("voice");
                if (vf == null) return null;
                Track t = new Track();
                t.chatId = old.chatId;
                t.messageId = old.messageId;
                t.date = msg.optInt("date");
                t.duration = vn.optInt("duration");
                t.title = old.title == null || old.title.isEmpty() ? "ویس — " + ir.moeshakteam.moeshakmusic.util.Ui.fmtDuration(t.duration) : old.title;
                t.performer = "پیام صوتی";
                t.fileId = vf.optInt("id");
                t.expectedSize = vf.optLong("expected_size", vf.optLong("size"));
                t.chatTitle = old.chatTitle == null || old.chatTitle.isEmpty() ? "ویس" : old.chatTitle;
                t.isVoice = true;
                t.thumbFileId = old.thumbFileId;
                t.chatPhotoFileId = old.chatPhotoFileId;
                return t;
            }
            org.json.JSONObject wrap = new org.json.JSONObject();
            org.json.JSONArray arr = new org.json.JSONArray();
            arr.put(msg);
            wrap.put("messages", arr);
            List<RawTrack> rs = extractAudioRaw(old.chatId, wrap);
            if (rs.isEmpty()) return null;
            RawTrack r = rs.get(0);
            String ct = old.chatTitle == null || old.chatTitle.isEmpty() ? "" : old.chatTitle;
            if (old.chatId == myUserId) ct = "Saved ⭐";
            Track nt = toTrack(r, ct);
            nt.thumbFileId = old.thumbFileId != 0 ? old.thumbFileId : nt.thumbFileId;
            Integer pf = chatPhotoFileIds.get(old.chatId);
            nt.chatPhotoFileId = pf != null ? pf : old.chatPhotoFileId;
            return nt;
        } catch (Throwable e) {
            return null;
        }
    }

    /** لغو اسکن جاری */
    public volatile boolean scanCancel = false;

    // ---------- دنبال‌شده‌ها: چک آهنگ جدید ----------

    /** آهنگ‌های جدید چت‌های دنبال‌شده — در صفحهٔ دنبال‌شده‌ها */
    public final List<Track> followedResults = new CopyOnWriteArrayList<>();
    /** هوک UI بعد از هر چک */
    public volatile Runnable onFollowedUpdate;

    public interface FollowDone { void onDone(boolean foundNew); }

    /**
     * فالو + دیپ‌اسکن کامل خودکار — چت فالو‌شده بلافاصله کامل خوانده می‌شود؛
     * آهنگ‌های جدید به scanResults می‌روند و knownIds بروز می‌شود (فقط آینده نوتیف بخورد).
     */
    public void followAndDeepScan(long chatId, String title) {
        FollowStore fs = FollowStore.get(ctx);
        if (fs.isFollowed(chatId)) return;
        // baseline = آهنگ‌های فعلی کتابخانه از این چت
        List<String> base = new ArrayList<>();
        for (Track t : library) if (t.chatId == chatId) base.add(t.chatId + ":" + t.messageId);
        fs.follow(chatId, title, base);
        log("🔔 فالو شد: " + title + " — دیپ‌اسکن کامل شروع می‌شود…");
        deepScanChat(chatId, new ScanListener() {
            @Override
            public void onProgress(int found, int chats) {
                for (ScanListener x : deepListeners) x.onProgress(found, chats);
            }

            @Override
            public void onDone(int added) {
                // ✅ نتایج این چت از «اسکن» به «کتابخانه» منتقل شوند — فالو کرده‌ای، باید در TRACKS باشد
                List<Track> mine = new ArrayList<>();
                for (Track t : scanResults) {
                    if (t.chatId == chatId) mine.add(t);
                }
                scanResults.removeAll(mine);
                mergeNew(mine, library);
                sortNewestFirst(library);
                notifyLibraryChanged();
                // knownIds = همهٔ آهنگ‌های دیده‌شده این چت — از این به بعد فقط جدید نوتیف می‌خورد
                Set<String> known = new HashSet<>(base);
                for (Track t : library) {
                    if (t.chatId == chatId) known.add(t.chatId + ":" + t.messageId);
                }
                fs.updateKnown(chatId, new ArrayList<>(known));
                persistLibraryNow();
                log("🔔 دیپ‌اسکن «" + title + "» تمام شد — " + added + " آهنگ به کتابخانه اضافه شد");
            }

            @Override
            public void onError(String msg) {
                log("⚠️ دیپ‌اسکن فالو: " + msg);
            }
        });
    }

    /** 🔧 فلاگ جدا — چک فالو نباید اسکن دستی را قفل کند */
    private volatile boolean followChecking = false;

    /** چک همهٔ چت‌های دنبال‌شده — نوتیف + followedResults */
    public void checkFollowed(FollowDone cb) {
        if (followChecking || scanning) {
            log("⏳ چک فالو عقب افتاد — اسکن/چک دیگری در جریان است");
            if (cb != null) cb.onDone(false);
            return;
        }
        List<FollowStore.Followed> fs = FollowStore.get(ctx).all();
        if (fs.isEmpty()) { if (cb != null) cb.onDone(false); return; }
        followChecking = true;
        EXEC.execute(() -> {
            boolean foundNew = false;
            try {
                log("🔔 چک دنبال‌شده‌ها (" + fs.size() + " چت)…");
                for (FollowStore.Followed f : fs) {
                    if (scanCancel) break;
                    TdApi.Chat chat = chatFromRaw(f.chatId);
                    if (chat.title != null && !chat.title.isEmpty() && !chat.title.equals(f.title)) {
                        FollowStore.get(ctx).updateTitle(f.chatId, chat.title);
                        f.title = chat.title;
                    }
                    int[] msgs = new int[1];
                    // کل چت — دیپ اسکن کامل (هرچقدر دارد)
                    List<Track> found = deepHistory(chat, msgs, (fnd, m) -> {
                        for (ScanListener x : deepListeners) x.onProgress(fnd, m);
                    });
                    List<Track> newOnes = new ArrayList<>();
                    for (Track t : found) {
                        if (!f.knownIds.contains(t.chatId + ":" + t.messageId)) newOnes.add(t);
                    }
                    if (!newOnes.isEmpty()) {
                        foundNew = true;
                        for (Track t : newOnes) {
                            boolean ex = false;
                            for (Track x : followedResults) if (x.sameAs(t)) { ex = true; break; }
                            if (!ex) followedResults.add(t);
                        }
                        // ✅ آهنگ‌های جدید به کتابخانه هم بیایند (TRACKS)
                        mergeNew(newOnes, library);
                        persistLibraryNow();
                        Set<String> all = new HashSet<>(f.knownIds);
                        for (Track t : found) all.add(t.chatId + ":" + t.messageId);
                        FollowStore.get(ctx).updateKnown(f.chatId, new ArrayList<>(all));
                        List<String> titles = new ArrayList<>();
                        for (int i = 0; i < Math.min(3, newOnes.size()); i++) titles.add(newOnes.get(i).title);
                        String chTitle = chat.title == null || chat.title.isEmpty() ? f.title : chat.title;
                        ir.moeshakteam.moeshakmusic.util.NotifHelper.newTracks(ctx, chTitle, titles);
                        log("🎵 جدید از «" + chTitle + "»: " + newOnes.size());
                    }
                }
                Runnable r = onFollowedUpdate;
                if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            } catch (Exception e) {
                log("⚠️ چک دنبال‌شده: " + e.getMessage());
            } finally {
                followChecking = false;
            }
            if (cb != null) cb.onDone(foundNew);
        });
    }

    public void cancelScan() {
        scanCancel = true;
        log("⏹ درخواست لغو اسکن…");
    }

    /** لود همهٔ چت‌های اصلی + آرشیو با عنوان — استخراج مستقیم JSON (ضدخطا) */
    private List<TdApi.Chat> loadAllChats() throws Exception {
        List<TdApi.Chat> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (TdApi.ChatList cl : new TdApi.ChatList[]{new TdApi.ChatListMain(), new TdApi.ChatListArchive()}) {
            while (true) {
                try {
                    TdClient.sync(new TdApi.LoadChats(cl, 100));
                } catch (TdClient.TdError e) {
                    break;
                }
            }
            org.json.JSONObject resp = TdClient.syncRaw(new TdApi.GetChats(cl, 5000));
            org.json.JSONArray idsArr = resp.optJSONArray("chat_ids");
            if (idsArr == null) continue;
            for (int i = 0; i < idsArr.length(); i++) {
                long id = idsArr.optLong(i);
                if (!seen.add(id)) continue;
                out.add(chatFromRaw(id));
            }
        }
        return out;
    }

    /** ساخت Chat از raw JSON — بدون کدک */
    private TdApi.Chat chatFromRaw(long id) {
        String title = "چت " + id;
        TdApi.ChatType type = null;
        try {
            org.json.JSONObject cj = TdClient.syncRaw(new TdApi.GetChat(id));
            String t = cj.optString("title", "");
            if (!t.isEmpty()) {
                title = t;
                chatTitles.put(id, t);
            }
            org.json.JSONObject tj = cj.optJSONObject("type");
            if (tj != null) {
                String tt = tj.optString("@type", "");
                if ("chatTypePrivate".equals(tt)) type = new TdApi.ChatTypePrivate(tj.optLong("user_id"));
                else if ("chatTypeBasicGroup".equals(tt)) type = new TdApi.ChatTypeBasicGroup(tj.optLong("basic_group_id"));
                else if ("chatTypeSupergroup".equals(tt)) type = new TdApi.ChatTypeSupergroup(tj.optLong("supergroup_id"), tj.optBoolean("is_channel", false));
                else if ("chatTypeSecret".equals(tt)) type = new TdApi.ChatTypeSecret(tj.optInt("secret_chat_id"), tj.optLong("user_id"));
            }
            // عکس کوچک چت — برای تامبنیل تراک‌های این چت
            org.json.JSONObject ph = cj.optJSONObject("photo");
            if (ph != null) {
                org.json.JSONObject sm = ph.optJSONObject("small");
                if (sm != null) {
                    int pf = sm.optInt("id");
                    if (pf > 0) chatPhotoFileIds.put(id, pf);
                }
            }
        } catch (Exception ignored) {
        }
        TdApi.Chat c = new TdApi.Chat();
        c.id = id;
        c.title = title;
        c.type = type;
        return c;
    }

    /** تراک استخراج‌شده از JSON خام */
    private static class RawTrack {
        long msgId, chatId;
        int date, duration, fileId, thumbFileId;
        long size;
        String title = "", performer = "";
        byte[] artMini;
    }

    /**
     * اسکن یک چت — موتور v4: GetChatHistory مستقیم، کل تاریخچهٔ چت بدون سقف.
     * deep=true → سرچ مکمل هم اجرا می‌شود (حالت «همهٔ چت‌ها»).
     * هیچ reflection/codec ای در مسیر نیست — چیزی که تلگرام می‌فرستد همان است که می‌خوانیم.
     */
    private List<Track> scanChatHistory(TdApi.Chat chat, int[] msgsOut, boolean deep) {
        // کل تاریخچهٔ چت — همیشه کامل (هیچ سقفی روی پیام‌ها)
        List<Track> out = deepHistory(chat, msgsOut, null);
        // سرچ مکمل فقط در حالت عمیق (روی چت‌های بدون فایل، سرچِ اضافه فقط کند می‌کند)
        if (out.isEmpty() && deep) {
            // سرچ فقط به‌عنوان مکمل
            String chatTitle = chat.title == null ? "" : chat.title;
            Set<String> seen = new HashSet<>();
            for (TdApi.SearchMessagesFilter filter : new TdApi.SearchMessagesFilter[]{
                    new TdApi.SearchMessagesFilterAudio()}) {
                org.json.JSONObject f;
                try {
                    f = TdClient.syncRaw(new TdApi.SearchChatMessages(chat.id, null, "", null, 0L, 0, 50, filter));
                } catch (Exception e) {
                    break;
                }
                for (RawTrack r : extractAudioRaw(chat.id, f)) {
                    if (seen.add(chat.id + ":" + r.msgId)) {
                        Track tt = toTrack(r, chatTitle);
                        Integer pf = chatPhotoFileIds.get(chat.id);
                        if (pf != null) tt.chatPhotoFileId = pf;
                        out.add(tt);
                    }
                }
                if (!out.isEmpty()) break;
            }
        }
        if (!out.isEmpty()) foundChats++;
        return out;
    }

    /** استخراج فایل‌های صوتی از پاسخ خام (messages[]) — بدون هیچ تبدیل میانی */
    private List<RawTrack> extractAudioRaw(long chatId, org.json.JSONObject resp) {
        List<RawTrack> out = new ArrayList<>();
        org.json.JSONArray arr = resp.optJSONArray("messages");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            org.json.JSONObject content = m.optJSONObject("content");
            if (content == null) continue;
            String type = content.optString("@type");
            RawTrack t = new RawTrack();
            t.chatId = chatId;
            t.msgId = m.optLong("id");
            t.date = m.optInt("date");
            if ("messageAudio".equals(type)) {
                org.json.JSONObject a = content.optJSONObject("audio");
                if (a == null) continue;
                org.json.JSONObject f = a.optJSONObject("audio");
                if (f == null) continue;
                t.title = a.optString("title", "");
                t.performer = a.optString("performer", "");
                if (t.title.isEmpty()) {
                    String fn = a.optString("file_name", "");
                    t.title = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : (fn.isEmpty() ? "بی‌نام" : fn);
                }
                t.duration = a.optInt("duration");
                t.fileId = f.optInt("id");
                t.size = f.optLong("expected_size", f.optLong("size"));
                // کاور خود موزیک: تامبنیل آلبوم (باکیفیت) + مینی‌تامب
                org.json.JSONObject coverThumb = a.optJSONObject("album_cover_thumbnail");
                if (coverThumb != null) {
                    org.json.JSONObject cf = coverThumb.optJSONObject("file");
                    if (cf != null) t.thumbFileId = cf.optInt("id");
                }
                org.json.JSONObject mini = a.optJSONObject("album_cover_minithumbnail");
                if (mini != null) {
                    try {
                        t.artMini = android.util.Base64.decode(mini.optString("data", ""), android.util.Base64.NO_WRAP);
                    } catch (Exception ignored) {
                    }
                }
                out.add(t);
            } else if ("messageDocument".equals(type)) {
                org.json.JSONObject d = content.optJSONObject("document");
                if (d == null) continue;
                String mime = d.optString("mime_type", "");
                if (!mime.startsWith("audio/")) continue;
                org.json.JSONObject f = d.optJSONObject("document");
                if (f == null) continue;
                String fn = d.optString("file_name", "");
                t.title = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : (fn.isEmpty() ? "فایل صوتی" : fn);
                t.performer = "فایل";
                t.fileId = f.optInt("id");
                t.size = f.optLong("expected_size", f.optLong("size"));
                // کاور فایل صوتی (thumbnail سند) — اگر فرستنده گذاشته باشد
                org.json.JSONObject dThumb = d.optJSONObject("thumbnail");
                if (dThumb != null) {
                    org.json.JSONObject df = dThumb.optJSONObject("file");
                    if (df != null) t.thumbFileId = df.optInt("id");
                }
                out.add(t);
            }
        }
        return out;
    }

    private Track toTrack(RawTrack r, String chatTitle) {
        Track t = new Track();
        t.chatId = r.chatId;
        t.messageId = r.msgId;
        t.date = r.date;
        t.title = r.title == null || r.title.isEmpty() ? "بی‌نام" : r.title;
        t.performer = r.performer == null || r.performer.isEmpty() ? "هنرمند ناشناس" : r.performer;
        t.duration = r.duration;
        t.fileId = r.fileId;
        t.expectedSize = r.size;
        t.chatTitle = chatTitle;
        t.artMini = r.artMini;
        t.thumbFileId = r.thumbFileId;
        return t;
    }

    /** شناسهٔ کاربر خودم (برای تشخیص Saved Messages) */
    public volatile long myUserId;

    /** اسکن عمیق دستی یک چت — از مرور چت‌ها (کل تاریخچه، بدون سقف) */
    public void deepScanChat(long chatId, ScanListener cb) {
        if (scanning) {
            Ui.toast(ctx, ctx.getString(R.string.scan_already));
            cb.onDone(0);
            return;
        }
        scanning = true;
        scanCancel = false;
        EXEC.execute(() -> {
            try {
                TdApi.Chat chat = (TdApi.Chat) TdClient.sync(new TdApi.GetChat(chatId));
                if (chat == null) {
                    cb.onDone(0);
                    return;
                }
                log("🔎 اسکن عمیق «" + (chat.title == null ? "بدون‌نام" : chat.title) + "» (کل تاریخچه)…");
                int[] msgs = new int[1];
                List<Track> found = deepHistory(chat, msgs,
                        (fnd, m) -> { for (Tg.ScanListener x : deepListeners) x.onProgress(fnd, m); });
                int added = mergeNew(found, scanResults);
                log("🔎 اسکن عمیق تمام شد: " + msgs[0] + " پیام → " + added + " موزیک جدید (در بخش اسکن)");
                cb.onDone(added);
            } catch (Exception e) {
                log("⚠️ خطای اسکن عمیق: " + e.getMessage());
                cb.onDone(0);
            } finally {
                scanning = false;
            }
        });
    }

    /** تاریخچهٔ عمیق با استخراج raw: کل تاریخچهٔ چت بدون سقف */
    public interface DeepProgress { void onProgress(int found, int msgs); }

    /** اسکن تاریخچهٔ چت — کل تاریخچه، هرچقدر که دارد (بدون سقف) — تیم موشک */
    private List<Track> deepHistory(TdApi.Chat chat, int[] msgsOut, DeepProgress progress) {
        List<Track> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String chatTitle = chat.title == null ? "" : chat.title;
        if (chat.id == myUserId) chatTitle = "Saved ⭐";
        Integer photoFileId = chatPhotoFileIds.get(chat.id);
        long from = 0L;
        while (true) {
            if (scanCancel) break;
            org.json.JSONObject h;
            try {
                h = TdClient.syncRaw(new TdApi.GetChatHistory(chat.id, from, 0, 100, false));
            } catch (Exception e) {
                break;
            }
            org.json.JSONArray arr = h.optJSONArray("messages");
            int n = arr == null ? 0 : arr.length();
            if (n == 0) break;  // دیگر پیامی نمانده
            msgsOut[0] += n;
            for (RawTrack r : extractAudioRaw(chat.id, h)) {
                if (seen.add(chat.id + ":" + r.msgId)) {
                    Track tt = toTrack(r, chatTitle);
                    if (photoFileId != null) tt.chatPhotoFileId = photoFileId;
                    out.add(tt);
                }
            }
            if (progress != null) progress.onProgress(out.size(), msgsOut[0]);
            if (msgsOut[0] % 200 == 0) {
                log("   … «" + chatTitle + "»: " + msgsOut[0] + " پیام، " + out.size() + " فایل");
            }
            long lastId = arr.optJSONObject(n - 1).optLong("id");
            if (lastId == from || lastId == 0) break;  // سدّ حلقهٔ بی‌نهایت
            from = lastId;
        }
        return out;
    }

    /** اسکن عمیق سیو — کل تاریخچهٔ Saved Messages */
    public void deepScanSaved(ScanListener cb) {
        if (scanning) {
            Ui.toast(ctx, ctx.getString(R.string.scan_already));
            cb.onDone(0);
            return;
        }
        scanning = true;
        scanCancel = false;
        EXEC.execute(() -> {
            try {
                TdApi.User me = (TdApi.User) TdClient.sync(new TdApi.GetMe());
                myUserId = me.id;
                TdApi.Chat saved = (TdApi.Chat) TdClient.sync(new TdApi.GetChat(me.id));
                if (saved == null) {
                    cb.onDone(0);
                    return;
                }
                log("⭐ اسکن عمیق Saved Messages…");
                int[] msgs = new int[1];
                List<Track> found = deepHistory(saved, msgs, null);
                int added = mergeNew(found, scanResults);
                log("⭐ اسکن سیو تمام شد: " + msgs[0] + " پیام → " + added + " موزیک جدید (در بخش اسکن)");
                cb.onDone(added);
            } catch (Exception e) {
                log("⚠️ خطای اسکن سیو: " + e.getMessage());
                cb.onDone(0);
            } finally {
                scanning = false;
            }
        });
    }

    // ---------- لاگ زنده ----------

    /** خطوط لاگ با زمان — حداکثر ۵۰۰ خط */
    public static final ArrayDeque<String> logLines = new ArrayDeque<>();

    /** ثبت یک خط لاگ با زمان */
    public static void log(String line) {
        synchronized (logLines) {
            String ts = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString();
            logLines.addLast(ts + "  " + line);
            while (logLines.size() > 500) logLines.removeFirst();
        }
        android.util.Log.i("Tg", line);
    }

    /** همهٔ خطوط لاگ به‌صورت متن + آخرین کرش (اگه باشه) */
    public static String dumpLog(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), "last_crash.txt");
            if (f.exists()) {
                sb.append("════════ آخرین کرش ════════\n");
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
                String line;
                int n = 0;
                while ((line = br.readLine()) != null && n < 40) {
                    sb.append(line).append('\n');
                    n++;
                }
                br.close();
                sb.append("══════════════════════════\n\n");
            }
        } catch (Throwable ignored) {
        }
        synchronized (logLines) {
            for (String l : logLines) sb.append(l).append('\n');
        }
        return sb.toString();
    }



    public List<Track> search(String q) {
        if (q == null || q.trim().isEmpty()) return library;
        String needle = q.toLowerCase().trim();
        List<Track> out = new ArrayList<>();
        for (Track t : library) {
            if ((t.title + " " + t.performer + " " + t.chatTitle).toLowerCase().contains(needle)) out.add(t);
        }
        return out;
    }

    /** تعداد چت‌های اسکن‌شده برای نمایش در UI */
    public volatile int scannedChats;
    /** تعداد چت‌هایی که موزیک داشتن */
    public volatile int foundChats;
    /** تعداد خطاهای سرچ (برای دیباگ) */
    public volatile int searchErrors;

    // ---------- دانلود کامل (برای کش و fallback) ----------

    private void onFileUpdate(TdApi.File f) {
        DownloadTask t = downloads.get(f.id);
        if (t == null || t.done) return;
        if (f.local != null && f.local.isDownloadingCompleted && f.local.path != null) {
            t.done = true;
            downloads.remove(f.id);
            t.cb.onDone(f.local.path);
        } else if (f.local != null) {
            long exp = f.expectedSize > 0 ? f.expectedSize : f.size;
            int pct = exp > 0 ? (int) Math.min(99, f.local.downloadedSize * 100 / exp) : 0;
            t.cb.onProgress(pct);
        }
    }

    /** دانلود با استخراج مستقیم JSON (ضدخطا) — مسیر محلی فایل رو برمی‌گردونه */
    public void download(int fileId, long expectedSize, DownloadListener cb) {
        DownloadTask t = new DownloadTask(cb);
        downloads.put(fileId, t);
        DL_EXEC.execute(() -> {
            try {
                org.json.JSONObject f = TdClient.syncRaw(new TdApi.GetFile(fileId));
                org.json.JSONObject local = f.optJSONObject("local");
                if (local != null && local.optBoolean("is_downloading_completed") && !local.optString("path").isEmpty()) {
                    t.done = true;
                    downloads.remove(fileId);
                    cb.onDone(local.optString("path"));
                    return;
                }
                long size = f.optLong("expected_size", f.optLong("size", expectedSize));
                long limit = size > 0 ? size : (1L << 30);
                TdClient.sendRaw(new TdApi.DownloadFile(fileId, 32, 0L, limit, false), r -> {
                    if ("error".equals(r.optString("@type")) && !t.done) {
                        t.done = true;
                        downloads.remove(fileId);
                        cb.onError(r.optString("message"));
                    }
                });
                // poll مسیر تا اتمام
                long deadline = System.currentTimeMillis() + 10 * 60_000L;
                while (!t.done && System.currentTimeMillis() < deadline) {
                    Thread.sleep(500);
                    if (t.done) return;
                    org.json.JSONObject f2 = TdClient.syncRaw(new TdApi.GetFile(fileId));
                    org.json.JSONObject loc = f2.optJSONObject("local");
                    if (loc != null) {
                        long done = loc.optLong("downloaded_size");
                        int pct = size > 0 ? (int) Math.min(99, done * 100 / size) : 0;
                        t.cb.onProgress(pct);
                        if (loc.optBoolean("is_downloading_completed") && !loc.optString("path").isEmpty()) {
                            t.done = true;
                            downloads.remove(fileId);
                            cb.onDone(loc.optString("path"));
                            return;
                        }
                    }
                }
                if (!t.done) {
                    t.done = true;
                    downloads.remove(fileId);
                    cb.onError("download timeout");
                }
            } catch (Exception e) {
                if (!t.done) {
                    t.done = true;
                    downloads.remove(fileId);
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    /** کش fileId زندهٔ این نشست — fileId در TDLib بین نشست‌ها عوض می‌شود */
    private final java.util.Map<String, Integer> liveFileIds = new java.util.concurrent.ConcurrentHashMap<>();

    /** رفرش fileId از پیام — برای تراک‌های قدیمی کتابخانه که fileId شان stale شده و «Not Found» می‌دهند */
    private boolean refreshFileId(Track t) {
        if (t.chatId == 0 || t.messageId == 0) return false;
        String key = t.chatId + ":" + t.messageId;
        Integer cached = liveFileIds.get(key);
        if (cached != null && cached > 0) {
            t.fileId = cached;
            return true;
        }
        try {
            org.json.JSONObject m = TdClient.syncRaw(new TdApi.GetMessage(t.chatId, t.messageId));
            if (m == null || "error".equals(m.optString("@type"))) return false;
            org.json.JSONObject content = m.optJSONObject("content");
            if (content == null) return false;
            String type = content.optString("@type", "");
            int fid = 0;
            long size = 0;
            org.json.JSONObject f = null;
            if ("messageAudio".equals(type)) {
                org.json.JSONObject a = content.optJSONObject("audio");
                if (a != null) f = a.optJSONObject("audio");
            } else if ("messageDocument".equals(type)) {
                org.json.JSONObject d = content.optJSONObject("document");
                if (d != null) f = d.optJSONObject("document");
            } else if ("messageVoiceNote".equals(type)) {
                org.json.JSONObject v = content.optJSONObject("voice_note");
                if (v != null) f = v.optJSONObject("voice");
            }
            if (f != null) {
                fid = f.optInt("id");
                size = f.optLong("expected_size", f.optLong("size", 0));
            }
            if (fid <= 0) return false;
            t.fileId = fid;
            if (size > 0) t.expectedSize = size;
            liveFileIds.put(key, fid);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * دانلود یک تراک با نمایش زنده در بخش دانلودها — fileId را از پیام تازه می‌گیرد
     * (ضدِ «Not Found» روی تراک‌های قدیمی کتابخانه).
     */
    public void downloadTrack(final Track t, final DownloadListener cb) {
        DL_EXEC.execute(() -> {
            // 💥 fileId ممکن است مال نشست قبلی باشد — از پیام، تازه بگیر
            if (!refreshFileId(t)) {
                if (cb != null) cb.onError("Not Found");
                return;
            }
            final ActiveDownload ad = new ActiveDownload(t.fileId,
                    t.title == null || t.title.isEmpty() ? "بی‌نام" : t.title,
                    t.chatTitle == null ? "" : t.chatTitle, t.expectedSize);
            activeDownloads.put(t.fileId, ad);
            notifyDownloadsChanged();
            download(t.fileId, t.expectedSize, new DownloadListener() {
                @Override
                public void onProgress(int pct) {
                    int before = ad.pct;
                    ad.pct = pct;
                    if (cb != null) cb.onProgress(pct);
                    if (pct != before) notifyDownloadsChanged();
                }

                @Override
                public void onDone(String path) {
                    ad.done = true;
                    activeDownloads.remove(t.fileId);
                    notifyDownloadsChanged();
                    if (cb != null) cb.onDone(path);
                }

                @Override
                public void onError(String msg) {
                    ad.error = true;
                    activeDownloads.remove(t.fileId);
                    notifyDownloadsChanged();
                    if (cb != null) cb.onError(msg);
                }
            });
        });
    }

    /** لغو دانلود فعال (از بخش دانلودها) — هم دانلود و هم ردیابی‌اش حذف می‌شود */
    public void cancelDownloadTrack(int fileId) {
        cancelDownload(fileId);
        if (activeDownloads.remove(fileId) != null) notifyDownloadsChanged();
    }

    public void cancelDownload(int fileId) {
        DownloadTask t = downloads.remove(fileId);
        if (t != null) t.done = true;
        TdClient.send(new TdApi.CancelDownloadFile(fileId, false), r -> {
        });
    }

    // ---------- چانک‌خوانی استریم (بازهای دلخواه از فایل ریموت) ----------

    /**
     * خواندن یک بازه از فایل تلگرام بدون دانلود کل فایل.
     * فقط از thread پس‌زمینه صدا زده بشه (thread لودر ExoPlayer).
     */
    public byte[] readRemoteChunk(int fileId, long offset, int count) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("readRemoteChunk on main thread");
        // ۱) اگر فایل کاملاً کش شده — از دیسک بخون
        org.json.JSONObject f = TdClient.syncRaw(new TdApi.GetFile(fileId));
        org.json.JSONObject local = f.optJSONObject("local");
        if (local != null && local.optBoolean("is_downloading_completed") && !local.optString("path").isEmpty()) {
            return readLocalChunk(local.optString("path"), offset, count);
        }
        // ۲) درخواست همین بازه از سرور
        try {
            TdClient.syncRaw(new TdApi.DownloadFile(fileId, 32, offset, (long) count, false));
        } catch (TdClient.TdError ignored) {
        }
        long deadline = System.currentTimeMillis() + 30_000L;
        boolean ready = false;
        String path = null;
        while (System.currentTimeMillis() < deadline) {
            f = TdClient.syncRaw(new TdApi.GetFile(fileId));
            local = f.optJSONObject("local");
            if (local == null) break;
            if (local.optBoolean("is_downloading_completed")) {
                ready = true;
                path = local.optString("path");
                break;
            }
            if (local.optLong("download_offset") == offset && local.optLong("downloaded_prefix_size") >= count) {
                ready = true;
                break;
            }
            Thread.sleep(120);
        }
        if (!ready) throw new IOException("chunk download timeout");
        // ۳) ReadFilePart با raw
        for (int attempt = 0; attempt < 12; attempt++) {
            try {
                org.json.JSONObject d = TdClient.syncRaw(new TdApi.ReadFilePart(fileId, offset, (long) count));
                String b64 = d.optString("data", "");
                if (!b64.isEmpty()) {
                    byte[] out = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
                    if (out.length > 0) return out;
                }
            } catch (TdClient.TdError ignored) {
            }
            Thread.sleep(250);
        }
        // ۴) آخرین راه: اگر فایل کامل شد از دیسک بخون
        if (path != null) return readLocalChunk(path, offset, count);
        f = TdClient.syncRaw(new TdApi.GetFile(fileId));
        local = f.optJSONObject("local");
        if (local != null && local.optBoolean("is_downloading_completed") && !local.optString("path").isEmpty()) {
            return readLocalChunk(local.optString("path"), offset, count);
        }
        throw new IOException("readFilePart failed");
    }

    private byte[] readLocalChunk(String path, long offset, int count) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(path, "r");
        try {
            long len = raf.length();
            if (offset >= len) return new byte[0];
            raf.seek(offset);
            int n = (int) Math.min(count, len - offset);
            byte[] out = new byte[n];
            raf.readFully(out);
            return out;
        } finally {
            raf.close();
        }
    }
}