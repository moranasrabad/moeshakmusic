import Foundation

/// موتور اسکن — همان v3 اندروید (تاریخچهٔ مستقیم + JSON خام) — تیم موشک
@MainActor
final class LibraryManager: ObservableObject {

    // MARK: - State

    /// کتابخانهٔ جاری (با هر اسکن/بازیابی ساخته می‌شود — دائمی نیست)
    @Published var library: [Track] = []
    /// نتایج اسکن — جدا از کتابخانه
    @Published var scanResults: [Track] = []
    @Published var scanState = ScanState.idle
    @Published var chatPhotoFiles: [Int64: Int] = [:]

    struct ScanState: Equatable {
        var running = false
        var chatsScanned = 0
        var found = 0
        var seconds = 0
        var cancelled = false

        static let idle = ScanState()
    }

    static let shared = LibraryManager()

    /// پرچم لغو — بین thread ها
    private static let cancelLock = NSLock()
    private static var _cancelled = false
    nonisolated static var cancelRequested: Bool {
        get { cancelLock.lock(); defer { cancelLock.unlock() }; return _cancelled }
        set { cancelLock.lock(); _cancelled = newValue; cancelLock.unlock() }
    }

    private var restored = false
    private var scanTimer: Timer?

    private init() {}

    // MARK: - Public API

    func wipeInMemory() {
        library = []
        scanResults = []
        scanState = .idle
        restored = false
    }

    func startScan(depth: Int) {
        guard !scanState.running, Session.shared.state == .ready else { return }
        Self.cancelRequested = false
        scanState = ScanState(running: true)
        let startedAt = Date()
        scanTimer?.invalidate()
        scanTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] t in
            Task { @MainActor [weak self] in
                guard let self else { t.invalidate(); return }
                if self.scanState.running {
                    self.scanState.seconds = Int(Date().timeIntervalSince(startedAt))
                } else { t.invalidate() }
            }
        }

        Task.detached { [weak self] in
            guard let self else { return }
            var cancelled = false
            var chats = 0
            let allChats = ScanEngine.loadAllChats()
            for chatId in allChats.prefix(depth) {
                if Self.cancelRequested { cancelled = true; break }
                let title = await self.titleSync(chatId)
                let found = ScanEngine.scanChatHistory(chatId: chatId, chatTitle: title)
                let total = await MainActor.run { () -> Int in
                    self.mergeInto(&self.scanResults, newTracks: found)
                    return self.scanResults.count
                }
                chats += 1
                let c = chats
                await MainActor.run {
                    self.scanState.chatsScanned = c
                    self.scanState.found = total
                }
            }
            let wasCancelled = cancelled
            await MainActor.run {
                self.scanState.running = false
                self.scanState.cancelled = wasCancelled
            }
        }
    }

    func cancelScan() { Self.cancelRequested = true }

    func addScanResultsToLibrary() -> Int {
        let added = mergeInto(&library, newTracks: scanResults)
        scanResults = []
        sortNewestFirst(&library)
        return added
    }

    func clearScanResults() { scanResults = [] }

    func tracksOfChat(_ chatId: Int64) -> [Track] {
        library.filter { $0.chatId == chatId }
    }

    func deepScan(chatId: Int64, completion: @escaping (Int) -> Void) {
        Task.detached { [weak self] in
            guard let self else { return }
            let title = await self.titleSync(chatId)
            let all = ScanEngine.deepHistory(chatId: chatId, chatTitle: title)
            let _ = await MainActor.run { self.mergeInto(&self.scanResults, newTracks: all) }
            let _ = all.count
            completion(all.count)
        }
    }

    // MARK: - Helpers

    private func mergeInto(_ target: inout [Track], newTracks: [Track]) -> Int {
        var added = 0
        let existing = Set(target.map(\.id))
        for var t in newTracks where !existing.contains(t.id) {
            t.chatPhotoFileId = chatPhotoFiles[t.chatId] ?? t.chatPhotoFileId
            target.append(t)
            added += 1
        }
        return added
    }

    private func sortNewestFirst(_ list: inout [Track]) {
        list.sort { $0.date > $1.date }
    }

    /// عنوان چت (روی MainActor نگه داشته می‌شود، درخواست TDLib sync در پس‌زمینه)
    private func titleSync(_ chatId: Int64) async -> String {
        if chatId == Session.shared.myUserId { return "سیو ⭐" }
        if let t = chatTitles[chatId] { return t }
        let id = chatId
        let (title, photoId): (String, Int?) = await Task.detached {
            guard let c = try? TDJson.syncDict(["@type": "getChat", "chat_id": id]) else {
                return ("چت \(id)", nil)
            }
            let t = c["title"] as? String ?? ""
            var pid: Int?
            if let photo = c["photo"] as? [String: Any],
               let small = photo["small"] as? [String: Any] {
                pid = small["id"] as? Int
            }
            return (t.isEmpty ? "چت \(id)" : t, pid)
        }.value
        chatTitles[chatId] = title
        if let p = photoId { chatPhotoFiles[chatId] = p }
        return title
    }

    // MARK: - بازیابی بعد از ورود (فقط فیوریت‌ها + پلی‌لیست‌ها)

    func restoreFavoritesAndPlaylists() {
        guard !restored else { return }
        restored = true
        Task.detached { [weak self] in
            guard let self else { return }
            let (favKeys, playlists, savedTracks) = await MainActor.run {
                (Store.shared.favorites.allKeys,
                 Store.shared.playlists.items,
                 Store.shared.load([Track].self, file: "savedTracks.json") ?? [])
            }
            let byKey = Dictionary(grouping: savedTracks, by: \.id).compactMapValues(\.first)
            var resolved: [Track] = []
            for key in favKeys {
                if var old = byKey[key] {
                    if let nt = ScanEngine.refreshFileId(old) { old = nt }
                    resolved.append(old)
                }
            }
            var updated: [PlaylistStore.Playlist] = []
            var playlistsChanged = false
            for var p in playlists {
                var changed = false
                for i in p.tracks.indices {
                    if let nt = ScanEngine.refreshFileId(p.tracks[i]) {
                        p.tracks[i] = nt
                        changed = true
                        resolved.append(nt)
                    }
                }
                if changed { playlistsChanged = true }
                updated.append(p)
            }
            await MainActor.run {
                self.mergeInto(&self.library, newTracks: resolved)
                self.library.sort { $0.date > $1.date }
                if playlistsChanged { Store.shared.playlists.items = updated }
            }
        }
    }

    /// ذخیرهٔ کامل تراک‌های فیوریت (هر بار که فیوریت عوض می‌شود)
    func persistFavoriteTracks() {
        var all: [Track] = []
        var seen = Set<String>()
        for t in library where Store.shared.favorites.contains(t) {
            if seen.insert(t.id).inserted { all.append(t) }
        }
        for t in PlayerManager.shared.queue where Store.shared.favorites.contains(t) {
            if seen.insert(t.id).inserted { all.append(t) }
        }
        for t in Store.shared.playlists.allTracks where Store.shared.favorites.contains(t) {
            if seen.insert(t.id).inserted { all.append(t) }
        }
        Store.shared.save(all, file: "savedTracks.json")
    }
}

