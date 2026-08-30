# -*- coding: utf-8 -*-
# پچ TrackAdapter — حالت انتخاب گروهی
import io

p = 'ui/TrackAdapter.java'
s = io.open(p, encoding='utf-8').read()

# فیلدهای انتخاب
s = s.replace('''    private final List<Track> all = new ArrayList<>();
    private final List<Track> shown = new ArrayList<>();
    private final OnClick click;
    private OnLongClick longClick;
    private Track now;
    private String query = "";''','''    private final List<Track> all = new ArrayList<>();
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
    }''')

# onBindViewHolder — نمایش حالت انتخاب
s = s.replace('''        boolean isNow = t.sameAs(now);
        h.ivNow.setVisibility(isNow ? View.VISIBLE : View.GONE);''','''        boolean isNow = t.sameAs(now);
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
        h.itemView.setAlpha(selectionMode && selected.contains(t.chatId + ":" + t.messageId) ? 1f : (selectionMode ? 0.75f : 1f));''')

# کلیک‌ها — در حالت انتخاب، لمس = انتخاب
s = s.replace('''        h.itemView.setOnClickListener(x -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0 && p < shown.size()) click.on(shown.get(p), p);
        });
        h.itemView.setOnLongClickListener(x -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0 || p >= shown.size()) return true;
            longClick.on(shown.get(p), p);
            return true;
        });''','''        h.itemView.setOnClickListener(x -> {
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
        });''')

io.open(p, 'w', encoding='utf-8').write(s)
print('TrackAdapter OK')
