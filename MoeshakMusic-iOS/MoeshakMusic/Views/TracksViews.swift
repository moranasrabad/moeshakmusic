import SwiftUI

/// ردیف تراک — کاور دایره‌ای + تیتر/ساب + مدت — تیم موشک
struct TrackRow: View {
    let track: Track
    let isNow: Bool
    let downloaded: Bool
    let index: Int?
    let onPlay: () -> Void
    let onMenu: () -> Void
    @State private var art: UIImage?

    var body: some View {
        Button(action: onPlay) {
            HStack(spacing: 12) {
                if let i = index {
                    Text("\(i)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.moeshakMuted)
                        .frame(width: 26)
                }
                ZStack {
                    if let art {
                        Image(uiImage: art).resizable().scaledToFill()
                    } else {
                        LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    }
                    if isNow {
                        Circle().fill(.black.opacity(0.35))
                        Image(systemName: "waveform").foregroundColor(.white)
                    }
                }
                .frame(width: 52, height: 52)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title)
                        .font(.system(size: 16, weight: isNow ? .bold : .regular))
                        .foregroundColor(isNow ? Color(hex: 0x22D3EE) : .moeshakText)
                        .lineLimit(1)
                    Text(track.subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(.moeshakMuted)
                        .lineLimit(1)
                }
                Spacer()
                Text((downloaded ? "✓ " : "") + Track.timeString(track.duration))
                    .font(.system(size: 13))
                    .foregroundColor(.moeshakMuted)
                Button(action: onMenu) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 15))
                        .foregroundColor(.moeshakMuted)
                        .padding(6)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
        .task(id: track.id) {
            art = await ArtLoader.load(track)
        }
    }
}

// MARK: - آهنگ‌ها

struct TracksView: View {
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager
    @State private var query = ""

