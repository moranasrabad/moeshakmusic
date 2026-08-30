# -*- coding: utf-8 -*-
# پچ v5.9.1 — تب Followed + دیپ‌اسکن خودکار فالوها + فیکس ویژوالایزر + دیپ‌اسکن کانال‌ها
import io

# ═══ ۱) MainActivity — تب Followed کنار Tracks ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

s = s.replace('''    /** صفحات اصلی — سواپ افقی */
    private static final int PAGE_TRACKS = 0;
    private static final int PAGE_SCAN = 1;
    private static final int PAGE_PLAYLISTS = 2;
    private static final int PAGE_FAVORITES = 3;
    private static final int PAGE_DOWNLOADS = 4;
    private static final int PAGE_CHANNELS = 5;
    private static final int PAGE_CHATS = 6;''','''    /** صفحات اصلی — سواپ افقی */
    private static final int PAGE_TRACKS = 0;
    private static final int PAGE_FOLLOWED = 1;
    private static final int PAGE_SCAN = 2;
    private static final int PAGE_PLAYLISTS = 3;
    private static final int PAGE_FAVORITES = 4;
    private static final int PAGE_DOWNLOADS = 5;
    private static final int PAGE_CHANNELS = 6;
    private static final int PAGE_CHATS = 7;''')

s = s.replace('''                tab.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position])''','''                tab.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.followed_tab),
                        getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position])''')

s = s.replace('''                tvTitle.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position]);''','''                tvTitle.setText(new String[]{
                        getString(R.string.tab_tracks), getString(R.string.followed_tab),
                        getString(R.string.tab_scan),
                        getString(R.string.tab_playlists), getString(R.string.tab_favorites),
                        getString(R.string.tab_downloads), getString(R.string.tab_channels),
                        getString(R.string.tab_chats)}[position]);''')

s = s.replace('''            if (id == R.id.sideFollowed) {
                showFullScreen(new FollowedFragment());
            } else if (id == R.id.sideProxy) {''','''            if (id == R.id.sideFollowed) {
                pager.setCurrentItem(PAGE_FOLLOWED, true);
            } else if (id == R.id.sideProxy) {''')

s = s.replace('''            switch (position) {
                case PAGE_SCAN:
                    return new ScanFragment();''','''            switch (position) {
                case PAGE_FOLLOWED:
                    return new FollowedFragment();
                case PAGE_SCAN:
                    return new ScanFragment();''')

s = s.replace('''        @Override
        public int getItemCount() {
            return 7;
        }''','''        @Override
        public int getItemCount() {
            return 8;
        }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('1. MainActivity ✓')

# ═══ ۲) activity_main.xml — حذف sideFollowed از منو ═══
p = 'res/layout/activity_main.xml'
s = io.open(p, encoding='utf-8').read()
import re
m = re.search(r'\s*<TextView\s+android:id="@\+id/sideFollowed".*?</TextView>', s, re.S)
if m:
    s = s[:m.start()] + s[m.end():]
    io.open(p, 'w', encoding='utf-8').write(s)
    print('2. sideFollowed از منو حذف ✓')
else:
    print('2. sideFollowed پیدا نشد!')

# ═══ ۳) FollowedFragment — بدون دکمهٔ بک (تب است) + لیست ترکیبی ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/FollowedFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());
        MaterialButton btnCheck = v.findViewById(R.id.btnCheckNow);''','''        MaterialButton btnCheck = v.findViewById(R.id.btnCheckNow);''')
# لیست: followedResults + scanResults (بدون تکرار)
s = s.replace('''        // آهنگ‌های جدید
        adapter.setAll(tg.followedResults);
        boolean emptyTracks = adapter.isEmpty();''','''        // آهنگ‌های جدید — دنبال‌شده‌ها + نتایج اسکن (بدون تکرار)
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<Track> combined = new java.util.ArrayList<>();
        for (Track t : tg.followedResults) {
            if (seen.add(t.chatId + ":" + t.messageId)) combined.add(t);
        }
        for (Track t : tg.scanResults) {
            if (seen.add(t.chatId + ":" + t.messageId)) combined.add(t);
        }
        adapter.setAll(combined);
        boolean emptyTracks = adapter.isEmpty();''')
# رفرش زنده بعد از دیپ‌اسکن فالوها
s = s.replace('''        // هوک UI — بعد از هر چک رفرش شود
        Tg.get(requireContext()).onFollowedUpdate = this::refreshSafe;''','''        // هوک UI — بعد از هر چک/دیپ‌اسکن رفرش شود
        Tg.get(requireContext()).onFollowedUpdate = this::refreshSafe;
        Tg.get(requireContext()).onLibraryChanged = this::refreshSafe;''')
io.open(p, 'w', encoding='utf-8').write(s)
print('3. FollowedFragment ✓')

# fragment_followed.xml — حذف btnBack
p = 'res/layout/fragment_followed.xml'
s = io.open(p, encoding='utf-8').read()
m = re.search(r'\s*<ImageButton\s+android:id="@\+id/btnBack".*?/>', s, re.S)
if m:
    s = s[:m.start()] + s[m.end():]
    io.open(p, 'w', encoding='utf-8').write(s)
    print('4. fragment_followed.xml btnBack حذف ✓')

