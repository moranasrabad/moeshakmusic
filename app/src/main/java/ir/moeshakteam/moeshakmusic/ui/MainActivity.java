package ir.moeshakteam.moeshakmusic.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.appcompat.widget.SearchView;

import ir.moeshakteam.moeshakmusic.R;
import ir.moeshakteam.moeshakmusic.data.Prefs;
import ir.moeshakteam.moeshakmusic.data.Tg;
import ir.moeshakteam.moeshakmusic.App;
import ir.moeshakteam.moeshakmusic.data.Track;
import ir.moeshakteam.moeshakmusic.player.PlayerManager;

/** تیم موشک — moeshakteam.ir */
public class MainActivity extends AppCompatActivity implements Tg.AuthListener {

    private DrawerLayout drawer;
    private ViewPager2 pager;
    private TabLayout tabs;
    private TextView tvTitle;
    private SearchView searchBar;
    private View miniPlayer;
    private ImageView miniArt;
    private TextView miniTitle, miniArtist;
    private ImageButton miniToggle, miniShuffle;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** صفحات اصلی — سواپ افقی */
    private static final int PAGE_TRACKS = 0;
    private static final int PAGE_PLAYLISTS = 1;
    private static final int PAGE_FAVORITES = 2;
    private static final int PAGE_DOWNLOADS = 3;
    private static final int PAGE_CHANNELS = 4;

    private TracksFragment tracksFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawer = findViewById(R.id.drawer);
        pager = findViewById(R.id.pager);
        tabs = findViewById(R.id.tabs);
        tvTitle = findViewById(R.id.tvPageTitle);
        searchBar = findViewById(R.id.searchBar);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniArt = findViewById(R.id.miniArt);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniToggle = findViewById(R.id.miniToggle);
        miniShuffle = findViewById(R.id.miniShuffle);