// MARK: - موتور پس‌زمینه (کاملاً sync — فقط از thread اسکن)

private enum ScanEngine {

    /// لود همهٔ چت‌ها (اصلی + آرشیو) — سیو اول
    static func loadAllChats() -> [Int64] {
        var ids: [Int64] = []
        for listType in ["chatListMain", "chatListArchive"] {
            while true {
                do {
                    _ = try TDJson.syncDict(["@type": "loadChats",
                                             "chat_list": ["@type": listType]])
                    continue
                } catch { break }
            }
            if let resp = try? TDJson.syncDict(["@type": "getChats",
                                                "chat_list": ["@type": listType],
                                                "limit": 3000]),
               let arr = resp["chat_ids"] as? [Int64] {
                ids.append(contentsOf: arr)
            }
        }
        var seen = Set<Int64>()
        var out: [Int64] = []
        let me = UserDefaults.standard.int64(forKey: "my_user_id")
        if me != 0, ids.contains(me) { out.append(me); seen.insert(me) }
        for id in ids where !seen.contains(id) {
            seen.insert(id); out.append(id)
        }
        return out
    }

    /// اسکن یک چت با تاریخچهٔ مستقیم (۶ دور × ۵۰) + سرچ مکمل
    static func scanChatHistory(chatId: Int64, chatTitle: String) -> [Track] {
        var out: [Track] = []
        var seen = Set<String>()
        var from: Int64 = 0
        let myId = UserDefaults.standard.int64(forKey: "my_user_id")
        let title = chatId == myId ? "سیو ⭐" : chatTitle

        for _ in 0..<6 {
            var req: TDJson.AnyDict = ["@type": "getChatHistory",
                                       "chat_id": chatId, "limit": 50, "only_local": false]
            if from != 0 { req["from_message_id"] = from }
            guard let h = try? TDJson.syncDict(req),
                  let messages = h["messages"] as? [[String: Any]] else { break }
            for m in messages {
                if let t = Track.fromMessage(m, chatId: chatId, chatTitle: title),
                   seen.insert(t.id).inserted {
                    out.append(t)
                }
            }
            if messages.count < 50 { break }
            if let lastId = messages.last?["id"] as? Int64 { from = lastId } else { break }
        }
        if out.isEmpty,
           let f = try? TDJson.syncDict(["@type": "searchChatMessages",
                                         "chat_id": chatId, "query": "", "limit": 50,
                                         "filter": ["@type": "searchMessagesFilterAudio"]]),
           let messages = f["messages"] as? [[String: Any]] {
            for m in messages {
                if let t = Track.fromMessage(m, chatId: chatId, chatTitle: title),
                   seen.insert(t.id).inserted { out.append(t) }
            }
        }
        return out
    }

    /// اسکن عمیق — تا ۳۰۰۰ پیام
    static func deepHistory(chatId: Int64, chatTitle: String) -> [Track] {
        var all: [Track] = []
        var from: Int64 = 0
        for _ in 0..<60 {
            if LibraryManager.cancelRequested { break }
            var req: TDJson.AnyDict = ["@type": "getChatHistory",
                                       "chat_id": chatId, "limit": 50, "only_local": false]
            if from != 0 { req["from_message_id"] = from }
            guard let h = try? TDJson.syncDict(req),
                  let messages = h["messages"] as? [[String: Any]] else { break }
            for m in messages {
                if let t = Track.fromMessage(m, chatId: chatId, chatTitle: chatTitle) {
                    all.append(t)
                }
            }
            if messages.count < 50 { break }
            if let lastId = messages.last?["id"] as? Int64 { from = lastId } else { break }
        }
        return all
    }

    /// گرفتن fileId تازهٔ یک تراک از پیام اصلی
    static func refreshFileId(_ old: Track) -> Track? {
        guard let msg = try? TDJson.syncDict(["@type": "getMessage",
                                              "chat_id": old.chatId,
                                              "message_id": old.messageId]),
              (msg["@type"] as? String) != "error" else { return nil }
        var t = Track.fromMessage(msg, chatId: old.chatId,
                                  chatTitle: old.chatTitle.isEmpty ? "سیو ⭐" : old.chatTitle)
        t?.thumbFileId = old.thumbFileId != 0 ? old.thumbFileId : (t?.thumbFileId ?? 0)
        return t
    }
}