# ═══ ۵) Tg.checkFollowed — دیپ اسکن کل چت ═══
p = 'java/ir/moeshakteam/moeshakmusic/data/Tg.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''                    int[] msgs = new int[1];
                    List<Track> found = scanChatHistory(chat, msgs);
                    List<Track> newOnes = new ArrayList<>();
                    for (Track t : found) {
                        if (!f.knownIds.contains(t.chatId + ":" + t.messageId)) newOnes.add(t);
                    }''','''                    int[] msgs = new int[1];
                    // کل چت — دیپ اسکن کامل (هرچقدر دارد)
                    List<Track> found = deepHistory(chat, msgs, (fnd, m) -> {
                        for (ScanListener x : deepListeners) x.onProgress(fnd, m);
                    });
                    List<Track> newOnes = new ArrayList<>();
                    for (Track t : found) {
                        if (!f.knownIds.contains(t.chatId + ":" + t.messageId)) newOnes.add(t);
                    }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('5. checkFollowed → deepHistory ✓')

# ═══ ۶) FollowStore.follow — baseline خالی؛ دیپ‌اسکن خودکار بعد از فالو (helper در Tg) ═══
s = io.open(p, encoding='utf-8').read()
s = s.replace('''    public interface FollowDone { void onDone(boolean foundNew); }''','''    public interface FollowDone { void onDone(boolean foundNew); }

    /**
     * فالو + دیپ‌اسکن کامل خودکار — چت فالو‌شده بلافاصله کامل خوانده می‌شود؛
     * آهنگ‌های جدید به scanResults می‌روند و knownIds بروز می‌شود (فقط آینده نوتیف بخورد).
     */
    public void followAndDeepScan(long chatId, String title) {
        FollowStore fs = FollowStore.get(ctx);
        if (fs.isFollowed(chatId)) return;
        // baseline = آهنگ‌های فعلی کتابخانه از این چت
        List<String> base = new ArrayList<>();
        for (Track t : library) if (t.chatId == chatId) base.add(t.chatId + ":" + t.messageId);
        fs.follow(chatId, title, base);
        log("🔔 فالو شد: " + title + " — دیپ‌اسکن کامل شروع می‌شود…");
        deepScanChat(chatId, new ScanListener() {
            @Override
            public void onProgress(int found, int chats) {
                for (ScanListener x : deepListeners) x.onProgress(found, chats);
            }

            @Override
            public void onDone(int added) {
                // knownIds = کتابخانهٔ چت + همهٔ scanResults این چت
                Set<String> known = new HashSet<>(base);
                for (Track t : scanResults) {
                    if (t.chatId == chatId) known.add(t.chatId + ":" + t.messageId);
                }
                for (Track t : library) {
                    if (t.chatId == chatId) known.add(t.chatId + ":" + t.messageId);
                }
                fs.updateKnown(chatId, new ArrayList<>(known));
                log("🔔 دیپ‌اسکن «" + title + "» تمام شد — " + added + " آهنگ جدید در بخش دنبال‌شده‌ها/اسکن");
            }

            @Override
            public void onError(String msg) {
                log("⚠️ دیپ‌اسکن فالو: " + msg);
            }
        });
    }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('6. followAndDeepScan ✓')

# ═══ ۷) ChannelsFragment — followAndDeepScan + دکمهٔ اسکن عمیق ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/ChannelsFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''            // 🔔 دنبال کردن — پایش آهنگ جدید
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
            });''','''            // 🔔 دنبال کردن — پایش + دیپ‌اسکن کامل خودکار
            h.btnFollow.setOnClickListener(x -> {
                ir.moeshakteam.moeshakmusic.data.FollowStore fs =
                        ir.moeshakteam.moeshakmusic.data.FollowStore.get(requireContext());
                long cid = tracks.isEmpty() ? 0 : tracks.get(0).chatId;
                if (fs.isFollowed(cid)) {
                    fs.unfollow(cid);
                    Ui.toast(requireContext(), R.string.followed_unfollowed);
                } else {
                    Tg.get(requireContext()).followAndDeepScan(cid, name);
                    Ui.toast(requireContext(), R.string.followed_deep_started);
                }
                refresh();
            });
            // 🔍 اسکن عمیق کل کانال
            h.btnDeepScan.setOnClickListener(x -> {
                if (tracks.isEmpty()) return;
                Ui.toast(requireContext(), getString(R.string.deep_scan_started, name));
                Tg.get(requireContext()).deepScanChat(tracks.get(0).chatId, new Tg.ScanListener() {
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
            });''')
s = s.replace('''            h.btnFollow.setImageResource(''','''            h.btnDeepScan.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);
            h.btnFollow.setImageResource(''')
