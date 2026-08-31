package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** صفحهٔ آهنگ‌ها + فیوریت — با انتخاب گروهی — تیم موشک */
public class TracksFragment extends Fragment {

    private TrackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView empty;
    private TextView tvStats;
    private boolean favOnly;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable libHook = () -> main.post(this::refresh);

    // ---------- انتخاب گروهی ----------
    private View selectionBar;
    private TextView tvSelCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        favOnly = args != null && args.getBoolean("fav", false);

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        empty = v.findViewById(R.id.empty);
        tvStats = v.findViewById(R.id.tvLibStats);
        RecyclerView recycler = v.findViewById(R.id.recycler);

        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick(this::showTrackMenu);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // نوار انتخاب گروهی — تیم موشک
        selectionBar = v.findViewById(R.id.selectionBar);
        tvSelCount = v.findViewById(R.id.tvSelCount);
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
            adapter.setSelectionListener(count -> main.post(() -> {
                if (!isAdded() || tvSelCount == null) return;
                tvSelCount.setText(getString(R.string.sel_count, count));
                selectionBar.setVisibility(count > 0 || adapter.isSelectionMode() ? View.VISIBLE : View.GONE);
            }));
            View bAll = v.findViewById(R.id.btnSelAll);
            if (bAll != null) bAll.setOnClickListener(x -> adapter.selectAll());
            View bNone = v.findViewById(R.id.btnSelNone);
            if (bNone != null) bNone.setOnClickListener(x -> adapter.clearSelection());
            View bClose = v.findViewById(R.id.btnSelClose);
            if (bClose != null) bClose.setOnClickListener(x -> {
                adapter.setSelectionMode(false);
                selectionBar.setVisibility(View.GONE);
            });
            View bPlaylist = v.findViewById(R.id.btnSelPlaylist);
            if (bPlaylist != null) bPlaylist.setOnClickListener(x -> {
                List<Track> sel = adapter.getSelectedTracks();
                if (!sel.isEmpty())
                    ir.moeshakteam.moeshakmusic.ui.PlaylistPicker.show(requireActivity(), sel);
            });
            View bDownload = v.findViewById(R.id.btnSelDownload);
            if (bDownload != null) bDownload.setOnClickListener(x -> downloadSelected());
            // در تب فیوریت: دکمهٔ حذف گروهی = حذف از فیوریت‌ها
            View bDelete = v.findViewById(R.id.btnSelDelete);
            if (bDelete != null) {
                bDelete.setVisibility(favOnly ? View.VISIBLE : View.GONE);
                bDelete.setOnClickListener(x -> deleteSelectedFavs());
            }
        }

        swipe.setOnRefreshListener(() -> {
            Tg tg = Tg.get(requireContext());
            Tg.log("♻️ رفرش دستی " + (favOnly ? "فیوریت‌ها" : "کتابخانه")
                    + " — وضعیت: " + tg.library.size() + " موزیک در کتابخانه، "
                    + ir.moeshakteam.moeshakmusic.player.PlayerManager.favoriteTracks().size() + " فیوریت");
            if (!favOnly) tg.reloadLibraryFromDisk();
            refresh();
            swipe.setRefreshing(false);
        });

        Tg.get(requireContext()).addLibraryListener(libHook);

        refresh();
        if (favOnly) liveFavs = this;
        else liveTracks = this;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        if (liveFavs == this) liveFavs = null;
        if (liveTracks == this) liveTracks = null;
        super.onDestroyView();
    }

    /** نمونه‌های زنده — سرچ MainActivity به اینها مسیریابی می‌شود */
    public static TracksFragment liveTracks;
    public static TracksFragment liveFavs;

    /** بارگذاری لیست از منبع (کتابخانه یا فیوریت‌ها) */
    public void refresh() {
        if (adapter == null || !isAdded()) return;
        if (adapter.isSelectionMode()) {
            adapter.setSelectionMode(false);
            if (selectionBar != null) selectionBar.setVisibility(View.GONE);
        }
        if (favOnly) {
            java.util.List<Track> favs = PlayerManager.favoriteTracks();
            adapter.setAll(favs);
            empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
            if (adapter.isEmpty()) empty.setText(R.string.fav_empty_hint);
            if (tvStats != null) {
                tvStats.setText(adapter.isEmpty()
                        ? getString(R.string.stats_fav_empty)
                        : getString(R.string.stats_fav, favs.size()));
            }
        } else {
            java.util.List<Track> lib = Tg.get(requireContext()).library;
            adapter.setAll(lib);
            empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
            if (adapter.isEmpty()) empty.setText(R.string.library_empty_hint);
            if (tvStats != null) {
                java.util.HashSet<Long> chats = new java.util.HashSet<>();
                for (Track t : lib) chats.add(t.chatId);
                tvStats.setText(adapter.isEmpty()
                        ? getString(R.string.stats_lib_empty)
                        : getString(R.string.stats_lib, lib.size(), chats.size()));
            }
        }
    }

    public void filter(String q) {
        if (adapter != null) adapter.filter(q);
    }

    // ---------- منوی تراک ----------

    private void showTrackMenu(Track t, int pos) {
        ir.moeshakteam.moeshakmusic.data.DownloadStore ds =
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext());
        ir.moeshakteam.moeshakmusic.data.PlaylistStore ps =
                ir.moeshakteam.moeshakmusic.data.PlaylistStore.get(requireContext());
        boolean downloaded = ds.isDownloaded(t);
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add(downloaded ? getString(R.string.downloaded_ok) : getString(R.string.download));
        opts.add(getString(R.string.add_to_playlist));
        if (ps.all().size() > 0) opts.add(getString(R.string.remove_from_playlist));
        opts.add(getString(R.string.add_to_queue));
        opts.add(getString(R.string.sel_enter));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(t.title)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == 0) {
                        if (!downloaded) startDownload(t);
                    } else if (w == 1) {
                        ir.moeshakteam.moeshakmusic.ui.PlaylistPicker.show(requireActivity(),
                                java.util.Collections.singletonList(t));
                    } else if (w == 2 && ps.all().size() > 0) {
                        String[] names = new String[ps.all().size()];
                        for (int i = 0; i < ps.all().size(); i++) names[i] = ps.all().get(i).name;
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.remove_from_which)
                                .setItems(names, (d2, w2) -> {
                                    ps.removeTrack(names[w2], t.chatId, t.messageId);
                                    Ui.toast(requireContext(), R.string.removed);
                                }).show();
                    } else if (w == 3) {
                        PlayerManager.get(requireContext()).addToQueue(t);
                        Ui.toast(requireContext(), R.string.added_to_queue);
                    } else if (w == 4) {
                        adapter.setSelectionMode(true);
                        adapter.toggle(t);
                        if (selectionBar != null) selectionBar.setVisibility(View.VISIBLE);
                        Ui.toast(requireContext(), R.string.sel_hint);
                    }
                }).show();
    }

    private void startDownload(Track t) {
        Ui.toast(requireContext(), "⬇️ " + t.title);
        Tg.get(requireContext()).downloadTrack(t, new Tg.DownloadListener() {
            @Override
            public void onProgress(int pct) {
            }

            @Override
            public void onDone(String path) {
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext()).mark(t, path);
                main.post(() -> Ui.toast(requireContext(), "✓ " + t.title));
            }

            @Override
            public void onError(String msg) {
                main.post(() -> Ui.toast(requireContext(), "⚠️ " + msg));
            }
        });
    }

    /** حذف گروهی از فیوریت‌ها (فقط تب فیوریت) */
    private void deleteSelectedFavs() {
        List<Track> sel = adapter.getSelectedTracks();
        int n = 0;
        for (Track t : sel) {
            if (PlayerManager.FAVORITES.remove(PlayerManager.key(t))) n++;
        }
        ir.moeshakteam.moeshakmusic.data.Tg.get(requireContext()).saveFavorites();
        adapter.clearSelection();
        adapter.setSelectionMode(false);
        if (selectionBar != null) selectionBar.setVisibility(View.GONE);
        refresh();
        Ui.toast(requireContext(), n + " از فیوریت حذف شد");
    }

    // ---------- دانلود گروهی ----------

    private void downloadSelected() {
        List<Track> sel = adapter.getSelectedTracks();
        ir.moeshakteam.moeshakmusic.data.DownloadStore ds =
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext());
        java.util.List<Track> toDl = new java.util.ArrayList<>();
        for (Track t : sel) if (!ds.isDownloaded(t)) toDl.add(t);
        if (toDl.isEmpty()) {
            Ui.toast(requireContext(), R.string.sel_all_downloaded);
            return;
        }
        Ui.toast(requireContext(), getString(R.string.channel_download_started, toDl.size()));
        downloadChain(toDl, 0);
    }

    private void downloadChain(java.util.List<Track> list, int i) {
        if (!isAdded()) return;
        if (i >= list.size()) {
            Ui.toast(requireContext(), R.string.channel_download_done);
            refresh();
            return;
        }
        Track t = list.get(i);
        Tg.get(requireContext()).downloadTrack(t, new Tg.DownloadListener() {
            @Override
            public void onProgress(int pct) {
            }

            @Override
            public void onDone(String path) {
                if (!isAdded()) return;
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext()).mark(t, path);
                main.post(() -> downloadChain(list, i + 1));
            }

            @Override
            public void onError(String msg) {
                main.post(() -> downloadChain(list, i + 1));
            }
        });
    }
}
