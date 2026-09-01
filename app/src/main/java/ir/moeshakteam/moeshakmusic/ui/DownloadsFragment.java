package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.DownloadStore;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** لیست دانلودها با گروه‌بندی کانال + دانلودهای فعال (پیشرفت زنده + لغو) — تیم موشک */
public class DownloadsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView empty, tvTitle;
    private LinearLayout activeList;
    private TextView activeHeader;
    private DownloadsAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** حالت انتخاب کانال برای دانلود گروهی */
    private boolean pickMode;
    private String pendingChannel;
    private final Runnable dlHook = () -> main.post(this::renderActive);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);
        tvTitle = v.findViewById(R.id.tvTitle);
        activeList = v.findViewById(R.id.activeList);
        activeHeader = v.findViewById(R.id.activeHeader);
        adapter = new DownloadsAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        View bDl = v.findViewById(R.id.btnDownloadChannel);
        if (bDl != null) bDl.setOnClickListener(x -> pickChannelToDownload());
        Tg.get(requireContext()).addDownloadsListener(dlHook);

        // ---------- وایر کردن نوار انتخاب گروهی — تیم موشک ----------
        selectionBar = v.findViewById(R.id.selectionBar);
        tvSelCount = v.findViewById(R.id.tvSelCount);
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
            View bAll = v.findViewById(R.id.btnSelAll);
            if (bAll != null) bAll.setOnClickListener(x -> {
                for (DownloadStore.Entry e : adapter.getItems()) selected.add(e.key);
                updateSelectionBar();
                refresh();
            });
            View bNone = v.findViewById(R.id.btnSelNone);
            if (bNone != null) bNone.setOnClickListener(x -> {
                selected.clear();
                updateSelectionBar();
                refresh();
            });
            View bClose = v.findViewById(R.id.btnSelClose);
            if (bClose != null) bClose.setOnClickListener(x -> exitSelection());
            View bPl = v.findViewById(R.id.btnSelPlaylist);
            if (bPl != null) bPl.setOnClickListener(x -> addSelectedToPlaylist());
            View bDel = v.findViewById(R.id.btnSelDelete);
            if (bDel != null) bDel.setOnClickListener(x -> deleteSelected());
        }

        // دکمهٔ «انتخاب» در هدر
        View bSelect = v.findViewById(R.id.btnSelectMode);
        if (bSelect != null) bSelect.setOnClickListener(x -> {
            if (selectionMode) exitSelection();
            else enterSelection();
        });

        refresh();
    }

    @Override
    public void onDestroyView() {
        try { Tg.get(requireContext()).removeDownloadsListener(dlHook); } catch (Throwable ignored) {}
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activeList != null) renderActive();
    }

    private void refresh() {
        List<DownloadStore.Entry> all = DownloadStore.get(requireContext()).all();
        adapter.setItems(all);
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        tvTitle.setText(getString(R.string.downloads_title) + " (" + all.size() + ")");
        renderActive();
    }

    /** رندر دانلودهای فعال — پیشرفت زنده + دکمهٔ لغو */
    private void renderActive() {
        if (activeList == null || !isAdded()) return;
        List<Tg.ActiveDownload> actives = Tg.get(requireContext()).activeDownloads();
        activeList.removeAllViews();
        if (activeHeader != null) {
            activeHeader.setVisibility(actives.isEmpty() ? View.GONE : View.VISIBLE);
        }
        for (Tg.ActiveDownload ad : actives) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_active_download, activeList, false);
            TextView tvTitle = row.findViewById(R.id.tvTitle);
            TextView tvSub = row.findViewById(R.id.tvSub);
            android.widget.ProgressBar bar = row.findViewById(R.id.progress);
            ImageButton btnCancel = row.findViewById(R.id.btnCancel);

            if (tvTitle != null) tvTitle.setText(ad.title);
            if (tvSub != null) {
                tvSub.setText(ad.chatTitle == null || ad.chatTitle.isEmpty()
                        ? getString(R.string.downloading, ad.pct < 0 ? 0 : ad.pct)
                        : ad.chatTitle + " • " + getString(R.string.downloading, ad.pct < 0 ? 0 : ad.pct));
            }
            if (bar != null) {
                if (ad.pct >= 0) {
                    bar.setIndeterminate(false);
                    bar.setProgress(ad.pct);
                } else {
                    bar.setIndeterminate(true);
                }
            }
            if (btnCancel != null) {
                btnCancel.setOnClickListener(x -> {
                    Tg.get(requireContext()).cancelDownloadTrack(ad.fileId);
                    Ui.toast(requireContext(), R.string.download_cancelled);
                    renderActive();
                });
            }
            activeList.addView(row);
        }
    }

    /** انتخاب کانال برای دانلود همهٔ موزیک‌هایش */
    private void pickChannelToDownload() {
        Tg tg = Tg.get(requireContext());
        if (tg.library.isEmpty()) {
            Ui.toast(requireContext(), getString(R.string.scan_zero_hint));
            return;
        }
        LinkedHashMap<String, Integer> groups = new LinkedHashMap<>();
        Map<String, Long> ids = new LinkedHashMap<>();
        for (Track t : tg.library) {
            String key = t.chatTitle == null || t.chatTitle.isEmpty() ? "بدون‌نام" : t.chatTitle;
            groups.merge(key, 1, Integer::sum);
            ids.putIfAbsent(key, t.chatId);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(groups.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++)
            names[i] = entries.get(i).getKey() + " (" + entries.get(i).getValue() + " موزیک)";
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.download_channel)
                .setItems(names, (d, w) -> {
                    pendingChannel = entries.get(w).getKey();
                    long cid = ids.get(pendingChannel);
                    int total = 0;
                    for (Track t : tg.library) if (t.chatId == cid && !DownloadStore.get(requireContext()).isDownloaded(t)) total++;
                    if (total == 0) {
                        Ui.toast(requireContext(), R.string.channel_all_downloaded);
                        return;
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.download_channel_confirm, pendingChannel, total))
                            .setPositiveButton(R.string.yes, (d2, w2) -> downloadChannel(cid))
                            .setNegativeButton(R.string.no, null)
                            .show();
                }).show();
    }

    // ---------- انتخاب گروهی ----------
    private boolean selectionMode;
    private final java.util.Set<String> selected = new java.util.HashSet<>();
    private View selectionBar;
    private TextView tvSelCount;

    private void enterSelection() {
        selectionMode = true;
        selected.clear();
        updateSelectionBar();
        refresh();
    }

    private void exitSelection() {
        selectionMode = false;
        selected.clear();
        updateSelectionBar();
        refresh();
    }

    private void updateSelectionBar() {
        if (selectionBar == null || !isAdded()) return;
        selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        if (tvSelCount != null)
            tvSelCount.setText(getString(R.string.sel_count, selected.size()));
    }

    private void toggleEntry(String key) {
        if (!selected.remove(key)) selected.add(key);
        updateSelectionBar();
        refresh();
    }

    private void downloadSelected() {
        List<Track> list = new ArrayList<>();
        for (DownloadStore.Entry e : adapter.getItems()) {
            if (selected.contains(e.key)) {
                Track t = new Track();
                t.chatId = parseChatId(e.key);
                t.messageId = parseMsgId(e.key);
                t.title = e.title;
                t.chatTitle = e.chatTitle;
                t.fileId = e.fileId;
                t.expectedSize = e.size;
                list.add(t);
            }
        }
        if (list.isEmpty()) return;
        Ui.toast(requireContext(), R.string.added_to_queue);
        downloadNext(list, 0);
    }

    private void deleteSelected() {
        int n = 0;
        for (DownloadStore.Entry e : adapter.getItems()) {
            if (selected.contains(e.key)) {
                try {
                    if (e.path != null && !e.path.isEmpty()) {
                        java.io.File f = new java.io.File(e.path);
                        if (f.exists() && f.delete()) Tg.log("🗑 گروهی: " + e.path);
                    }
                } catch (Throwable ignored) {
                }
                DownloadStore.get(requireContext()).remove(e.key);
                n++;
            }
        }
        selected.clear();
        updateSelectionBar();
        refresh();
        Ui.toast(requireContext(), getString(R.string.sel_deleted, n));
    }

    private void addSelectedToPlaylist() {
        List<Track> list = new ArrayList<>();
        for (DownloadStore.Entry e : adapter.getItems()) {
            if (selected.contains(e.key)) {
                Track t = new Track();
                t.chatId = parseChatId(e.key);
                t.messageId = parseMsgId(e.key);
                t.title = e.title;
                t.chatTitle = e.chatTitle;
                t.fileId = e.fileId;
                t.expectedSize = e.size;
                list.add(t);
            }
        }
        if (!list.isEmpty())
            ir.moeshakteam.moeshakmusic.ui.PlaylistPicker.show(requireActivity(), list);
    }

    private void downloadChannel(long chatId) {
        Tg tg = Tg.get(requireContext());
        List<Track> queueDl = new ArrayList<>();
        for (Track t : tg.library) if (t.chatId == chatId && !DownloadStore.get(requireContext()).isDownloaded(t)) queueDl.add(t);
        Ui.toast(requireContext(), getString(R.string.channel_download_started, queueDl.size()));
        downloadNext(queueDl, 0);
    }

    private void downloadNext(List<Track> list, int i) {
        if (i >= list.size()) {
            main.post(() -> {
                Ui.toast(requireContext(), R.string.channel_download_done);
                refresh();
            });
            return;
        }
        Track t = list.get(i);
        Tg.log("⬇️ دانلود کانال [" + (i + 1) + "/" + list.size() + "] " + t.title);
        Tg.get(requireContext()).downloadTrack(t, new Tg.DownloadListener() {
            @Override
            public void onProgress(int pct) {
            }

            @Override
            public void onDone(String path) {
                DownloadStore.get(requireContext()).mark(t, path);
                main.post(() -> downloadNext(list, i + 1));
            }

            @Override
            public void onError(String msg) {
                Tg.log("⚠️ خطای دانلود کانال: " + t.title + " → " + msg);
                main.post(() -> downloadNext(list, i + 1));
            }
        });
    }

    private class DownloadsAdapter extends RecyclerView.Adapter<VH> {
        private List<DownloadStore.Entry> items = new ArrayList<>();

        List<DownloadStore.Entry> getItems() {
            return items;
        }

        void setItems(List<DownloadStore.Entry> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DownloadStore.Entry e = items.get(h.getBindingAdapterPosition());
            h.tvTitle.setText(e.title);
            h.tvSub.setText(e.chatTitle + " • " + Ui.fmtDuration((int) (e.size / 20000)));
            h.itemView.setOnClickListener(x -> {
                if (selectionMode) {
                    toggleEntry(e.key);
                    return;
                }
                // پخش از فایل محلی
                Track t = new Track();
                t.chatId = parseChatId(e.key);
                t.messageId = parseMsgId(e.key);
                t.title = e.title;
                t.chatTitle = e.chatTitle;
                t.fileId = e.fileId;
                t.expectedSize = e.size;
                t.cachedPath = e.path;
                List<Track> one = new ArrayList<>();
                one.add(t);
                PlayerManager.get(requireContext()).play(one, 0);
                if (requireActivity() instanceof MainActivity)
                    ((MainActivity) requireActivity()).openPlayer();
            });
            h.itemView.setOnLongClickListener(x -> {
                if (!selectionMode) enterSelection();
                toggleEntry(e.key);
                return true;
            });
            // نشان انتخاب
            h.tvTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            if (selectionMode) {
                h.tvTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        selected.contains(e.key) ? R.drawable.ic_download_done : 0, 0, 0, 0);
                h.tvTitle.setCompoundDrawablePadding(8);
                h.itemView.setAlpha(selected.contains(e.key) ? 1f : 0.75f);
            } else {
                h.itemView.setAlpha(1f);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static long parseChatId(String key) {
        int i = key.indexOf(':');
        try {
            return Long.parseLong(i > 0 ? key.substring(0, i) : key);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseMsgId(String key) {
        int i = key.indexOf(':');
        try {
            return Long.parseLong(i > 0 ? key.substring(i + 1) : "0");
        } catch (Exception e) {
            return 0;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvSub;

        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub = v.findViewById(R.id.tvSub);
        }
    }
}
