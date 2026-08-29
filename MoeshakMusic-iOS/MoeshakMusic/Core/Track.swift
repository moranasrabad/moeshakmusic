import Foundation

/// مدل تراک — قابل ذخیره در JSON — تیم موشک
struct Track: Identifiable, Hashable, Codable {
    var chatId: Int64
    var messageId: Int64
    var chatTitle: String
    var title: String
    var performer: String
    var duration: Int
    var date: Int
    var fileId: Int
    var expectedSize: Int64
    var thumbFileId: Int
    var chatPhotoFileId: Int
    var isVoice: Bool

    var id: String { "\(chatId):\(messageId)" }

    var subtitle: String {
        let p = performer.isEmpty ? "هنرمند ناشناس" : performer
        return chatTitle.isEmpty ? p : p + " • " + chatTitle
    }

    static func == (l: Track, r: Track) -> Bool { l.id == r.id }

    static func timeString(_ sec: Int) -> String {
        guard sec > 0 else { return "—" }
        return String(format: "%d:%02d", sec / 60, sec % 60)
    }
}

// MARK: - پارس از JSON خام TDLib (مثل موتور اندروید)

extension Track {
    static func fromMessage(_ m: [String: Any], chatId: Int64, chatTitle: String) -> Track? {
        guard let content = m["content"] as? [String: Any],
              let type = content["@type"] as? String else { return nil }
        let date = m["date"] as? Int ?? 0
        switch type {
        case "messageAudio":
            guard let a = content["audio"] as? [String: Any],
                  let f = a["audio"] as? [String: Any] else { return nil }
            var t = Track(chatId: chatId, messageId: m["id"] as? Int64 ?? 0,
                          chatTitle: chatTitle, title: "", performer: "",
                          duration: a["duration"] as? Int ?? 0, date: date,
                          fileId: f["id"] as? Int ?? 0,
                          expectedSize: f["expected_size"] as? Int64 ?? 0,
                          thumbFileId: 0, chatPhotoFileId: 0, isVoice: false)
            let title = a["title"] as? String ?? ""
            let fileName = a["file_name"] as? String ?? ""
            let baseName: String
            if let dot = fileName.range(of: ".", options: .backwards) {
                baseName = String(fileName[..<dot.lowerBound])
            } else {
                baseName = fileName
            }
            t.title = !title.isEmpty ? title : (baseName.isEmpty ? "بی‌نام" : baseName)
            t.performer = a["performer"] as? String ?? ""
            if let mini = a["album_cover_minithumbnail"] as? [String: Any] {
                _ = mini["data"] as? String // مینی‌تامب — در iOS کاور بزرگ را از thumbnail می‌گیریم
            }
            if let thumb = a["album_cover_thumbnail"] as? [String: Any] {
                t.thumbFileId = thumb["id"] as? Int ?? 0
            }
            return t
        case "messageDocument":
            guard let d = content["document"] as? [String: Any],
                  let mime = d["mime_type"] as? String, mime.hasPrefix("audio/"),
                  let f = d["document"] as? [String: Any] else { return nil }
            let fn = d["file_name"] as? String ?? ""
            let name = fn.contains(".") ? String(fn.prefix((fn.range(of: ".", options: .backwards)?.lowerBound ?? fn.index(fn.startIndex, offsetBy: 0)))) : fn
            return Track(chatId: chatId, messageId: m["id"] as? Int64 ?? 0,
                         chatTitle: chatTitle, title: name.isEmpty ? "فایل صوتی" : name,
                         performer: "فایل", duration: 0, date: date,
                         fileId: f["id"] as? Int ?? 0,
                         expectedSize: f["expected_size"] as? Int64 ?? 0,
                         thumbFileId: 0, chatPhotoFileId: 0, isVoice: false)
        default:
            return nil
        }
    }
}
