package ir.moeshakteam.moeshakmusic.td;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.github.up9cloud.td.JsonClient;

/**
 * کلاینت TDLib روی رابط JSON کتابخانهٔ libtdjson — با API شبیه Client رسمی.
 * تیم موشک — moeshakteam.ir
 */
public final class TdClient {

    private static final String TAG = "TdClient";

    /** نتیجهٔ یک درخواست (یا TdApi.Error) */
    public interface ResultHandler {
        void onResult(TdApi.Object obj);
    }

    /** آپدیت‌های سراسری (UpdateAuthorizationState، UpdateFile و…) */
    public interface UpdateHandler {
        void onUpdate(TdApi.Object update);
    }

    /** خطای سطح بالا */
    public static class TdError extends RuntimeException {
        public final int code;

        TdError(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static volatile int clientId = -1;
    private static volatile UpdateHandler updates;
    private static Handler main;
    /** وقتی نشست در حال خاتمه است، هیچ درخواستی به کلاینتِ در حال مرگ نرود (ضدکرش نیتیو) */
    private static volatile boolean gated = false;

    /** قفل/بازکردن درگاه ارسال — هنگام LOGGING_OUT بسته می‌شود */
    public static void gate(boolean on) {
        gated = on;
    }

    private static final Map<Long, ResultHandler> uiHandlers = new ConcurrentHashMap<>();
    private static final Map<Long, ResultHandler> syncHandlers = new ConcurrentHashMap<>();
    private static final Map<Long, RawHandler> rawHandlers = new ConcurrentHashMap<>();
    private static final AtomicLong ids = new AtomicLong(1);

    /** هندلر پاسخ خام JSON (بدون تبدیل به TdApi) — برای مسیرهای حساس */
    public interface RawHandler {
        void onResult(JSONObject jo);
    }

    /** ارسال با پاسخ خام — هیچ پارسی انجام نمی‌شود */
    public static void sendRaw(TdApi.Function<?> fn, RawHandler h) {
        long extra = ids.getAndIncrement();
        if (gated) {
            if (h != null) h.onResult(new JSONObject());
            return;
        }
        if (h != null) rawHandlers.put(extra, h);
        String req;
        try {
            String body = TdCodec.toJson(fn);
            req = body.substring(0, body.length() - 1) + ",\"@extra\":" + extra + "}";
        } catch (Exception e) {
            rawHandlers.remove(extra);
            return;
        }
        JsonClient.td_send(clientId, req);
    }

    /** sync با پاسخ خام JSON — فقط از thread پس‌زمینه */
    public static JSONObject syncRaw(TdApi.Function<?> fn) throws TdError {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("syncRaw on main thread");
        if (gated) throw new TdError(503, "client unavailable (logging out)");
        long extra = ids.getAndIncrement();
        CountDownLatch latch = new CountDownLatch(1);
        final JSONObject[] out = new JSONObject[1];
        rawHandlers.put(extra, jo -> {
            out[0] = jo;
            latch.countDown();
        });
        String req;
        try {
            String body = TdCodec.toJson(fn);
            req = body.substring(0, body.length() - 1) + ",\"@extra\":" + extra + "}";
        } catch (Exception e) {
            rawHandlers.remove(extra);
            throw new TdError(500, "serialize failed");
        }
        JsonClient.td_send(clientId, req);
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) throw new TdError(408, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TdError(408, "interrupted");
        }
        JSONObject r = out[0];
        if (r == null) throw new TdError(408, "no response");
        if ("error".equals(r.optString("@type"))) {
            throw new TdError(r.optInt("code"), r.optString("message"));
        }
        return r;
    }

    private TdClient() {
    }

