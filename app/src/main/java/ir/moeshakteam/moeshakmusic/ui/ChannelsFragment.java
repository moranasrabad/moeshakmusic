package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/** صفحهٔ کانال‌ها — با آواتار حرف اول، تعداد، افزودن به پلی‌لیست و دانلود گروهی — تیم موشک */
public class ChannelsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView empty;
    private ChannelAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channels, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);
        adapter = new ChannelAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Tg tg = Tg.get(requireContext());
        LinkedHashMap<String, List<Track>> groups = new LinkedHashMap<>();
        for (Track t : tg.library) {
            String key = t.chatTitle == null || t.chatTitle.isEmpty() ? "بدون‌نام" : t.chatTitle;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<Map.Entry<String, List<Track>>> entries = new ArrayList<>(groups.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        adapter.setItems(entries);
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class ChannelAdapter extends RecyclerView.Adapter<VH> {
        private List<Map.Entry<String, List<Track>>> items = new ArrayList<>();

        void setItems(List<Map.Entry<String, List<Track>>> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map.Entry<String, List<Track>> e = items.get(h.getBindingAdapterPosition());
            String name = e.getKey();
            List<Track> tracks = e.getValue();
            int downloaded = 0;
            for (Track t : tracks) if (DownloadStore.get(requireContext()).isDownloaded(t)) downloaded++;
            h.tvAvatar.setText(name.substring(0, 1).toUpperCase());
            h.tvName.setText(name);
            String count = getString(R.string.count_tracks, tracks.size());
            if (downloaded > 0) count += " • " + getString(R.string.count_downloaded, downloaded);
            h.tvCount.setText(count);
            // لمس → فیلتر کتابخانه بر اساس این کانال
            h.itemView.setOnClickListener(x -> {
                // شروع پخش کل کانال + باز شدن پلیر در لایهٔ مستقل
                PlayerManager.get(requireContext()).play(tracks, 0);
                if (requireActivity() instanceof MainActivity)
                    ((MainActivity) requireActivity()).openPlayer();
            });
            h.btnAddToPlaylist.setOnClickListener(x -> {
                Ui.toast(requireContext(), R.string.add_channel_to_playlist);
                PlaylistPicker.show(requireActivity(), tracks);
            });
            h.btnDownloadAll.setOnClickListener(x -> {
                List<Track> toDl = new ArrayList<>();
                for (Track t : tracks) if (!DownloadStore.get(requireContext()).isDownloaded(t)) toDl.add(t);
                if (toDl.isEmpty()) {
                    Ui.toast(requireContext(), R.string.channel_all_downloaded);
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.download_channel))
                        .setMessage(getString(R.string.channel_download_confirm, name, toDl.size()))
                        .setPositiveButton(R.string.yes, (d, w) -> downloadAll(toDl, 0))
                        .setNegativeButton(R.string.no, null).show();
            });
        }

        private void downloadAll(List<Track> list, int i) {
            if (i >= list.size()) {
                Ui.toast(requireContext(), R.string.channel_download_done);
                refresh();
                return;
            }
            Track t = list.get(i);
            Tg.log("⬇️ دانلود کانال [" + (i + 1) + "/" + list.size() + "] " + t.title);
            Tg.get(requireContext()).download(t.fileId, t.expectedSize, new Tg.DownloadListener() {
                @Override
                public void onProgress(int pct) {
                }

                @Override
                public void onDone(String path) {
                    DownloadStore.get(requireContext()).mark(t, path);
                    downloadAll(list, i + 1);
                }

                @Override
                public void onError(String msg) {
                    Tg.log("⚠️ خطای دانلود: " + msg);
                    downloadAll(list, i + 1);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAvatar, tvName, tvCount;
        final ImageButton btnAddToPlaylist, btnDownloadAll;

        VH(@NonNull View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
            btnAddToPlaylist = v.findViewById(R.id.btnAddToPlaylist);
            btnDownloadAll = v.findViewById(R.id.btnDownloadAll);
        }
    }
}
