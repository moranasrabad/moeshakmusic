package ir.moeshakteam.moeshakmusic.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ir.moeshakteam.moeshakmusic.BuildConfig;

/** تنظیمات ذخیره‌شدهٔ برنامه + لیست پروکسی‌ها — تیم موشک */
public final class Prefs {

    /** یک سرور پروکسی MTProto */
    public static class ProxyEntry {
        public String server = "";
        public int port = 443;
        public String secret = "";
        public String comment = "";
        public int pingMs = -1; // -1 = تست نشده

        public String label() {
            return server + ":" + port;
        }
    }

    private static Prefs inst;
    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getSharedPreferences("moeshak_music", Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (inst == null) inst = new Prefs(c.getApplicationContext());
        return inst;
    }

    // ---------- کلیدهای API ----------

    public boolean hasKeys() {
        if (sp.getInt("api_id", 0) != 0 && sp.getString("api_hash", "").length() > 10) return true;
        return bakedApiId() != 0 && bakedApiHash().length() > 10;
    }

    public int apiId() {
        int v = sp.getInt("api_id", 0);
        return v != 0 ? v : bakedApiId();
    }

    public String apiHash() {
        String v = sp.getString("api_hash", "");
        return v.length() > 10 ? v : bakedApiHash();
    }

    private int bakedApiId() {
        try {
            return BuildConfig.BAKED_API_ID;
        } catch (Throwable t) {
            return 0;
        }
    }

    private String bakedApiHash() {
        try {
            return BuildConfig.BAKED_API_HASH == null ? "" : BuildConfig.BAKED_API_HASH;
        } catch (Throwable t) {
            return "";
        }
    }

    public void saveKeys(int id, String hash) {
        sp.edit().putInt("api_id", id).putString("api_hash", hash).apply();
    }

    /** حذف کلید شخصی — برگشت به کلید بیک‌شده */
    public void clearKeys() {
        sp.edit().remove("api_id").remove("api_hash").apply();
    }

    // ---------- پروکسی ----------

    public boolean proxyEnabled() {
        return sp.getBoolean("proxy_on", false);
    }

    /** ایندکس پروکسی فعال در لیست؛ -1 = هیچ */
    public int activeProxyIndex() {
        return sp.getInt("proxy_active", -1);
    }

    public void setActiveProxyIndex(int idx) {
        if (idx >= 0) sp.edit().putInt("proxy_last", idx).apply();
        sp.edit().putInt("proxy_active", idx).putBoolean("proxy_on", idx >= 0).apply();
    }

    /** آخرین پروکسی انتخابی (برای روشن کردن دوباره بعد از خاموشی) */
    public int lastProxyIndex() {
        return sp.getInt("proxy_last", -1);
    }

    public List<ProxyEntry> proxies() {
        List<ProxyEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("proxies", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ProxyEntry e = new ProxyEntry();
                e.server = o.optString("server", "");
                e.port = o.optInt("port", 443);
                e.secret = o.optString("secret", "");
                e.comment = o.optString("comment", "");
                e.pingMs = o.optInt("ping", -1);
                if (!e.server.isEmpty() && !e.secret.isEmpty()) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void saveProxies(List<ProxyEntry> list) {
        try {
            JSONArray arr = new JSONArray();
            for (ProxyEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("server", e.server);
                o.put("port", e.port);
                o.put("secret", e.secret);
                o.put("comment", e.comment);
                o.put("ping", e.pingMs);
                arr.put(o);
            }
            sp.edit().putString("proxies", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------- سازگاری با نسخه‌های قبل (تک پروکسی) ----------

    public String proxyServer() {
        int idx = activeProxyIndex();
        List<ProxyEntry> all = proxies();
        if (idx >= 0 && idx < all.size()) return all.get(idx).server;
        return sp.getString("proxy_server", "");
    }

    public String proxyPort() {
        int idx = activeProxyIndex();
        List<ProxyEntry> all = proxies();
        if (idx >= 0 && idx < all.size()) return String.valueOf(all.get(idx).port);
        return sp.getString("proxy_port", "443");
    }

    public String proxySecret() {
        int idx = activeProxyIndex();
        List<ProxyEntry> all = proxies();
        if (idx >= 0 && idx < all.size()) return all.get(idx).secret;
        return sp.getString("proxy_secret", "");
    }

    public void setProxy(boolean on, String server, String port, String secret) {
        List<ProxyEntry> list = proxies();
        ProxyEntry e = new ProxyEntry();
        e.server = server == null ? "" : server.trim();
        e.port = 443;
        try {
            e.port = Integer.parseInt(port == null ? "443" : port.trim());
        } catch (Exception ignored) {
        }
        e.secret = secret == null ? "" : secret.trim();
        list.add(e);
        saveProxies(list);
        setActiveProxyIndex(list.size() - 1);
    }

    // ---------- تم و سایر ----------

    /** رنگ اکسنت: 0 = پیش‌فرض برند، وگرنه ARGB */
    public int accentColor() {
        return sp.getInt("accent_color", 0);
    }

    public void setAccentColor(int color) {
        sp.edit().putInt("accent_color", color).apply();
    }

    /** 0 = سیستمی، 1 = روشن، 2 = تاریک */
    public int themeMode() {
        return sp.getInt("theme_mode", 0);
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt("theme_mode", mode).apply();
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}