    /** باید قبل از هر send صدا زده شود (فقط یک بار) */
    public static synchronized void init(UpdateHandler uh) {
        updates = uh;
        if (main != null) return;
        main = new Handler(Looper.getMainLooper());
        JsonClient.td_execute("{\"@type\":\"setLogVerbosityLevel\",\"new_verbosity_level\":1}");
        Thread t = new Thread(() -> {
            clientId = JsonClient.td_create_client_id();
            while (true) {
                String resp = null;
                try {
                    resp = JsonClient.td_receive(1.0);
                } catch (Throwable th) {
                    Log.w(TAG, "receive crashed", th);
                }
                if (resp == null) continue;
                try {
                    process(resp);
                } catch (Throwable th) {
                    Log.w(TAG, "process failed", th);
                }
            }
        }, "TdReceiver");
        t.setDaemon(true);
        t.start();
    }

    /** بعد از خروج از حساب، کلاینت جدید می‌سازیم تا جریان لاگین از سر شروع شود */
    public static synchronized void recreate() {
        uiHandlers.clear();
        syncHandlers.clear();
        rawHandlers.clear();
        clientId = JsonClient.td_create_client_id();
    }

    public static void send(TdApi.Function<?> fn, ResultHandler h) {
        long extra = ids.getAndIncrement();
        if (gated) {
            final TdApi.Error err = new TdApi.Error(503, "client unavailable");
            if (h != null && main != null) main.post(() -> h.onResult(err));
            return;
        }
        if (h != null) uiHandlers.put(extra, h);
        String req;
        try {
            String body = TdCodec.toJson(fn);
            req = body.substring(0, body.length() - 1) + ",\"@extra\":" + extra + "}";
        } catch (Exception e) {
            uiHandlers.remove(extra);
            final TdApi.Error err = new TdApi.Error(500, "serialize failed");
            if (h != null && main != null) main.post(() -> h.onResult(err));
            return;
        }
        JsonClient.td_send(clientId, req);
    }

    /** صدا زدن همگام — فقط از thread پس‌زمینه */
    public static TdApi.Object sync(TdApi.Function<?> fn) throws TdError {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("sync() روی main thread ممنوع است");
        long extra = ids.getAndIncrement();
        CountDownLatch latch = new CountDownLatch(1);
        final TdApi.Object[] out = new TdApi.Object[1];
        syncHandlers.put(extra, r -> {
            out[0] = r;
            latch.countDown();
        });
        String req;
        try {
            String body = TdCodec.toJson(fn);
            req = body.substring(0, body.length() - 1) + ",\"@extra\":" + extra + "}";
        } catch (Exception e) {
            syncHandlers.remove(extra);
            throw new TdError(500, "serialize failed");
        }
        JsonClient.td_send(clientId, req);
        try {
            if (!latch.await(120, TimeUnit.SECONDS)) throw new TdError(408, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TdError(408, "interrupted");
        }
        TdApi.Object r = out[0];
        if (r == null) throw new TdError(408, "no response");
        if (r instanceof TdApi.Error) {
            TdApi.Error e = (TdApi.Error) r;
            throw new TdError(e.code, e.message);
        }
        return r;
    }

    private static void process(String json) {
        JSONObject jo;
        try {
            jo = new JSONObject(json);
        } catch (Exception e) {
            return;
        }
        long extra = jo.has("@extra") ? jo.optLong("@extra", -1L) : -1L;
        jo.remove("@extra");
        if (extra != -1) {
            RawHandler rh = rawHandlers.remove(extra);
            if (rh != null) {
                try {
                    rh.onResult(jo);
                } catch (Throwable ignored) {
                }
                return;
            }
        }
        TdApi.Object obj = TdCodec.fromJson(jo);
        if (obj == null) return;
        if (extra != -1) {
            ResultHandler sh = syncHandlers.remove(extra);
            if (sh != null) {
                try {
                    sh.onResult(obj);
                } catch (Throwable ignored) {
                }
                return;
            }
            ResultHandler uh = uiHandlers.remove(extra);
            if (uh != null && main != null) main.post(() -> {
                try {
                    uh.onResult(obj);
                } catch (Throwable ignored) {
                }
            });
        } else if (obj instanceof TdApi.Update && updates != null && main != null) {
            main.post(() -> {
                try {
                    updates.onUpdate(obj);
                } catch (Throwable ignored) {
                }
            });
        }
    }
}
