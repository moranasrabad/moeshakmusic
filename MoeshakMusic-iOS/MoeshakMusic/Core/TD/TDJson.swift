import Foundation
import TDLibFramework

/// پل JSON خام به TDLib — همان معماری نسخهٔ اندروید (کلاینت JSON بدون کدک) — تیم موشک
enum TDJson {

    typealias AnyDict = [String: Any]

    struct TDError: Error {
        let code: Int
        let message: String
    }

    private struct Client {
        let id: Int32
    }

    // MARK: - State

    private static let lock = NSLock()
    private static var client: Client?
    private static var receiveThreadStarted = false
    private static var handlers: [Int64: (Result<AnyDict, TDError>) -> Void] = [:]
    private static var updateHandler: ((AnyDict) -> Void)?
    /// درگاه ضدکرش — هنگام خروج/خاتمهٔ نشست همهٔ درخواست‌ها خطا می‌گیرند
    private static var gated = false
    private static var nextId: Int64 = 1

    // MARK: - Client lifecycle

    static func start(updateHandler: @escaping (AnyDict) -> Void) {
        lock.lock(); defer { lock.unlock() }
        self.updateHandler = updateHandler
        guard client == nil else { return }
        let id = td_create_client_id()
        client = Client(id: id, thread: nil)
        gated = false
        // حلقهٔ دریافت — یک بار برای همیشه
        guard !receiveThreadStarted else { return }
        receiveThreadStarted = true
        Thread.detachNewThread {
            while true {
                guard let raw = td_receive(2.0) else { continue }
                let str = String(cString: raw)
                guard let data = str.data(using: .utf8),
                      let obj = (try? JSONSerialization.jsonObject(with: data)) as? AnyDict else { continue }
                dispatch(obj)
            }
        }
        // درخواست اولیه برای بیدار کردن کلاینت (UpdateAuthorizationState می‌آید)
        sendRawDict(["@type": "getOption", "name": "version"], handler: nil)
    }

    /// ساخت کلاینت تازه (بعد از بسته شدن نشست)
    static func recreate() {
        lock.lock(); defer { lock.unlock() }
        handlers.removeAll()
        let id = td_create_client_id()
        client = Client(id: id, thread: nil)
        gated = false
        sendRawDict(["@type": "getOption", "name": "version"], handler: nil)
    }

    /// بستن درگاه — هنگام خاتمهٔ نشست از راه دور
    static func gate(_ on: Bool) {
        lock.lock(); defer { lock.unlock() }
        gated = on
        if on { handlers.removeAll() }
    }

    private static func currentClientId() -> Int32 {
        lock.lock(); defer { lock.unlock() }
        return client?.id ?? -1
    }

    private static func dispatch(_ obj: AnyDict) {
        // پاسخ درخواست‌ها (دارای @extra) → هندلر خودش
        if let extra = (obj["@extra"] as? NSNumber)?.int64Value {
            lock.lock()
            let h = handlers.removeValue(forKey: extra)
            lock.unlock()
            if let h = h {
                if let err = obj["@type"] as? String, err == "error" {
                    h(.failure(TDError(code: obj["code"] as? Int ?? 500,
                                       message: obj["message"] as? String ?? "unknown")))
                } else {
                    h(.success(obj))
                }
            }
            return
        }
        // آپدیت‌های سراسری (بدون @extra) — از جمله updateAuthorizationState
        updateHandler?(obj)
    }

    // MARK: - Send

    static func sendRawDict(_ dict: AnyDict, handler: ((Result<AnyDict, TDError>) -> Void)?) {
        lock.lock()
        if gated {
            lock.unlock()
            handler?(.failure(TDError(code: 503, message: "client unavailable")))
            return
        }
        var d = dict
        let extra = nextId; nextId += 1
        d["@extra"] = extra
        if let h = handler { handlers[extra] = h }
        let cid = client?.id ?? -1
        lock.unlock()

        guard let data = try? JSONSerialization.data(withJSONObject: d, options: [.withoutEscapingSlashes]),
              let json = String(data: data, encoding: .utf8) else {
            handler?(.failure(TDError(code: 500, message: "serialize failed")))
            return
        }
        json.withCString { ptr in
            td_send(cid, ptr)
        }
    }

    /// درخواست sync — فقط از thread پس‌زمینه
    static func syncDict(_ dict: AnyDict, timeout: TimeInterval = 30) throws -> AnyDict {
        let sem = DispatchSemaphore(value: 0)
        let box = ResultBox()
        sendRawDict(dict) { r in
            box.result = r
            sem.signal()
        }
        if sem.wait(timeout: .now() + timeout) == .timedOut {
            throw TDError(code: 408, message: "timeout")
        }
        switch box.result {
        case .success(let o): return o
        case .failure(let e): throw e
        case .none: throw TDError(code: 408, message: "no response")
        }
    }

    private final class ResultBox {
        var result: Result<AnyDict, TDError>?
    }
}
