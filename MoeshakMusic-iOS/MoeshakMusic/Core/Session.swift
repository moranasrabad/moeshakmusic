import Foundation
import SwiftUI

/// کلاینت تلگرام سطح بالا: ورود (شماره/کد/رمز/QR)، خاتمهٔ نشست، بازیابی — تیم موشک
@MainActor
final class Session: ObservableObject {

    enum AuthState: Equatable {
        case loading
        case waitPhoneNumber
        case waitCode
        case waitPassword(hint: String)
        case waitQR(link: String)
        case ready
        case loggingOut
        case failed(String)
    }

    @Published var state: AuthState = .loading
    @Published var connState: ConnState = .connecting
    @Published var myUserId: Int64 = 0

    enum ConnState { case connecting, ready, waiting, updating }

    static let shared = Session()
    private var pendingQR = false
    private var loggedOutManually = false

    private init() {}

    func start() {
        let prefs = Prefs.shared
        TDJson.start { [weak self] update in
            Task { @MainActor in self?.handle(update) }
        }
        applyProxy()
        applyTdParameters()
    }

    /// بازسازی کلاینت (بعد از خاتمهٔ نشست) — برای ورود مجدد QR
    func relogin() {
        TDJson.gate(false)
        TDJson.recreate()
        state = .loading
        pendingQR = false
        applyTdParameters()
    }

    private func applyTdParameters() {
        let prefs = Prefs.shared
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("tdlib").path
        let params: TDJson.AnyDict = [
            "@type": "setTdlibParameters",
            "database_directory": dir,
            "files_directory": dir + "/files",
            "database_encryption_key": NSNull(),
            "use_file_database": true,
            "use_chat_info_database": true,
            "use_message_database": true,
            "use_secret_chats": false,
            "api_id": prefs.apiId,
            "api_hash": prefs.apiHash,
            "system_language_code": "fa",
            "device_model": "iOS",
            "application_version": "1.0.0",
            "system_version": "iOS"
        ]
        TDJson.sendRawDict(params, handler: nil)
    }

    // MARK: - Updates

    private func handle(_ u: TDJson.AnyDict) {
        guard let type = u["@type"] as? String else { return }
        switch type {
        case "updateAuthorizationState":
            guard let a = u["authorization_state"] as? TDJson.AnyDict else { return }
            handleAuth(a)
        case "updateConnectionState":
            let s = (u["state"] as? TDJson.AnyDict)?["@type"] as? String ?? ""
            switch s {
            case "connectionStateReady": connState = .ready
            case "connectionStateWaitingForNetwork": connState = .waiting
            case "connectionStateUpdating": connState = .updating
            default: connState = .connecting
            }
        case "updateOption":
            // برخی build ها اولین پاسخ را به‌صورت updateOption/version می‌دهند — مهم نیست
            break
        default:
            break
        }
    }

    private func handleAuth(_ a: TDJson.AnyDict) {
        switch a["@type"] as? String ?? "" {
        case "authorizationStateWaitTdlibParameters":
            applyTdParameters()
        case "authorizationStateWaitPhoneNumber":
            if pendingQR { requestQR(); pendingQR = false }
            else { state = .waitPhoneNumber }
        case "authorizationStateWaitCode":
            state = .waitCode
        case "authorizationStateWaitPassword":
            let hint = a["password_hint"] as? String ?? ""
            state = .waitPassword(hint: hint)
        case "authorizationStateWaitOtherDeviceConfirmation":
            let link = a["link"] as? String ?? ""
            state = .waitQR(link: link)
        case "authorizationStateReady":
            pendingQR = false
            loggedOutManually = false
            state = .ready
            Task.detached { await LibraryManager.shared.restoreFavoritesAndPlaylists() }
        case "authorizationStateLoggingOut":
            if !loggedOutManually {
                // خاتمهٔ نشست از دستگاه دیگر — بدون کرش، QR دوباره
                pendingQR = true
                TDJson.gate(true)
                PlayerManager.shared.stopAll()
            }
            state = .loggingOut
        case "authorizationStateClosed":
            LibraryManager.shared.wipeInMemory()
            TDJson.recreate()
            state = .loading
            applyTdParameters()
        default:
            break
        }
    }

    // MARK: - Actions

    func sendPhone(_ phone: String) {
        guard case .waitPhoneNumber = state else { return }
        TDJson.sendRawDict([
            "@type": "setAuthenticationPhoneNumber",
            "phone_number": phone,
            "allow_flash_call": false,
            "is_current_phone_number": false
        ], handler: nil)
    }

    func sendCode(_ code: String) {
        guard case .waitCode = state else { return }
        TDJson.sendRawDict(["@type": "checkAuthenticationCode", "code": code], handler: nil)
    }

    func sendPassword(_ pw: String) {
        guard case .waitPassword = state else { return }
        TDJson.sendRawDict(["@type": "checkAuthenticationPassword", "password": pw], handler: nil)
    }

    func requestQR() {
        TDJson.sendRawDict(["@type": "requestQrCodeAuthentication", "other_user_ids": [] as [Int64]], handler: nil)
    }

    func logout() {
        loggedOutManually = true
        TDJson.gate(true)
        PlayerManager.shared.stopAll()
        TDJson.sendRawDict(["@type": "logOut"], handler: nil)
    }

    func getMe() async -> (name: String, phone: String, id: Int64)? {
        guard case .ready = state else { return nil }
        if let me = try? TDJson.syncDict(["@type": "getMe"]) {
            let id = (me["id"] as? NSNumber)?.int64Value ?? 0
            myUserId = id
            let first = me["first_name"] as? String ?? ""
            let last = me["last_name"] as? String ?? ""
            var name = (first + " " + last).trimmingCharacters(in: .whitespaces)
            if name.isEmpty, let un = (me["usernames"] as? TDJson.AnyDict)?["active_usernames"] as? [String], let u = un.first {
                name = "@" + u
            }
            let phone = me["phone_number"] as? String ?? "—"
            return (name, phone, id)
        }
        return nil
    }
}
