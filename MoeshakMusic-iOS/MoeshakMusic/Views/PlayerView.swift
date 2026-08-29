import SwiftUI

/// مینی‌پلیر پایین صفحه — تیم موشک
struct MiniPlayerView: View {
    @EnvironmentObject var player: PlayerManager
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                MiniCover(track: player.current)
                VStack(alignment: .leading, spacing: 3) {
                    Text(player.current?.title ?? "")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: 0x22D3EE))
                        .lineLimit(1)
                    Text(player.current?.subtitle ?? "")
                        .font(.system(size: 12))
                        .foregroundColor(.moeshakMuted)
                        .lineLimit(1)
                }
                Spacer()
                Button { player.toggleShuffle() } label: {
                    Image(systemName: "shuffle")
                        .foregroundColor(.moeshakMuted)
                        .opacity(player.shuffle ? 1 : 0.45)
                }.buttonStyle(.plain)
                // دکمهٔ گرادیانی مثل استور
                Button { player.toggle() } label: {
                    ZStack {
                        Circle().fill(
                            LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                           startPoint: .topLeading, endPoint: .bottomTrailing)
                        ).shadow(color: Color(hex: 0x22D3EE).opacity(0.45), radius: 10)
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .foregroundColor(Color(hex: 0x05242B))
                    }
                }
                .buttonStyle(.plain)
                .frame(width: 40, height: 40)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.moeshakSurface)
            .overlay(alignment: .top) { Rectangle().fill(Color.moeshakOutline).frame(height: 1) }
        }
        .buttonStyle(.plain)
    }
}

struct MiniCover: View {
    let track: Track?
    @State private var img: UIImage?

