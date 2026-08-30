import Foundation
import SwiftUI

/// تنظیمات ذخیره‌شده — کلیدهای API، تم، اکسنت، پروکسی — تیم موشک
final class Prefs: ObservableObject {
    static let shared = Prefs()
    private let d = UserDefaults.standard

    private init() {}

    // MARK: - API Keys

    /// کلید پیش‌فرض بیک‌شده — تلگرام دسکتاپ (مثل اندروید)
    static let bakedApiId = 6
    static let bakedApiHash = "eb06d4abfb49dc3eeb1aeb98ae0f581e"

    var apiId: Int {
        let v = d.integer(forKey: "api_id")
        return v != 0 ? v : Self.bakedApiId
    }

    var apiHash: String {
        let v = d.string(forKey: "api_hash") ?? ""
        return v.count > 10 ? v : Self.bakedApiHash
    }

    var hasCustomKeys: Bool { d.integer(forKey: "api_id") != 0 }

    func saveKeys(id: Int, hash: String) {
        d.set(id, forKey: "api_id")
        d.set(hash, forKey: "api_hash")
    }

    func clearKeys() {
        d.removeObject(forKey: "api_id")
        d.removeObject(forKey: "api_hash")
    }

    // MARK: - Theme & Accent

    /// 0=سیستم 1=روشن 2=تاریک
    var themeMode: Int {
        get { d.integer(forKey: "theme_mode") }
        set { d.set(newValue, forKey: "theme_mode"); objectWillChange.send() }
    }

    /// ایندکس رنگ اکسنت — 0 = پیش‌فرض برند
    var accentIndex: Int {
        get { d.integer(forKey: "accent") }
        set { d.set(newValue, forKey: "accent"); objectWillChange.send() }
    }

    static let accentColors: [Color] = [
        Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6), Color(hex: 0x34D399),
        Color(hex: 0xF59E0B), Color(hex: 0xF43F5E), Color(hex: 0x3B82F6)
    ]
    static let accentNames = ["یخی", "بنفش", "سبز", "کهربایی", "سرخ", "آبی"]

    var accent: Color {
        accentIndex == 0 ? Color(hex: 0x22D3EE) : Self.accentColors[accentIndex - 1]
    }

    // MARK: - Proxy

    var proxyEnabled: Bool {
        get { d.bool(forKey: "proxy_on") }
        set { d.set(newValue, forKey: "proxy_on"); objectWillChange.send() }
    }

    var proxyServer: String {
        get { d.string(forKey: "proxy_server") ?? "" }
        set { d.set(newValue, forKey: "proxy_server") }
    }

    var proxyPort: Int {
        get { d.integer(forKey: "proxy_port") }
        set { d.set(newValue, forKey: "proxy_port") }
    }

    var proxySecret: String {
        get { d.string(forKey: "proxy_secret") ?? "" }
        set { d.set(newValue, forKey: "proxy_secret") }
    }

    var lastProxyIndex: Int {
        get { d.integer(forKey: "proxy_last") }
        set { d.set(newValue, forKey: "proxy_last") }
    }

    var proxies: [ProxyEntry] {
        get {
            guard let data = d.data(forKey: "proxies"),
                  let list = try? JSONDecoder().decode([ProxyEntry].self, from: data) else { return [] }
            return list
        }
        set {
            d.set(try? JSONEncoder().encode(newValue), forKey: "proxies")
            objectWillChange.send()
        }
    }
}

struct ProxyEntry: Identifiable, Codable, Hashable {
    var id = UUID()
    var server: String
    var port: Int
    var secret: String
    var pingMs: Int = -1 // -1 = تست‌نشده، -2 = خطا

    var label: String { "\(server):\(port)" }
}

// MARK: - HEX Color helper

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    static let moeshakBg = Color(hex: 0x070A0D)
    static let moeshakSurface = Color(hex: 0x0D141B)
    static let moeshakCard = Color(hex: 0x10161D)
    static let moeshakOutline = Color(hex: 0x1E2A36)
    static let moeshakText = Color(hex: 0xE8F2F8)
    static let moeshakMuted = Color(hex: 0x8FA3B5)
    static let moeshakPurple = Color(hex: 0x8B5CF6)
}
