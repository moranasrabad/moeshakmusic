# 🚀 موشک موزیک — نسخهٔ iOS (SwiftUI + TDLib)

نسخهٔ آیفونِ موشک موزیک — همان برند، همان امکانات نسخهٔ اندروید، با SwiftUI و TDLib 1.8.66.

> ✅ **IPA آماده است!** خودِ سرور مکِ گیت‌هاب (GitHub Actions) بیلدش کرده:
> **📥 دانلود:** [Release ios-v7 — MoeshakMusic-unsigned.ipa](https://github.com/moranasrabad/moeshakmusic/releases/download/ios-v7/MoeshakMusic-unsigned.ipa)
> (ریپو خصوصی است — اول در گیت‌هاب لاگین باش)
>
> ## 📲 نصب بدون مک (سه راه)
> | راه | نیاز | محدودیت |
> |---|---|---|
> | **TrollStore** | iOS 14.0–16.6.1 یا 17.0 | بدون امضا، دائمی ⭐ بهترین |
> | **Sideloadly** (ویندوز) | کابل + اپل‌آیدی رایگان | هر ۷ روز دوباره امضا |
> | **AltStore** (ویندوز/مک) | کابل + اپل‌آیدی رایگان | هر ۷ روز + رفرش خودکار با Wi-Fi |

## ⚡️ راه‌اندازی (۳ دقیقه)

```bash
# ۱) XcodeGen را نصب کن (فقط یک بار)
brew install xcodegen

# ۲) پروژهٔ Xcode را بساز
cd MoeshakMusic-iOS
xcodegen

# ۳) باز کن و Run بزن
open MoeshakMusic.xcodeproj
```

اولین بار که بیلد می‌کنی، Xcode پکیج **TDLibFramework** (~۳۰۰MB باینری prebuilt TDLib برای iOS) را خودش از GitHub دانلود می‌کند — صبور باش.

## ✨ امکانات (مطابق نسخهٔ اندروید)

- **ورود**: شماره / کد (به چت Telegram می‌آید) / رمز ۲FA / **QR** (CoreImage) — خاتمهٔ نشست از راه دور هم بدون کرش، QR دوباره خودکار باز می‌شود
- **۷ تب**: آهنگ‌ها / اسکن / پلی‌لیست / فیوریت / دانلودها / کانال‌ها / چت‌ها
- **اسکن زنده**: عمق ۵۰/۱۰۰/۳۰۰/همه، پیشرفت لحظه‌ای (چت/آهنگ/ثانیه)، لغو وسط کار، نتایج جدا از کتابخانه + «افزودن همه به کتابخانه»
- **ذخیرهٔ دائمی فقط فیوریت‌ها و پلی‌لیست‌ها** — بقیه با هر اسکن ساخته می‌شوند؛ بعد از ورود، fileId تازه خودکار گرفته می‌شود
- **پلی‌لیست‌ها**: هر کدام دکمهٔ پخش / ادیت / دانلود / حذف
- **دانلودها**: حذف با swipe = حذف واقعی فایل از حافظه
- **پلیر**: کاور دایره‌ای HD (کاور آلبوم ← عکس کانال)، ویژوالایزر حلقه‌ای، شافل/ریپیت، پخش پس‌زمینه + Control Center، قفل صفحه با کاور
- **سرچ**: فقط در آهنگ‌ها و فیوریت + سرچ جدا در چت‌ها
- **تنظیمات**: ۶ رنگ اکسنت، زبان فارسی/English (RTL/LTR)، تم، **سوییچ پروکسی + پینگ**، **کلید API شخصی** (وقتی کد ورود نمی‌آید)، خروج
- **سپر وضعیت اتصال** در هدر مثل تلگرام: سبز=وصل، کهربایی=اتصال، سرخ=شبکه

## 🏗 ساختار

```
MoeshakMusic-iOS/
├── project.yml                    # تعریف پروژهٔ XcodeGen
└── MoeshakMusic/
    ├── MoeshakMusicApp.swift      # نقطهٔ ورود + تم
    ├── Core/
    │   ├── TD/TDJson.swift        # پل JSON خام به TDLib (مثل اندروید)
    │   ├── Session.swift          # ورود/خاتمه/QR — ضدکرش
    │   ├── Track.swift            # مدل + پارس JSON خام
    │   ├── Prefs.swift            # کلیدها/تم/اکسنت/پروکسی
    │   ├── Stores.swift           # فیوریت/پلی‌لیست/دانلود (JSON دائمی)
    │   ├── LibraryManager.swift   # اسکن زنده + بازیابی fileId
    │   ├── PlayerManager.swift    # AVPlayer + Now Playing + ویژوالایزر
    │   ├── TDLoader.swift         # دانلود فایل/کاور + کش
    │   └── Services.swift         # دانلود ترتیبی، چت‌سرویس، توست
    └── Views/
        ├── RootView.swift         # ۷ تب + اسپلش
        ├── LoginView.swift        # ورود (شماره/کد/2FA/QR)
        ├── PlayerView.swift       # Now Playing تمام‌صفحه + مینی‌پلیر
        ├── TracksViews.swift      # آهنگ‌ها / فیوریت / اسکن
        ├── SectionsViews.swift    # پلی‌لیست / دانلود / کانال / چت
        └── SettingsView.swift     # تنظیمات + پروکسی + کلید API
```

## 📲 خروجی IPA (برای TestFlight/ sideways)

بعد از Run موفق روی گوشی خودت:
1. `Product → Archive` در Xcode
2. `Distribute App → Ad-hoc / TestFlight` با اکانت Apple Developer خودت (۹۹$/سال) یا **امضای رایگان ۷ روزه** با اکانت شخصی

## 🔧 نکات فنی

- TDLib از طریق **[Swiftgram/TDLibFramework](https://github.com/Swiftgram/TDLibFramework)** (SPM، prebuilt 1.8.66) — همان API کلاینت JSON که نسخهٔ اندروید استفاده می‌کند
- معماری: `Session` (ورود) + `LibraryManager` (اسکن) + `PlayerManager` (پخش) — همه ObservableObject و هماهنگ با SwiftUI
- درگاه ضدکرش `TDJson.gate()` — دقیقاً مثل اندروید، هنگام خاتمهٔ نشست درخواست‌ها خطای ۵۰۳ می‌گیرند به‌جای کرش نیتیو

---

ساخته‌شده با ❤️ توسط **تیم موشک** — [moeshakteam.ir](https://moeshakteam.ir)
