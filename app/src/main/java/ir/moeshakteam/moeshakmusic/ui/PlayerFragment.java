package ir.moeshakteam.moeshakmusic.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.List;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;
import ir.moeshakteam.moeshakmusic.util.Ui;
import ir.moeshakteam.moeshakmusic.viz.NeonVisualizer;

/** صفحهٔ پخش با ویژوالایزر — تیم موشک */
public class PlayerFragment extends Fragment implements PlayerManager.Listener {

    private static final int REQ_MIC = 7;

    private NeonVisualizer viz;
    private ImageView art;
    private TextView tvTitle, tvArtist, tvChat, tvCur, tvDur, dlText;
    private SeekBar seek;
    private ImageButton btnPlay;
    private LinearLayout dlRow;
    private ProgressBar dlBar;
    private boolean userSeeking;
    private PlayerManager pm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        try {
        super.onViewCreated(v, savedInstanceState);
        pm = PlayerManager.get(requireContext());
        viz = v.findViewById(R.id.viz);
        art = v.findViewById(R.id.art);
        tvTitle = v.findViewById(R.id.tvTitle);
        tvArtist = v.findViewById(R.id.tvArtist);
        tvChat = v.findViewById(R.id.tvChat);
        tvCur = v.findViewById(R.id.tvCur);
        tvDur = v.findViewById(R.id.tvDur);
        seek = v.findViewById(R.id.seek);
        btnPlay = v.findViewById(R.id.btnPlay);
        dlRow = v.findViewById(R.id.dlRow);
        dlBar = v.findViewById(R.id.dlBar);
        dlText = v.findViewById(R.id.dlText);

        v.findViewById(R.id.btnBack).setOnClickListener(x ->
                requireActivity().onBackPressed());
        btnPlay.setOnClickListener(x -> pm.toggle());
        v.findViewById(R.id.btnNext).setOnClickListener(x -> pm.next());
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> pm.prev());
        v.findViewById(R.id.btnShuffle).setOnClickListener(x -> {
            pm.toggleShuffle();
            updateSecondary();
            Ui.toast(requireContext(), pm.shuffle ? R.string.shuffle_on : R.string.shuffle_off);
        });
        v.findViewById(R.id.btnRepeat).setOnClickListener(x -> {
            pm.cycleRepeat();
            updateSecondary();
            Ui.toast(requireContext(), new int[]{
                    R.string.repeat_off, R.string.repeat_all, R.string.repeat_one}[pm.repeatMode]);
        });
        v.findViewById(R.id.btnFav).setOnClickListener(x -> {
            Track cur = pm.current();
            if (cur != null) {
                pm.toggleFavorite(cur);
                updateSecondary();
            }
        });
        v.findViewById(R.id.btnQueue).setOnClickListener(x ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fullScreenContainer, new QueueFragment())
                        .addToBackStack("queue")
                        .commit());
        v.findViewById(R.id.btnPlaylist).setOnClickListener(x -> {
            Track cur = pm.current();
            if (cur == null) return;
            addToPlaylistDialog(cur);
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) tvCur.setText(Ui.fmtTime(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                pm.seekTo(s.getProgress());
            }
        });

        pm.attach(this);
        Track cur = pm.current();
        if (cur != null) bindTrack(cur);
        if (btnPlay != null) btnPlay.setImageResource(pm.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        if (viz != null) viz.setPlaying(pm.isPlaying());
        updateSecondary();

        // ویژوالایزر همیشه وصل است: با دسترسی میکروفن موج واقعی، بدون آن انیمیشن ریتمیک
        pm.setVisualizerView(viz);
        // 💥 کاور با ضرب ویژوالایزر بزرگ/کوچک می‌شود
        viz.setBeatListener(level -> {
            if (art == null || !isAdded()) return;
            float sc = 1f + level * 0.06f;
            art.animate().scaleX(sc).scaleY(sc).setDuration(90).start();
        });
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
        } catch (Throwable t) {
            ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ PlayerUI crash: " + t);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && viz != null) {
            pm.setVisualizerView(viz);
        }
    }

    /** افزودن آهنگ در حال پخش به پلی‌لیست */
    private void addToPlaylistDialog(ir.moeshakteam.moeshakmusic.data.Track t) {
        ir.moeshakteam.moeshakmusic.data.PlaylistStore ps =
                ir.moeshakteam.moeshakmusic.data.PlaylistStore.get(requireContext());
        List<ir.moeshakteam.moeshakmusic.data.PlaylistStore.Playlist> all = ps.all();
        if (all.isEmpty()) {
            // ساخت مستقیم
            android.widget.EditText et = new android.widget.EditText(requireContext());
            et.setHint(R.string.playlist_name_hint);
            et.setPadding(48, 24, 48, 24);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.playlist_new)
                    .setView(et)
                    .setPositiveButton(R.string.create_and_add, (d, w) -> {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) return;
                        ps.create(name);
                        ps.addTrack(name, t);
                        Ui.toast(requireContext(), getString(R.string.added_to_fmt, name));
                    })
                    .setNegativeButton(R.string.no, null).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name + " (" + all.get(i).tracks.size() + ")";
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.add_track_to, t.title))
                .setItems(names, (d, w) -> {
                    boolean ok = ps.addTrack(all.get(w).name, t);
                    Ui.toast(requireContext(), ok ? getString(R.string.added_to_fmt, all.get(w).name) : getString(R.string.already_there));
                })
                .setPositiveButton(R.string.playlist_new, (d, w) -> {
                    android.widget.EditText et = new android.widget.EditText(requireContext());
                    et.setHint(R.string.playlist_name_hint);
                    et.setPadding(48, 24, 48, 24);
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(R.string.playlist_new)
                            .setView(et)
                            .setPositiveButton(R.string.create_and_add, (d2, w2) -> {
                                String name = et.getText().toString().trim();
                                if (name.isEmpty()) return;
                                ps.create(name);
                                ps.addTrack(name, t);
                                Ui.toast(requireContext(), getString(R.string.added_to_fmt, name));
                            })
                            .setNegativeButton(R.string.no, null).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void updateSecondary() {
        if (!isAdded() || getView() == null) return;
        View root = getView();
        ImageButton sh = root.findViewById(R.id.btnShuffle);
        if (sh != null) sh.setAlpha(pm.shuffle ? 1f : 0.45f);
        ImageButton rp = root.findViewById(R.id.btnRepeat);
        if (rp != null) {
            rp.setImageResource(pm.repeatMode == 2 ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
            rp.setAlpha(pm.repeatMode == 0 ? 0.45f : 1f);
        }
        ImageButton fv = root.findViewById(R.id.btnFav);
        if (fv != null) {
            Track cur = pm.current();
            boolean fav = cur != null && pm.isFavorite(cur);
            fv.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
            fv.setColorFilter(fav ? 0xFFE11D48 : getResources().getColor(R.color.moeshak_muted, requireActivity().getTheme()));
        }
    }

    private void bindTrack(Track t) {
        if (t == null) return;
        tvTitle.setText(t.title);
        tvArtist.setText(t.subtitle());
        tvChat.setText(t.chatTitle);
        updateSecondary();
        // تامبنیل با لودر برند (مینی‌تامب ← کاور ← عکس کانال)
        ir.moeshakteam.moeshakmusic.data.ArtLoader.load(t, art);
        if (t.downloadPct >= 0) {
            dlRow.setVisibility(View.VISIBLE);
            dlBar.setProgress(t.downloadPct);
            dlText.setText(getString(R.string.downloading, t.downloadPct));
        } else {
            dlRow.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTrackChanged(Track t) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> bindTrack(t));
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            if (viz != null) viz.setPlaying(playing);
        });
    }

    @Override
    public void onProgress(long pos, long dur) {
        if (!isAdded() || userSeeking) return;
        requireActivity().runOnUiThread(() -> {
            if (dur > 0) {
                seek.setMax((int) dur);
                seek.setProgress((int) pos);
                tvDur.setText(Ui.fmtTime(dur));
            }
            tvCur.setText(Ui.fmtTime(pos));
        });
    }

    @Override
    public void onDownload(Track t, int pct, String path, String err) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            Track cur = pm.current();
            if (cur == null || !cur.sameAs(t)) return;
            if (err != null) {
                dlRow.setVisibility(View.GONE);
                Ui.toast(requireContext(), getString(R.string.err_generic, err));
            } else if (path != null) {
                dlRow.setVisibility(View.GONE);
            } else if (pct >= 0) {
                dlRow.setVisibility(View.VISIBLE);
                dlBar.setProgress(pct);
                dlText.setText(getString(R.string.downloading, pct));
            } else {
                dlRow.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        pm.detach(this);
        pm.setVisualizerView(null);
        super.onDestroyView();
    }
}
