package ir.moeshakteam.moeshakmusic.player;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.chibde.visualizer.CircleBarVisualizer;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * مدیریت پخش موزیک با ExoPlayer — استریم مستقیم + کش محلی + ویژوالایزر. تیم موشک
 */
public final class PlayerManager {

    public interface Listener {
        default void onTrackChanged(Track t) {
        }

        default void onPlayStateChanged(boolean playing) {
        }

        default void onProgress(long pos, long dur) {
        }

        default void onDownload(Track t, int pct, String path, String err) {
        }
    }

    private static PlayerManager inst;

    private final Context ctx;
    private ExoPlayer player;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    public final List<Track> queue = new CopyOnWriteArrayList<>();
    public volatile int index = -1;
    /** حالت تکرار: 0=خاموش 1=همه 2=یک آهنگ */
    public volatile int repeatMode = 0;
    public volatile boolean shuffle = false;
    /** ترتیب شافل (ایندکس‌های صف) */
    private final List<Integer> shuffleOrder = new CopyOnWriteArrayList<>();
    /** علاقه‌مندی‌ها — chatId:messageId */
    public static final java.util.Set<String> FAVORITES = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static String key(Track t) {
        return t.chatId + ":" + t.messageId;
    }

    public boolean isFavorite(Track t) {
        return FAVORITES.contains(key(t));
    }

    public void toggleFavorite(Track t) {
        if (!FAVORITES.remove(key(t))) FAVORITES.add(key(t));
        // ذخیرهٔ دائمی فیوریت‌ها
        ir.moeshakteam.moeshakmusic.data.Tg.get(ctx).saveFavorites();
    }

    /** صف علاقه‌مندی‌ها بر اساس ترتیب کتابخانه */
    public static List<Track> favoriteTracks() {
        List<Track> out = new ArrayList<>();
        for (Track t : ir.moeshakteam.moeshakmusic.data.Tg.get(ir.moeshakteam.moeshakmusic.App.get()).library) {
            if (FAVORITES.contains(key(t))) out.add(t);
        }
        return out;
    }

    private void rebuildShuffle() {
        shuffleOrder.clear();
        for (int i = 0; i < queue.size(); i++) shuffleOrder.add(i);
        java.util.Collections.shuffle(shuffleOrder, new java.util.Random());
        // آهنگ فعلی اول بماند
        shuffleOrder.remove(Integer.valueOf(index));
        shuffleOrder.add(0, index);
    }

    /** ایندکس بعدی بر اساس شافل/ریپیت */
    private int nextIndex() {
        if (queue.isEmpty()) return -1;
        if (shuffle) {
            int pos = shuffleOrder.indexOf(index);
            if (pos + 1 < shuffleOrder.size()) return shuffleOrder.get(pos + 1);
            rebuildShuffle();
            return shuffleOrder.isEmpty() ? -1 : shuffleOrder.get(0);
        }
        return index + 1 < queue.size() ? index + 1 : (repeatMode == 1 ? 0 : -1);
    }

