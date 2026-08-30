package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.moeshakteam.moeshakmusic.App;
import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.td.TdClient;

/**
 * لودر تامبنیل تراک‌ها — تیم موشک
 * ترتیب: مینی‌تامب داخل پیام ← کاور آلبوم (thumbFileId) ← عکس چت/کانال ← گرادیان برند.
 * کش حافظه + کش دیسک (art/) + دانلودهای کوچک همگام در thread پس‌زمینه.
 */
public final class ArtLoader {

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "ArtLoader");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** کش حافظه: «m:chatId:msgId» برای مینی‌تامب، «f:fileId» برای بقیه */
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<Integer> INFLIGHT = new HashSet<>();
    private static File diskDir;

    private ArtLoader() {
    }

    /** ست کردن عکس روی ImageView — فوری اگر کش باشد، وگرنه async با placeholder گرادیان */
    public static void load(Track t, ImageView iv) {
        if (t == null || iv == null) return;
        Bitmap b = fromCache(t);
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }
        iv.setImageResource(R.drawable.bg_art);
        final String tag = t.chatId + ":" + t.messageId;
        iv.setTag(tag);
        EXEC.execute(() -> {
            Bitmap r = fetch(t);
            if (r == null) return;
            MAIN.post(() -> {
                if (tag.equals(iv.getTag())) iv.setImageBitmap(r);
            });
        });
    }

    /** گرفتن همگام اگر موجود باشد (کش حافظه/دیسک) — thread پس‌زمینه */
    public static Bitmap fromCache(Track t) {
        if (t.artBitmap != null) return t.artBitmap;
        if (t.artMini != null) {
            Bitmap b = MEM.get("m:" + t.chatId + ":" + t.messageId);
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
        }
        if (t.thumbFileId > 0) {
            Bitmap b = MEM.get("f:" + t.thumbFileId);
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
            Bitmap d = diskGet(t.thumbFileId);
            if (d != null) {
                MEM.put("f:" + t.thumbFileId, d);
                t.artBitmap = d;
                return d;
            }
        }
        if (t.chatPhotoFileId > 0) {
            Bitmap b = MEM.get("f:" + t.chatPhotoFileId);
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
            Bitmap d = diskGet(t.chatPhotoFileId);
            if (d != null) {
                MEM.put("f:" + t.chatPhotoFileId, d);
                t.artBitmap = d;
                return d;
            }
        }
        return null;
    }

    private static Bitmap fetch(Track t) {
        // ۱) کاور آلبوم (تا ۳۲۰px — باکیفیت‌ترین)
        if (t.thumbFileId > 0) {
            Bitmap b = downloadIfFree(t.thumbFileId);
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
        }
        // ۲) عکس چت/کانال
        if (t.chatPhotoFileId > 0) {
            Bitmap b = downloadIfFree(t.chatPhotoFileId);
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
        }
        // ۳) مینی‌تامب داخل خود پیام (کوچک ~۹۰px — آخرین گزینه)
        if (t.artMini != null) {
            Bitmap b = MEM.get("m:" + t.chatId + ":" + t.messageId);
            if (b == null) {
                try {
                    b = BitmapFactory.decodeByteArray(t.artMini, 0, t.artMini.length);
                } catch (Throwable ignored) {
                }
                if (b != null) MEM.put("m:" + t.chatId + ":" + t.messageId, b);
            }
            if (b != null) {
                t.artBitmap = b;
                return b;
            }
        }
        return null;
    }

    /** دانلود کوچک همگام — فقط اگر همین هنوز در حال دانلود نباشد */
    private static synchronized Bitmap downloadIfFree(int fileId) {
        if (!INFLIGHT.add(fileId)) return null;
        try {
            Bitmap b = diskGet(fileId);
            if (b != null) {
                MEM.put("f:" + fileId, b);
                return b;
            }
            b = downloadSync(fileId);
            if (b != null) MEM.put("f:" + fileId, b);
            return b;
        } finally {
            INFLIGHT.remove(fileId);
        }
    }

    private static Bitmap downloadSync(int fileId) {
        try {
            org.json.JSONObject f = TdClient.syncRaw(new TdApi.GetFile(fileId));
            org.json.JSONObject local = f.optJSONObject("local");
            if (local != null && local.optBoolean("is_downloading_completed") && !local.optString("path", "").isEmpty()) {
                return diskCopy(fileId, local.optString("path"));
            }
            long size = f.optLong("expected_size", f.optLong("size", 256 * 1024));
            TdClient.syncRaw(new TdApi.DownloadFile(fileId, 1, 0L, Math.max(size, 64 * 1024), true));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                f = TdClient.syncRaw(new TdApi.GetFile(fileId));
                local = f.optJSONObject("local");
                if (local != null && local.optBoolean("is_downloading_completed") && !local.optString("path", "").isEmpty()) {
                    return diskCopy(fileId, local.optString("path"));
                }
                Thread.sleep(200);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** کپی به کش دیسک خودمان (فایل‌های TDLib ممکن است پاک شوند) + دیکد */
    private static Bitmap diskCopy(int fileId, String srcPath) {
        try {
            File dst = diskFile(fileId);
            if (!dst.exists() || dst.length() == 0) {
                FileInputStream in = new FileInputStream(srcPath);
                FileOutputStream out = new FileOutputStream(dst);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.close();
            }
            Bitmap b = BitmapFactory.decodeFile(dst.getAbsolutePath());
            if (b == null) //noinspection ResultOfMethodCallIgnored
                dst.delete();
            return b;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap diskGet(int fileId) {
        try {
            File f = diskFile(fileId);
            if (f.exists() && f.length() > 0) return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static File diskFile(int fileId) {
        if (diskDir == null) {
            diskDir = new File(App.get().getCacheDir(), "art");
            //noinspection ResultOfMethodCallIgnored
            diskDir.mkdirs();
        }
        return new File(diskDir, fileId + ".img");
    }

    /** پاک کردن کش (برای تست) */
    public static void clearDisk(Context c) {
        try {
            File[] fs = new File(c.getCacheDir(), "art").listFiles();
            if (fs != null) for (File f : fs) //noinspection ResultOfMethodCallIgnored
                f.delete();
            MEM.evictAll();
        } catch (Throwable ignored) {
        }
    }
}
