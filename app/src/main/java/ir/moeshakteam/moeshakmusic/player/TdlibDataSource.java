package ir.moeshakteam.moeshakmusic.player;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ir.moeshakteam.moeshakmusic.data.Tg;

/**
 * دیتاسورس استریمی: بازه‌های ۵۱۲ کیلوبایتی از سرور تلگرام (بدون دانلود کل فایل)
 * با پشتیبانی از seek های ExoPlayer. تیم موشک — moeshakteam.ir
 */
public final class TdlibDataSource implements DataSource {

    private static final int CHUNK = 512 * 1024;

    private final Tg tg;
    private final List<TransferListener> listeners = new ArrayList<>();

    private Uri uri;
    private int fileId;
    private long totalSize = C.LENGTH_UNSET;
    private long position;
    private long bytesRemaining = C.LENGTH_UNSET;
    private byte[] chunk;
    private int chunkOffset;
    private int chunkLen;
    private boolean eof;

    public TdlibDataSource(Tg tg) {
        this.tg = tg;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        listeners.add(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        parseUri(uri);
        chunk = null;
        chunkOffset = 0;
        chunkLen = 0;
        eof = false;
        position = dataSpec.position;
        if (dataSpec.length != C.LENGTH_UNSET) {
            bytesRemaining = dataSpec.length;
        } else if (totalSize >= 0) {
            bytesRemaining = Math.max(0, totalSize - position);
        } else {
            bytesRemaining = C.LENGTH_UNSET;
        }
        return bytesRemaining;
    }

    private void parseUri(Uri u) throws IOException {
        // tdlib://file/<fileId>?size=<expected>
        try {
            String path = u.getPath();
            String[] seg = path != null ? path.split("/") : new String[0];
            fileId = Integer.parseInt(seg[seg.length - 1]);
        } catch (Exception e) {
            throw new IOException("bad tdlib uri: " + u);
        }
        totalSize = C.LENGTH_UNSET;
        String q = u.getQueryParameter("size");
        if (q != null) {
            try {
                totalSize = Long.parseLong(q);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public int read(byte[] target, int offset, int readLength) throws IOException {
        if (readLength == 0) return 0;
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;
        if (eof) return C.RESULT_END_OF_INPUT;
        if (chunk == null || chunkOffset >= chunkLen) {
            fillChunk();
            if (chunkLen == 0) {
                eof = true;
                return C.RESULT_END_OF_INPUT;
            }
        }
        int toCopy = Math.min(readLength, chunkLen - chunkOffset);
        if (bytesRemaining != C.LENGTH_UNSET) toCopy = (int) Math.min(toCopy, bytesRemaining);
        System.arraycopy(chunk, chunkOffset, target, offset, toCopy);
        chunkOffset += toCopy;
        position += toCopy;
        if (bytesRemaining != C.LENGTH_UNSET) bytesRemaining -= toCopy;
        return toCopy;
    }

    private void fillChunk() throws IOException {
        long start = (position / 1024) * 1024; // تراز ۱ کیلوبایتی
        int skip = (int) (position - start);
        byte[] data;
        try {
            data = tg.readRemoteChunk(fileId, start, CHUNK);
        } catch (Exception e) {
            throw new IOException("tdlib chunk failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()), e);
        }
        if (data.length <= skip) {
            chunkLen = 0;
            chunkOffset = 0;
            return;
        }
        chunk = data;
        chunkOffset = skip;
        chunkLen = data.length;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        chunk = null;
        chunkLen = 0;
        chunkOffset = 0;
        try {
            tg.cancelDownload(fileId);
        } catch (Throwable ignored) {
        }
    }
}
