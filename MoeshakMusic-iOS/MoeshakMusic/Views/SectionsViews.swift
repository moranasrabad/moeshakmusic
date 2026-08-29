import SwiftUI

/// اجزای مشترک
struct HeaderView: View {
    @EnvironmentObject var session: Session
    let title: String
    var showSearch: Bool
    var query: Binding<String> = .constant("")
    @State private var showSettings = false

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 12) {
                Text(title.uppercased())
                    .font(.system(size: 26, weight: .heavy))
                    .foregroundColor(.moeshakText)
                Spacer()
                // سپر وضعیت اتصال — مثل تلگرام
                Button { UIHelpers.showProxy() } label: {
                    Image(systemName: "shield.lefthalf.filled")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(session.connColor)
                        .opacity(Prefs.shared.proxyEnabled ? 1 : 0.4)
                }
                .buttonStyle(.plain)
                Button { showSettings = true } label: {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 19, weight: .bold))
                        .foregroundColor(.moeshakText)
                }
            }
            .padding(.horizontal, 16).padding(.top, 8)

            if showSearch {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(.moeshakMuted)
                    TextField("جستجوی آهنگ…", text: query)
                        .foregroundColor(.moeshakText)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 24).fill(Color.moeshakCard)
                    .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.moeshakOutline, lineWidth: 1)))
                .padding(.horizontal, 14)
            }
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
    }
}

extension Session {
    var connColor: Color {
        switch connState {
        case .ready: return Color(hex: 0x34D399)
        case .waiting: return Color(hex: 0xF87171)
        default: return Color(hex: 0xF59E0B)
        }
    }
}

struct EmptyStateView: View {
    let icon: String
    let text: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 40)).foregroundColor(.moeshakMuted)
            Text(text).font(.system(size: 14)).foregroundColor(.moeshakMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - پلی‌لیست‌ها (ادیت/حذف/دانلود)

struct PlaylistsView: View {
    @ObservedObject private var store = Store.shared.playlists
    @EnvironmentObject var player: PlayerManager
    @State private var showNew = false
    @State private var newName = ""
    @State private var renameTarget: PlaylistStore.Playlist?

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "پلی‌لیست", showSearch: false)
            Button {
                showNew = true
            } label: {
                Label("پلی‌لیست جدید", systemImage: "plus.circle.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: 0x22D3EE))
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.horizontal, 16)
            if store.items.isEmpty {
                EmptyStateView(icon: "list.bullet.rectangle", text: "هنوز پلی‌لیستی نداری —\nروی آهنگ «⋯» بزن و «افزودن به پلی‌لیست»")
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(store.items) { p in
                            VStack(spacing: 0) {
                                Button {
                                    if !p.tracks.isEmpty { player.play(p.tracks, at: 0) }
                                } label: {
                                    HStack(spacing: 14) {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 14).fill(
                                                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                                            )
                                            Image(systemName: "music.note").foregroundColor(.white)
                                        }
                                        .frame(width: 52, height: 52)
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(p.name).font(.system(size: 17, weight: .bold)).foregroundColor(.moeshakText)
                                            Text("\(p.tracks.count) آهنگ").font(.system(size: 13)).foregroundColor(.moeshakMuted)
                                        }
                                        Spacer()
                                    }
                                    .padding(14)
                                }
                                .buttonStyle(.plain)
                                // ردیف دکمه‌ها: پخش/ادیت/دانلود/حذف
                                HStack {
                                    Spacer()
                                    Button { if !p.tracks.isEmpty { player.play(p.tracks, at: 0) } } label: {
                                        Label("پخش", systemImage: "play.fill")
                                    }.buttonStyle(PLButton())
                                    Button { renameTarget = p } label: {
                                        Label("ادیت", systemImage: "pencil")
                                    }.buttonStyle(PLButton())
                                    Button { downloadAll(p) } label: {
                                        Label("دانلود", systemImage: "arrow.down")
                                    }.buttonStyle(PLButton())
                                    Button(role: .destructive) {
                                        Store.shared.playlists.delete(p)
                                    } label: {
                                        Label("حذف", systemImage: "trash")
                                    }.buttonStyle(PLButton(destructive: true))
                                }
                                .padding(.horizontal, 14).padding(.bottom, 12)
                            }
                            .background(RoundedRectangle(cornerRadius: 20).fill(Color.moeshakCard)
                                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.moeshakOutline, lineWidth: 1)))
                            .padding(.horizontal, 14)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
        .sheet(isPresented: $showNew) {
            NewPlaylistSheet { name in Store.shared.playlists.create(name) }
        }
        .sheet(item: $renameTarget) { p in
            NewPlaylistSheet(initial: p.name, title: "تغییر نام پلی‌لیست") { nn in
                _ = Store.shared.playlists.rename(p, to: nn)
            }
        }
    }

    private func downloadAll(_ p: PlaylistStore.Playlist) {
        let toDl = p.tracks.filter { !Store.shared.downloads.isDownloaded($0) }
        guard !toDl.isEmpty else {
            UIHelpers.toast("همه از قبل دانلود شده‌اند ✓"); return
        }
        UIHelpers.toast("⬇️ دانلود \(toDl.count) آهنگ شروع شد")
        DownloadService.shared.downloadAll(toDl)
    }
}

