package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.PlaylistStore;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** پلی‌لیست‌های سفارشی — ساخت/حذف/پخش — تیم موشک */
public class PlaylistsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView empty;
    private PlaylistAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);
        adapter = new PlaylistAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btnNew).setOnClickListener(x -> promptNew(null));
        refresh();
    }

    private void refresh() {
        List<PlaylistStore.Playlist> all = PlaylistStore.get(requireContext()).all();
        adapter.setItems(all);
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void promptNew(Runnable after) {
        EditText et = new EditText(requireContext());
        et.setHint(R.string.playlist_name_hint);
        et.setPadding(48, 24, 48, 24);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.playlist_new)
                .setView(et)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    PlaylistStore.get(requireContext()).create(name);
                    refresh();
                    if (after != null) after.run();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private class PlaylistAdapter extends RecyclerView.Adapter<VH> {
        private List<PlaylistStore.Playlist> items = new ArrayList<>();

        void setItems(List<PlaylistStore.Playlist> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PlaylistStore.Playlist p = items.get(h.getBindingAdapterPosition());
            h.tvName.setText(p.name);
            h.tvCount.setText(getString(R.string.count_tracks, p.tracks.size()));
            h.itemView.setOnClickListener(x -> openPlaylist(p));
            h.btnPlay.setOnClickListener(x -> {
                if (p.tracks.isEmpty()) {
                    Ui.toast(requireContext(), R.string.playlist_empty_toast);
                    return;
                }
                PlayerManager.get(requireContext()).play(p.tracks, 0);
                if (requireActivity() instanceof MainActivity)
                    ((MainActivity) requireActivity()).openPlayer();
            });
            // ✏️ تغییر نام
            h.btnEdit.setOnClickListener(x -> promptRename(p));
            // 🗑 حذف
            h.btnDelete.setOnClickListener(x -> confirmDelete(p));
            // ⬇️ دانلود کل پلی‌لیست
            h.btnDownloadAll.setOnClickListener(x -> downloadPlaylist(p));
            h.itemView.setOnLongClickListener(x -> {
                confirmDelete(p);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private void promptRename(PlaylistStore.Playlist p) {
        EditText et = new EditText(requireContext());
        et.setHint(R.string.playlist_name_hint);
        et.setText(p.name);
        et.setPadding(48, 24, 48, 24);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.playlist_rename)
                .setView(et)
                .setPositiveButton(R.string.save_btn, (d, w) -> {
                    String nn = et.getText().toString().trim();
                    if (nn.isEmpty()) return;
                    if (PlaylistStore.get(requireContext()).rename(p.name, nn)) {
                        refresh();
                        Ui.toast(requireContext(), R.string.playlist_renamed);
                    } else {
                        Ui.toast(requireContext(), R.string.playlist_name_taken);
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void confirmDelete(PlaylistStore.Playlist p) {
        new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.playlist_delete_confirm, p.name))
                .setPositiveButton(R.string.yes, (d, w) -> {
                    PlaylistStore.get(requireContext()).delete(p.name);
                    refresh();
                    Ui.toast(requireContext(), R.string.removed);
                })
                .setNegativeButton(R.string.no, null).show();
    }

    private void downloadPlaylist(PlaylistStore.Playlist p) {
        List<Track> toDl = new ArrayList<>();
        for (Track t : p.tracks)
            if (!ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext()).isDownloaded(t))
                toDl.add(t);
        if (toDl.isEmpty()) {
            Ui.toast(requireContext(), R.string.channel_all_downloaded);
            return;
        }
        Ui.toast(requireContext(), getString(R.string.channel_download_started, toDl.size()));
        downloadNext(toDl, 0);
    }

    private void downloadNext(List<Track> list, int i) {
        if (!isAdded()) return;
        if (i >= list.size()) {
            Ui.toast(requireContext(), R.string.channel_download_done);
            return;
        }
        Track t = list.get(i);
        Tg.log("⬇️ دانلود پلی‌لیست [" + (i + 1) + "/" + list.size() + "] " + t.title);
        Tg.get(requireContext()).download(t.fileId, t.expectedSize, new Tg.DownloadListener() {
            @Override
            public void onProgress(int pct) {
            }

            @Override
            public void onDone(String path) {
                if (!isAdded()) return;
                ir.moeshakteam.moeshakmusic.data.DownloadStore.get(requireContext()).mark(t, path);
                downloadNext(list, i + 1);
            }

            @Override
            public void onError(String msg) {
                Tg.log("⚠️ خطای دانلود پلی‌لیست: " + t.title + " → " + msg);
                downloadNext(list, i + 1);
            }
        });
    }

    private void openPlaylist(PlaylistStore.Playlist p) {
        if (requireActivity() instanceof MainActivity) {
            PlaylistTracksFragment f = new PlaylistTracksFragment();
            Bundle b = new Bundle();
            b.putString(PlaylistTracksFragment.ARG_NAME, p.name);
            f.setArguments(b);
            ((MainActivity) requireActivity()).showFullScreen(f);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName, tvCount;
        final ImageButton btnPlay, btnEdit, btnDelete, btnDownloadAll;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
            btnPlay = v.findViewById(R.id.btnPlay);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnDownloadAll = v.findViewById(R.id.btnDownloadAll);
        }
    }
}
