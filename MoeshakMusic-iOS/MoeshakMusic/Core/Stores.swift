import Foundation

/// ذخیره‌سازی دائمی — فقط فیوریت‌ها و پلی‌لیست‌ها (طبق تصمیم: بقیه با هر اسکن ساخته می‌شوند)
final class Store {
    static let shared = Store()
    private let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]

    lazy var favorites = FavoritesStore(self)
    lazy var playlists = PlaylistStore(self)
    lazy var downloads = DownloadStore(self)

    private init() {}

    func url(_ name: String) -> URL { dir.appendingPathComponent(name) }

    func load<T: Decodable>(_ type: T.Type, file: String) -> T? {
        guard let data = try? Data(contentsOf: url(file)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    func save<T: Encodable>(_ obj: T, file: String) {
        if let data = try? JSONEncoder().encode(obj) {
            try? data.write(to: url(file), options: .atomic)
        }
    }
}

// MARK: - Favorites

final class FavoritesStore: ObservableObject {
    @Published var keys: Set<String> = [] { didSet { persist() } }

    private weak var store: Store?
    init(_ store: Store) {
        self.store = store
        if let saved = store.load(SetPayload.self, file: "favorites.json") {
            keys = Set(saved.keys)
        }
    }

    private struct SetPayload: Codable { var keys: [String] }

    private func persist() {
        store?.save(SetPayload(keys: Array(keys)), file: "favorites.json")
    }

    func contains(_ t: Track) -> Bool { keys.contains(t.id) }

    func toggle(_ t: Track) {
        if keys.contains(t.id) { keys.remove(t.id) } else { keys.insert(t.id) }
    }

    func insert(_ t: Track) { keys.insert(t.id) }

    func clear() { keys.removeAll() }

    /// همهٔ کلیدها — برای بازیابی بعد از ورود
    var allKeys: [String] { Array(keys) }
}

// MARK: - Playlists

final class PlaylistStore: ObservableObject {
    struct Playlist: Identifiable, Codable, Hashable {
        var id: UUID = UUID()
        var name: String
        var tracks: [Track]
    }

    @Published var items: [Playlist] = [] { didSet { persist() } }

    private weak var store: Store?
    init(_ store: Store) {
        self.store = store
        if let saved = store.load([Playlist].self, file: "playlists.json") { items = saved }
    }

    private func persist() {
        store?.save(items, file: "playlists.json")
    }

    func create(_ name: String) {
        guard !name.isEmpty, !items.contains(where: { $0.name == name }) else { return }
        items.append(Playlist(name: name, tracks: []))
    }

    func delete(_ p: Playlist) { items.removeAll { $0.id == p.id } }

    @discardableResult
    func rename(_ p: Playlist, to newName: String) -> Bool {
        guard !newName.isEmpty, !items.contains(where: { $0.name == newName }) else { return false }
        if let i = items.firstIndex(where: { $0.id == p.id }) { items[i].name = newName }
        return true
    }

    @discardableResult
    func add(_ track: Track, to p: Playlist) -> Bool {
        guard let i = items.firstIndex(where: { $0.id == p.id }) else { return false }
        guard !items[i].tracks.contains(track) else { return false }
        items[i].tracks.append(track)
        return true
    }

    func remove(_ track: Track, from p: Playlist) {
        if let i = items.firstIndex(where: { $0.id == p.id }) {
            items[i].tracks.removeAll { $0 == track }
        }
    }

    /// همهٔ تراک‌های داخل پلی‌لیست‌ها (برای بازیابی fileId بعد از ورود)
    var allTracks: [Track] { items.flatMap(\.tracks) }
}

// MARK: - Downloads

final class DownloadStore: ObservableObject {
    struct Entry: Identifiable, Codable, Hashable {
        var id: String { key }
        var key: String
        var title: String
        var chatTitle: String
        var fileId: Int
        var size: Int64
        var path: String
    }

    @Published var entries: [Entry] = [] { didSet { persist() } }

    private weak var store: Store?
    init(_ store: Store) {
        self.store = store
        if let saved = store.load([Entry].self, file: "downloads.json") { entries = saved }
    }

    private func persist() {
        store?.save(entries, file: "downloads.json")
    }

    func pathOf(_ t: Track) -> String? {
        entries.first { $0.key == t.id }?.path
    }

    func isDownloaded(_ t: Track) -> Bool {
        if let p = pathOf(t) { return FileManager.default.fileExists(atPath: p) }
        return false
    }

    func mark(_ t: Track, path: String) {
        entries.removeAll { $0.key == t.id }
        entries.append(Entry(key: t.id, title: t.title, chatTitle: t.chatTitle,
                             fileId: t.fileId, size: t.expectedSize, path: path))
    }

    /// حذف از لیست + حذف فایل از حافظه
    func removeAndDeleteFile(_ e: Entry) {
        try? FileManager.default.removeItem(atPath: e.path)
        entries.removeAll { $0.key == e.key }
    }
}
