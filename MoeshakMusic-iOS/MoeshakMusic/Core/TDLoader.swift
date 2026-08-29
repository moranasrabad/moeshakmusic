import Foundation
import UIKit

/// دانلود فایل از TDLib به کش — با درصد پیشرفت — تیم موشک
enum TDLoader {

    static var cacheDir: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("files", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    /// دانلود کامل فایل به کش (اگر از قبل نباشد) — مسیر محلی برمی‌گردد
    static func downloadToCache(_ t: Track, progress: @escaping (Int) -> Void) throws -> String {
        let dest = cacheDir.appendingPathComponent("\(t.fileId)_\(t.expectedSize).mp3")
        if FileManager.default.fileExists(atPath: dest.path) { return dest.path }

        // وضعیت فعلی فایل
        let f = try TDJson.syncDict(["@type": "getFile", "file_id": t.fileId])
        if let local = f["local"] as? TDJson.AnyDict,
           local["is_downloading_completed"] as? Bool == true,
           let p = local["path"] as? String {
            copyToCache(from: p, to: dest)
            return dest.path
        }

        // درخواست دانلود کامل
        let limit = t.expectedSize > 0 ? t.expectedSize : 1 << 30
        TDJson.sendRawDict(["@type": "downloadFile",
                            "file_id": t.fileId,
                            "priority": 32,
                            "offset": 0,
                            "limit": limit], handler: nil)

        // poll تا اتمام (حداکثر ۱۰ دقیقه)
        let deadline = Date().addingTimeInterval(600)
        while Date() < deadline {
            if LibraryManager.cancelRequested { throw TDJson.TDError(code: -1, message: "cancelled") }
            Thread.sleep(forTimeInterval: 0.5)
            guard let f2 = try? TDJson.syncDict(["@type": "getFile", "file_id": t.fileId]) else { continue }
            let local = f2["local"] as? TDJson.AnyDict
            let downloaded = local?["downloaded_size"] as? Int64 ?? 0
            let size = f2["expected_size"] as? Int64 ?? t.expectedSize
            if size > 0 {
                progress(Int(min(99, downloaded * 100 / size)))
            }
            if local?["is_downloading_completed"] as? Bool == true,
               let p = local?["path"] as? String {
                copyToCache(from: p, to: dest)
                progress(100)
                return dest.path
            }
        }
        throw TDJson.TDError(code: 408, message: "download timeout")
    }

    /// دانلود کوچک (کاور/عکس) همگام — Bitmap برمی‌گرداند
    static func downloadImage(fileId: Int, maxSize: Int = 320) -> UIImage? {
        // کش دیسک
        let dest = cacheDir.appendingPathComponent("art_\(fileId).jpg")
        if let img = UIImage(contentsOfFile: dest.path) { return img }
        do {
            let f = try TDJson.syncDict(["@type": "getFile", "file_id": fileId])
            if let local = f["local"] as? TDJson.AnyDict,
               local["is_downloading_completed"] as? Bool == true,
               let p = local["path"] as? String {
                let img = UIImage(contentsOfFile: p)
                if let img { try? img.jpegData(compressionQuality: 0.9)?.write(to: dest) }
                return img
            }
            let size = f["expected_size"] as? Int64 ?? 256 * 1024
            _ = try TDJson.syncDict(["@type": "downloadFile",
                                     "file_id": fileId,
                                     "priority": 1,
                                     "offset": 0,
                                     "limit": max(size, 64 * 1024)], timeout: 20)
            let deadline = Date().addingTimeInterval(15)
            while Date() < deadline {
                Thread.sleep(forTimeInterval: 0.2)
                guard let f2 = try? TDJson.syncDict(["@type": "getFile", "file_id": fileId]) else { continue }
                if let local = f2["local"] as? TDJson.AnyDict,
                   local["is_downloading_completed"] as? Bool == true,
                   let p = local["path"] as? String {
                    let img = UIImage(contentsOfFile: p)
                    if let img { try? img.jpegData(compressionQuality: 0.9)?.write(to: dest) }
                    return img
                }
            }
        } catch {}
        return nil
    }

    /// عکس پروفایل چت (برای تامبنیل)
    static func chatPhoto(chatId: Int64) async -> UIImage? {
        guard let c = try? await Task.detached {
            try TDJson.syncDict(["@type": "getChat", "chat_id": chatId])
        }.value else { return nil }
        guard let photo = c["photo"] as? TDJson.AnyDict,
              let small = photo["small"] as? TDJson.AnyDict,
              let fid = small["id"] as? Int else { return nil }
        return await Task.detached { downloadImage(fileId: fid, maxSize: 160) }.value
    }

    private static func copyToCache(from src: String, to dest: URL) {
        let s = URL(fileURLWithPath: src)
        try? FileManager.default.removeItem(at: dest)
        try? FileManager.default.copyItem(at: s, to: dest)
    }
}

/// لودر کاور — کاور آلبوم (۳۲۰) ← عکس چت — با کش حافظه
@MainActor
enum ArtLoader {

    private static let cache = NSCache<NSString, UIImage>()
    private static var inflight = Set<String>()

    static func cacheForSync(_ t: Track) -> UIImage? {
        if let img = cache.object(forKey: t.id as NSString) { return img }
        // دیسک
        if t.thumbFileId != 0,
           let img = UIImage(contentsOfFile: TDLoader.cacheDir.appendingPathComponent("art_\(t.thumbFileId).jpg").path) {
            cache.setObject(img, forKey: t.id as NSString)
            return img
        }
        return nil
    }

    /// لود async — کاور آلبوم اولویت، بعد عکس چت
    static func load(_ t: Track) async -> UIImage? {
        if let img = cacheForSync(t) { return img }
        if let img = cache.object(forKey: "c\(t.chatId)" as NSString) {
            cache.setObject(img, forKey: t.id as NSString)
            return img
        }
        if inflight.contains(t.id) { return nil }
        inflight.insert(t.id)
        defer { inflight.remove(t.id) }

        var img: UIImage?
        if t.thumbFileId != 0 {
            img = await Task.detached { TDLoader.downloadImage(fileId: t.thumbFileId) }.value
        }
        if img == nil, t.chatPhotoFileId != 0 {
            img = await Task.detached { TDLoader.downloadImage(fileId: t.chatPhotoFileId, maxSize: 160) }.value
            if let img { cache.setObject(img, forKey: "c\(t.chatId)" as NSString) }
        }
        if let img { cache.setObject(img, forKey: t.id as NSString) }
        return img
    }
}
