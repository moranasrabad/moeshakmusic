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

        swipe.setOnRefreshListener(this::scan);
        // ساید منو
        androidx.drawerlayout.widget.DrawerLayout drawer = v.findViewById(R.id.drawer);
        if (drawer != null) {
            View bm = v.findViewById(R.id.btnMenu);
            if (bm != null) bm.setOnClickListener(x -> drawer.openDrawer(android.view.Gravity.START));
            bindSideMenu(v, drawer);
        }

        Tg tg = Tg.get(requireContext());
        if (!tg.library.isEmpty()) {
            adapter.setAll(tg.library);
            if (favOnly) {
                java.util.List<Track> favs = PlayerManager.favoriteTracks();
                adapter.setAll(favs);
            }
            empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (!favOnly) {
            scan();
        }
        if (favOnly && adapter.isEmpty()) empty.setVisibility(View.VISIBLE);
    }

    public void filter(String q) {
        if (adapter != null) adapter.filter(q);
    }

    private void bindSideMenu(View v, androidx.drawerlayout.widget.DrawerLayout drawer) {
        View.OnClickListener go = x -> {
            drawer.closeDrawer(android.view.Gravity.START);
            int id = x.getId();
            if (id == R.id.sidePlaylists) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new PlaylistsFragment()).addToBackStack("pl").commit();
            } else if (id == R.id.sideFavorites) {
                List<Track> favs = PlayerManager.favoriteTracks();
                if (favs.isEmpty()) {
                    Ui.toast(requireContext(), getString(R.string.fav_empty));
                    return;
                }
                PlayerManager.get(requireContext()).play(favs, 0);
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
            } else if (id == R.id.sideDownloads) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new DownloadsFragment()).addToBackStack("dl").commit();
            } else if (id == R.id.sideChannels) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new ChannelsFragment()).addToBackStack("cn").commit();
            } else if (id == R.id.sideChats) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new ChatsFragment()).addToBackStack("ct").commit();
            } else if (id == R.id.sideProxy) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new ProxyFragment()).addToBackStack("px").commit();
            } else if (id == R.id.sideLog) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new LogFragment()).addToBackStack("lg").commit();
            } else if (id == R.id.sideSettings) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new SettingsFragment()).addToBackStack("st").commit();
            } else if (id == R.id.sideTheme) {
                int mode = (Prefs.get(requireContext()).themeMode() + 1) % 3;
                Prefs.get(requireContext()).setThemeMode(mode);
                ir.moeshakteam.moeshakmusic.App.applyTheme(requireContext());
            }
        };
        int[] ids = {R.id.sidePlaylists, R.id.sideFavorites, R.id.sideDownloads, R.id.sideChannels,
                R.id.sideChats, R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};
        for (int res : ids) {
            View vv = v.findViewById(res);
            if (vv != null) vv.setOnClickListener(go);
        }
    }

    private void scan() {
        Tg tg = Tg.get(requireContext());
        if (tg.auth() != Tg.Auth.READY) return;
        if (tg.isScanning()) {
            Ui.toast(requireContext(), "اسکن در حال اجراست");
            return;
        }
        String[] options = {"۵۰ چت اول (سریع)", "۱۰۰ چت اول", "۳۰۰ چت اول", "همهٔ چت‌ها (کامل)"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("اسکن دستی — چند چت؟")
                .setItems(options, (d, w) -> {
                    int count = w == 0 ? 50 : w == 1 ? 100 : w == 2 ? 300 : Integer.MAX_VALUE;
                    doScan(count);
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void doScan(int count) {
        Tg tg = Tg.get(requireContext());
        progress.setVisibility(tg.library.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setVisibility(View.GONE);
        swipe.setRefreshing(true);
        final long t0 = System.currentTimeMillis();
        tg.scanRange(0, count, new Tg.ScanListener() {
            @Override
            public void onProgress(int found, int chats) {
                main.post(() -> {
                    if (!isAdded()) return;
                    long sec = (System.currentTimeMillis() - t0) / 1000;
                    progress.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("🔍 " + chats + " چت اسکن شد\n🎵 " + found + " موزیک پیدا شد\n⏱ " + sec + " ثانیه");
                });
            }

            @Override
            public void onDone(int total) {
                main.post(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    swipe.setRefreshing(false);
                    adapter.setAll(Tg.get(requireContext()).library);
                    long sec = (System.currentTimeMillis() - t0) / 1000;
                    empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                    if (adapter.isEmpty()) {
                        empty.setText("🏁 تمام شد — چیزی پیدا نشد\n⏱ " + sec + " ثانیه");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                main.post(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    swipe.setRefreshing(false);
                    Ui.toast(requireContext(), msg);
                });
            }
        });
    }

    private void showTrackMenu(Track t, int pos) {
        ir.moeshakteam.moeshakmusic.data.DownloadStore ds = ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext());
        ir.moeshakteam.moeshakmusic.data.PlaylistStore ps = ir.moeshakteam.moeshakmusic.data.PlaylistStore.get(requireContext());
        boolean downloaded = ds.isDownloaded(t);
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add(downloaded ? "✓ دانلود شده" : "⬇️ دانلود");
        opts.add("➕ افزودن به پلی‌لیست");
        if (ps.all().size() > 0) opts.add("➖ حذف از پلی‌لیست");
        opts.add("🔀 افزودن به صف");
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
                                .setTitle("حذف از کدام پلی‌لیست؟")
                                .setItems(names, (d2, w2) -> {
                                    ps.removeTrack(names[w2], t.chatId, t.messageId);
                                    Ui.toast(requireContext(), "حذف شد");
                                }).show();
                    } else if (w == opts.size() - 1) {
                        PlayerManager.get(requireContext()).addToQueue(t);
                        Ui.toast(requireContext(), "به صف اضافه شد");
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
