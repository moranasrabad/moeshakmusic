package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** وضعیت دانلودهای دائمی — تیم موشک */
public final class DownloadStore {

    public static class Entry {
        public String key;      // chatId:messageId
        public String title;
        public String chatTitle;
        public int fileId;
        public long size;
        public String path;
    }

    private static DownloadStore inst;
    private final SharedPreferences sp;

    private DownloadStore(Context c) {
        sp = c.getSharedPreferences("moeshak_downloads", Context.MODE_PRIVATE);
    }

    public static synchronized DownloadStore get(Context c) {
        if (inst == null) inst = new DownloadStore(c.getApplicationContext());
        return inst;
    }

    public boolean isDownloaded(Track t) {
        return sp.contains(t.chatId + ":" + t.messageId);
    }

    public String pathOf(Track t) {
        String j = sp.getString(t.chatId + ":" + t.messageId, null);
        if (j == null) return null;
        try {
            return new JSONObject(j).optString("path");
        } catch (Exception e) {
            return null;
        }
    }

    public void mark(Track t, String path) {
        try {
            JSONObject o = new JSONObject();
            o.put("title", t.title);
            o.put("chatTitle", t.chatTitle);
            o.put("fileId", t.fileId);
            o.put("size", t.expectedSize);
            o.put("path", path);
            sp.edit().putString(t.chatId + ":" + t.messageId, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (String k : sp.getAll().keySet()) {
            try {
                JSONObject o = new JSONObject(sp.getString(k, "{}"));
                Entry e = new Entry();
                e.key = k;
                e.title = o.optString("title");
                e.chatTitle = o.optString("chatTitle");
                e.fileId = o.optInt("fileId");
                e.size = o.optLong("size");
                e.path = o.optString("path");
                out.add(e);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public int count() {
        return sp.getAll().size();
    }

    public void remove(String key) {
        sp.edit().remove(key).apply();
    }
}
