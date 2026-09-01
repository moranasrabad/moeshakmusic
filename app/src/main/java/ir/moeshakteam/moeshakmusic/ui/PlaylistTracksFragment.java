package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * لیست آهنگ‌های یک پلی‌لیست — مثل لیست چت‌ها.
 * لمس = پخش از همان آهنگ؛ لمس طولانی = حذف از پلی‌لیست؛ دکمهٔ پخش همه.
 */
public class PlaylistTracksFragment extends Fragment {

    public static final String ARG_NAME = "name";

    private String name = "";
    private TrackAdapter adapter;
    private TextView tvTitle, tvCount, empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist_tracks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) name = args.getString(ARG_NAME, "");
        if (savedInstanceState != null) name = savedInstanceState.getString(ARG_NAME, name);

        tvTitle = v.findViewById(R.id.tvTitle);
        tvCount = v.findViewById(R.id.tvCount);
        empty = v.findViewById(R.id.empty);
        RecyclerView recycler = v.findViewById(R.id.recycler);

        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        // لمس طولانی → حذف از پلی‌لیست
        adapter.setLongClick(this::confirmRemove);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());
        v.findViewById(R.id.btnPlayAll).setOnClickListener(x -> {
            List<Track> tracks = tracksOf();
            if (tracks.isEmpty()) {
                Ui.toast(requireContext(), R.string.playlist_empty_toast);
                return;
            }
            PlayerManager.get(requireContext()).play(tracks, 0);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });

        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_NAME, name);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private List<Track> tracksOf() {
        PlaylistStore.Playlist p = PlaylistStore.get(requireContext()).byName(name);
        return p == null ? new ArrayList<>() : p.tracks;
    }

    private void refresh() {
        if (!isAdded() || adapter == null) return;
        List<Track> tracks = tracksOf();
        adapter.setAll(tracks);
        if (tvTitle != null) tvTitle.setText(name.isEmpty() ? getString(R.string.playlists_title) : name);
        if (tvCount != null) tvCount.setText(getString(R.string.playlist_tracks_count, tracks.size()));
        if (empty != null) empty.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** حذف آهنگ از پلی‌لیست — با تأیید */
    private void confirmRemove(Track t, int pos) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_from_playlist)
                .setMessage(getString(R.string.playlist_remove_confirm, t.title, name))
                .setPositiveButton(R.string.yes, (d, w) -> {
                    PlaylistStore.get(requireContext()).removeTrack(name, t.chatId, t.messageId);
                    refresh();
                    Ui.toast(requireContext(), R.string.removed);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
