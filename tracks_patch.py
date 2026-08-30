# -*- coding: utf-8 -*-
# پچ TracksFragment — نوار انتخاب گروهی + اکشن‌ها + منوی انتخاب
import io

p = 'ui/TracksFragment.java'
s = io.open(p, encoding='utf-8').read()

# فیلدها
s = s.replace('''    private TrackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView empty;
    private boolean favOnly;
    private final Handler main = new Handler(Looper.getMainLooper());''','''    private TrackAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView empty;
    private boolean favOnly;
    private final Handler main = new Handler(Looper.getMainLooper());

    // ---------- انتخاب گروهی ----------
    private View selectionBar;
    private TextView tvSelCount;''')

# onViewCreated — وایر کردن نوار انتخاب
s = s.replace('''        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick(this::showTrackMenu);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);''','''        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick(this::showTrackMenu);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // نوار انتخاب گروهی
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
        }''')

# refresh — خروج از حالت انتخاب هنگام رفرش
s = s.replace('''    /** بارگذاری لیست از منبع (کتابخانهٔ دائمی یا فیوریت‌ها) */
    public void refresh() {
        if (adapter == null || !isAdded()) return;''','''    /** بارگذاری لیست از منبع (کتابخانهٔ دائمی یا فیوریت‌ها) */
    public void refresh() {
        if (adapter == null || !isAdded()) return;
        if (adapter.isSelectionMode()) {
            adapter.setSelectionMode(false);
            if (selectionBar != null) selectionBar.setVisibility(View.GONE);
        }''')

# showTrackMenu — گزینهٔ ورود به حالت انتخاب
s = s.replace('''        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add(downloaded ? getString(R.string.downloaded_ok) : getString(R.string.download));
        opts.add(getString(R.string.add_to_playlist));
        if (ps.all().size() > 0) opts.add(getString(R.string.remove_from_playlist));
        opts.add(getString(R.string.add_to_queue));''','''        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add(downloaded ? getString(R.string.downloaded_ok) : getString(R.string.download));
        opts.add(getString(R.string.add_to_playlist));
        if (ps.all().size() > 0) opts.add(getString(R.string.remove_from_playlist));
        opts.add(getString(R.string.add_to_queue));
        opts.add(getString(R.string.sel_enter));''')

# هندلر گزینهٔ جدید (آخرین آیتم)
s = s.replace('''                    } else if (w == opts.size() - 1) {
                        PlayerManager.get(requireContext()).addToQueue(t);
                        Ui.toast(requireContext(), R.string.added_to_queue);
                    }
                }).show();
    }''','''                    } else if (w == opts.size() - 1) {
                        PlayerManager.get(requireContext()).addToQueue(t);
                        Ui.toast(requireContext(), R.string.added_to_queue);
                    } else if (w == opts.size() - 2) {
                        adapter.setSelectionMode(true);
                        adapter.toggle(t);
                        if (selectionBar != null) selectionBar.setVisibility(View.VISIBLE);
                        Ui.toast(requireContext(), R.string.sel_hint);
                    }
                }).show();
    }

    /** دانلود گروهی تراک‌های انتخاب‌شده — ترتیبی */
    private void downloadSelected() {
        List<Track> sel = adapter.getSelectedTracks();
        List<Track> toDl = new java.util.ArrayList<>();
        ir.moeshakteam.moeshakmusic.data.DownloadStore ds =
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext());
        for (Track t : sel) if (!ds.isDownloaded(t)) toDl.add(t);
        if (toDl.isEmpty()) {
            Ui.toast(requireContext(), R.string.sel_all_downloaded);
            return;
        }
        Ui.toast(requireContext(), getString(R.string.channel_download_started, toDl.size()));
        downloadChain(toDl, 0);
    }

    private void downloadChain(List<Track> list, int i) {
        if (!isAdded()) return;
        if (i >= list.size()) {
            Ui.toast(requireContext(), R.string.channel_download_done);
            refresh();
            return;
        }
        Track t = list.get(i);
        Tg.get(requireContext()).download(t.fileId, t.expectedSize, new Tg.DownloadListener() {
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
    }''')

io.open(p, 'w', encoding='utf-8').write(s)
print('TracksFragment OK')
