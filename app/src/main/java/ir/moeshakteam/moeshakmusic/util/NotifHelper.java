package ir.moeshakteam.moeshakmusic.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;

/** اعلان‌ها — اسکن و آهنگ جدید دنبال‌شده‌ها — تیم موشک */
public final class NotifHelper {

    public static final String CH_SCAN = "moeshak_scan";
    public static final String CH_FOLLOW = "moeshak_follow";
    private static final int SCAN_ID = 42;

    private NotifHelper() {
    }

    public static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH_SCAN, "اسکن",
                NotificationManager.IMPORTANCE_LOW));
        NotificationChannel f = new NotificationChannel(CH_FOLLOW, "آهنگ جدید دنبال‌شده‌ها",
                NotificationManager.IMPORTANCE_DEFAULT);
        f.enableVibration(true);
        nm.createNotificationChannel(f);
    }

    /** نوتیف زندهٔ اسکن — با همان شناسه آپدیت می‌شود */
    public static void scanProgress(Context c, int chats, int found) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_SCAN)
                .setSmallIcon(R.drawable.ic_rocket)
                .setContentTitle("🔍 در حال اسکن…")
                .setContentText(chats + " چت اسکن شد • " + found + " آهنگ پیدا شد")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true);
        nm(c).notify(SCAN_ID, b.build());
    }

    public static void scanDone(Context c, int chats, int found, int seconds) {
        nm(c).cancel(SCAN_ID);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_SCAN)
                .setSmallIcon(R.drawable.ic_rocket)
                .setContentTitle("🏁 اسکن تمام شد")
                .setContentText(found + " آهنگ از " + chats + " چت — در " + seconds + " ثانیه")
                .setAutoCancel(true);
        nm(c).notify(SCAN_ID + 1, b.build());
    }

    public static void cancelScan(Context c) {
        nm(c).cancel(SCAN_ID);
    }

    /** آهنگ جدید در چت‌های دنبال‌شده */
    public static void newTracks(Context c, String channel, List<String> titles) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_FOLLOW)
                .setSmallIcon(R.drawable.ic_rocket)
                .setContentTitle("🎵 " + channel + " — آهنگ جدید!")
                .setContentText(android.text.TextUtils.join(" • ", titles))
                .setAutoCancel(true);
        nm(c).notify(("follow_" + channel).hashCode(), b.build());
    }

    private static NotificationManager nm(Context c) {
        return c.getSystemService(NotificationManager.class);
    }
}
