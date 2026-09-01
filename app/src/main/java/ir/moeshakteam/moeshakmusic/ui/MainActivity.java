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
    private ImageView ivConn;
    private View miniPlayer;
    private ImageView miniArt;
    private TextView miniTitle, miniArtist;
    private ImageButton miniToggle, miniShuffle;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** صفحات اصلی — سواپ افقی */
    private static final int PAGE_TRACKS = 0;
    private static final int PAGE_FOLLOWED = 1;
    private static final int PAGE_SCAN = 2;
    private static final int PAGE_PLAYLISTS = 3;
    private static final int PAGE_FAVORITES = 4;
    private static final int PAGE_DOWNLOADS = 5;
    private static final int PAGE_CHANNELS = 6;
    private static final int PAGE_CHATS = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // رنگ اکسنت — باید قبل از inflate شدن هر ویویی اعمال شود
        try {
            ir.moeshakteam.moeshakmusic.App.applyAccentTheme(this);
        } catch (Throwable t) {
            ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ accent: " + t);
        }
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
        } catch (Throwable t) {
            ir.moeshakteam.moeshakmusic.data.Tg.log("💥 setContentView: " + t);
            throw t;
        }

        drawer = findViewById(R.id.drawer);
        pager = findViewById(R.id.pager);
        tabs = findViewById(R.id.tabs);
        tvTitle = findViewById(R.id.tvPageTitle);
        searchBar = findViewById(R.id.searchBar);
        ivConn = findViewById(R.id.ivConn);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniArt = findViewById(R.id.miniArt);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniToggle = findViewById(R.id.miniToggle);
        miniShuffle = findViewById(R.id.miniShuffle);

        pager.setAdapter(new PagerAdapter(this));
        new com.google.android.material.tabs.TabLayoutMediator(tabs, pager, (tab, position) ->
                tab.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.followed_tab),
                        getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position])
        ).attach();

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvTitle.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.followed_tab),
                        getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position]);
                // سرچ روی TRACKS و FAVORITES
                boolean searchable = position == PAGE_TRACKS || position == PAGE_FAVORITES;
                searchBar.setVisibility(searchable ? View.VISIBLE : View.GONE);
                if (!searchable) setQuery("");
            }
        });

        // ساید‌منو — با کشیدن از لبه چپ هم باز می‌شود
        findViewById(R.id.btnMenu).setOnClickListener(x -> drawer.openDrawer(Gravity.START));
        bindSideMenu();

        // نشانگر اتصال/پروکسی — لمس ← صفحهٔ پروکسی
        if (ivConn != null) ivConn.setOnClickListener(x -> showFullScreen(new ProxyFragment()));

        // سرچ — مسیریابی زنده به لیست صفحهٔ فعلی (فیکس: قبلاً tracksFragment همیشه null بود)
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                if (pager.getCurrentItem() == PAGE_FAVORITES) {
                    if (TracksFragment.liveFavs != null) TracksFragment.liveFavs.filter(q);
                } else if (TracksFragment.liveTracks != null) {
                    TracksFragment.liveTracks.filter(q);
                }
                return true;
            }
        });
        // ظاهر سرچ مثل استور: بدون پلیت داخلی + متن روشن
        try {
            View plate = searchBar.findViewById(androidx.appcompat.R.id.search_plate);
            if (plate != null) plate.setBackgroundColor(0);
            View srcText = searchBar.findViewById(androidx.appcompat.R.id.search_src_text);
            if (srcText instanceof TextView) {
                ((TextView) srcText).setHintTextColor(0xFF5B6B7B);
                ((TextView) srcText).setTextColor(0xFFE8F2F8);
            }
        } catch (Throwable ignored) {
        }

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

    /** تایمر پایش دنبال‌شده‌ها — هر ۱۵ دقیقه تا وقتی اپ باز است */
    private final android.os.Handler followHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean followTimerStarted;

    private void startFollowChecks() {
        if (followTimerStarted) return;
        followTimerStarted = true;
        followHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isLoggedIn() == false) return;
                Tg.get(MainActivity.this).checkFollowed(ok -> followHandler.postDelayed(this, 15 * 60 * 1000L));
            }
        }, 30_000);
    }

    private void setQuery(String q) {
        searchBar.setQuery(q, false);
        if (TracksFragment.liveTracks != null) TracksFragment.liveTracks.filter("");
        if (TracksFragment.liveFavs != null) TracksFragment.liveFavs.filter("");
    }

    private void bindSideMenu() {
        // ساید منو فقط امکانات جانبی — همهٔ بخش‌ها تب هستند
        // 💥 ضدکرش: هیچ findViewById نباید NPE بدهد حتی اگر XML تغییر کرد
        try {
            View.OnClickListener go = x -> {
                try {
                    drawer.closeDrawer(Gravity.START);
                    int id = x.getId();
                    if (id == R.id.sideProxy) {
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
                } catch (Throwable t) {
                    ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ sideMenu tap: " + t);
                }
            };
            int[] ids = {R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};
            for (int res : ids) {
                View vv = findViewById(res);
                if (vv != null) vv.setOnClickListener(go);
            }
        } catch (Throwable t) {
            ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ bindSideMenu: " + t);
        }
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
        try {
            showFullScreen(new PlayerFragment());
        } catch (Throwable t) {
            Tg.log("💥 openPlayer: " + t);
        }
    }

    /** پرش به تب (از منوهای فرگمنت‌های داخل pager) */
    public void openTab(int index) {
        pager.setCurrentItem(index, true);
    }

    /** 📥 باز کردن تب دانلودها (از صفحهٔ پروکسی و…) — صفحهٔ تمام‌صفحه هم بسته می‌شود */
    public void openDownloads() {
        try {
            View fs = findViewById(R.id.fullScreenContainer);
            if (fs != null) fs.setVisibility(View.GONE);
            hideFullScreenContent();
            if (getSupportFragmentManager().getBackStackEntryCount() > 0)
                getSupportFragmentManager().popBackStackImmediate();
            pager.setCurrentItem(PAGE_DOWNLOADS, true);
            updateMini();
        } catch (Throwable t) {
            Tg.log("⚠️ openDownloads: " + t);
        }
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
        ir.moeshakteam.moeshakmusic.data.ArtLoader.load(t, miniArt);
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

    /** نشانگر اتصال در هدر — مثل تلگرام: سبز=وصل، کهربایی=در حال اتصال، سرخ=قطع */
    @Override
    public void onConnState(String s) {
        runOnUiThread(() -> {
            try {
                if (ivConn == null || isFinishing() || s == null) return;
            int color;
            switch (s) {
                case "ready":
                    color = 0xFF34D399;
                    break;
                case "waiting":
                    color = 0xFFF87171;
                    break;
                default:
                    color = 0xFFF59E0B;
                    break;
            }
            // پروکسی فعال → سپر پررنگ؛ غیرفعال → کم‌رنگ
            boolean proxyOn = Prefs.get(this).proxyEnabled();
            ivConn.setAlpha(proxyOn ? 1f : 0.4f);
            ivConn.setImageTintList(android.content.res.ColorStateList.valueOf(color));
            } catch (Throwable t) {
                ir.moeshakteam.moeshakmusic.data.Tg.log("⚠️ conn badge: " + t);
            }
        });
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
                // دسترسی میکروفن — فقط برای ویژوالایزر زندهٔ Now Playing
                if (Build.VERSION.SDK_INT >= 23 &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 12);
                }
                PlayerManager.get(this).attach(miniListener);
                updateMini();
                startFollowChecks();
            } else {
                PlayerManager.get(this).detach(miniListener);
                // در حال خروج/خاتمهٔ نشست — هنوز صفحهٔ ورود را نشان نده (toast خودش آمده)
                if (a == Tg.Auth.LOGGING_OUT) return;
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
                case PAGE_FOLLOWED:
                    return new FollowedFragment();
                case PAGE_SCAN:
                    return new ScanFragment();
                case PAGE_PLAYLISTS:
                    return new PlaylistsFragment();
                case PAGE_FAVORITES: {
                    // صفحه علاقه‌مندی = همان تراک‌ها ولی فیلترشده — TracksFragment با فیلتر fav
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
                case PAGE_CHATS:
                    return new ChatsFragment();
                default:
                    return new TracksFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 8;
        }
    }
}