s = s.replace('''    static class VH extends RecyclerView.ViewHolder {
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
    }''','''    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAvatar, tvName, tvCount;
        final ImageButton btnAddToPlaylist, btnDownloadAll, btnFollow, btnDeepScan;

        VH(@NonNull View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvAvatar);
            tvName = v.findViewById(R.id.tvName);
            tvCount = v.findViewById(R.id.tvCount);
            btnAddToPlaylist = v.findViewById(R.id.btnAddToPlaylist);
            btnDownloadAll = v.findViewById(R.id.btnDownloadAll);
            btnFollow = v.findViewById(R.id.btnFollow);
            btnDeepScan = v.findViewById(R.id.btnDeepScan);
        }
    }''')
io.open(p, 'w', encoding='utf-8').write(s)
print('7. ChannelsFragment ✓')

# item_channel.xml — دکمهٔ دیپ‌اسکن
p = 'res/layout/item_channel.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''        <ImageButton
            android:id="@+id/btnFollow"''','''        <ImageButton
            android:id="@+id/btnDeepScan"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/chat_deep_scan"
            android:src="@drawable/ic_search"
            app:tint="?attr/colorOnSurfaceVariant" />

        <ImageButton
            android:id="@+id/btnFollow"''')
io.open(p, 'w', encoding='utf-8').write(s)
print('8. item_channel.xml ✓')

# ═══ ۹) ChatsFragment — فالو با followAndDeepScan ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/ChatsFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''                    if (w == 0) {
                        if (fol) {
                            fs.unfollow(c.id);
                            Ui.toast(requireContext(), R.string.followed_unfollowed);
                        } else {
                            List<String> base = new ArrayList<>();
                            for (Track t : Tg.get(requireContext()).library)
                                if (t.chatId == c.id) base.add(t.chatId + ":" + t.messageId);
                            fs.follow(c.id, c.title == null ? "چت" : c.title, base);
                            Ui.toast(requireContext(), R.string.followed_now);
                        }
                    } else {''','''                    if (w == 0) {
                        if (fol) {
                            fs.unfollow(c.id);
                            Ui.toast(requireContext(), R.string.followed_unfollowed);
                        } else {
                            Tg.get(requireContext()).followAndDeepScan(c.id,
                                    c.title == null ? "چت" : c.title);
                            Ui.toast(requireContext(), R.string.followed_deep_started);
                        }
                    } else {''')
io.open(p, 'w', encoding='utf-8').write(s)
print('9. ChatsFragment ✓')

# ═══ ۱۰) ویژوالایزر — کاور کوچک‌تر تا میله‌ها دیده شوند ═══
p = 'res/layout/fragment_player.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('''    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/art"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@drawable/bg_glow"
        android:scaleType="centerCrop"
        android:src="@drawable/bg_art"
        app:layout_constraintBottom_toBottomOf="@id/viz"
        app:layout_constraintDimensionRatio="1:1"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="@id/viz"
        app:shapeAppearanceOverlay="@style/CircleImg" />''','''    <!-- کاور: ۶۴٪ فضای ویژوالایزر تا میله‌های نئونی دورش دیده شوند -->
    <com.google.android.material.imageview.ShapeableImageView
        android:id="@+id/art"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@drawable/bg_glow"
        android:scaleType="centerCrop"
        android:src="@drawable/bg_art"
        app:layout_constraintBottom_toBottomOf="@id/viz"
        app:layout_constraintDimensionRatio="1:1"
        app:layout_constraintEnd_toEndOf="@id/viz"
        app:layout_constraintHeight_percent="0.64"
        app:layout_constraintStart_toStartOf="@id/viz"
        app:layout_constraintTop_toTopOf="@id/viz"
        app:layout_constraintWidth_percent="0.64"
        app:shapeAppearanceOverlay="@style/CircleImg" />''')
io.open(p, 'w', encoding='utf-8').write(s)
print('10. fragment_player.xml — کاور ۶۴٪ ✓')

# ═══ ۱۱) استرینگ‌های جدید ═══
p = 'res/values/strings.xml'
s = io.open(p, encoding='utf-8').read()
fa = '''    <string name="followed_tab">دنبال‌شده</string>
    <string name="followed_deep_started">🔔 دنبال شد — کل چت دیپ‌اسکن می‌شود…</string>
'''
s = s.replace('</resources>', fa + '</resources>')
io.open(p, 'w', encoding='utf-8').write(s)
p = 'res/values-en/strings.xml'
s = io.open(p, encoding='utf-8').read()
en = '''    <string name="followed_tab">FOLLOWED</string>
    <string name="followed_deep_started">🔔 Followed — deep scanning the whole chat…</string>
'''
s = s.replace('</resources>', en + '</resources>')
io.open(p, 'w', encoding='utf-8').write(s)
print('11. strings ✓')

# دوباره‌ها
import re, collections
for pp in ['res/values/strings.xml', 'res/values-en/strings.xml']:
    names = re.findall(r'<string name="([^"]+)"', io.open(pp, encoding='utf-8').read())
    dups = [n for n, c in collections.Counter(names).items() if c > 1]
    print(pp, 'dups:', dups if dups else 'هیچ ✓')
