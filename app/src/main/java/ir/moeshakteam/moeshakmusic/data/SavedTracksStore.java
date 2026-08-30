package ir.moeshakteam.moeshakmusic.data;

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

/**
 * ذخیرهٔ فیوریت‌ها — تنها تراک‌هایی که دائمی می‌مانند.
 * (به‌همراه تراک‌های پلی‌لیست که در PlaylistStore ذخیره می‌شوند)
 * fileId تلگرام بین ری‌استارت‌ها عوض می‌شود؛ پس بعد از ورود، Tg.restoreSaved
 * پیام‌ها را دوباره می‌گیرد تا fileId تازه باشد.
 * تیم موشک — moeshakteam.ir
 */
public final class SavedTracksStore {

    private static final ExecutorService SAVE_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FavSave");
        t.setDaemon(true);
        return t;
    });

    private SavedTracksStore() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "favorites.json");
    }

    /** خواندن فیوریت‌ها از دیسک */
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
                    Track t = fromJson(arr.getJSONObject(i));
                    if (t != null && t.fileId != 0) out.add(t);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** ذخیرهٔ ناهمگام فیوریت‌ها */
    public static void save(Context ctx, List<Track> favs) {
        final String json = toJson(favs);
        final File f = file(ctx.getApplicationContext());
        SAVE_EXEC.execute(() -> {
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

    private static Track fromJson(JSONObject o) {
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
