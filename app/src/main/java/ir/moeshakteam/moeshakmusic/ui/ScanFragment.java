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

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * بخش جداکنندهٔ نتایج اسکن از کتابخانه — تیم موشک
 * اینجا اسکن می‌کنی، نتایج زنده می‌آیند و اگر خوشت آمد «افزودن به کتابخانه» می‌زنی.
 */
public class ScanFragment extends Fragment {

    private TrackAdapter adapter;
    private TextView state, detail, btnScan, btnAdd, btnClear;
    private ProgressBar bar;
    private View card;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int depth = 100;
    private final long t0 = 0;
    private long startedAt;
    private boolean running;

    private static final int[] DEPTHS = {50, 100, 300, Integer.MAX_VALUE};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        state = v.findViewById(R.id.tvScanState);
        detail = v.findViewById(R.id.tvScanDetail);
        bar = v.findViewById(R.id.scanProgress);
        card = v.findViewById(R.id.scanCard);
        btnScan = v.findViewById(R.id.btnScan);
        btnAdd = v.findViewById(R.id.btnAddAll);
        btnClear = v.findViewById(R.id.btnClear);

        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick(this::showResultMenu);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // چیپ‌های عمق اسکن
        int[] chipIds = {R.id.chip50, R.id.chip100, R.id.chip300, R.id.chipAll};
        View[] chips = new View[chipIds.length];
        for (int i = 0; i < chipIds.length; i++) {
            final int idx = i;
            chips[i] = v.findViewById(chipIds[i]);
            chips[i].setOnClickListener(x -> {
                depth = DEPTHS[idx];
                for (int j = 0; j < chips.length; j++)
                    chips[j].setActivated(j == idx);
            });
        }
        chips[1].setActivated(true); // پیش‌فرض ۱۰۰

        btnScan.setOnClickListener(x -> {
            if (running) {
                Tg.get(requireContext()).cancelScan();
                Ui.toast(requireContext(), R.string.scan_canceling);
                return;
            }
            startScan();
        });
        btnAdd.setOnClickListener(x -> addToLibrary());
        btnClear.setOnClickListener(x -> {
            Tg.get(requireContext()).clearScanResults();
            refreshResults();
            updateButtons();
        });

        refreshResults();
        updateButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshResults();
        updateButtons();
    }

    private void startScan() {
        Tg tg = Tg.get(requireContext());
        if (tg.auth() != Tg.Auth.READY) {
            Ui.toast(requireContext(), R.string.login_first);
            return;
        }
        running = true;
        startedAt = System.currentTimeMillis();
        btnScan.setText(R.string.scan_cancel);
        state.setText(R.string.scan_running);
        detail.setVisibility(View.VISIBLE);
        bar.setIndeterminate(true);
        final int startCount = tg.scanResults.size();
        tg.scanRange(0, depth, new Tg.ScanListener() {
            @Override
            public void onProgress(int found, int chats) {
                main.post(() -> {
                    if (!isAdded()) return;
                    long sec = (System.currentTimeMillis() - startedAt) / 1000;
                    detail.setVisibility(View.VISIBLE);
                    detail.setText(getString(R.string.scan_progress_line, chats, found, sec));
                    refreshResults();
                    updateButtons();
                    bar.setIndeterminate(false);
                    int cap = depth == Integer.MAX_VALUE ? Math.max(chats, 1) : Math.max(depth, 1);
                    bar.setMax(cap);
                    bar.setProgress(Math.min(chats, cap));
                    state.setText(getString(R.string.scan_status_bar, chats, found));
                });
            }

            @Override
            public void onDone(int total) {
                main.post(() -> {
                    if (!isAdded()) return;
                    running = false;
                    btnScan.setText(R.string.scan_start);
                    state.setText(R.string.scan_ready);
                    long sec = (System.currentTimeMillis() - startedAt) / 1000;
                    detail.setText(getString(R.string.scan_done_line, sec));
                    bar.setIndeterminate(false);
                    refreshResults();
                    updateButtons();
                    // 🎁 دیالوگ پیشنهادی — تا نتایج قاطی کتابخانه شوند بدون قدم اضافه
                    int n = Tg.get(requireContext()).scanResults.size();
                    if (n > 0 && isAdded()) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.scan_finished, n))
                                .setMessage(R.string.scan_add_dialog)
                                .setPositiveButton(R.string.scan_add_yes, (d2, w2) -> addToLibrary())
                                .setNegativeButton(R.string.no, null)
                                .show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                main.post(() -> {
                    if (!isAdded()) return;
                    running = false;
                    btnScan.setText(R.string.scan_start);
                    state.setText(R.string.scan_ready);
                    bar.setIndeterminate(false);
                    Ui.toast(requireContext(), msg);
                    refreshResults();
                    updateButtons();
                });
            }
        });
    }

    private void refreshResults() {
        if (!isAdded() || adapter == null) return;
        adapter.setAll(Tg.get(requireContext()).scanResults);
        View empty = requireView().findViewById(R.id.empty);
        if (empty != null) empty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateButtons() {
        if (!isAdded()) return;
        int n = Tg.get(requireContext()).scanResults.size();
        btnAdd.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        btnClear.setVisibility(n > 0 && !running ? View.VISIBLE : View.GONE);
        btnAdd.setText(getString(R.string.scan_add_all, n));
    }

    private void addToLibrary() {
        int added = Tg.get(requireContext()).addScanResultsToLibrary();
        if (added == 0) {
            Ui.toast(requireContext(), R.string.already_in_library);
        } else {
            Ui.toast(requireContext(), getString(R.string.scan_added, added));
        }
        refreshResults();
        updateButtons();
    }

    private void showResultMenu(Track t, int pos) {
        java.util.List<String> opts = new java.util.ArrayList<>();
        boolean downloaded = ir.moeshakteam.moeshakmusic.data.DownloadStore
                .get(requireContext()).isDownloaded(t);
        opts.add(downloaded ? getString(R.string.downloaded_ok) : getString(R.string.download));
        opts.add(getString(R.string.add_only_this));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(t.title)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == 0) {
                        if (!downloaded) {
                            Ui.toast(requireContext(), getString(R.string.downloading, 0));
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
                    } else if (w == 1) {
                        Tg.get(requireContext()).addScanResultsToLibrary();
                        refreshResults();
                        updateButtons();
                    }
                }).show();
    }
}
