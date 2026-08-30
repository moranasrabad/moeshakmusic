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
                Text(rowDuration)
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

    private var rowDuration: String {
        downloaded ? "✓ " + Track.timeString(track.duration) : Track.timeString(track.duration)
    }
}

// MARK: - لیست سادهٔ تراک‌ها (مشترک)

struct TrackListView: View {
    @EnvironmentObject var player: PlayerManager
    let tracks: [Track]
    var onMenu: ((Track) -> Void)? = nil

    var body: some View {
        List {
            ForEach(Array(tracks.enumerated()), id: \.element.id) { pair in
                let i = pair.offset
                let t = pair.element
                TrackRow(track: t,
                         isNow: t.id == player.current?.id,
                         downloaded: Store.shared.downloads.isDownloaded(t),
                         index: i + 1,
                         onPlay: { player.play(tracks, at: i) },
                         onMenu: { onMenu?(t) })
                .listRowBackground(Color.clear)
                .listRowSeparatorTint(Color.moeshakOutline)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }
}

// MARK: - آهنگ‌ها

struct TracksView: View {
    @EnvironmentObject var library: LibraryManager
    @EnvironmentObject var player: PlayerManager
    @State private var query = ""

    private var filtered: [Track] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return library.library }
        let n = q.lowercased()
        return library.library.filter { t in
            t.title.lowercased().contains(n)
                || t.performer.lowercased().contains(n)
                || t.chatTitle.lowercased().contains(n)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "آهنگ‌ها", showSearch: true, query: $query)
            if filtered.isEmpty {
                EmptyStateView(icon: "music.note",
                               text: "کتابخانه‌ات خالیه 🎵\nاز تب اسکن اسکن کن و به کتابخانه اضافه کن!")
            } else {
                TrackListView(tracks: filtered) { t in
                    UIHelpers.trackMenu(t)
                }
            }
        }
    }
}

// MARK: - فیوریت

struct FavoritesView: View {
    @EnvironmentObject var library: LibraryManager
    @State private var query = ""
    @ObservedObject private var favs = Store.shared.favorites

    private var tracks: [Track] {
        let favs = library.library.filter { Store.shared.favorites.keys.contains($0.id) }
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return favs }
        return favs.filter { t in
            t.title.lowercased().contains(q) || t.performer.lowercased().contains(q)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "فیوریت", showSearch: true, query: $query)
            if tracks.isEmpty {
                EmptyStateView(icon: "heart", text: "هنوز فیوریتی نداری ❤️\nدر پلیر روی قلب بزن")
            } else {
                TrackListView(tracks: tracks)
            }
        }
    }
}

// MARK: - اسکن

struct ScanView: View {
    @EnvironmentObject var library: LibraryManager
    @State private var depth = 100

    var body: some View {
        VStack(spacing: 0) {
            HeaderView(title: "اسکن", showSearch: false)
            ScrollView {
                VStack(spacing: 14) {
                    scanCard
                    if !library.scanResults.isEmpty {
                        addAllButton
                        clearButton
                    }
                    resultsList
                }
                .padding(.vertical, 8)
            }
        }
    }

    // MARK: کارت کنترل اسکن

    private var scanCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(stateTitle)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.moeshakText)

            if library.scanState.running {
                Text(progressLine)
                    .font(.system(size: 13))
                    .foregroundColor(.moeshakMuted)
                ProgressView().tint(Color(hex: 0x22D3EE))
            } else if library.scanState.chatsScanned > 0 {
                Text(finishLine)
                    .font(.system(size: 13))
                    .foregroundColor(.moeshakMuted)
            }

            HStack(spacing: 8) {
                DepthChip(text: "۵۰", active: depth == 50) { depth = 50 }
                DepthChip(text: "۱۰۰", active: depth == 100) { depth = 100 }
                DepthChip(text: "۳۰۰", active: depth == 300) { depth = 300 }
                DepthChip(text: "همه", active: depth == 3000) { depth = 3000 }
            }

            Button(action: toggleScan) {
                Text(library.scanState.running ? "⏹ لغو اسکن" : "🔍 شروع اسکن")
                    .moeshakButton()
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.moeshakCard)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.moeshakOutline, lineWidth: 1)))
        .padding(.horizontal, 14)
    }

    private var stateTitle: String {
        if library.scanState.running { return "در حال اسکن چت‌های تلگرام…" }
        return "آمادهٔ اسکن — عمق را انتخاب کن 🔍"
    }

    private var progressLine: String {
        let c = library.scanState.chatsScanned
        let f = library.scanState.found
        let s = library.scanState.seconds
        return "🔍 \(c) چت • 🎵 \(f) آهنگ • ⏱ \(s) ثانیه"
    }

    private var finishLine: String {
        library.scanState.cancelled ? "⏹ اسکن لغو شد" : "🏁 تمام شد"
    }

    private func toggleScan() {
        if library.scanState.running {
            library.cancelScan()
        } else {
            library.startScan(depth: depth)
        }
    }

    // MARK: دکمه‌های نتیجه

    private var addAllButton: some View {
        Button {
            let n = library.addScanResultsToLibrary()
            let msg = n > 0 ? "\(n) آهنگ به کتابخانه اضافه شد ✓" : "همه از قبل در کتابخانه بودند"
            UIHelpers.toast(msg)
        } label: {
            Text("➕ افزودن همه به کتابخانه (\(library.scanResults.count))")
                .moeshakButton()
        }
        .padding(.horizontal, 14)
    }

    private var clearButton: some View {
        Button("🗑 پاک کردن نتایج") { library.clearScanResults() }
            .font(.system(size: 13))
            .foregroundColor(.moeshakMuted)
    }

    // MARK: نتایج زنده

    @ViewBuilder
    private var resultsList: some View {
        if library.scanResults.isEmpty {
            EmptyStateView(icon: "magnifyingglass",
                           text: "هنوز اسکنی انجام نشده\nدکمهٔ «شروع اسکن» را بزن 🚀")
        } else {
            VStack(spacing: 0) {
                ForEach(Array(library.scanResults.enumerated()), id: \.element.id) { pair in
                    let i = pair.offset
                    let t = pair.element
                    ScanRow(track: t, index: i + 1)
                }
            }
        }
    }
}

/// ردیف نتیجهٔ اسکن — پخش با لمس، منو با ⋯
private struct ScanRow: View {
    @EnvironmentObject var player: PlayerManager
    let track: Track
    let index: Int

    var body: some View {
        TrackRow(track: track,
                 isNow: track.id == player.current?.id,
                 downloaded: Store.shared.downloads.isDownloaded(track),
                 index: index,
                 onPlay: {
                     // نتایج زنده ممکن است وسط پخش عوض شوند — از کپی لحظه‌ای پخش کن
                     let current = LibraryManager.shared.scanResults
                     if let idx = current.firstIndex(where: { $0.id == track.id }) {
                         player.play(current, at: idx)
                     }
                 },
                 onMenu: { UIHelpers.trackMenu(track) })
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
