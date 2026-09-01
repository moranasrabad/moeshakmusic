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

    // ---------- انتخاب گروهی — تیم موشک ----------
    public interface SelectionListener {
        void onChanged(int count);
    }

    private boolean selectionMode;
    private final java.util.Set<String> selected = new java.util.HashSet<>();
    private SelectionListener selectionListener;

    public void setSelectionListener(SelectionListener l) {
        selectionListener = l;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        selected.clear();
        if (selectionListener != null) selectionListener.onChanged(0);
        notifyDataSetChanged();
    }

    public void toggle(Track t) {
        String k = t.chatId + ":" + t.messageId;
        if (!selected.remove(k)) selected.add(k);
        if (selectionListener != null) selectionListener.onChanged(selected.size());
        notifyDataSetChanged();
    }

    public void selectAll() {
        selected.clear();
        for (Track t : shown) selected.add(t.chatId + ":" + t.messageId);
        if (selectionListener != null) selectionListener.onChanged(selected.size());
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selected.clear();
        if (selectionListener != null) selectionListener.onChanged(0);
        notifyDataSetChanged();
    }

    public List<Track> getSelectedTracks() {
        List<Track> out = new ArrayList<>();
        for (Track t : shown) {
            if (selected.contains(t.chatId + ":" + t.messageId)) out.add(t);
        }
        return out;
    }

    public int getSelectedCount() {
        return selected.size();
    }

    public TrackAdapter(OnClick click) {
        this.click = click;
        setHasStableIds(true); // 🔧 RecyclerView بهینه برای لیست‌های بزرگ
    }

    @Override
    public long getItemId(int position) {
        Track t = shown.get(position);
        return (t.chatId * 31 + t.messageId);
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
        // حالت انتخاب گروهی — شماره ← تیک
        if (selectionMode) {
            String k = t.chatId + ":" + t.messageId;
            h.tvIndex.setText(selected.contains(k) ? "✓" : "○");
            h.tvIndex.setTextColor(selected.contains(k)
                    ? 0xFF22D3EE : h.itemView.getContext().getColor(R.color.moeshak_muted));
            h.tvIndex.setTextSize(18);
        } else {
            h.tvIndex.setTextSize(15);
        }
        h.itemView.setAlpha(selectionMode && selected.contains(t.chatId + ":" + t.messageId) ? 1f : (selectionMode ? 0.75f : 1f));
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
            if (p < 0 || p >= shown.size()) return;
            if (selectionMode) {
                toggle(shown.get(p));
            } else {
                click.on(shown.get(p), p);
            }
        });
        h.itemView.setOnLongClickListener(x -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0 || p >= shown.size()) return true;
            if (selectionMode) {
                toggle(shown.get(p));
            } else {
                longClick.on(shown.get(p), p);
            }
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
