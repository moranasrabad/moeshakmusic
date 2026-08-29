package ir.moeshakteam.moeshakmusic.td;

import android.util.Base64;
import android.util.Log;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * تبدیل دوطرفهٔ JSON کتابخانهٔ tdjson ↔ آبجکت‌های TdApi (مطابق TDLib 1.8.65)
 * تیم موشک — moeshakteam.ir
 */
final class TdCodec {

    private static final String TAG = "TdCodec";
    private static final Map<String, Class<?>> CLASSES = new HashMap<>();

    private TdCodec() {
    }

    /** TdApi.Function → رشتهٔ JSON با قرارداد tdjson (فیلدهای snake_case) */
    static String toJson(TdApi.Function<?> fn) {
        try {
            JSONObject o = new JSONObject();
            fill(o, fn);
            return o.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("toJson failed: " + fn.getClass().getSimpleName(), e);
        }
    }

    /** JSON پاسخ tdjson → TdApi.Object — مقاوم: یک فیلد خراب کل آبجکت رو نمی‌کشه */
    static TdApi.Object fromJson(JSONObject jo) {
        String type = jo.optString("@type", "");
        if (type.isEmpty()) return null;
        Class<?> c = classFor(type);
        if (c == null) {
            Log.w(TAG, "unknown tdlib type: " + type);
            return null;
        }
        try {
            Object obj = c.getDeclaredConstructor().newInstance();
            for (Field f : c.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String key = snake(f.getName());
                if (!jo.has(key)) continue;
                try {
                    f.set(obj, valueOf(jo.get(key), f.getType()));
                } catch (Throwable fe) {
                    // ⚠️ یک فیلد خراب = فقط همون فیلد skip می‌شه
                    Log.w(TAG, "field skipped: " + type + "." + f.getName() + " → " + fe);
                }
            }
            return (TdApi.Object) obj;
        } catch (Exception e) {
            Log.w(TAG, "parse failed: " + type, e);
            return null;
        }
    }

    private static void fill(JSONObject o, Object td) throws Exception {
        o.put("@type", decap(td.getClass().getSimpleName()));
        for (Field f : td.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Object v = f.get(td);
            if (v == null) continue;
            o.put(snake(f.getName()), box(v));
        }
    }

    private static Object box(Object v) throws Exception {
        if (v instanceof TdApi.Function || v instanceof TdApi.Object) {
            JSONObject j = new JSONObject();
            fill(j, v);
            return j;
        }
        if (v instanceof byte[]) return Base64.encodeToString((byte[]) v, Base64.NO_WRAP);
        Class<?> c = v.getClass();
        if (c.isArray()) {
            JSONArray arr = new JSONArray();
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) arr.put(box(Array.get(v, i)));
            return arr;
        }
        return v;
    }

    private static Object valueOf(Object jv, Class<?> t) throws Exception {
        if (jv == null || jv == JSONObject.NULL) return null;
        if (t == int.class) return ((Number) jv).intValue();
        if (t == long.class) return ((Number) jv).longValue();
        if (t == double.class) return ((Number) jv).doubleValue();
        if (t == float.class) return ((Number) jv).floatValue();
        if (t == boolean.class) return Boolean.TRUE.equals(jv) || "true".equals(jv.toString());
        if (t == String.class) return jv.toString();
        if (t == byte[].class) return Base64.decode((String) jv, Base64.NO_WRAP);
        if (t.isArray()) {
            JSONArray ja = (JSONArray) jv;
            Object arr = Array.newInstance(t.getComponentType(), ja.length());
            for (int i = 0; i < ja.length(); i++) Array.set(arr, i, valueOf(ja.get(i), t.getComponentType()));
            return arr;
        }
        if (jv instanceof JSONObject) return fromJson((JSONObject) jv);
        return null;
    }

    private static Class<?> classFor(String type) {
        synchronized (CLASSES) {
            Class<?> c = CLASSES.get(type);
            if (c != null) return c;
            String n = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            try {
                c = Class.forName("org.drinkless.tdlib.TdApi$" + n);
                CLASSES.put(type, c);
                return c;
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    /** نام کلاس TDLib → نام constructor در JSON (حرف اول کوچک) */
    private static String decap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** camelCase (فیلد جاوا) → snake_case (کلید JSON در tdjson) */
    private static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.US);
    }
}
