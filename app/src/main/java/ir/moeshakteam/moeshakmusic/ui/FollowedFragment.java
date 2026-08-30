package ir.moeshakteam.moeshakmusic.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.FollowStore;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;

/**
 * صفحهٔ دنبال‌شده‌ها — کارت‌های امن (حذف فقط با دکمهٔ مشخص + تأیید) — تیم موشک
 */
public class FollowedFragment extends Fragment {

    private TextView status;
    private LinearLayout followedList;
    private TextView btnLoadMore;
    private static final int PAGE_SIZE = 30;
    private int shownCount = PAGE_SIZE;
    private TrackAdapter adapter;
    private RecyclerView recycler;
    private View empty;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_followed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        followedList = v.findViewById(R.id.followedList);
        status = v.findViewById(R.id.tvFollowStatus);
        recycler = v.findViewById(R.id.recycler);
        empty = v.findViewById(R.id.empty);

        adapter = new TrackAdapter((t, pos) -> {
            PlayerManager.get(requireContext()).play(adapter.getShown(), pos);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
        });
        adapter.setLongClick((t, pos) -> Ui.toast(requireContext(), R.string.followed_hint));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        // 🔧 لیست داخل ScrollView — اسکرول تو در تو درست کار کند
        recycler.setNestedScrollingEnabled(false);

        MaterialButton btnCheck = v.findViewById(R.id.btnCheckNow);
        btnCheck.setOnClickListener(x -> {
            Ui.toast(requireContext(), R.string.followed_checking);
            Tg.get(requireContext()).checkFollowed(ok -> main.post(this::refreshSafe));
        });

        // هوک رفرش زنده
        Tg.get(requireContext()).onFollowedUpdate = this::refreshSafe;
        Tg.get(requireContext()).onLibraryChanged = this::refreshSafe;

        // پایش دوره‌ای
        startPeriodicCheck();
        refresh();
    }

    private void startPeriodicCheck() {
        Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                Tg.get(requireContext()).checkFollowed(ok -> main.post(() -> {
                    refreshSafe();
                    if (isAdded()) main.postDelayed(this, 15 * 60 * 1000L);
                }));
            }
        };
        main.postDelayed(loop, 30_000);
    }

    private void refreshSafe() {
        if (!isAdded()) return;
        main.post(() -> { shownCount = PAGE_SIZE; refresh(); });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!isAdded()) return;
        Tg tg = Tg.get(requireContext());

        // ─── کارت‌های دنبال‌شده — امن، بدون حذف تصادفی ───
        List<FollowStore.Followed> fs = FollowStore.get(requireContext()).all();
        followedList.removeAllViews();
        if (fs.isEmpty()) {
            TextView none = new TextView(requireContext());
            none.setText(R.string.followed_none);
            none.setTextColor(getResources().getColor(R.color.moeshak_muted, requireActivity().getTheme()));
            none.setTextSize(13);
            none.setPadding(8, 8, 8, 8);
            followedList.addView(none);
        } else {
            for (FollowStore.Followed f : fs) {
                View card = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_followed, followedList, false);
                TextView av = card.findViewById(R.id.tvAvatar);
                TextView name = card.findViewById(R.id.tvName);
                TextView meta = card.findViewById(R.id.tvMeta);
                ImageButton play = card.findViewById(R.id.btnPlay);
                ImageButton unf = card.findViewById(R.id.btnUnfollow);

                av.setText(f.title.isEmpty() ? "?" : f.title.substring(0, 1).toUpperCase());
                name.setText(f.title);
                meta.setText(getString(R.string.followed_card_meta, f.knownIds.size()));

                play.setOnClickListener(x -> {
                    List<Track> tracks = libraryTracksOf(f.chatId);
                    if (tracks.isEmpty()) {
                        Ui.toast(requireContext(), R.string.followed_empty_tracks);
                        return;
                    }
                    PlayerManager.get(requireContext()).play(tracks, 0);
                    if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openPlayer();
                });
                // لغو دنبال — فقط با این دکمه + تأیید
                unf.setOnClickListener(x -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.followed_unfollow)
                        .setMessage(getString(R.string.followed_unfollow_confirm, f.title))
                        .setPositiveButton(R.string.yes, (d, w) -> {
                            FollowStore.get(requireContext()).unfollow(f.chatId);
                            Ui.toast(requireContext(), R.string.followed_unfollowed);
                            refresh();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show());

                followedList.addView(card);
            }
        }

        status.setText(getString(R.string.followed_status, fs.size()));

        // آهنگ‌های جدید — فقط از چت‌های دنبال‌شده — با «لود بیشتر»
        java.util.List<Track> all = tg.followedResults;
        java.util.List<Track> page = new java.util.ArrayList<>(
                all.subList(0, Math.min(shownCount, all.size())));
        adapter.setAll(page);

        // دکمهٔ لود بیشتر — اگر باز هم هست
        boolean hasMore = all.size() > page.size();
        View parent = (View) recycler.getParent();
        if (btnLoadMore == null && parent instanceof android.view.ViewGroup) {
            btnLoadMore = new TextView(requireContext());
            btnLoadMore.setText(R.string.load_more);
            btnLoadMore.setTextColor(getResources().getColor(R.color.moeshak_accent, requireActivity().getTheme()));
            btnLoadMore.setTextSize(14);
            btnLoadMore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            btnLoadMore.setGravity(android.view.Gravity.CENTER);
            btnLoadMore.setPadding(0, 28, 0, 28);
            btnLoadMore.setOnClickListener(x -> {
                shownCount += PAGE_SIZE * 2;
                refresh();
            });
            ((android.view.ViewGroup) parent).addView(btnLoadMore,
                    new android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (btnLoadMore != null) {
            btnLoadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
            if (hasMore) btnLoadMore.setText(getString(R.string.load_more_n, all.size() - page.size()));
        }

        boolean emptyTracks = adapter.isEmpty();
        recycler.setVisibility(emptyTracks ? View.GONE : View.VISIBLE);
        empty.setVisibility(emptyTracks && !hasMore ? View.VISIBLE : View.GONE);
    }

    private List<Track> libraryTracksOf(long chatId) {
        List<Track> out = new java.util.ArrayList<>();
        for (Track t : Tg.get(requireContext()).library) {
            if (t.chatId == chatId) out.add(t);
        }
        return out;
    }
}
