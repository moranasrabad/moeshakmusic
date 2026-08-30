# -*- coding: utf-8 -*-
# پچ DownloadsFragment — انتخاب گروهی + حذف/پلی‌لیست گروهی
import io

p = 'ui/DownloadsFragment.java'
s = io.open(p, encoding='utf-8').read()

# فیلدهای انتخاب
s = s.replace('''    private void downloadChannel(long chatId) {''','''    // ---------- انتخاب گروهی ----------
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

    private void downloadChannel(long chatId) {''')

# onViewCreated — نوار انتخاب
s = s.replace('''        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());''','''        v.findViewById(R.id.btnBack).setOnClickListener(x -> {
            if (selectionMode) {
                exitSelection();
                return;
            }
            requireActivity().onBackPressed();
        });

        // نوار انتخاب گروهی
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
        }''')

# آداپتور — کلیک/لانگ‌کلیک حالت انتخاب + setItems با نگه‌داشتن لیست
s = s.replace('''    private class DownloadsAdapter extends RecyclerView.Adapter<VH> {
        private List<DownloadStore.Entry> items = new ArrayList<>();

        void setItems(List<DownloadStore.Entry> list) {
            items = list;
            notifyDataSetChanged();
        }''','''    private class DownloadsAdapter extends RecyclerView.Adapter<VH> {
        private List<DownloadStore.Entry> items = new ArrayList<>();

        List<DownloadStore.Entry> getItems() {
            return items;
        }

        void setItems(List<DownloadStore.Entry> list) {
            items = list;
            notifyDataSetChanged();
        }''')

s = s.replace('''            h.itemView.setOnClickListener(x -> {
                // پخش از فایل محلی
                Track t = new Track();''','''            h.itemView.setOnClickListener(x -> {
                if (selectionMode) {
                    toggleEntry(e.key);
                    return;
                }
                // پخش از فایل محلی
                Track t = new Track();''')

s = s.replace('''            h.itemView.setOnLongClickListener(x -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.delete_download_confirm, e.title))
                        .setPositiveButton(R.string.yes, (d, w) -> {
                            // حذف فایل از حافظه هم
                            try {
                                if (e.path != null && !e.path.isEmpty()) {
                                    java.io.File f = new java.io.File(e.path);
                                    if (f.exists() && f.delete()) {
                                        Tg.log("🗑 فایل حذف شد: " + e.path);
                                    } else {
                                        Tg.log("⚠️ فایل پیدا نشد: " + e.path);
                                    }
                                }
                            } catch (Throwable t) {
                                Tg.log("⚠️ حذف فایل: " + t);
                            }
                            DownloadStore.get(requireContext()).remove(e.key);
                            refresh();
                            Ui.toast(requireContext(), R.string.removed);
                        })
                        .setNegativeButton(R.string.no, null).show();
                return true;
            });''','''            h.itemView.setOnLongClickListener(x -> {
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
            }''')

io.open(p, 'w', encoding='utf-8').write(s)
print('DownloadsFragment OK')
