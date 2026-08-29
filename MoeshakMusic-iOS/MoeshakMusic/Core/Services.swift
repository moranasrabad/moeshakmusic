import Foundation
import SwiftUI
import UIKit

/// دانلود ترتیبی — با صف — تیم موشک
@MainActor
final class DownloadService: ObservableObject {
    static let shared = DownloadService()
    private var running = false
    private var pending: [Track] = []

    func download(_ t: Track) {
        UIHelpers.toast("⬇️ دانلود: \(t.title)")
        downloadAll([t])
    }

    func downloadAll(_ list: [Track]) {
        pending.append(contentsOf: list)
        if !running { runNext() }
    }

    private func runNext() {
        guard let t = pending.first else { running = false; return }
        pending.removeFirst()
        running = true
        Task.detached { [weak self] in
            guard let self else { return }
            if let p = try? TDLoader.downloadToCache(t, progress: { _ in }) {
                await MainActor.run { Store.shared.downloads.mark(t, path: p) }
            } else {
                await MainActor.run { TgLog.shared.add("⚠️ خطای دانلود: \(t.title)") }
            }
            await MainActor.run { self.runNext() }
        }
    }
}

/// لود چت‌ها برای تب چت‌ها
enum ChatService {
    static func loadAllChats() async -> [(id: Int64, title: String, type: String)] {
        await Task.detached {
            var out: [(id: Int64, title: String, type: String)] = []
            let me = UserDefaults.standard.int64(forKey: "my_user_id")
            for listType in ["chatListMain", "chatListArchive"] {
                while true {
                    do { _ = try TDJson.syncDict(["@type": "loadChats",
                                                  "chat_list": ["@type": listType]]); continue }
                    catch { break }
                }
                guard let resp = try? TDJson.syncDict(["@type": "getChats",
                                                       "chat_list": ["@type": listType],
                                                       "limit": 500]),
                      let ids = resp["chat_ids"] as? [Int64] else { continue }
                for id in ids where !out.contains(where: { $0.id == id }) {
                    var title = "چت \(id)"
                    var type = "چت"
                    if let c = try? TDJson.syncDict(["@type": "getChat", "chat_id": id]) {
                        if let t = c["title"] as? String, !t.isEmpty { title = t }
                        let tid = (c["type"] as? TDJson.AnyDict)?["@type"] as? String ?? ""
                        switch tid {
                        case "chatTypePrivate": type = id == me ? "سیو ⭐" : "چت خصوصی"
                        case "chatTypeSupergroup":
                            type = ((c["type"] as? TDJson.AnyDict)?["is_channel"] as? Bool ?? false) ? "کانال 📢" : "سوپرگروه"
                        case "chatTypeBasicGroup": type = "گروه"
                        case "chatTypeSecret": type = "چت مخفی"
                        default: break
                        }
                    }
                    out.append((id, title, type))
                }
            }
            return out
        }.value
    }
}

/// لاگ ساده
@MainActor
final class TgLog: ObservableObject {
    static let shared = TgLog()
    @Published var lines: [String] = []
    func add(_ l: String) {
        let ts = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        lines.append("\(ts)  \(l)")
        if lines.count > 500 { lines.removeFirst() }
    }
}

/// ابزارهای UI
enum UIHelpers {
    static func toast(_ msg: String) {
        DispatchQueue.main.async {
            guard let vc = UIApplication.topViewController() else { return }
            let label = UILabel()
            label.text = " \(msg) "
            label.textColor = .white
            label.backgroundColor = UIColor(red: 0x10/255, green: 0x16/255, blue: 0x1D/255, alpha: 0.97)
            label.layer.cornerRadius = 14
            label.clipsToBounds = true
            label.font = .systemFont(ofSize: 13, weight: .semibold)
            label.numberOfLines = 2
            label.textAlignment = .center
            label.frame = CGRect(x: 30, y: vc.view.frame.height - 150,
                                 width: vc.view.frame.width - 60, height: 44)
            label.alpha = 0
            vc.view.addSubview(label)
            UIView.animate(withDuration: 0.25) { label.alpha = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
                UIView.animate(withDuration: 0.3, animations: { label.alpha = 0 }) { _ in
                    label.removeFromSuperview()
                }
            }
        }
    }

    /// دیالوگ انتخاب پلی‌لیست برای یک تراک
    static func pickPlaylist(for t: Track) {
        DispatchQueue.main.async {
            guard let vc = UIApplication.topViewController() else { return }
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
                vc.present(nameAlert, animated: true)
            })
            alert.addAction(UIAlertAction(title: "انصراف", style: .cancel))
            vc.present(alert, animated: true)
        }
    }

    /// صفحهٔ پروکسی/تنظیمات
    static func showProxy() {
        DispatchQueue.main.async {
            guard let vc = UIApplication.topViewController() else { return }
            let host = UIHostingController(rootView: SettingsView())
            host.modalPresentationStyle = .pageSheet
            vc.present(host, animated: true)
        }
    }

    /// ری‌استارت اپ — بعد از تغییر کلید API (کلاینت TDLib هم با کلید جدید راه می‌افتد)
    static func restartApp() {
        NotificationCenter.default.post(name: .init("moeshak.restart"), object: nil)
    }
}

extension Notification.Name {
    static let moeshakRestart = Notification.Name("moeshak.restart")
}
