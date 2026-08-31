package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * لیست آهنگ‌های یک چت/کانال — صفحهٔ تمام‌صفحه با لیست واقعی + پخش همه + اسکن عمیق.
 * جایگزین bulk-play قبلی: کاربر خودش انتخاب می‌کند از کجا پخش شود.
 */
public class ChannelTracksFragment extends Fragment {

    public static final String ARG_CHAT_ID = "chatId";
    public static final String ARG_CHAT_TITLE = "chatTitle";

    private long chatId;
    private String chatTitle = "";
    private TrackAdapter adapter;
    private TextView tvChatTitle, tvCount, empty;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable libHook = () -> main.post(this::refresh);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tracks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            chatId = args.getLong(ARG_CHAT_ID, 0);
            chatTitle = args.getString(ARG_CHAT_TITLE, "");
        }
        if (savedInstanceState != null) {
            chatId = savedInstanceState.getLong(ARG_CHAT_ID, chatId);
            chatTitle = savedInstanceState.getString(ARG_CHAT_TITLE, chatTitle);
        }

        tvChatTitle = v.findViewById(R.id.tvChatTitle);
        tvCount = v.findViewById(R.id.tvCount);
        empty = v.findViewById(R.id.empty);
        RecyclerView recycler = v.findViewById(R.id.recycler);

        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());

        v.findViewById(R.id.btnPlayAll).setOnClickListener(x -> {
            List<Track> tracks = tracksOf();
            if (tracks.isEmpty()) {
                Ui.toast(requireContext(), R.string.channel_tracks_empty);
                return;
            }
            PlayerManager.get(requireContext()).play(tracks, 0);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });

        v.findViewById(R.id.btnDeepScan).setOnClickListener(x -> {
            Ui.toast(requireContext(), getString(R.string.deep_scan_started, chatTitle));
            Tg.get(requireContext()).deepScanChat(chatId, new Tg.ScanListener() {
                @Override
                public void onProgress(int found, int chats) {
                }

                @Override
                public void onDone(int added) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Ui.toast(requireContext(), added > 0
                                ? getString(R.string.deep_scan_done, added)
                                : getString(R.string.deep_scan_none));
                        refresh();
                    });
                }

                @Override
                public void onError(String msg) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> Ui.toast(requireContext(), msg));
                }
            });
        });

        Tg.get(requireContext()).addLibraryListener(libHook);
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(ARG_CHAT_ID, chatId);
        outState.putString(ARG_CHAT_TITLE, chatTitle);
    }

    @Override
    public void onDestroyView() {
        try { Tg.get(requireContext()).removeLibraryListener(libHook); } catch (Throwable ignored) {}
        super.onDestroyView();
    }

    private List<Track> tracksOf() {
        List<Track> out = new ArrayList<>();
        for (Track t : Tg.get(requireContext()).library) {
            if (t.chatId == chatId) out.add(t);
        }
        return out;
    }

    private void refresh() {
        if (!isAdded() || adapter == null) return;
        List<Track> tracks = tracksOf();
        adapter.setAll(tracks);
        if (tvChatTitle != null) tvChatTitle.setText(chatTitle.isEmpty() ? getString(R.string.tab_channels) : chatTitle);
        if (tvCount != null) tvCount.setText(getString(R.string.channel_tracks_count, tracks.size()));
        if (empty != null) empty.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
