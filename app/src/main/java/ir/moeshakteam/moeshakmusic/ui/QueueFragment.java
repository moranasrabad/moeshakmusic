package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/** صف پخش + علاقه‌مندی‌ها — با شافل از اینجا هم می‌شه زد — تیم موشک */
public class QueueFragment extends Fragment {

    private RecyclerView recycler;
    private TextView empty, title;
    private ImageButton btnMode; // تب صف/علاقه‌مندی
    private boolean showFavorites;
    private QueueAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);
        title = v.findViewById(R.id.tvTitle);
        btnMode = v.findViewById(R.id.btnMode);
        adapter = new QueueAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());
        btnMode.setOnClickListener(x -> {
            showFavorites = !showFavorites;
            refresh();
        });
        v.findViewById(R.id.btnShuffle).setOnClickListener(x -> {
            PlayerManager.get(requireContext()).toggleShuffle();
            Ui.toast(requireContext(), "شافل: " + (PlayerManager.get(requireContext()).shuffle ? "روشن" : "خاموش"));
        });
        refresh();
    }

    private void refresh() {
        PlayerManager pm = PlayerManager.get(requireContext());
        title.setText(showFavorites ? getString(R.string.favorites_title) : getString(R.string.queue_title));
        List<Track> items = showFavorites ? PlayerManager.favoriteTracks() : new ArrayList<>(pm.queue);
        adapter.setItems(items, pm.index, showFavorites);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(showFavorites ? getString(R.string.fav_empty) : getString(R.string.queue_empty));
    }

    private class QueueAdapter extends RecyclerView.Adapter<VH> {
        private List<Track> items = new ArrayList<>();
        private int playingIdx = -1;
        private boolean favMode;

        void setItems(List<Track> list, int playing, boolean fav) {
            items = list;
            playingIdx = playing;
            favMode = fav;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final int idx = h.getBindingAdapterPosition();
            Track t = items.get(idx);
            h.tvTitle.setText(t.title);
            h.tvSub.setText(t.subtitle() + " • " + Ui.fmtDuration(t.duration));
            boolean isPlaying = !favMode && idx == playingIdx;
            h.tvTitle.setTextColor(isPlaying
                    ? getResources().getColor(R.color.moeshak_accent, requireActivity().getTheme())
                    : getResources().getColor(R.color.moeshak_on_surface, requireActivity().getTheme()));
            h.ivType.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_music);
            h.btnFav.setImageResource(PlayerManager.get(requireContext()).isFavorite(t)
                    ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
            h.itemView.setOnClickListener(x -> {
                if (favMode) {
                    List<Track> one = new ArrayList<>();
                    one.add(t);
                    PlayerManager.get(requireContext()).play(one, 0);
                } else {
                    PlayerManager.get(requireContext()).load(idx, true);
                }
                requireActivity().onBackPressed();
            });
            h.btnFav.setOnClickListener(x -> {
                PlayerManager.get(requireContext()).toggleFavorite(t);
                if (favMode) refresh();
                else notifyItemChanged(idx);
            });
            h.itemView.setOnLongClickListener(x -> {
                if (favMode) return false;
                new AlertDialog.Builder(requireContext())
                        .setItems(new String[]{getString(R.string.remove_from_queue)}, (d, w) -> {
                            PlayerManager.get(requireContext()).removeFromQueue(idx);
                            refresh();
                        }).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvSub;
        final android.widget.ImageView ivType, btnFav;

        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub = v.findViewById(R.id.tvSub);
            ivType = v.findViewById(R.id.ivType);
            btnFav = v.findViewById(R.id.btnFav);
        }
    }
}