struct PLButton: ButtonStyle {
    var destructive = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(destructive ? Color(hex: 0xF87171) : Color(hex: 0x22D3EE))
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 12)
                .fill(Color.moeshakSurface)
                .overlay(RoundedRectangle(cornerRadius: 12)
                    .stroke(destructive ? Color(hex: 0xF87171).opacity(0.4) : Color(hex: 0x22D3EE).opacity(0.4), lineWidth: 1)))
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

struct NewPlaylistSheet: View {
    @Environment(\.dismiss) private var dismiss
    var initial = ""
    var title = "پلی‌لیست جدید"
    let onCreate: (String) -> Void
    @State private var name = ""

    var body: some View {
        VStack(spacing: 16) {
            Text(title).font(.system(size: 18, weight: .bold)).foregroundColor(.moeshakText)
            TextField("نام پلی‌لیست", text: $name)
                .textFieldStyle(MoeshakField())
                .padding(.horizontal, 20)
            HStack(spacing: 12) {
                Button("انصراف") { dismiss() }
                    .foregroundColor(.moeshakMuted)
                    .frame(maxWidth: .infinity)
                    .padding(12)
                Button("ذخیره") {
                    onCreate(name.trimmingCharacters(in: .whitespaces))
                    dismiss()
                }
                .frame(maxWidth: .infinity).padding(12)
                .background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0x22D3EE)))
                .foregroundColor(Color(hex: 0x05242B))
            }
            .padding(.horizontal, 20)
            Spacer()
        }
        .padding(.top, 30)
        .background(Color.moeshakBg)
        .onAppear { name = initial }
        .presentationDetents([.height(220)])
    }
}

// MARK: - دانلودها (حذف = حذف فایل)

struct DownloadsView: View {
    @ObservedObject private var store = Store.shared.downloads
    @EnvironmentObject var player: PlayerManager

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "دانلودها", showSearch: false)
            if store.entries.isEmpty {
                EmptyStateView(icon: "arrow.down.circle", text: "هنوز چیزی دانلود نشده —\nروی آهنگ «⋯» بزن و «دانلود»")
            } else {
                List {
                    ForEach(store.entries) { e in
                        Button {
                            var t = Track(chatId: 0, messageId: 0, chatTitle: e.chatTitle,
                                          title: e.title, performer: "", duration: 0, date: 0,
                                          fileId: e.fileId, expectedSize: e.size,
                                          thumbFileId: 0, chatPhotoFileId: 0, isVoice: false)
                            t.chatId = Int64(e.key.split(separator: ":").first ?? "0") ?? 0
                            t.messageId = Int64(e.key.split(separator: ":").last ?? "0") ?? 0
                            player.play([t], at: 0)
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(Color(hex: 0x34D399))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(e.title).font(.system(size: 14, weight: .bold)).foregroundColor(.moeshakText).lineLimit(1)
                                    Text(e.chatTitle).font(.system(size: 12)).foregroundColor(.moeshakMuted)
                                }
                                Spacer()
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                // حذف فایل از حافظه هم — طبق خواسته
                                store.removeAndDeleteFile(e)
                                UIHelpers.toast("حذف شد (فایل هم پاک شد)")
                            } label: {
                                Label("حذف", systemImage: "trash")
                            }
                        }
                        .listRowBackground(Color.clear)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }
}

// MARK: - کانال‌ها

struct ChannelsView: View {
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager

    private var groups: [(name: String, tracks: [Track])] {
        let dict = Dictionary(grouping: library.library, by: { $0.chatTitle.isEmpty ? "بدون‌نام" : $0.chatTitle })
        return dict.map { (name: $0.key, tracks: $0.value) }.sorted { $0.tracks.count > $1.tracks.count }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "کانال‌ها", showSearch: false)
            if groups.isEmpty {
                EmptyStateView(icon: "megaphone", text: "بعد از اسکن، کانال‌ها اینجا جمع می‌شوند 📢")
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(groups, id: \.name) { g in
                            VStack(spacing: 0) {
                                Button {
                                    player.play(g.tracks, at: 0)
                                } label: {
                                    HStack(spacing: 14) {
                                        Text(String(g.name.prefix(1)).uppercased())
                                            .font(.system(size: 19, weight: .heavy)).foregroundColor(.white)
                                            .frame(width: 46, height: 46)
                                            .background(Circle().fill(
                                                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                                               startPoint: .topLeading, endPoint: .bottomTrailing)))
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(g.name).font(.system(size: 16, weight: .bold)).foregroundColor(.moeshakText).lineLimit(1)
                                            Text("\(g.tracks.count) آهنگ").font(.system(size: 13)).foregroundColor(.moeshakMuted)
                                        }
                                        Spacer()
                                    }
                                    .padding(14)
                                }
                                .buttonStyle(.plain)
                                HStack {
                                    Spacer()
                                    Button { player.play(g.tracks, at: 0) } label: {
                                        Label("پخش", systemImage: "play.fill")
                                    }.buttonStyle(PLButton())
                                    Button {
                                        library.deepScan(chatId: g.tracks.first?.chatId ?? 0) { added in
                                            UIHelpers.toast(added > 0 ? "\(added) آهنگ جدید پیدا شد" : "چیزی پیدا نشد")
                                        }
                                    } label: {
                                        Label("اسکن عمیق", systemImage: "magnifyingglass")
                                    }.buttonStyle(PLButton())
                                    Button {
                                        let toDl = g.tracks.filter { !Store.shared.downloads.isDownloaded($0) }
                                        if toDl.isEmpty { UIHelpers.toast("همه دانلود شده ✓") }
                                        else {
                                            UIHelpers.toast("⬇️ دانلود \(toDl.count) آهنگ")
                                            DownloadService.shared.downloadAll(toDl)
                                        }
                                    } label: {
                                        Label("دانلود", systemImage: "arrow.down")
                                    }.buttonStyle(PLButton())
                                }
                                .padding(.horizontal, 14).padding(.bottom, 12)
                            }
                            .background(RoundedRectangle(cornerRadius: 20).fill(Color.moeshakCard)
                                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.moeshakOutline, lineWidth: 1)))
                            .padding(.horizontal, 14)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
    }
}

// MARK: - چت‌ها (با سرچ)

struct ChatsView: View {
    @State private var chats: [(id: Int64, title: String, type: String)] = []
    @State private var query = ""
    @State private var loading = false
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager

    var filtered: [(id: Int64, title: String, type: String)] {
        query.isEmpty ? chats
            : chats.filter { $0.title.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "چت‌ها", showSearch: false)
            // سرچ چت‌ها
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(.moeshakMuted)
                TextField("جستجوی چت…", text: $query).foregroundColor(.moeshakText)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 24).fill(Color.moeshakCard)
                .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.moeshakOutline, lineWidth: 1)))
            .padding(.horizontal, 14)

            if loading {
                ProgressView().tint(Color(hex: 0x22D3EE)).padding(30)
            }
            if filtered.isEmpty {
                EmptyStateView(icon: "bubble.left.and.bubble.right", text: loading ? "" : "چتی پیدا نشد")
            } else {
                List {
                    ForEach(filtered, id: \.id) { c in
                        Button {
                            let tracks = library.tracksOfChat(c.id)
                            if !tracks.isEmpty { player.play(tracks, at: 0) }
                            else {
                                library.deepScan(chatId: c.id) { added in
                                    UIHelpers.toast(added > 0 ? "\(added) آهنگ جدید در بخش اسکن" : "چیزی پیدا نشد")
                                }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Text(String(c.title.prefix(1)).uppercased())
                                    .font(.system(size: 16, weight: .heavy)).foregroundColor(.white)
                                    .frame(width: 42, height: 42)
                                    .background(Circle().fill(
                                        LinearGradient(colors: [Color(hex: 0x8B5CF6), Color(hex: 0x22D3EE)],
                                                       startPoint: .topLeading, endPoint: .bottomTrailing)))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(c.title).font(.system(size: 15, weight: .bold)).foregroundColor(.moeshakText).lineLimit(1)
                                    Text(c.type).font(.system(size: 12)).foregroundColor(.moeshakMuted)
                                }
                                Spacer()
                                let n = library.tracksOfChat(c.id).count
                                if n > 0 {
                                    Text("\(n)").font(.system(size: 12, weight: .bold))
                                        .foregroundColor(Color(hex: 0x22D3EE))
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(Capsule().fill(Color(hex: 0x22D3EE).opacity(0.12)))
                                }
                            }
                        }
                        .listRowBackground(Color.clear)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .task {
            guard chats.isEmpty else { return }
            loading = true
            let list = await ChatService.loadAllChats()
            chats = list
            loading = false
        }
    }
}
