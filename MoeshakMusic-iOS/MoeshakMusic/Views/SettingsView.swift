import SwiftUI

/// تنظیمات + پروکسی + کلیدهای API — تیم موشک
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var session: Session
    @ObservedObject private var prefs = Prefs.shared
    @State private var account: (name: String, phone: String, id: Int64)?
    @State private var etApiId = ""
    @State private var etApiHash = ""
    @AppStorage("lang") private var lang = "fa"

    var body: some View {
        NavigationStack {
            ZStack {
                Color.moeshakBg.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 14) {
                        // اکانت
                        card("👤 حساب") {
                            if let a = account {
                                Text(a.name).font(.system(size: 16, weight: .bold)).foregroundColor(.moeshakText)
                                Text("+" + a.phone + " • id \(a.id)")
                                    .font(.system(size: 13)).foregroundColor(.moeshakMuted)
                            } else {
                                Text("در حال لود…").font(.system(size: 13)).foregroundColor(.moeshakMuted)
                            }
                            Button("🚪 خروج از حساب", role: .destructive) {
                                session.logout()
                                dismiss()
                            }
                            .font(.system(size: 14, weight: .semibold))
                            .padding(.top, 8)
                        }

                        // 🎨 رنگ اکسنت
                        card("🎨 رنگ اکسنت") {
                            HStack(spacing: 10) {
                                // 0 = پیش‌فرض
                                Circle()
                                    .fill(Color(hex: 0x22D3EE))
                                    .frame(width: 36, height: 36)
                                    .overlay(alignment: .bottom) {
                                        Text("پیش‌فرض").font(.system(size: 8)).foregroundColor(.moeshakMuted)
                                    }
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 4).stroke(
                                            prefs.accentIndex == 0 ? Color.white : .clear, lineWidth: 2))
                                    .onTapGesture { prefs.accentIndex = 0 }
                                ForEach(0..<Prefs.accentColors.count, id: \.self) { i in
                                    Circle()
                                        .fill(Prefs.accentColors[i])
                                        .frame(width: 36, height: 36)
                                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(
                                            prefs.accentIndex == i + 1 ? Color.white : .clear, lineWidth: 2))
                                        .onTapGesture { prefs.accentIndex = i + 1 }
                                        .id(i)
                                }
                            }
                            .frame(maxWidth: .infinity)
                        }

                        // 🌐 زبان + تم
                        card("⚙️ نمایش") {
                            HStack {
                                Text("زبان / Language").foregroundColor(.moeshakText)
                                Spacer()
                                Button(lang == "fa" ? "🌐 فارسی" : "🌐 English") {
                                    lang = lang == "fa" ? "en" : "fa"
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: 0x22D3EE))
                            }
                            Divider().overlay(Color.moeshakOutline)
                            Picker("حالت", selection: Binding(
                                get: { prefs.themeMode },
                                set: { prefs.themeMode = $0 })) {
                                Text("سیستمی").tag(0)
                                Text("روشن").tag(1)
                                Text("تاریک").tag(2)
                            }
                            .pickerStyle(.segmented)
                        }

                        // 🛡 پروکسی
                        card("🛡 پروکسی") {
                            Toggle("پروکسی فعال", isOn: Binding(
                                get: { prefs.proxyEnabled },
                                set: { on in
                                    if on {
                                        guard !prefs.proxies.isEmpty else {
                                            UIHelpers.toast("اول یک پروکسی اضافه کن"); return
                                        }
                                        var idx = prefs.lastProxyIndex
                                        if idx >= prefs.proxies.count { idx = 0 }
                                        prefs.lastProxyIndex = idx
                                        let e = prefs.proxies[idx]
                                        prefs.proxyServer = e.server
                                        prefs.proxyPort = e.port
                                        prefs.proxySecret = e.secret
                                        prefs.proxyEnabled = true
                                        Session.shared.applyProxy()
                                    } else {
                                        prefs.proxyEnabled = false
                                        Session.shared.applyProxy()
                                    }
                                }))
                                .tint(Color(hex: 0x22D3EE))
                            Text("وضعیت اتصال: \(session.connText)")
                                .font(.system(size: 12)).foregroundColor(.moeshakMuted)
                            // لیست پروکسی‌ها
                            ForEach(prefs.proxies) { e in
                                HStack {
                                    Text(e.label).font(.system(size: 12, weight: .medium)).foregroundColor(.moeshakText)
                                    Spacer()
                                    Text(e.pingMs == -2 ? "✗" : e.pingMs == -1 ? "—" : "\(e.pingMs) ms")
                                        .font(.system(size: 11)).foregroundColor(.moeshakMuted)
                                    Button { ping(e) } label: { Image(systemName: "bolt") }
                                        .font(.system(size: 12)).foregroundColor(Color(hex: 0x22D3EE))
                                    Button { deleteProxy(e) } label: { Image(systemName: "trash") }
                                        .font(.system(size: 12)).foregroundColor(Color(hex: 0xF87171))
                                }
                            }
                            ProxyAddRow()
                        }

                        // 🔑 کلیدهای API
                        card("🔑 کلید API تلگرام") {
                            Text("اگر کد ورود نمی‌آید، کلید شخصی خودت را از my.telegram.org (با VPN) بگیر و اینجا وارد کن")
                                .font(.system(size: 12)).foregroundColor(.moeshakMuted)
                            TextField("API ID", text: $etApiId)
                                .keyboardType(.numberPad)
                                .textFieldStyle(MoeshakField())
                            TextField("API Hash", text: $etApiHash)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .textFieldStyle(MoeshakField())
                            Button("ذخیره و ری‌استارت اپ") {
                                guard let id = Int(etApiId.trimmingCharacters(in: .whitespaces)),
                                      etApiHash.count >= 32 else {
                                    UIHelpers.toast("API ID عدد و Hash حداقل ۳۲ کاراکتر"); return
                                }
                                prefs.saveKeys(id: id, hash: etApiHash.trimmingCharacters(in: .whitespaces))
                                UIHelpers.restartApp()
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: 0x05242B))
                            .padding(10)
                            .frame(maxWidth: .infinity)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0x22D3EE)))
                            Button("↺ برگشت به کلید پیش‌فرض") {
                                prefs.clearKeys()
                                UIHelpers.restartApp()
                            }
                            .font(.system(size: 12)).foregroundColor(.moeshakMuted)
                        }

                        Text("ساخته‌شده با 🚀 توسط تیم موشک — moeshakteam.ir")
                            .font(.system(size: 11)).foregroundColor(.moeshakMuted)
                            .padding(.bottom, 20)
                    }
                    .padding(14)
                }
            }
            .navigationTitle("تنظیمات")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("بستن") { dismiss() }
                }
            }
            .task {
                account = await session.getMe()
            }
        }
        .preferredColorScheme(.dark)
    }

    private func card<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.system(size: 13, weight: .bold)).foregroundColor(Color(hex: 0x22D3EE))
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.moeshakCard)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.moeshakOutline, lineWidth: 1)))
    }

    private func ping(_ e: ProxyEntry) {
        Task.detached {
            let ms: Int
            do {
                let r = try TDJson.syncDict(["@type": "pingProxy",
                                             "proxy": ["@type": "proxy", "server": e.server,
                                                       "port": e.port, "secret": e.secret]])
                ms = Int((r["seconds"] as? Double ?? 0) * 1000)
            } catch { ms = -2 }
            await MainActor.run {
                var list = Prefs.shared.proxies
                if let i = list.firstIndex(where: { $0.id == e.id }) { list[i].pingMs = ms }
                Prefs.shared.proxies = list
            }
        }
    }


    private func deleteProxy(_ e: ProxyEntry) {
        prefs.proxies.removeAll { $0.id == e.id }
    }
}

