import SwiftUI
import CoreImage.CIFilterBuiltins

/// ورود — شماره / کد / رمز / QR — مثل تلگرام رسمی — تیم موشک
struct LoginView: View {
    @EnvironmentObject var session: Session
    @State private var phone = ""
    @State private var code = ""
    @State private var password = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            Image("Logo")
                .resizable().scaledToFit()
                .frame(width: 96, height: 96)
                .shadow(color: Color(hex: 0x22D3EE).opacity(0.4), radius: 20)
                .padding(.top, 40)
            Text("موشک موزیک")
                .font(.system(size: 26, weight: .heavy))
                .foregroundColor(.moeshakText)
                .padding(.top, 10)

            ScrollView {
                switch session.state {
                case .waitPhoneNumber: phoneStep
                case .waitCode: codeStep
                case .waitPassword(let hint): passwordStep(hint)
                case .waitQR(let link): qrStep(link)
                default: ProgressView().tint(.white).padding(60)
                }
            }
        }
        .background(Color.moeshakBg)
    }

    // MARK: شماره

    private var phoneStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("ورود به تلگرام")
                .font(.system(size: 20, weight: .bold)).foregroundColor(.moeshakText)
            Text("شماره موبایل بین‌المللی، مثل ‎+98912…")
                .font(.system(size: 13)).foregroundColor(.moeshakMuted)
            TextField("+98912…", text: $phone)
                .keyboardType(.phonePad)
                .focused($focused)
                .textFieldStyle(MoeshakField())
            Button {
                focused = false
                session.sendPhone(phone.trimmingCharacters(in: .whitespaces))
            } label: {
                Text("ارسال کد").moeshakButton()
            }
            qrButton
        }
        .padding(.horizontal, 24).padding(.top, 24)
    }

    // MARK: کد

    private var codeStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("کد تأیید")
                .font(.system(size: 20, weight: .bold)).foregroundColor(.moeshakText)
            Text("کد ۵ رقمی به چت «Telegram» در خود اپ تلگرام می‌آید (نه پیامک!)")
                .font(.system(size: 13)).foregroundColor(.moeshakMuted)
            TextField("12345", text: $code)
                .keyboardType(.numberPad)
                .focused($focused)
                .textFieldStyle(MoeshakField())
            Button {
                session.sendCode(code.trimmingCharacters(in: .whitespaces))
            } label: {
                Text("تأیید").moeshakButton()
            }
            Button("📩 کد نرسید؟ ورود با QR") { session.requestQR() }
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Color(hex: 0x22D3EE))
        }
        .padding(.horizontal, 24).padding(.top, 24)
    }

    // MARK: رمز 2FA

    private func passwordStep(_ hint: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("رمز دو مرحله‌ای")
                .font(.system(size: 20, weight: .bold)).foregroundColor(.moeshakText)
            if !hint.isEmpty {
                Text("راهنما: \(hint)").font(.system(size: 13)).foregroundColor(.moeshakMuted)
            }
            SecureField("رمز عبور اکانت", text: $password)
                .focused($focused)
                .textFieldStyle(MoeshakField())
            Button {
                session.sendPassword(password)
            } label: {
                Text("ورود").moeshakButton()
            }
        }
        .padding(.horizontal, 24).padding(.top, 24)
    }

    // MARK: QR

    private func qrStep(_ link: String) -> some View {
        VStack(spacing: 14) {
            Text("ورود با QR")
                .font(.system(size: 20, weight: .bold)).foregroundColor(.moeshakText)
            if link.isEmpty {
                ProgressView("در حال ساخت کد…").tint(.white)
            } else {
                QRCodeView(text: link)
                    .frame(width: 230, height: 230)
                    .background(Color.white.cornerRadius(20))
                    .padding(10)
                    .shadow(color: Color(hex: 0x22D3EE).opacity(0.35), radius: 24)
                Text("تلگرام ← تنظیمات ← دستگاه‌ها ← اتصال دستگاه")
                    .font(.system(size: 12.5)).foregroundColor(.moeshakMuted)
                    .multilineTextAlignment(.center)
            }
            Button("← بازگشت به ورود با شماره") { session.relogin() }
                .font(.system(size: 13)).foregroundColor(.moeshakMuted)
        }
        .padding(.horizontal, 24).padding(.top, 24)
    }

    private var qrButton: some View {
        Button {
            session.requestQR()
        } label: {
            HStack {
                Image(systemName: "qrcode")
                Text("ورود سریع با QR (پیشنهادی — بدون کد)")
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Color(hex: 0x22D3EE))
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - اجزا

struct MoeshakField: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .foregroundColor(.moeshakText)
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.moeshakCard)
                    .overlay(RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.moeshakOutline, lineWidth: 1))
            )
    }
}

extension View {
    func moeshakButton() -> some View {
        frame(maxWidth: .infinity)
            .padding(14)
            .background(
                LinearGradient(colors: [Color(hex: 0x22D3EE), Color(hex: 0x8B5CF6)],
                               startPoint: .leading, endPoint: .trailing)
            )
            .foregroundColor(Color(hex: 0x05242B))
            .font(.system(size: 16, weight: .bold))
            .cornerRadius(16)
    }
}

/// تولید QR با CoreImage
struct QRCodeView: View {
    let text: String
    var body: some View {
        if let img = generate() {
            Image(uiImage: img).interpolation(.none).resizable()
        } else {
            Color.gray
        }
    }

    private func generate() -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scale = 12.0
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let context = CIContext()
        if let cg = context.createCGImage(scaled, from: scaled.extent) {
            return UIImage(cgImage: cg)
        }
        return nil
    }
}
