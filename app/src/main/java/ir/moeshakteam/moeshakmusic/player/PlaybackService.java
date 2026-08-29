package ir.moeshakteam.moeshakmusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.ui.MainActivity;

/** سرویس پخش با نوتیفیکیشن — تیم موشک */
public class PlaybackService extends Service {

    private static final String CH = "moeshak_playback";
    private static final int NOTIF_ID = 41;
    static final String ACTION_TOGGLE = "ir.moeshakteam.moeshakmusic.TOGGLE";
    static final String ACTION_NEXT = "ir.moeshakteam.moeshakmusic.NEXT";
    static final String ACTION_PREV = "ir.moeshakteam.moeshakmusic.PREV";
    static final String ACTION_CLOSE = "ir.moeshakteam.moeshakmusic.CLOSE";

    private PlayerManager pm;
    private boolean notifActive;

    private final PlayerManager.Listener notifListener = new PlayerManager.Listener() {
        @Override
        public void onTrackChanged(Track t) {
            refresh();
        }

        @Override
        public void onPlayStateChanged(boolean playing) {
            refresh();
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void update(Context c) {
        if (PlayerManager.get(c).current() == null) {
            c.stopService(new Intent(c, PlaybackService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = PlayerManager.get(this);
        pm.attach(notifListener);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action != null && pm != null) {
            switch (action) {
                case ACTION_TOGGLE:
                    pm.toggle();
                    break;
                case ACTION_NEXT:
                    pm.next();
                    break;
                case ACTION_PREV:
                    pm.prev();
                    break;
                case ACTION_CLOSE:
                    pm.seekTo(0);
                    pm.detach(notifListener);
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
            }
        }
        if (pm.current() == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, build());
        notifActive = true;
        return START_NOT_STICKY;
    }

    private void refresh() {
        if (!notifActive) return;
        if (pm.current() == null) {
            stopForeground(true);
            notifActive = false;
            stopSelf();
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, build());
    }

    private Notification build() {
        Track t = pm.current();
        String title = t == null ? getString(R.string.notif_title) : t.title;
        String text = t == null ? "" : t.subtitle();
        boolean playing = pm.isPlaying();
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_music)
                .setColor(0x67E8F9)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(new NotificationCompat.Action(R.drawable.ic_prev, "prev", service(ACTION_PREV, 1)))
                .addAction(new NotificationCompat.Action(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        "toggle", service(ACTION_TOGGLE, 2)))
                .addAction(new NotificationCompat.Action(R.drawable.ic_next, "next", service(ACTION_NEXT, 3)))
                .setStyle(new MediaStyle()
                        .setMediaSession(null)
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private PendingIntent service(String action, int rc) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (pm != null) pm.detach(notifListener);
        super.onDestroy();
    }
}
