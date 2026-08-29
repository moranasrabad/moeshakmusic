package ir.moeshakteam.moeshakmusic;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import ir.moeshakteam.moeshakmusic.data.Prefs;

/** تیم موشک — moeshakteam.ir */
public class App extends Application {

    private static App inst;
    /** رنگ اکسنت فعلی (0 = پیش‌فرض برند) */
    public static int ACCENT = 0;

    public static App get() {
        return inst;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        ACCENT = Prefs.get(this).accentColor();
        applyTheme(this);
        // ضبط کرش‌ها — بعد از راه‌اندازی در لاگ زنده دیده می‌شن
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String trace = "💥 CRASH: " + e + "\n" + sw;
                java.io.File f = new java.io.File(getFilesDir(), "last_crash.txt");
                java.io.FileWriter fw = new java.io.FileWriter(f);
                fw.write(trace);
                fw.close();
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    public static void applyTheme(android.content.Context c) {
        int m = Prefs.get(c).themeMode();
        AppCompatDelegate.setDefaultNightMode(
                m == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                        : m == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
