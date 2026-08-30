# -*- coding: utf-8 -*-
# پچ v5.9.5 — ۴ فیکس: کرش پروکسی، قفل اسکن، پایداری کتابخانه، اسکرول دنبال‌شده‌ها
import io

# ═══ ۱) ProxyFragment — کرش requireContext در thread پینگ ═══
p = 'app/src/main/java/ir/moeshakteam/moeshakmusic/ui/ProxyFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''    private void pingAll() {
        List<Prefs.ProxyEntry> list = Prefs.get(requireContext()).proxies();
        if (list.isEmpty()) return;
        Ui.toast(requireContext(), getString(R.string.proxy_pinging));
        exec.execute(() -> {
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                try {
                    Prefs.ProxyEntry e = list.get(i);
                    TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                    TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                    e.pingMs = (int) Math.round(s.seconds * 1000);
                } catch (Exception ex) {
                    list.get(idx).pingMs = -2; // خطا
                }
                // 🔧 فیکس: نتیجه پینگ باید ذخیره بشه وگرنه رفرش، مقادیر قدیمی رو نشون می‌داد
                Prefs.get(requireContext()).saveProxies(list);
                main.post(this::refreshList);
            }
            main.post(() -> Ui.toast(requireContext(), getString(R.string.proxy_ping_done)));
        });
    }''','''    private void pingAll() {
        // 💥 ضدکرش: کانتکست یک‌بار گرفته شود، thread ها به requireContext دست نزنند
        android.content.Context appCtx = requireContext().getApplicationContext();
        List<Prefs.ProxyEntry> list = Prefs.get(appCtx).proxies();
        if (list.isEmpty()) return;
        Ui.toast(requireContext(), getString(R.string.proxy_pinging));
        exec.execute(() -> {
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                try {
                    Prefs.ProxyEntry e = list.get(i);
                    TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                    TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                    e.pingMs = (int) Math.round(s.seconds * 1000);
                } catch (Exception ex) {
                    list.get(idx).pingMs = -2; // خطا
                }
                Prefs.get(appCtx).saveProxies(list);
                main.post(this::refreshList);
            }
            main.post(() -> {
                if (isAdded()) Ui.toast(requireContext(), getString(R.string.proxy_ping_done));
            });
        });
    }''')
# pingOne هم همین
s = s.replace('''        exec.execute(() -> {
            try {
                Prefs.ProxyEntry e = list.get(idx);
                TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                e.pingMs = (int) Math.round(s.seconds * 1000);
            } catch (Exception ex) {
                list.get(idx).pingMs = -2;
            }
            Prefs.get(requireContext()).saveProxies(list);
            main.post(this::refreshList);
        });''','''        final android.content.Context appCtx = requireContext().getApplicationContext();
        exec.execute(() -> {
            try {
                Prefs.ProxyEntry e = list.get(idx);
                TdApi.Proxy pr = new TdApi.Proxy(e.server, e.port, new TdApi.ProxyTypeMtproto(e.secret));
                TdApi.Seconds s = (TdApi.Seconds) TdClient.sync(new TdApi.PingProxy(pr));
                e.pingMs = (int) Math.round(s.seconds * 1000);
            } catch (Exception ex) {
                list.get(idx).pingMs = -2;
            }
            Prefs.get(appCtx).saveProxies(list);
            main.post(this::refreshList);
        });''')
io.open(p, 'w', encoding='utf-8').write(s)
print('1. ProxyFragment anti-crash ✓')

# ═══ ۲) Tg — فلاگ جدا برای چک فالو + پایداری کتابخانه ═══
p = 'app/src/main/java/ir/moeshakteam/moeshakmusic/data/Tg.java'
s = io.open(p, encoding='utf-8').read()

# ۲-الف) فلاگ followChecking جدا
s = s.replace('''    public interface FollowDone { void onDone(boolean foundNew); }

    /** چک همهٔ چت‌های دنبال‌شده — نوتیف + followedResults */
    public void checkFollowed(FollowDone cb) {
        if (scanning) { if (cb != null) cb.onDone(false); return; }''','''    public interface FollowDone { void onDone(boolean foundNew); }

    /** 🔧 فلاگ جدا — چک فالو نباید اسکن دستی را قفل کند */
    private volatile boolean followChecking = false;

    /** چک همهٔ چت‌های دنبال‌شده — نوتیف + followedResults */
    public void checkFollowed(FollowDone cb) {
        if (followChecking || scanning) {
            log("⏳ چک فالو عقب افتاد — اسکن/چک دیگری در جریان است");
            if (cb != null) cb.onDone(false);
            return;
        }''')
s = s.replace('''        if (fs.isEmpty()) { if (cb != null) cb.onDone(false); return; }
        scanning = true;
        EXEC.execute(() -> {
            boolean foundNew = false;
            try {
                log("🔔 چک دنبال‌شده‌ها (" + fs.size() + " چت)…");''','''        if (fs.isEmpty()) { if (cb != null) cb.onDone(false); return; }
        followChecking = true;
        EXEC.execute(() -> {
            boolean foundNew = false;
            try {
                log("🔔 چک دنبال‌شده‌ها (" + fs.size() + " چت)…");''')
s = s.replace('''            } catch (Exception e) {
                log("⚠️ چک دنبال‌شده: " + e.getMessage());
            } finally {
                scanning = false;
            }
            if (cb != null) cb.onDone(foundNew);
        });
    }''','''            } catch (Exception e) {
                log("⚠️ چک دنبال‌شده: " + e.getMessage());
            } finally {
                followChecking = false;
            }
            if (cb != null) cb.onDone(foundNew);
        });
    }''')

# ۲-ب) library دائمی — ذخیره/لود (فقط آهنگ‌هایی که صریحاً به کتابخانه اضافه شده‌اند)
s = s.replace('''    private Tg(Context c) {
        ctx = c.getApplicationContext();
        prefs = Prefs.get(ctx);
        // فقط فیوریت‌ها و پلی‌لیست‌ها دائمی‌اند — کل کتابخانه با هر اسکن دوباره ساخته می‌شود
        favsSaved = SavedTracksStore.load(ctx);
        for (Track t : favsSaved) PlayerManager.FAVORITES.add(PlayerManager.key(t));
        if (!favsSaved.isEmpty())
            log("❤️ " + favsSaved.size() + " فیوریت از دیسک بازیابی شد (پس از ورود، fileId تازه گرفته می‌شود)");
    }''','''    private Tg(Context c) {
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
    }''')

# ذخیره بعد از ادغام‌های مهم
s = s.replace('''                log("📚 " + added + " تراک از اسکن به کتابخانه اضافه شد (مجموع " + library.size() + ")");
        notifyLibraryChanged();
        return added;''','''                log("📚 " + added + " تراک از اسکن به کتابخانه اضافه شد (مجموع " + library.size() + ")");
        if (added > 0) persistLibraryNow();
        notifyLibraryChanged();
        return added;''')
s = s.replace('''        log("📚 " + added + " تراک از اسکن به کتابخانه اضافه شد (مجموع " + library.size() + ")");
        notifyLibraryChanged();
        return added;''','''        log("📚 " + added + " تراک از اسکن به کتابخانه اضافه شد (مجموع " + library.size() + ")");
        if (added > 0) persistLibraryNow();
        notifyLibraryChanged();
        return added;''')
# followAndDeepScan onDone ذخیره
s = s.replace('''                fs.updateKnown(chatId, new ArrayList<>(known));
                log("🔔 دیپ‌اسکن «" + title + "» تمام شد — " + added + " آهنگ به کتابخانه اضافه شد");''','''                fs.updateKnown(chatId, new ArrayList<>(known));
                persistLibraryNow();
                log("🔔 دیپ‌اسکن «" + title + "» تمام شد — " + added + " آهنگ به کتابخانه اضافه شد");''')
# checkFollowed merge ذخیره
s = s.replace('''                        // ✅ آهنگ‌های جدید به کتابخانه هم بیایند (TRACKS)
                        mergeNew(newOnes, library);''','''                        // ✅ آهنگ‌های جدید به کتابخانه هم بیایند (TRACKS)
                        mergeNew(newOnes, library);
                        persistLibraryNow();''')
# clearLibrary ذخیره
s = s.replace('''    public void clearLibrary() {
        library.clear();
        scanResults.clear();''','''    public void clearLibrary() {
        library.clear();
        scanResults.clear();
        persistLibraryNow();''')

io.open(p, 'w', encoding='utf-8').write(s)
print('2. Tg followChecking + persistence ✓')

# ═══ ۳) LibraryPersistence — کلاس جدید ═══
io.open('app/src/main/java/ir/moeshakteam/moeshakmusic/data/LibraryPersistence.java', 'w', encoding='utf-8').write('''package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ذخیرهٔ کتابخانهٔ کاربر (آهنگ‌هایی که صریحاً اضافه شده‌اند) — تیم موشک */
public final class LibraryPersistence {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LibPersist");
        t.setDaemon(true);
        return t;
    });

    private LibraryPersistence() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "library.json");
    }

    public static List<Track> load(Context ctx) {
        List<Track> out = new ArrayList<>();
        try {
            File f = file(ctx);
            if (!f.exists()) return out;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            //noinspection ResultOfMethodCallIgnored
            fis.read(buf);
            fis.close();
            JSONArray arr = new JSONArray(new String(buf, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                try {
                    Track t = parse(arr.getJSONObject(i));
                    if (t != null && t.fileId != 0) out.add(t);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(Context ctx, List<Track> lib) {
        final String json = toJson(lib);
        final File f = file(ctx.getApplicationContext());
        EXEC.execute(() -> {
            try {
                File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmp);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.close();
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(f);
            } catch (Throwable ignored) {
            }
        });
    }

    private static String toJson(List<Track> lib) {
        try {
            JSONArray arr = new JSONArray();
            for (Track t : lib) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("c", t.chatId);
                    o.put("m", t.messageId);
                    o.put("d", t.date);
                    o.put("t", t.title);
                    o.put("p", t.performer);
                    o.put("du", t.duration);
                    o.put("f", t.fileId);
                    o.put("s", t.expectedSize);
                    o.put("tf", t.thumbFileId);
                    o.put("cf", t.chatPhotoFileId);
                    o.put("ct", t.chatTitle);
                    o.put("v", t.isVoice);
                    if (t.artMini != null)
                        o.put("am", android.util.Base64.encodeToString(t.artMini, android.util.Base64.NO_WRAP));
                    arr.put(o);
                } catch (Throwable ignored) {
                }
            }
            return arr.toString();
        } catch (Throwable ignored) {
            return "[]";
        }
    }

    private static Track parse(JSONObject o) {
        try {
            Track t = new Track();
            t.chatId = o.optLong("c");
            t.messageId = o.optLong("m");
            t.date = o.optInt("d");
            t.title = o.optString("t", "بی‌نام");
            t.performer = o.optString("p", "");
            t.duration = o.optInt("du");
            t.fileId = o.optInt("f");
            t.expectedSize = o.optLong("s");
            t.thumbFileId = o.optInt("tf");
            t.chatPhotoFileId = o.optInt("cf");
            t.chatTitle = o.optString("ct", "");
            t.isVoice = o.optBoolean("v");
            String am = o.optString("am", "");
            if (!am.isEmpty()) {
                try {
                    t.artMini = android.util.Base64.decode(am, android.util.Base64.NO_WRAP);
                } catch (Throwable ignored) {
                }
            }
            return t;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
''')
print('3. LibraryPersistence ✓')
