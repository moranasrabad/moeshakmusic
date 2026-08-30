# -*- coding: utf-8 -*-
# پچ Tg.java — اسکن عمیق بی‌نهایت + نوتیف اسکن + چک دنبال‌شده‌ها
import io

p = 'data/Tg.java'
s = io.open(p, encoding='utf-8').read()

# ═══ ۱) deepHistory: بی‌نهایت تا پایان چت + progress callback ═══
old_deep = '''    private List<Track> deepHistory(TdApi.Chat chat, int[] msgsOut) {
        List<Track> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String chatTitle = chat.title == null ? "" : chat.title;
        if (chat.id == myUserId) chatTitle = "Saved ⭐";
        long from = 0L;
        for (int round = 0; round < 60; round++) {
            if (scanCancel) break;
            org.json.JSONObject h;
            try {
                h = TdClient.syncRaw(new TdApi.GetChatHistory(chat.id, from, 0, 50, false));
            } catch (Exception e) {
                break;
            }
            org.json.JSONArray arr = h.optJSONArray("messages");
            int n = arr == null ? 0 : arr.length();
            msgsOut[0] += n;
            for (RawTrack r : extractAudioRaw(chat.id, h)) {
                if (seen.add(chat.id + ":" + r.msgId)) out.add(toTrack(r, chatTitle));
            }
            if (msgsOut[0] % 200 == 0) {
                log("   … " + msgsOut[0] + " پیام، " + out.size() + " فایل");
            }
            if (n < 50 || arr == null) break;
            from = arr.optJSONObject(n - 1).optLong("id");
        }
        return out;
    }'''
new_deep = '''    public interface DeepProgress { void onProgress(int found, int msgs); }

    /** اسکن عمیق — کل تاریخچهٔ چت، هرچقدر که دارد (بدون سقف) — تیم موشک */
    private List<Track> deepHistory(TdApi.Chat chat, int[] msgsOut, DeepProgress progress) {
        List<Track> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String chatTitle = chat.title == null ? "" : chat.title;
        if (chat.id == myUserId) chatTitle = "Saved ⭐";
        long from = 0L;
        int emptyRounds = 0;
        while (true) {
            if (scanCancel) break;
            org.json.JSONObject h;
            try {
                h = TdClient.syncRaw(new TdApi.GetChatHistory(chat.id, from, 0, 50, false));
            } catch (Exception e) {
                break;
            }
            org.json.JSONArray arr = h.optJSONArray("messages");
            int n = arr == null ? 0 : arr.length();
            msgsOut[0] += n;
            for (RawTrack r : extractAudioRaw(chat.id, h)) {
                if (seen.add(chat.id + ":" + r.msgId)) out.add(toTrack(r, chatTitle));
            }
            if (progress != null) progress.onProgress(out.size(), msgsOut[0]);
            if (msgsOut[0] % 500 == 0) {
                log("   … " + msgsOut[0] + " پیام، " + out.size() + " فایل");
            }
            if (n < 50 || arr == null) {
                emptyRounds++;
                if (emptyRounds >= 2) break;
            } else {
                emptyRounds = 0;
            }
            long lastId = arr == null ? 0 : arr.optJSONObject(n - 1).optLong("id");
            if (lastId == from || lastId == 0) break;
            from = lastId;
        }
        return out;
    }'''
assert old_deep in s, 'deepHistory not found'
s = s.replace(old_deep, new_deep)

# فراخواننده deepScanChat
old_call = '''                int[] msgs = new int[1];
                List<Track> found = deepHistory(chat, msgs);
                // ادغام در بخش اسکن (جدا از کتابخانه)'''
new_call = '''                int[] msgs = new int[1];
                List<Track> found = deepHistory(chat, msgs,
                        (fnd, m) -> { for (Tg.ScanListener x : deepListeners) x.onProgress(fnd, m); });'''
assert old_call in s, 'deepScan caller not found'
s = s.replace(old_call, new_call)

# deepScanSaved
old_saved = '''                List<Track> found = deepHistory(saved, msgs);
                int added = mergeNew(found, scanResults);'''
new_saved = '''                List<Track> found = deepHistory(saved, msgs, null);
                int added = mergeNew(found, scanResults);'''
assert old_saved in s, 'deepScanSaved caller not found'
s = s.replace(old_saved, new_saved)

# deepListeners
s = s.replace('''    /** هوک UI — بعد از تغییر کتابخانه صدا زده می‌شود */
    public volatile Runnable onLibraryChanged;''','''    /** هوک UI — بعد از تغییر کتابخانه صدا زده می‌شود */
    public volatile Runnable onLibraryChanged;
    /** شنونده‌های پیشرفت اسکن عمیق (UI) */
    public final List<ScanListener> deepListeners = new CopyOnWriteArrayList<>();''')

# ═══ ۲) نوتیف اسکن ═══
old_prog = '''                    chats++;
                    scannedChats = chats;'''
