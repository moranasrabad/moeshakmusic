package ir.moeshakteam.moeshakmusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.ui.MainActivity;

/**
 * سرویس پخش با MediaSession واقعی — تیم موشک.
 * MediaSession باعث می‌شود:
 *  ۱) نوتیفیکیشن استاندارد مدیا (با کنترل پخش) نمایش داده شود،
 *  ۲) نام/خوانندهٔ آهنگ از طریق AVRCP روی بلوتوث (ماشین/هندزفری) دیده شود،
 *  ۳) دکمه‌های پخش بلوتوث (play/pause/next/prev) کار کنند.
 */
public class PlaybackService extends Service {

    private static final String CH = "moeshak_playback";
    private static final int NOTIF_ID = 41;
    static final String ACTION_TOGGLE = "ir.moeshakteam.moeshakmusic.TOGGLE";
    static final String ACTION_NEXT = "ir.moeshakteam.moeshakmusic.NEXT";
    static final String ACTION_PREV = "ir.moeshakteam.moeshakmusic.PREV";
    static final String ACTION_CLOSE = "ir.moeshakteam.moeshakmusic.CLOSE";

    private PlayerManager pm;
    private boolean notifActive;
    private MediaSessionCompat session;

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
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("پخش موزیک موشک");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        createSession();
    }

    /** ساخت MediaSession و ثبت callback کنترل‌ها (نوتیف + بلوتوث) */
    private void createSession() {
        try {
            session = new MediaSessionCompat(this, "MoeshakMusic");
            session.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    if (!pm.isPlaying()) pm.toggle();
                }

                @Override
                public void onPause() {
                    if (pm.isPlaying()) pm.toggle();
                }

                @Override
                public void onSkipToNext() {
                    pm.next();
                }

                @Override
                public void onSkipToPrevious() {
                    pm.prev();
                }

                @Override
                public void onStop() {
                    pm.seekTo(0);
                    if (pm.isPlaying()) pm.toggle();
                }

                @Override
                public void onSeekTo(long pos) {
                    pm.seekTo(pos);
                }
            });
            session.setSessionActivity(PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } catch (Throwable t) {
            session = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // دکمهٔ مدیا (بلوتوث/هدست) — مستقیم به MediaSession برود
        if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            if (session != null) {
                MediaButtonReceiver.handleIntent(session, intent);
                if (pm.current() != null) {
                    startForeground(NOTIF_ID, build());
                    notifActive = true;
                }
                return START_NOT_STICKY;
            }
        }
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
                    if (pm.isPlaying()) pm.toggle();
                    if (session != null) session.setActive(false);
                    pm.detach(notifListener);
                    stopForeground(true);
                    notifActive = false;
                    stopSelf();
                    return START_NOT_STICKY;
            }
        }
        if (pm.current() == null) {
            if (session != null) session.setActive(false);
            stopForeground(true);
            notifActive = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (session != null) session.setActive(true);
        updatePlaybackState();
        startForeground(NOTIF_ID, build());
        notifActive = true;
        return START_NOT_STICKY;
    }

    private void refresh() {
        Track cur = pm.current();
        if (cur == null) {
            if (session != null) session.setActive(false);
            if (notifActive) {
                stopForeground(true);
                notifActive = false;
                stopSelf();
            }
            return;
        }
        if (session != null && !session.isActive()) session.setActive(true);
        updatePlaybackState();
        if (notifActive) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, build());
        }
    }

    /** همگام‌سازی PlaybackState + Metadata — منبع متادیتا برای سیستم و بلوتوث (AVRCP) */
    private void updatePlaybackState() {
        if (session == null || pm == null) return;
        try {
            Track t = pm.current();
            long pos = pm.position();
            long dur = pm.duration();
            boolean playing = pm.isPlaying();
            int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            long actions = PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_SEEK_TO;
            session.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, pos, 1.0f)
                    .build());

            if (t != null) {
                MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                                t.performer == null || t.performer.isEmpty() ? t.subtitle() : t.performer)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                                t.chatTitle == null || t.chatTitle.isEmpty() ? "موشک موزیک" : t.chatTitle)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur > 0 ? dur : Math.max(0, t.duration) * 1000L);
                Bitmap art = t.artBitmap != null ? t.artBitmap : (t.art() != null ? t.art() : null);
                if (art != null) {
                    mb.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
                }
                session.setMetadata(mb.build());
            }
        } catch (Throwable ignored) {
        }
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
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(new NotificationCompat.Action(R.drawable.ic_prev, "prev", service(ACTION_PREV, 1)))
                .addAction(new NotificationCompat.Action(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        "toggle", service(ACTION_TOGGLE, 2)))
                .addAction(new NotificationCompat.Action(R.drawable.ic_next, "next", service(ACTION_NEXT, 3)))
                .addAction(new NotificationCompat.Action(R.drawable.ic_close, "close", service(ACTION_CLOSE, 4)))
                .setStyle(new MediaStyle()
                        .setMediaSession(session != null ? session.getSessionToken() : null)
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
        if (session != null) {
            try {
                session.setActive(false);
                session.release();
            } catch (Throwable ignored) {
            }
            session = null;
        }
        super.onDestroy();
    }
}
