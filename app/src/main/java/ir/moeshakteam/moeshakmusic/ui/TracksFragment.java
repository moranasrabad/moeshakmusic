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
import android.widget.LinearLayout;
import ir.moeshakteam.moeshakmusic.data.Prefs;

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** صفحهٔ تراک‌ها — تیم موشک */
public class TracksFragment extends Fragment {

    private TrackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView empty;
    private boolean favOnly;
    private final Handler main = new Handler(Looper.getMainLooper());

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
        RecyclerView recycler = v.findViewById(R.id.recycler);

        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick(this::showTrackMenu);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> {
            refresh();
            swipe.setRefreshing(false);
        });
        if (isAdded()) {
            Tg.get(requireContext()).onLibraryChanged = () -> {
                if (TracksFragment.liveTracks != null) TracksFragment.liveTracks.refresh();
                if (TracksFragment.liveFavs != null) TracksFragment.liveFavs.refresh();
            };
        }
        refresh();
        // ثبت نمونهٔ زنده برای سرچ از MainActivity
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

    /** نمونه‌های زنده — MainActivity سرچ را به اینها می‌فرستد */
    public static TracksFragment liveTracks;
    public static TracksFragment liveFavs;

    /** بارگذاری لیست از منبع (کتابخانهٔ دائمی یا فیوریت‌ها) */
    public void refresh() {
        if (adapter == null || !isAdded()) return;
        if (favOnly) {
            adapter.setAll(PlayerManager.favoriteTracks());
            empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
            if (adapter.isEmpty())
                empty.setText(R.string.fav_empty_hint);
        } else {
            adapter.setAll(Tg.get(requireContext()).library);
            empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
            if (adapter.isEmpty())
                empty.setText(R.string.library_empty_hint);
        }
    }

    public void filter(String q) {
        if (adapter != null) adapter.filter(q);
    }


    /** حذف شد — اسکن فقط از بخش SCAN انجام می‌شود تا با کتابخانه قاطی نشود */

    private void showTrackMenu(Track t, int pos) {
        ir.moeshakteam.moeshakmusic.data.DownloadStore ds = ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext());
        ir.moeshakteam.moeshakmusic.data.PlaylistStore ps = ir.moeshakteam.moeshakmusic.data.PlaylistStore.get(requireContext());
        boolean downloaded = ds.isDownloaded(t);
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add(downloaded ? getString(R.string.downloaded_ok) : getString(R.string.download));
        opts.add(getString(R.string.add_to_playlist));
        if (ps.all().size() > 0) opts.add(getString(R.string.remove_from_playlist));
        opts.add(getString(R.string.add_to_queue));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(t.title)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == 0) {
                        if (!downloaded) startDownload(t);
                    } else if (w == 1) {
                        ir.moeshakteam.moeshakmusic.ui.PlaylistPicker.show(requireActivity(), java.util.Collections.singletonList(t));
                    } else if (w == 2 && ps.all().size() > 0) {
                        String[] names = new String[ps.all().size()];
                        for (int i = 0; i < ps.all().size(); i++) names[i] = ps.all().get(i).name;
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.remove_from_which)
                                .setItems(names, (d2, w2) -> {
                                    ps.removeTrack(names[w2], t.chatId, t.messageId);
                                    Ui.toast(requireContext(), R.string.removed);
                                }).show();
                    } else if (w == opts.size() - 1) {
                        PlayerManager.get(requireContext()).addToQueue(t);
                        Ui.toast(requireContext(), R.string.added_to_queue);
                    }
                }).show();
    }

    private void startDownload(Track t) {
        Ui.toast(requireContext(), "⬇️ دانلود: " + t.title);
        Tg.get(requireContext()).download(t.fileId, t.expectedSize, new Tg.DownloadListener() {
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
}
