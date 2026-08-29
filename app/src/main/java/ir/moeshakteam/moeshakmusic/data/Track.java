package ir.moeshakteam.moeshakmusic.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.drinkless.tdlib.TdApi;

import ir.moeshakteam.moeshakmusic.util.Ui;

/** یک تراک موزیک پیدا شده در اکانت تلگرام — تیم موشک */
public class Track {

    public long chatId;
    public long messageId;
    public String chatTitle = "";
    public String title = "";
    public String performer = "";
    public int duration;
    public int date;
    public int fileId;
    public long expectedSize;
    public int thumbFileId;
    public byte[] artMini; // کوچک‌تصویر آلبوم (فوری)
    public Bitmap artBitmap; // دیکد شده
    public volatile String cachedPath;
    /** -1 = در حال دانلود نیست، 0..99 درصد، -2 = خطا */
    public volatile int downloadPct = -1;
    /** اگه استریم شکست خورد، دفعه بعد دانلود کامل (fallback) */
    public transient volatile boolean streamFailed;
    /** آیا این تراک یه پیام صوتی (ویس) است */
    public boolean isVoice;
    /** موج صدا (برای ویس‌ها) */
    public byte[] waveform;

    public static Track from(long chatId, TdApi.Message m, TdApi.Audio a) {
        if (a == null || a.audio == null) return null;
        Track t = new Track();
        t.chatId = chatId;
        t.messageId = m.id;
        t.date = m.date;
        String fileName = a.fileName == null ? "" : a.fileName;
        if (a.title != null && !a.title.isEmpty()) t.title = a.title;
        else t.title = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : (fileName.isEmpty() ? "بی‌نام" : fileName);
        t.performer = a.performer == null ? "" : a.performer;
        t.duration = a.duration;
        t.fileId = a.audio.id;
        t.expectedSize = a.audio.expectedSize > 0 ? a.audio.expectedSize : a.audio.size;
        if (a.albumCoverThumbnail != null && a.albumCoverThumbnail.file != null)
            t.thumbFileId = a.albumCoverThumbnail.file.id;
        if (a.albumCoverMinithumbnail != null && a.albumCoverMinithumbnail.data != null)
            t.artMini = a.albumCoverMinithumbnail.data;
        return t;
    }

    public Bitmap art() {
        if (artBitmap != null) return artBitmap;
        if (artMini != null) {
            try {
                byte[] data = Base64.decode(artMini, Base64.NO_WRAP);
                artBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignored) {
            }
        }
        return artBitmap;
    }

    public String subtitle() {
        String p = performer == null || performer.isEmpty() ? "هنرمند ناشناس" : performer;
        return chatTitle == null || chatTitle.isEmpty() ? p : p + " • " + chatTitle;
    }

    /** حالت سند — فایل‌های صوتی که به شکل document فرستاده شدن */
    public static Track fromDocument(long chatId, TdApi.Message m, TdApi.Document d) {
        if (d == null || d.document == null) return null;
        Track t = new Track();
        t.chatId = chatId;
        t.messageId = m.id;
        t.date = m.date;
        String fileName = d.fileName == null ? "" : d.fileName;
        t.title = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : (fileName.isEmpty() ? "فایل صوتی" : fileName);
        t.performer = "فایل";
        t.fileId = d.document.id;
        t.expectedSize = d.document.expectedSize > 0 ? d.document.expectedSize : d.document.size;
        return t;
    }

    /** حالت ویس — کد خود پیام (VoiceNote) */
    public static Track fromVoice(long chatId, TdApi.Message m, TdApi.VoiceNote v) {
        if (v == null || v.voice == null) return null;
        Track t = new Track();
        t.chatId = chatId;
        t.messageId = m.id;
        t.date = m.date;
        t.title = "ویس — " + Ui.fmtDuration(v.duration);
        t.performer = "پیام صوتی";
        t.duration = v.duration;
        t.fileId = v.voice.id;
        t.expectedSize = v.voice.expectedSize > 0 ? v.voice.expectedSize : v.voice.size;
        t.isVoice = true;
        if (v.waveform != null) t.waveform = v.waveform;
        return t;
    }

    public boolean sameAs(Track o) {
        return o != null && o.chatId == chatId && o.messageId == messageId;
    }
}
