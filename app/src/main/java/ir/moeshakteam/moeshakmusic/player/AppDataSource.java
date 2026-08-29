package ir.moeshakteam.moeshakmusic.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ir.moeshakteam.moeshakmusic.data.Tg;

/**
 * مسیریاب دیتاسورس: فایل‌های کش‌شده از دیسک، بقیه استریم از تلگرام.
 * تیم موشک — moeshakteam.ir
 */
public final class AppDataSource implements DataSource {

    private final Context ctx;
    private final List<TransferListener> listeners = new ArrayList<>();
    private DataSource active;

    private AppDataSource(Context c) {
        ctx = c.getApplicationContext();
    }

    public static class Factory implements DataSource.Factory {
        private final Context c;

        public Factory(Context c) {
            this.c = c.getApplicationContext();
        }

        @Override
        public DataSource createDataSource() {
            return new AppDataSource(c);
        }
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        listeners.add(transferListener);
    }

    private DataSource pick(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            FileDataSource fds = new FileDataSource();
            for (TransferListener l : listeners) fds.addTransferListener(l);
            return fds;
        }
        TdlibDataSource tds = new TdlibDataSource(Tg.get(ctx));
        for (TransferListener l : listeners) tds.addTransferListener(l);
        return tds;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        active = pick(dataSpec.uri);
        return active.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return active.read(buffer, offset, readLength);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return active == null ? null : active.getUri();
    }

    @Override
    public void close() throws IOException {
        if (active != null) {
            active.close();
            active = null;
        }
    }
}
