import SwiftUI

/// ریشهٔ اپ — ۷ تب + ورود + پلیر تمام‌صفحه — تیم موشک
struct RootView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var player: PlayerManager
    @ObservedObject private var prefs = Prefs.shared
    @State private var showPlayer = false

    var body: some View {
        Group {
            switch session.state {
            case .ready:
                TabRootView(showPlayer: $showPlayer)
                    .overlay(alignment: .bottom) {
                        if let t = player.current, !showPlayer {
                            MiniPlayerView { showPlayer = true }
                        }
                    }
            case .waitPhoneNumber, .waitCode, .waitPassword, .waitQR:
                LoginView()
            default:
                // loading / loggingOut / failed — اسپلش برند
                SplashView()
            }
        }
    }
}

struct SplashView: View {
    @EnvironmentObject var session: Session
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 18) {
            Image("Logo")
                .resizable().scaledToFit()
                .frame(width: 150, height: 150)
                .shadow(color: Color(hex: 0x22D3EE).opacity(0.45), radius: pulse ? 30 : 16)
                .animation(.easeInOut(duration: 1.2).repeatForever(), value: pulse)
            Text("موشک موزیک")
                .font(.system(size: 26, weight: .heavy))
                .foregroundColor(.moeshakText)
            Text(statusLine)
                .font(.system(size: 13))
                .foregroundColor(.moeshakMuted)
        }
        .onAppear { pulse = true }
    }

    private var statusLine: String {
        switch session.state {
        case .loggingOut:
            return "🚪 در حال خروج امن…"
        case .failed(let e):
            return e
        default:
            switch session.connState {
            case .waiting: return "شبکه در دسترس نیست — VPN یا پروکسی لازم است ⚠️"
            case .ready: return "اتصال برقرار ✓"
            default: return "در حال اتصال به تلگرام…"
            }
        }
    }
}

struct TabRootView: View {
    @Binding var showPlayer: Bool
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            TracksView()
                .tabItem { Label("آهنگ‌ها", systemImage: "music.note.list") }
                .tag(0)
            ScanView()
                .tabItem { Label("اسکن", systemImage: "magnifyingglass") }
                .tag(1)
            PlaylistsView()
                .tabItem { Label("پلی‌لیست", systemImage: "list.bullet.rectangle") }
                .tag(2)
            FavoritesView()
                .tabItem { Label("فیوریت", systemImage: "heart") }
                .tag(3)
            DownloadsView()
                .tabItem { Label("دانلودها", systemImage: "arrow.down.circle") }
                .tag(4)
            ChannelsView()
                .tabItem { Label("کانال‌ها", systemImage: "megaphone") }
                .tag(5)
            ChatsView()
                .tabItem { Label("چت‌ها", systemImage: "bubble.left.and.bubble.right") }
                .tag(6)
        }
        .tint(prefs.accent)
        .fullScreenCover(isPresented: $showPlayer) {
            PlayerView()
        }
    }
}
