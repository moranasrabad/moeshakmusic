# -*- coding: utf-8 -*-
# پچ UI دنبال کردن: ChannelsFragment bell + ChatsFragment لمس طولانی + منو + آداپتور چت انتخاب گروهی
import io

BASE = 'java/ir/moeshakteam/moeshakmusic/'
RES = 'res/'

# ═══ ۱) ChannelsFragment — دکمهٔ bell ═══
p = BASE + 'ui/ChannelsFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            h.btnDownloadAll.setOnClickListener(x -> {''','''            // 🔔 دنبال کردن — پایش آهنگ جدید
            h.btnFollow.setOnClickListener(x -> {
                ir.moeshakteam.moeshakmusic.data.FollowStore fs =
                        ir.moeshakteam.moeshakmusic.data.FollowStore.get(requireContext());
                long cid = tracks.isEmpty() ? 0 : tracks.get(0).chatId;
                if (fs.isFollowed(cid)) {
                    fs.unfollow(cid);
                    Ui.toast(requireContext(), R.string.followed_unfollowed);
                } else {
                    List<String> base = new ArrayList<>();
                    for (Track t : tracks) base.add(t.chatId + ":" + t.messageId);
                    fs.follow(cid, name, base);
                    Ui.toast(requireContext(), R.string.followed_now);
                }
                refresh();
            });
            h.btnFollow.setImageResource(
                    ir.moeshakteam.moeshakmusic.data.FollowStore.get(requireContext())
                            .isFollowed(tracks.isEmpty() ? 0 : tracks.get(0).chatId)
                            ? R.drawable.ic_bell_on : R.drawable.ic_bell);
            h.btnDownloadAll.setOnClickListener(x -> {''')
s = s.replace('''    static class VH extends RecyclerView.ViewHolder {
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
    }''','''    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAvatar, tvName, tvCount;
        final ImageButton btnAddToPlaylist, btnDownloadAll, btnFollow;

        VH(@NonNull View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
            btnAddToPlaylist = v.findViewById(R.id.btnAddToPlaylist);
            btnDownloadAll = v.findViewById(R.id.btnDownloadAll);
            btnFollow = v.findViewById(R.id.btnFollow);
        }
    }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('ChannelsFragment ✓')

# item_channel.xml — دکمهٔ bell
p = RES + 'layout/item_channel.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''        <ImageButton
            android:id="@+id/btnAddToPlaylist"''','''        <ImageButton
            android:id="@+id/btnFollow"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/followed_follow"
            android:src="@drawable/ic_bell"
            app:tint="?attr/colorPrimary" />

        <ImageButton
            android:id="@+id/btnAddToPlaylist"''')
io.open(p, 'w', encoding='utf-8').write(s)
print('item_channel.xml ✓')

# ═══ ۲) آیکون‌های bell ═══
open(RES + 'drawable/ic_bell.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
</vector>
''')
open(RES + 'drawable/ic_bell_on.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2zM19.78,2.81l-1.24,1.25c1.51,1.51 2.45,3.6 2.45,5.94h2c0,-2.9 -1.19,-5.53 -3.21,-7.19zM5.46,4.06L4.22,2.81C2.2,4.47 1,7.1 1,10h2c0,-2.34 0.95,-4.43 2.46,-5.94z" />
</vector>
''')
print('icons ✓')

# ═══ ۳) ChatsFragment — لمس طولانی = منو (دنبال/اسکن عمیق) ═══
p = BASE + 'ui/ChatsFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            h.tvCount.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            h.tvCount.setText(String.valueOf(n));
            h.itemView.setOnClickListener(x -> showChatTracks(c));''','''            h.tvCount.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            h.tvCount.setText(String.valueOf(n));
            h.itemView.setOnClickListener(x -> showChatTracks(c));
            h.itemView.setOnLongClickListener(x -> {
                showChatMenu(c);
                return true;
            });''')
s = s.replace('''    private void deepScan(TdApi.Chat c) {''','''    /** منوی لمس طولانی چت: دنبال کردن / اسکن عمیق — تیم موشک */
    private void showChatMenu(TdApi.Chat c) {
        ir.moeshakteam.moeshakmusic.data.FollowStore fs =
                ir.moeshakteam.moeshakmusic.data.FollowStore.get(requireContext());
        boolean fol = fs.isFollowed(c.id);
        String[] opts = {
                fol ? getString(R.string.followed_unfollow) : getString(R.string.followed_follow),
                getString(R.string.chat_deep_scan)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(c.title == null ? "چت" : c.title)
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        if (fol) {
                            fs.unfollow(c.id);
                            Ui.toast(requireContext(), R.string.followed_unfollowed);
                        } else {
                            List<Track> base = new ArrayList<>();
                            for (Track t : Tg.get(requireContext()).library)
                                if (t.chatId == c.id) base.add(t.chatId + ":" + t.messageId);
                            fs.follow(c.id, c.title == null ? "چت" : c.title, base);
                            Ui.toast(requireContext(), R.string.followed_now);
                        }
                    } else {
                        deepScan(c);
                    }
                }).show();
    }

    private void deepScan(TdApi.Chat c) {''')
io.open(p, 'w', encoding='utf-8').write(s)
print('ChatsFragment ✓')

# ═══ ۴) MainActivity — منوی کنار: دنبال‌شده‌ها + تایمر پایش ═══
p = BASE + 'ui/MainActivity.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            if (id == R.id.sideProxy) {
                showFullScreen(new ProxyFragment());''','''            if (id == R.id.sideFollowed) {
                showFullScreen(new FollowedFragment());
            } else if (id == R.id.sideProxy) {
                showFullScreen(new ProxyFragment());''')
s = s.replace('''        int[] ids = {R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};''','''        int[] ids = {R.id.sideFollowed, R.id.sideProxy, R.id.sideLog, R.id.sideSettings, R.id.sideTheme};''')
# تایمر پایش — بعد از READY
s = s.replace('''                PlayerManager.get(this).attach(miniListener);
                updateMini();''','''                PlayerManager.get(this).attach(miniListener);
                updateMini();
                startFollowChecks();''')
s = s.replace('''    private void setQuery(String q) {''','''    /** تایمر پایش دنبال‌شده‌ها — هر ۱۵ دقیقه تا وقتی اپ باز است */
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

    private void setQuery(String q) {''')
io.open(p, 'w', encoding='utf-8').write(s)
print('MainActivity ✓')

# ساید منو — آیتم دنبال‌شده‌ها
p = RES + 'layout/activity_main.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            <TextView
                android:id="@+id/sideProxy"''','''            <TextView
                android:id="@+id/sideFollowed"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:paddingHorizontal="20dp"
                android:paddingVertical="14dp"
                android:text="@string/followed_title"
                android:textColor="?attr/colorPrimary"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/sideProxy"''')
io.open(p, 'w', encoding='utf-8').write(s)
print('activity_main.xml ✓')

# ═══ ۵) App.onCreate — ساخت کانال‌های نوتیف ═══
p = BASE + 'App.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''        inst = this;
        ACCENT = Prefs.get(this).accentColor();
        applyTheme(this);''','''        inst = this;
        ACCENT = Prefs.get(this).accentColor();
        applyTheme(this);
        // کانال‌های نوتیف — اسکن و دنبال‌شده‌ها
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ir.moeshakteam.moeshakmusic.util.NotifHelper.ensureChannels(this);
        }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('App ✓')