        pager.setAdapter(new PagerAdapter(this));
        new com.google.android.material.tabs.TabLayoutMediator(tabs, pager, (tab, position) ->
                tab.setText(new String[]{"TRACKS", "PLAYLISTS", "FAVORITES", "DOWNLOADS", "CHANNELS"}[position])
        ).attach();

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvTitle.setText(new String[]{"TRACKS", "PLAYLISTS", "FAVORITES", "DOWNLOADS", "CHANNELS"}[position]);
                // سرچ فقط روی TRACKS
                searchBar.setVisibility(position == PAGE_TRACKS ? View.VISIBLE : View.GONE);
            }
        });

        // ساید‌منو — با کشیدن از لبه چپ هم باز می‌شود
        findViewById(R.id.btnMenu).setOnClickListener(x -> drawer.openDrawer(Gravity.START));
        bindSideMenu();

        // سرچ
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                if (tracksFragment != null) tracksFragment.filter(q);
                return true;
            }
        });
        findViewById(R.id.btnSearchIcon).setOnClickListener(x -> {
            searchBar.setIconified(false);
            searchBar.requestFocus();
        });

        // مینی‌پلیر
        miniPlayer.setOnClickListener(x -> {
            Tg.log("🖱️ مینی‌پلیر کلیک شد — باز کردن Now Playing…");
            miniPlayer.setVisibility(View.GONE);
            showFullScreen(new PlayerFragment());
        });
        miniToggle.setOnClickListener(x -> PlayerManager.get(this).toggle());
        miniShuffle.setOnClickListener(x -> PlayerManager.get(this).toggleShuffle());

        if (Prefs.get(this).hasKeys()) Tg.get(this).start();
        Tg.get(this).addAuthListener(this);
    }

    private void bindSideMenu() {
        View.OnClickListener go = x -> {
            // بدون ورود، هیچ صفحه‌ای باز نمی‌شود (به‌جز تم)
            if (Tg.get(this).auth() != Tg.Auth.READY && x.getId() != R.id.sideTheme) {
                ir.moeshakteam.moeshakmusic.util.Ui.toast(this, "اول وارد اکانت شو ⚡");
                return;
            }
            drawer.closeDrawer(Gravity.START);
            int id = x.getId();
            if (id == R.id.sideLibrary) {
                pager.setCurrentItem(PAGE_TRACKS, true);
            } else if (id == R.id.sidePlaylists) {
                pager.setCurrentItem(PAGE_PLAYLISTS, true);
            } else if (id == R.id.sideFavorites) {
                List<Track> favs = PlayerManager.favoriteTracks();
                if (favs.isEmpty()) {
                    ir.moeshakteam.moeshakmusic.util.Ui.toast(this, getString(R.string.fav_empty));
                    return;
                }
                PlayerManager.get(this).play(favs, 0);
                openPlayer();
            } else if (id == R.id.sideDownloads) {
                pager.setCurrentItem(PAGE_DOWNLOADS, true);
            } else if (id == R.id.sideChannels) {
                pager.setCurrentItem(PAGE_CHANNELS, true);
            } else if (id == R.id.sideChats) {
                showFullScreen(new ChatsFragment());
            } else if (id == R.id.sideProxy) {
                showFullScreen(new ProxyFragment());
            } else if (id == R.id.sideLog) {
                showFullScreen(new LogFragment());
            } else if (id == R.id.sideSettings) {
                showFullScreen(new SettingsFragment());
            } else if (id == R.id.sideTheme) {
                int mode = (Prefs.get(this).themeMode() + 1) % 3;
                Prefs.get(this).setThemeMode(mode);
                App.applyTheme(this);
            }
        };
        int[] ids = {R.id.sideLibrary, R.id.sidePlaylists, R.id.sideFavorites, R.id.sideDownloads,
                R.id.sideChannels, R.id.sideChats, R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};
        for (int res : ids) findViewById(res).setOnClickListener(go);
    }

    public void showFullScreen(Fragment f) {
        View fs = findViewById(R.id.fullScreenContainer);
        fs.setVisibility(View.VISIBLE);
        fs.setAlpha(1f);
        fs.setZ(100f);
        // پنهان کردن محتوای زیرین تا صفحهٔ تمام‌صفحه کاملاً جدا دیده شود
        View pagerRoot = findViewById(R.id.mainContent);
        if (pagerRoot != null) pagerRoot.setVisibility(View.GONE);
        View mpv = findViewById(R.id.miniPlayer);
        if (mpv != null) mpv.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fullScreenContainer, f)
                .addToBackStack(null)
                .commit();
    }

    private void hideFullScreenContent() {
        View pagerRoot = findViewById(R.id.mainContent);
        if (pagerRoot != null) pagerRoot.setVisibility(View.VISIBLE);
    }

    /** آیا کاربر وارد شده؟ */
    public boolean isLoggedIn() {
        return Tg.get(this).auth() == Tg.Auth.READY;
    }

    public void openPlayer() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fullScreenContainer, new PlayerFragment())
                .addToBackStack("player")
                .commit();
    }

    private void updateMini() {
        Track t = PlayerManager.get(this).current();
        if (t == null) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(t.title);
        miniArtist.setText(t.subtitle());
        Bitmap b = t.art();
        if (b != null) miniArt.setImageBitmap(b);
        else miniArt.setImageResource(R.drawable.bg_art);
        miniToggle.setImageResource(PlayerManager.get(this).isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        miniShuffle.setAlpha(PlayerManager.get(this).shuffle ? 1f : 0.45f);
    }

    private final PlayerManager.Listener miniListener = new PlayerManager.Listener() {
        @Override
        public void onTrackChanged(Track t) {
            main.post(() -> {
                if (!isFinishing()) updateMini();
            });
        }

        @Override
        public void onPlayStateChanged(boolean playing) {
            main.post(() -> {
                if (!isFinishing()) updateMini();
            });
        }
    };

    @Override
    public void onAuth(Tg.Auth a, String error) {
        runOnUiThread(() -> route(a));
    }

    private void route(Tg.Auth a) {
        try {
            Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fullScreenContainer);
            if (a == Tg.Auth.READY) {
                View fs = findViewById(R.id.fullScreenContainer);
                fs.setVisibility(View.GONE);
                View mc = findViewById(R.id.mainContent);
                if (mc != null) mc.setVisibility(View.VISIBLE);
                View mp = findViewById(R.id.miniPlayer);
                if (mp != null) mp.setVisibility(PlayerManager.get(this).current() != null ? View.VISIBLE : View.GONE);
                requestNotifPermission();
                PlayerManager.get(this).attach(miniListener);
                updateMini();
            } else {
                PlayerManager.get(this).detach(miniListener);
                View mc = findViewById(R.id.mainContent);
                if (mc != null) mc.setVisibility(View.GONE);
                View mp = findViewById(R.id.miniPlayer);
                if (mp != null) mp.setVisibility(View.GONE);
                if (!(cur instanceof LoginFragment)) showFullScreen(new LoginFragment());
            }
        } catch (Throwable t) {
            Tg.log("⚠️ route crash: " + t);
        }
    }

    private void show(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .commitAllowingStateLoss();
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    @Override
    public void onBackPressed() {
        View fs = findViewById(R.id.fullScreenContainer);
        if (fs != null && fs.getVisibility() == View.VISIBLE) {
            fs.setVisibility(View.GONE);
            hideFullScreenContent();
            updateMini();
            if (getSupportFragmentManager().getBackStackEntryCount() > 0)
                getSupportFragmentManager().popBackStackImmediate();
            return;
        }
        if (drawer.isDrawerOpen(Gravity.START)) {
            drawer.closeDrawer(Gravity.START);
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        Tg.get(this).removeAuthListener(this);
        super.onDestroy();
    }

    /** آداپتور صفحات */
    private class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case PAGE_PLAYLISTS:
                    return new PlaylistsFragment();
                case PAGE_FAVORITES: {
                    List<Track> favs = PlayerManager.favoriteTracks();
                    androidx.appcompat.app.AlertDialog d = null;
                    // صفحه علاقه‌مندی = همان تراک‌ها ولی فیلترشده — ساده: TracksFragment با فیلتر fav
                    TracksFragment tf = new TracksFragment();
                    Bundle b = new Bundle();
                    b.putBoolean("fav", true);
                    tf.setArguments(b);
                    return tf;
                }
                case PAGE_DOWNLOADS:
                    return new DownloadsFragment();
                case PAGE_CHANNELS:
                    return new ChannelsFragment();
                default:
                    TracksFragment tf = new TracksFragment();
                    return tf;
            }
        }

        @Override
        public int getItemCount() {
            return 5;
        }
    }
}