// ردیف افزودن پروکسی
struct ProxyAddRow: View {
    @State private var link = ""
    @ObservedObject private var prefs = Prefs.shared

    var body: some View {
        VStack(spacing: 8) {
            TextField("tg://proxy?… یا server:port:secret", text: $link)
                .textFieldStyle(MoeshakField())
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            Button("➕ افزودن") { add() }
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Color(hex: 0x22D3EE))
        }
    }

    private func add() {
        let s = link.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return }
        // پارس tg://proxy?server=..&port=..&secret=.. یا server:port:secret
        var server: String?, secret: String?
        var port = 443
        if let rg = try? NSRegularExpression(pattern: "server=([^&\\s]+).*?port=(\\d+).*?secret=([a-zA-Z0-9]+)") {
            if let m = rg.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
               let sr = Range(m.range(at: 1), in: s), let pr = Range(m.range(at: 2), in: s),
               let cr = Range(m.range(at: 3), in: s) {
                server = String(s[sr]); port = Int(s[pr]) ?? 443; secret = String(s[cr])
            }
        }
        if server == nil {
            let parts = s.split(whereSeparator: { ":/# ".contains($0) }).map(String.init)
            if parts.count >= 3, let p = Int(parts[parts.count - 2]) {
                server = parts[parts.count - 3]
                port = p
                secret = parts[parts.count - 1]
            }
        }
        guard let srv = server, let sec = secret, sec.count >= 16 else {
            UIHelpers.toast("لینک پروکسی قابل خواندن نبود"); return
        }
        var list = prefs.proxies
        guard !list.contains(where: { $0.server == srv && $0.port == port && $0.secret == sec }) else {
            UIHelpers.toast("از قبل تو لیست است"); return
        }
        list.append(ProxyEntry(server: srv, port: port, secret: sec))
        prefs.proxies = list
        link = ""
        UIHelpers.toast("پروکسی اضافه شد ✓")
    }
}

extension Session {
    var connText: String {
        switch connState {
        case .ready: return "وصل ✓"
        case .waiting: return "منتظر شبکه"
        case .updating: return "به‌روزرسانی…"
        default: return "در حال اتصال…"
        }
    }

    /// اعمال پروکسی به TDLib
    func applyProxy() {
        let prefs = Prefs.shared
        guard prefs.proxyEnabled, !prefs.proxyServer.isEmpty else {
            TDJson.sendRawDict(["@type": "disableProxy"], handler: nil)
            return
        }
        TDJson.sendRawDict([
            "@type": "addProxy",
            "server": prefs.proxyServer,
            "port": prefs.proxyPort,
            "enable": true,
            "type": ["@type": "proxyTypeMtproto", "secret": prefs.proxySecret]
        ], handler: nil)
    }
}
