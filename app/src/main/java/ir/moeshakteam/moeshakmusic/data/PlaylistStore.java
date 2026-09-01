package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** پلی‌لیست‌های سفارشی کاربر — تیم موشک */
public final class PlaylistStore {

    public static class Playlist {
        public String name;
        public List<Track> tracks = new ArrayList<>();
    }

    private static PlaylistStore inst;
    private final SharedPreferences sp;

    private PlaylistStore(Context c) {
        sp = c.getSharedPreferences("moeshak_playlists", Context.MODE_PRIVATE);
    }

    public static synchronized PlaylistStore get(Context c) {
        if (inst == null) inst = new PlaylistStore(c.getApplicationContext());
        return inst;
    }

    public List<Playlist> all() {
        List<Playlist> out = new ArrayList<>();
        for (String name : sp.getAll().keySet()) {
            Playlist p = new Playlist();
            p.name = name;
            try {
                JSONArray arr = new JSONArray(sp.getString(name, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Track t = new Track();
                    t.chatId = o.optLong("chatId");
                    t.messageId = o.optLong("messageId");
                    t.title = o.optString("title");
                    t.performer = o.optString("performer");
                    t.chatTitle = o.optString("chatTitle");
                    t.duration = o.optInt("duration");
                    t.date = o.optInt("date");
                    t.fileId = o.optInt("fileId");
                    t.expectedSize = o.optLong("size");
                    p.tracks.add(t);
                }
            } catch (Exception ignored) {
            }
            out.add(p);
        }
        return out;
    }

    public Playlist byName(String name) {
        for (Playlist p : all()) if (p.name.equals(name)) return p;
        return null;
    }

    public void save(Playlist p) {
        try {
            JSONArray arr = new JSONArray();
            for (Track t : p.tracks) {
                JSONObject o = new JSONObject();
                o.put("chatId", t.chatId);
                o.put("messageId", t.messageId);
                o.put("title", t.title);
                o.put("performer", t.performer);
                o.put("chatTitle", t.chatTitle);
                o.put("duration", t.duration);
                o.put("date", t.date);
                o.put("fileId", t.fileId);
                o.put("size", t.expectedSize);
                arr.put(o);
            }
            sp.edit().putString(p.name, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void create(String name) {
        if (!name.isEmpty()) sp.edit().putString(name, "[]").apply();
    }

    public void delete(String name) {
        sp.edit().remove(name).apply();
    }

    /** تغییر نام پلی‌لیست — false اگر اسم جدید موجود باشد */
    public boolean rename(String oldName, String newName) {
        if (oldName.equals(newName) || newName.isEmpty() || sp.contains(newName)) return false;
        String data = sp.getString(oldName, null);
        if (data == null) return false;
        sp.edit().remove(oldName).putString(newName, data).apply();
        return true;
    }

    /** افزودن تراک به پلی‌لیست (اگر نباشد) — true = اضافه شد */
    public boolean addTrack(String playlistName, Track t) {
        Playlist p = byName(playlistName);
        if (p == null) return false;
        for (Track x : p.tracks) {
            if (x.chatId == t.chatId && x.messageId == t.messageId) return false;
        }
        p.tracks.add(t);
        save(p);
        return true;
    }

    public boolean removeTrack(String playlistName, long chatId, long messageId) {
        Playlist p = byName(playlistName);
        if (p == null) return false;
        boolean removed = false;
        for (int i = p.tracks.size() - 1; i >= 0; i--) {
            Track x = p.tracks.get(i);
            if (x.chatId == chatId && x.messageId == messageId) {
                p.tracks.remove(i);
                removed = true;
            }
        }
        if (removed) save(p);
        return removed;
    }
}
