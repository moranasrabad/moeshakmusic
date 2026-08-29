package ir.moeshakteam.moeshakmusic.util;

import android.content.Context;
import android.widget.Toast;

/** ابزارهای کوچک UI — تیم موشک */
public final class Ui {

    private Ui() {
    }

    public static String fmtTime(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public static String fmtDuration(int sec) {
        if (sec <= 0) return "—";
        return String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    public static void toast(Context c, String msg) {
        if (c != null && msg != null) Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context c, int resId) {
        if (c != null) Toast.makeText(c, resId, Toast.LENGTH_SHORT).show();
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
