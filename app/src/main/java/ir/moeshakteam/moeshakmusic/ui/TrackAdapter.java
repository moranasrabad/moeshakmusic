package ir.moeshakteam.moeshakmusic.ui;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** آداپتور لیست تراک‌ها — تیم موشک */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {

    public interface OnClick {
        void on(Track t, int pos);
    }

    public interface OnLongClick {
        void on(Track t, int pos);
    }

    private final List<Track> all = new ArrayList<>();
    private final List<Track> shown = new ArrayList<>();
    private final OnClick click;
    private OnLongClick longClick;
    private Track now;
    private String query = "";

    public TrackAdapter(OnClick click) {
        this.click = click;
    }

    public void setLongClick(OnLongClick lc) {
        this.longClick = lc;
    }

    public void setAll(List<Track> tracks) {
        all.clear();
        all.addAll(tracks);
        cachedQuery = "";
        cachedResult.clear();
        refilter();
    }

    public void setNow(Track t) {
        now = t;
        notifyDataSetChanged();
    }

    public void filter(String q) {
        query = q == null ? "" : q;
        refilter();
    }

    private String cachedQuery = "";
    private final java.util.List<Track> cachedResult = new ArrayList<>();

    private void refilter() {
        shown.clear();
        if (query.trim().isEmpty()) {
            cachedQuery = "";
            cachedResult.clear();
            shown.addAll(all);
        } else {
            String n = query.toLowerCase().trim();
            if (n.equals(cachedQuery) && !cachedResult.isEmpty()) {
                shown.addAll(cachedResult);
            } else {
                cachedResult.clear();
                for (Track t : all) {
                    if ((t.title + " " + t.performer + " " + t.chatTitle).toLowerCase().contains(n)) cachedResult.add(t);
                }
                cachedQuery = n;
                shown.addAll(cachedResult);
            }
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return shown.isEmpty();
    }

    public List<Track> getShown() {
        return shown;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Track t = shown.get(position);
        h.tvTitle.setText(t.title);
        h.tvSubtitle.setText(t.subtitle());
        h.tvDuration.setText(Ui.fmtDuration(t.duration));
        // تامبنیل: مینی‌تامب ← کاور آلبوم ← عکس کانال ← گرادیان برند
        ir.moeshakteam.moeshakmusic.data.ArtLoader.load(t, h.art);
        boolean isNow = t.sameAs(now);
        h.ivNow.setVisibility(isNow ? View.VISIBLE : View.GONE);
        if (isNow) {
            android.util.TypedValue tv = new android.util.TypedValue();
            h.tvTitle.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary, tv, true);
            h.tvTitle.setTextColor(tv.data);
        } else {
            android.util.TypedValue tv = new android.util.TypedValue();
            h.tvTitle.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface, tv, true);
            h.tvTitle.setTextColor(tv.data);
        }
        boolean downloaded = ir.moeshakteam.moeshakmusic.data.DownloadStore
                .get(h.itemView.getContext()).isDownloaded(t);
        // شماره ترتیب یا تیک دانلود
        int idxShown = h.getBindingAdapterPosition() + 1;
        h.tvIndex.setText(downloaded ? "✓" : String.valueOf(idxShown));
        h.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(
                downloaded ? R.drawable.ic_download_done : 0, 0, 0, 0);
        h.tvDuration.setCompoundDrawablePadding(6);
        h.itemView.setOnClickListener(x -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0 && p < shown.size()) click.on(shown.get(p), p);
        });
        h.itemView.setOnLongClickListener(x -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0 || p >= shown.size()) return true;
            longClick.on(shown.get(p), p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView art, ivNow;
        final TextView tvIndex, tvTitle, tvSubtitle, tvDuration;

        VH(@NonNull View v) {
            super(v);
            art = v.findViewById(R.id.art);
            ivNow = v.findViewById(R.id.ivNow);
            tvIndex = v.findViewById(R.id.tvIndex);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSubtitle = v.findViewById(R.id.tvSubtitle);
            tvDuration = v.findViewById(R.id.tvDuration);
        }
    }
}
