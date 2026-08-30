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
        // کانال‌های نوتیف — اسکن و دنبال‌شده‌ها
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ir.moeshakteam.moeshakmusic.util.NotifHelper.ensureChannels(this);
        }
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
        ACCENT = Prefs.get(c).accentColor();
    }

    /** پالت اکسنت — باید با ترتیب colors در SettingsFragment یکی باشد */
    public static final int[] ACCENT_PALETTE = {0xFF22D3EE, 0xFF8B5CF6, 0xFF34D399, 0xFFF59E0B, 0xFFF43F5E, 0xFF3B82F6};

    /**
     * اعمال رنگ اکسنت روی اکتیویتی — «قبل از» setContentView صدا زده شود.
     * هر اکسنت یک تم کامل با والد Theme.Moeshak است (شب/روز خودکار رعایت می‌شود).
     */
    public static void applyAccentTheme(android.app.Activity act) {
        int c = Prefs.get(act).accentColor();
        if (c == 0) return;
        for (int i = 0; i < ACCENT_PALETTE.length; i++) {
            if (ACCENT_PALETTE[i] == c) {
                int style;
                switch (i) {
                    case 0: style = R.style.Accent_Icy; break;
                    case 1: style = R.style.Accent_Purple; break;
                    case 2: style = R.style.Accent_Green; break;
                    case 3: style = R.style.Accent_Amber; break;
                    case 4: style = R.style.Accent_Rose; break;
                    default: style = R.style.Accent_Blue; break;
                }
                act.setTheme(style);
                ACCENT = c;
                return;
            }
        }
    }
}
