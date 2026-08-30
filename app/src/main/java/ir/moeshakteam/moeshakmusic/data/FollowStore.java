package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * چت‌های دنبال‌شده — پایش خودکار آهنگ جدید — تیم موشک
 * ذخیره: moeshak_followed (JSON) — شامل knownIds تا فقط آهنگِ «جدید» نوتیف بخورد.
 */
public final class FollowStore {

    public static class Followed {
        public long chatId;
        public String title;
        public List<String> knownIds = new ArrayList<>();
    }

    private static FollowStore inst;
    private final SharedPreferences sp;

    private FollowStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("moeshak_followed", Context.MODE_PRIVATE);
    }

    public static synchronized FollowStore get(Context c) {
        if (inst == null) inst = new FollowStore(c);
        return inst;
    }

    public List<Followed> all() {
        List<Followed> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Followed f = new Followed();
                f.chatId = o.getLong("chatId");
                f.title = o.optString("title", "چت");
                JSONArray k = o.optJSONArray("knownIds");
                if (k != null) for (int j = 0; j < k.length(); j++) f.knownIds.add(k.optString(j));
                out.add(f);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public boolean isFollowed(long chatId) {
        for (Followed f : all()) if (f.chatId == chatId) return true;
        return false;
    }

    public void follow(long chatId, String title, List<String> baseIds) {
        if (isFollowed(chatId)) return;
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            JSONObject o = new JSONObject();
            o.put("chatId", chatId);
            o.put("title", title == null ? "چت" : title);
            JSONArray k = new JSONArray();
            if (baseIds != null) for (String id : baseIds) k.put(id);
            o.put("knownIds", k);
            arr.put(o);
            sp.edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void unfollow(long chatId) {
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).getLong("chatId") != chatId) out.put(arr.getJSONObject(i));
            }
            sp.edit().putString("list", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void updateTitle(long chatId, String title) {
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("chatId") == chatId) {
                    o.put("title", title);
                    arr.put(i, o);
                    break;
                }
            }
            sp.edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void updateKnown(long chatId, List<String> knownIds) {
        try {
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("chatId") == chatId) {
                    JSONArray k = new JSONArray();
                    for (String id : knownIds) k.put(id);
                    o.put("knownIds", k);
                    arr.put(i, o);
                    break;
                }
            }
            sp.edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
