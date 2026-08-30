import SwiftUI

@main
struct MoeshakMusicApp: App {
    @StateObject private var session = Session.shared
    @StateObject private var player = PlayerManager.shared
    @StateObject private var library = LibraryManager.shared
    @AppStorage("lang") private var lang = "fa"
    @State private var restartToken = 0

    init() {
        // ظاهر شب برند
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0x07/255, green: 0x0A/255, blue: 0x0D/255, alpha: 1)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                Color.moeshakBg.ignoresSafeArea()
                if lang == "fa" {
                    RootView().environment(\.layoutDirection, .rightToLeft)
                } else {
                    RootView().environment(\.layoutDirection, .leftToRight)
                }
            }
            .id(restartToken) // ری‌استارت اپ بعد از تغییر کلید API
            .preferredColorScheme(Prefs.shared.themeMode == 1 ? .light : .dark)
            .environmentObject(session)
            .environmentObject(player)
            .environmentObject(library)
            .onAppear {
                session.start()
                // ذخیرهٔ شناسهٔ خودم برای موتور اسکن
                Task.detached {
                    for _ in 0..<24 {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        let (ready, uid): (Bool, Int64) = await MainActor.run {
                            (Session.shared.state == .ready, Session.shared.myUserId)
                        }
                        if ready {
                            if uid != 0 { UserDefaults.standard.set(uid, forKey: "my_user_id") }
                            break
                        }
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .moeshakRestart)) { _ in
                // کلاینت TDLib با کلید تازه از نو راه بیفتد
                Session.shared.relogin()
                restartToken += 1
            }
        }
    }
}
