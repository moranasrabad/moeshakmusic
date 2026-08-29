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
        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());
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
        et.setHint("نام پلی‌لیست");
        et.setPadding(48, 24, 48, 24);
        new AlertDialog.Builder(requireContext())
                .setTitle("پلی‌لیست جدید")
                .setView(et)
                .setPositiveButton("ساخت", (d, w) -> {
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
            h.tvCount.setText(p.tracks.size() + " موزیک");
            h.itemView.setOnClickListener(x -> openPlaylist(p));
            h.btnPlay.setOnClickListener(x -> {
                if (p.tracks.isEmpty()) {
                    Ui.toast(requireContext(), "این پلی‌لیست خالیه");
                    return;
                }
                PlayerManager.get(requireContext()).play(p.tracks, 0);
                if (requireActivity() instanceof MainActivity)
                    ((MainActivity) requireActivity()).openPlayer();
            });
            h.itemView.setOnLongClickListener(x -> {
                new AlertDialog.Builder(requireContext())
                        .setMessage("پلی‌لیست «" + p.name + "» حذف شود؟")
                        .setPositiveButton(R.string.yes, (d, w) -> {
                            PlaylistStore.get(requireContext()).delete(p.name);
                            refresh();
                        })
                        .setNegativeButton(R.string.no, null).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private void openPlaylist(PlaylistStore.Playlist p) {
        List<Track> tracks = p.tracks;
        String[] names = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++)
            names[i] = "🎵 " + tracks.get(i).title + " — " + Ui.fmtDuration(tracks.get(i).duration);
        new AlertDialog.Builder(requireContext())
                .setTitle(p.name)
                .setItems(names, (d, w) -> {
                    PlayerManager.get(requireContext()).play(tracks, w);
                    if (requireActivity() instanceof MainActivity)
                        ((MainActivity) requireActivity()).openPlayer();
                })
                .setNeutralButton("پخش همه", (d, w) -> {
                    PlayerManager.get(requireContext()).play(tracks, 0);
                    if (requireActivity() instanceof MainActivity)
                        ((MainActivity) requireActivity()).openPlayer();
                })
                .setNegativeButton(R.string.back, null)
                .show();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName, tvCount;
        final ImageButton btnPlay;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
            btnPlay = v.findViewById(R.id.btnPlay);
        }
    }
}