new_prog = '''                    chats++;
                    scannedChats = chats;
                    if (chats == 1) ir.moeshakteam.moeshakmusic.util.NotifHelper.scanProgress(ctx, 0, target.size());
                    else if (chats % 10 == 0) ir.moeshakteam.moeshakmusic.util.NotifHelper.scanProgress(ctx, chats, target.size());'''
assert old_prog in s, 'scan progress anchor not found'
s = s.replace(old_prog, new_prog, 1)

old_done = '''                log("🏁 اسکن تمام شد: " + chats + " چت، " + files + " فایل صوتی، " + added + " تراک جدید");
                cb.onDone(target.size());'''
new_done = '''                log("🏁 اسکن تمام شد: " + chats + " چت، " + files + " فایل صوتی، " + added + " تراک جدید");
                long dsec = (System.currentTimeMillis() - tStart) / 1000;
                ir.moeshakteam.moeshakmusic.util.NotifHelper.scanDone(ctx, chats, target.size(), (int) dsec);
                cb.onDone(target.size());'''
assert old_done in s, 'scan done anchor not found'
s = s.replace(old_done, new_done, 1)

old_target = '''        final List<Track> target = toLibrary ? library : scanResults;
        EXEC.execute(() -> {'''
new_target = '''        final List<Track> target = toLibrary ? library : scanResults;
        final long tStart = System.currentTimeMillis();
        EXEC.execute(() -> {'''
assert old_target in s, 'target anchor not found'
s = s.replace(old_target, new_target, 1)

# ═══ ۳) چک دنبال‌شده‌ها ═══
old_cancel = '''    /** لغو اسکن جاری */
    public volatile boolean scanCancel = false;'''
new_cancel = '''    /** لغو اسکن جاری */
    public volatile boolean scanCancel = false;

    // ---------- دنبال‌شده‌ها: چک آهنگ جدید ----------

    /** آهنگ‌های جدید چت‌های دنبال‌شده — در صفحهٔ دنبال‌شده‌ها */
    public final List<Track> followedResults = new CopyOnWriteArrayList<>();
    /** هوک UI بعد از هر چک */
    public volatile Runnable onFollowedUpdate;

    public interface FollowDone { void onDone(boolean foundNew); }

    /** چک همهٔ چت‌های دنبال‌شده — نوتیف + followedResults */
    public void checkFollowed(FollowDone cb) {
        if (scanning) { if (cb != null) cb.onDone(false); return; }
        List<FollowStore.Followed> fs = FollowStore.get(ctx).all();
        if (fs.isEmpty()) { if (cb != null) cb.onDone(false); return; }
        scanning = true;
        EXEC.execute(() -> {
            boolean foundNew = false;
            try {
                log("🔔 چک دنبال‌شده‌ها (" + fs.size() + " چت)…");
                for (FollowStore.Followed f : fs) {
                    if (scanCancel) break;
                    TdApi.Chat chat = chatFromRaw(f.chatId);
                    if (chat.title != null && !chat.title.isEmpty() && !chat.title.equals(f.title)) {
                        FollowStore.get(ctx).updateTitle(f.chatId, chat.title);
                        f.title = chat.title;
                    }
                    int[] msgs = new int[1];
                    List<Track> found = scanChatHistory(chat, msgs);
                    List<Track> newOnes = new ArrayList<>();
                    for (Track t : found) {
                        if (!f.knownIds.contains(t.chatId + ":" + t.messageId)) newOnes.add(t);
                    }
                    if (!newOnes.isEmpty()) {
                        foundNew = true;
                        for (Track t : newOnes) {
                            boolean ex = false;
                            for (Track x : followedResults) if (x.sameAs(t)) { ex = true; break; }
                            if (!ex) followedResults.add(t);
                        }
                        Set<String> all = new HashSet<>(f.knownIds);
                        for (Track t : found) all.add(t.chatId + ":" + t.messageId);
                        FollowStore.get(ctx).updateKnown(f.chatId, new ArrayList<>(all));
                        List<String> titles = new ArrayList<>();
                        for (int i = 0; i < Math.min(3, newOnes.size()); i++) titles.add(newOnes.get(i).title);
                        String chTitle = chat.title == null || chat.title.isEmpty() ? f.title : chat.title;
                        ir.moeshakteam.moeshakmusic.util.NotifHelper.newTracks(ctx, chTitle, titles);
                        log("🎵 جدید از «" + chTitle + "»: " + newOnes.size());
                    }
                }
                Runnable r = onFollowedUpdate;
                if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            } catch (Exception e) {
                log("⚠️ چک دنبال‌شده: " + e.getMessage());
            } finally {
                scanning = false;
            }
            if (cb != null) cb.onDone(foundNew);
        });
    }'''
assert old_cancel in s, 'cancel anchor not found'
s = s.replace(old_cancel, new_cancel, 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('Tg.java patched OK')