    var body: some View {
        ZStack {
            if let img {
                Image(uiImage: img).resizable().scaledToFill()
            } else {
                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
        .frame(width: 46, height: 46)
        .clipShape(Circle())
        .task(id: track?.id) {
            guard let t = track else { return }
            img = await ArtLoader.load(t)
        }
    }
}

/// Now Playing تمام‌صفحه — کاور دایره‌ای + ویژوالایزر — تیم موشک
struct PlayerView: View {
    @EnvironmentObject var player: PlayerManager
    @Environment(\.dismiss) private var dismiss
    @State private var userSeeking = false
    @State private var seekPos: Double = 0
    @State private var cover: UIImage?

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0x0A1220), Color.moeshakBg],
                           startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.75))
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // هدر
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.moeshakText)
                    }
                    Spacer()
                    Text("در حال پخش")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.moeshakMuted)
                    Spacer()
                    Menu {
                        Button { addToPlaylist() } label: {
                            Label("افزودن به پلی‌لیست", systemImage: "plus")
                        }
                        if player.current != nil {
                            Button { player.addToQueue(player.current!) } label: {
                                Label("افزودن به صف", systemImage: "text.line.first.and.arrowtriangle.forward")
                            }
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.moeshakText)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 8)

                // کاور دایره‌ای + حلقهٔ ویژوالایزر
                ZStack {
                    VisualizerRing(bars: $player.visualizer)
                        .frame(width: 320, height: 320)
                    coverCircle
                }
                .padding(.top, 26)

                // عنوان
                VStack(spacing: 6) {
                    Text(player.current?.title ?? "—")
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundColor(.moeshakText)
                        .lineLimit(1)
                    Text(player.current?.subtitle ?? "")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(Color(hex: 0x22D3EE))
                        .lineLimit(1)
                    if let chat = player.current?.chatTitle, !chat.isEmpty {
                        Text(chat)
                            .font(.system(size: 12))
                            .foregroundColor(.moeshakMuted)
                    }
                }
                .padding(.top, 24)

                if player.downloadPct >= 0 {
                    ProgressView(value: Double(player.downloadPct), total: 100)
                        .tint(Color(hex: 0x22D3EE))
                        .padding(.horizontal, 32)
                    Text("در حال دریافت… \(player.downloadPct)٪")
                        .font(.system(size: 11)).foregroundColor(.moeshakMuted)
                }

                Spacer()

                // سیکبار
                HStack(spacing: 10) {
                    Text(Self.time(player.position)).font(.system(size: 12)).foregroundColor(.moeshakMuted)
                    Slider(
                        value: Binding(
                            get: { userSeeking ? seekPos : player.position },
                            set: { userSeeking = true; seekPos = $0 }
                        ),
                        in: 0...max(player.duration, 1)
                    ) { editing in
                        if !editing {
                            player.seek(to: seekPos)
                            userSeeking = false
                        }
                    }
                    .tint(Color(hex: 0x22D3EE))
                    Text(Self.time(player.duration)).font(.system(size: 12)).foregroundColor(.moeshakMuted)
                }
                .padding(.horizontal, 24)

                // دکمه‌های اصلی
                HStack(spacing: 34) {
                    Button { player.toggleShuffle() } label: {
                        Image(systemName: "shuffle")
                            .font(.system(size: 22))
                            .foregroundColor(player.shuffle ? Color(hex: 0x22D3EE) : .moeshakMuted)
                    }
                    Button { player.prev() } label: {
                        Image(systemName: "backward.fill").font(.system(size: 26)).foregroundColor(.moeshakText)
                    }
                    Button { player.toggle() } label: {
                        ZStack {
                            Circle().fill(
                                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                            ).frame(width: 76, height: 76)
                                .shadow(color: Color(hex: 0x22D3EE).opacity(0.4), radius: 18)
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 30))
                                .foregroundColor(Color(hex: 0x05242B))
                        }
                    }
                    Button { player.next() } label: {
                        Image(systemName: "forward.fill").font(.system(size: 26)).foregroundColor(.moeshakText)
                    }
                    Button { player.cycleRepeat() } label: {
                        Image(systemName: player.repeatMode == 2 ? "repeat.1" : "repeat")
                            .font(.system(size: 22))
                            .foregroundColor(player.repeatMode != 0 ? Color(hex: 0x22D3EE) : .moeshakMuted)
                    }
                }
                .padding(.vertical, 20)

                // فیوریت
                Button { player.toggleFavorite() } label: {
                    Image(systemName: isFav ? "heart.fill" : "heart")
                        .font(.system(size: 24))
                        .foregroundColor(isFav ? Color(hex: 0xE11D48) : .moeshakMuted)
                }
                .padding(.bottom, 24)
            }
        }
        .task(id: player.current?.id) {
            guard let t = player.current else { return }
            cover = await ArtLoader.load(t)
        }
    }

    private var isFav: Bool {
        guard let t = player.current else { return false }
        return Store.shared.favorites.contains(t)
    }

    private var coverCircle: some View {
        ZStack {
            Circle().fill(
                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            )
            if let cover {
                Image(uiImage: cover).resizable().scaledToFill()
            } else {
                Image(systemName: "music.note")
                    .font(.system(size: 54))
                    .foregroundColor(Color(hex: 0x05242B))
            }
        }
        .frame(width: 250, height: 250)
        .clipShape(Circle())
        .shadow(color: Color(hex: 0x22D3EE).opacity(0.35), radius: 30)
        .padding(35)
    }

    private func addToPlaylist() {
        guard let t = player.current else { return }
        let alert = UIAlertController(title: "افزودن «\(t.title)» به…", message: nil, preferredStyle: .actionSheet)
        for p in Store.shared.playlists.items {
            alert.addAction(UIAlertAction(title: p.name, style: .default) { _ in
                _ = Store.shared.playlists.add(t, to: p)
            })
        }
        alert.addAction(UIAlertAction(title: "پلی‌لیست جدید…", style: .default) { _ in
            let nameAlert = UIAlertController(title: "پلی‌لیست جدید", message: nil, preferredStyle: .alert)
            nameAlert.addTextField { $0.placeholder = "نام پلی‌لیست" }
            nameAlert.addAction(UIAlertAction(title: "ساخت و افزودن", style: .default) { _ in
                let name = nameAlert.textFields?.first?.text ?? ""
                if !name.isEmpty {
                    Store.shared.playlists.create(name)
                    if let p = Store.shared.playlists.items.first(where: { $0.name == name }) {
                        _ = Store.shared.playlists.add(t, to: p)
                    }
                }
            })
            nameAlert.addAction(UIAlertAction(title: "انصراف", style: .cancel))
            UIApplication.topViewController()?.present(nameAlert, animated: true)
        })
        alert.addAction(UIAlertAction(title: "انصراف", style: .cancel))
        UIApplication.topViewController()?.present(alert, animated: true)
    }

    static func time(_ sec: Double) -> String {
        guard sec.isFinite, sec > 0 else { return "0:00" }
        let s = Int(sec)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// حلقهٔ میله‌ای ویژوالایزر — مثل اسکرین‌شات‌های استور
struct VisualizerRing: View {
    @Binding var bars: [Float]

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let baseR = size * 0.42
            ZStack {
                Circle()
                    .stroke(Color(hex: 0x22D3EE).opacity(0.25), lineWidth: 1.2)
                    .frame(width: baseR * 2 - 16, height: baseR * 2 - 16)
                ForEach(0..<bars.count, id: \.self) { i in
                    let angle = Double(i) / Double(bars.count) * 2 * .pi
                    let level = CGFloat(max(bars[i], 0.06))
                    let len = size * 0.06 + level * size * 0.10
                    Capsule()
                        .fill(LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                                             startPoint: .top, endPoint: .bottom))
                        .frame(width: 6, height: len)
                        .offset(y: -(baseR + len / 2 + 4))
                        .rotationEffect(.radians(angle))
                }
            }
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .animation(.linear(duration: 0.15), value: bars)
    }
}

extension UIApplication {
    @MainActor static func topViewController() -> UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }
}