    var filtered: [Track] {
        query.isEmpty ? library.library
            : library.library.filter {
                ($0.title + " " + $0.performer + " " + $0.chatTitle).localizedCaseInsensitiveContains(query)
            }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "آهنگ‌ها", showSearch: true, query: $query)
            if filtered.isEmpty {
                EmptyStateView(icon: "music.note", text: "کتابخانه‌ات خالیه 🎵\nاز تب اسکن اسکن کن و به کتابخانه اضافه کن!")
            } else {
                List {
                    ForEach(Array(filtered.enumerated()), id: \.element.id) { i, t in
                        TrackRow(track: t,
                                 isNow: t.id == player.current?.id,
                                 downloaded: Store.shared.downloads.isDownloaded(t),
                                 index: i + 1,
                                 onPlay: { play(t, at: i) },
                                 onMenu: { menu(t) })
                        .listRowBackground(Color.clear)
                        .listRowSeparatorTint(Color.moeshakOutline)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }

    private func play(_ t: Track, at i: Int) {
        player.play(filtered, at: i)
    }

    private func menu(_ t: Track) {
        let alert = UIAlertController(title: t.title, message: nil, preferredStyle: .actionSheet)
        let dl = Store.shared.downloads.isDownloaded(t)
        alert.addAction(UIAlertAction(title: dl ? "✓ دانلود شده" : "⬇️ دانلود", style: .default) { _ in
            if !dl { DownloadService.shared.download(t) }
        })
        alert.addAction(UIAlertAction(title: "➕ افزودن به پلی‌لیست", style: .default) { _ in
            UIHelpers.pickPlaylist(for: t)
        })
        alert.addAction(UIAlertAction(title: "🔀 افزودن به صف", style: .default) { _ in
            player.addToQueue(t)
        })
        alert.addAction(UIAlertAction(title: "انصراف", style: .cancel))
        UIApplication.topViewController()?.present(alert, animated: true)
    }
}

// MARK: - فیوریت

struct FavoritesView: View {
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager
    @State private var query = ""
    @ObservedObject private var favs = Store.shared.favorites

    var tracks: [Track] {
        let favs = library.library.filter { Store.shared.favorites.contains($0) }
        return query.isEmpty ? favs
            : favs.filter { ($0.title + " " + $0.performer).localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "فیوریت", showSearch: true, query: $query)
            if tracks.isEmpty {
                EmptyStateView(icon: "heart", text: "هنوز فیوریتی نداری ❤️\nدر پلیر روی قلب بزن")
            } else {
                List {
                    ForEach(Array(tracks.enumerated()), id: \.element.id) { i, t in
                        TrackRow(track: t, isNow: t.id == player.current?.id,
                                 downloaded: Store.shared.downloads.isDownloaded(t),
                                 index: i + 1,
                                 onPlay: { player.play(tracks, at: i) },
                                 onMenu: { _ in })
                        .listRowBackground(Color.clear)
                        .listRowSeparatorTint(Color.moeshakOutline)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }
}

// MARK: - اسکن

struct ScanView: View {
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager
    @State private var depth = 100

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "اسکن", showSearch: false)
            ScrollView {
                VStack(spacing: 14) {
                    // کارت کنترل
                    VStack(alignment: .leading, spacing: 12) {
                        Text(library.scanState.running ? "در حال اسکن چت‌های تلگرام…" : "آمادهٔ اسکن — عمق را انتخاب کن 🔍")
                            .font(.system(size: 16, weight: .bold)).foregroundColor(.moeshakText)
                        if library.scanState.running {
                            Text("🔍 \(library.scanState.chatsScanned) چت • 🎵 \(library.scanState.found) آهنگ • ⏱ \(library.scanState.seconds) ثانیه")
                                .font(.system(size: 13)).foregroundColor(.moeshakMuted)
                            ProgressView().tint(Color(hex: 0x22D3EE))
                        } else if library.scanState.chatsScanned > 0 {
                            Text(library.scanState.cancelled ? "⏹ لغو شد" : "🏁 تمام شد")
                                .font(.system(size: 13)).foregroundColor(.moeshakMuted)
                        }
                        // چیپ‌های عمق
                        HStack(spacing: 8) {
                            DepthChip(text: "۵۰", active: depth == 50) { depth = 50 }
                            DepthChip(text: "۱۰۰", active: depth == 100) { depth = 100 }
                            DepthChip(text: "۳۰۰", active: depth == 300) { depth = 300 }
                            DepthChip(text: "همه", active: depth == 3000) { depth = 3000 }
                        }
                        Button {
                            if library.scanState.running { library.cancelScan() }
                            else { library.startScan(depth: depth) }
                        } label: {
                            Text(library.scanState.running ? "⏹ لغو اسکن" : "🔍 شروع اسکن").moeshakButton()
                        }
                    }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 20).fill(Color.moeshakCard)
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.moeshakOutline, lineWidth: 1)))
                    .padding(.horizontal, 14)

                    // دکمه‌های نتیجه
                    if !library.scanResults.isEmpty {
                        Button {
                            let n = library.addScanResultsToLibrary()
                            UIHelpers.toast(n > 0 ? "\(n) آهنگ به کتابخانه اضافه شد ✓" : "همه از قبل در کتابخانه بودند")
                        } label: {
                            Text("➕ افزودن همه به کتابخانه (\(library.scanResults.count))").moeshakButton()
                        }
                        .padding(.horizontal, 14)
                        Button("🗑 پاک کردن نتایج") { library.clearScanResults() }
                            .font(.system(size: 13)).foregroundColor(.moeshakMuted)
                    }

                    // نتایج زنده
                    if library.scanResults.isEmpty {
                        EmptyStateView(icon: "magnifyingglass", text: "هنوز اسکنی انجام نشده\nدکمهٔ «شروع اسکن» را بزن 🚀")
                    } else {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(library.scanResults.enumerated()), id: \.element.id) { i, t in
                                TrackRow(track: t, isNow: t.id == player.current?.id,
                                         downloaded: Store.shared.downloads.isDownloaded(t),
                                         index: i + 1,
                                         onPlay: { player.play(library.scanResults, at: i) },
                                         onMenu: { _ in })
                            }
                        }
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }
}

struct DepthChip: View {
    let text: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(active ? Color(hex: 0x22D3EE) : .moeshakText)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(active ? Color(hex: 0x22D3EE).opacity(0.15) : Color.moeshakSurface)
                        .overlay(RoundedRectangle(cornerRadius: 20)
                            .stroke(active ? Color(hex: 0x22D3EE) : Color.moeshakOutline,
                                    lineWidth: active ? 1.5 : 1))
                )
        }
    }
}