    private int prevIndex() {
        if (queue.isEmpty()) return -1;
        if (shuffle) {
            int pos = shuffleOrder.indexOf(index);
            if (pos > 0) return shuffleOrder.get(pos - 1);
            return shuffleOrder.isEmpty() ? -1 : shuffleOrder.get(shuffleOrder.size() - 1);
        }
        return index - 1 >= 0 ? index - 1 : (repeatMode == 1 ? queue.size() - 1 : -1);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private CircleBarVisualizer vizView;
    private int vizSessionId;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player != null && !listeners.isEmpty()) {
                long dur = player.getDuration();
                for (Listener l : listeners) l.onProgress(player.getCurrentPosition(), dur);
            }
            if (player != null && player.isPlaying()) main.postDelayed(this, 400);
        }
    };

    private final Player.Listener exoListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) main.post(PlayerManager.this::next);
            if (state == Player.STATE_READY) bindVisualizer();
            notifyState();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            notifyState();
            // اتصال session صوتی به ویژوالایزر را دوباره امتحان کن
            if (isPlaying) main.postDelayed(PlayerManager.this::bindVisualizer, 250);
            if (isPlaying) {
                main.removeCallbacks(ticker);
                main.post(ticker);
                PlaybackService.start(ctx);
            } else {
                main.removeCallbacks(ticker);
                PlaybackService.update(ctx);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Tg.log("⚠️ خطای پلیر: " + (error.getCause() != null ? error.getCause() : error.getMessage()));
            Track cur = current();
            if (cur != null && cur.cachedPath == null && !cur.streamFailed) {
                cur.streamFailed = true;
                main.post(() -> load(index, true));
                return;
            }
            Ui.toast(ctx, ctx.getString(R.string.player_error));
            main.postDelayed(PlayerManager.this::next, 900);
        }
    };

    public static synchronized PlayerManager get(Context c) {
        if (inst == null) inst = new PlayerManager(c.getApplicationContext());
        return inst;
    }

    private PlayerManager(Context c) {
        ctx = c.getApplicationContext();
    }

    private synchronized ExoPlayer p() {
        if (player == null) {
            DataSource.Factory factory = new AppDataSource.Factory(ctx);
            player = new ExoPlayer.Builder(ctx, new DefaultMediaSourceFactory(factory)).build();
            player.addListener(exoListener);
            // ویژوالایزر بعد از آماده شدن session صوتی وصل شود
            main.postDelayed(this::bindVisualizer, 400);
            main.postDelayed(this::bindVisualizer, 1200);
        }
        return player;
    }

    // ---------- کنترل ----------

    public void play(List<Track> tracks, int idx) {
        queue.clear();
        queue.addAll(tracks);
        index = idx;
        if (shuffle) rebuildShuffle();
        load(idx, true);
    }

    public void load(int idx, boolean autoplay) {
        if (idx < 0 || idx >= queue.size()) return;
        index = idx;
        Track t = queue.get(idx);
        for (Listener l : listeners) l.onTrackChanged(t);
        // ۱) کش حافظه
        if (t.cachedPath != null && new File(t.cachedPath).exists()) {
            playUri(Uri.fromFile(new File(t.cachedPath)), autoplay);
            return;
        }
        // ۲) کش دائمی (DownloadStore)
        String stored = ir.moeshakteam.moeshakmusic.data.DownloadStore.get(ctx).pathOf(t);
        if (stored != null && new File(stored).exists()) {
            t.cachedPath = stored;
            playUri(Uri.fromFile(new File(stored)), autoplay);
            return;
        }
        // مسیر مطمئن v2: دانلود کامل (با درصد) → پخش از فایل محلی
        // (استریم tdlib:// بعداً وقتی پایدار شد برمی‌گردد)
        t.downloadPct = 0;
        Tg.log("▶️ شروع دانلود برای پخش: " + t.title + " (fileId=" + t.fileId + ")");
        Tg.get(ctx).downloadTrack(t, new Tg.DownloadListener() {
            @Override
            public void onProgress(int pct) {
                t.downloadPct = pct;
                for (Listener l : listeners) l.onDownload(t, pct, null, null);
            }

            @Override
            public void onDone(String path) {
                t.downloadPct = -1;
                t.cachedPath = path;
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(ctx).mark(t, path);
                java.io.File fl = new java.io.File(path);
                Tg.log("✅ دانلود تمام شد: " + path + " (" + fl.length() + " بایت)");
                if (fl.length() == 0) {
                    t.downloadPct = -2;
                    Tg.log("⚠️ فایل صفر بایته — دانلود ناموفق");
                    for (Listener l : listeners) l.onDownload(t, -1, null, "empty file");
                    return;
                }
                for (Listener l : listeners) l.onDownload(t, 100, path, null);
                Track cur = current();
                if (cur != null && cur.sameAs(t)) playUri(Uri.fromFile(new File(path)), autoplay);
            }

            @Override
            public void onError(String msg) {
                t.downloadPct = -2;
                Tg.log("⚠️ خطای دانلود برای پخش: " + msg);
                for (Listener l : listeners) l.onDownload(t, -1, null, msg);
            }
        });
    }

    private void playUri(Uri uri, boolean autoplay) {
        ExoPlayer pl = p();
        pl.setMediaItem(MediaItem.fromUri(uri));
        pl.prepare();
        pl.setPlayWhenReady(autoplay);
    }

    public void toggle() {
        if (index < 0 || index >= queue.size()) {
            if (!queue.isEmpty()) load(0, true);
            return;
        }
        ExoPlayer pl = p();
        if (pl.isPlaying()) pl.pause();
        else pl.play();
    }

    /** توقف کامل — هنگام خروج/خاتمهٔ نشست (صف خالی + توقف پخش) */
    public void stopAll() {
        try {
            if (player != null) {
                player.pause();
                player.clearMediaItems();
            }
        } catch (Throwable ignored) {
        }
        queue.clear();
        index = -1;
        notifyState();
        ir.moeshakteam.moeshakmusic.player.PlaybackService.update(ctx);
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == 2) { // تکرار یک آهنگ
            seekTo(0);
            if (player != null) player.play();
            return;
        }
        int ni = nextIndex();
        if (ni == -1) { // پایان صف بدون تکرار → توقف
            if (player != null) player.pause();
            return;
        }
        load(ni, true);
    }

    public void prev() {
        if (queue.isEmpty()) return;
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }
        int pi = prevIndex();
        if (pi == -1) pi = 0;
        load(pi, true);
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
        if (shuffle) rebuildShuffle();
        notifyState();
    }

    public void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        notifyState();
    }

    public void removeFromQueue(int pos) {
        if (pos < 0 || pos >= queue.size()) return;
        queue.remove(pos);
        if (pos < index) index--;
        else if (pos == index && !queue.isEmpty() && index >= queue.size()) index = queue.size() - 1;
    }

    public void addToQueue(Track t) {
        queue.add(t);
    }

    public void moveInQueue(int from, int to) {
        if (from < 0 || from >= queue.size() || to < 0 || to >= queue.size() || from == to) return;
        Track t = queue.remove(from);
        queue.add(to, t);
        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
    }

    public void seekTo(long ms) {
        if (player != null) player.seekTo(Math.max(0, ms));
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public Track current() {
        if (index >= 0 && index < queue.size()) return queue.get(index);
        return null;
    }

    public long position() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    public long duration() {
        return player == null ? 0 : player.getDuration();
    }

    // ---------- ویژوالایزر ----------

    /** وصل کردن ویوی میله‌ای به session صوتی ExoPlayer */
    public void setVisualizerSession(CircleBarVisualizer v) {
        vizView = v;
        bindVisualizer();
    }

    /**
     * اتصال ویوی میله‌ای به session صوتی ExoPlayer — کتابخانهٔ audiovisualizer
     * خودش کپچر موج واقعی صدا را انجام می‌دهد و میله‌ها را با آن می‌رقصاند.
     */
    public void bindVisualizer() {
        if (player == null || vizView == null) return;
        boolean micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (!micGranted) return;
        int sid = player.getAudioSessionId();
        if (sid == 0) return;
        if (sid == vizSessionId) return;
        try {
            try { vizView.release(); } catch (Throwable ignored) {}
            vizView.setPlayer(sid);
            vizSessionId = sid;
        } catch (Throwable ignored) {
        }
    }

    // ---------- listener ها ----------

    public void attach(Listener l) {
        listeners.add(l);
        Track t = current();
        if (t != null) l.onTrackChanged(t);
        l.onPlayStateChanged(isPlaying());
    }

    public void detach(Listener l) {
        listeners.remove(l);
    }

    private void notifyState() {
        boolean ip = isPlaying();
        for (Listener l : listeners) l.onPlayStateChanged(ip);
    }
}
