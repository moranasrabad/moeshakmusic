# 📥 دانلود آخرین نسخه اندروید

## MoeshakMusic v6.0.1 (نسخهٔ فعلی)

📦 از صفحهٔ **[Releases on GitHub](https://github.com/moranasrabad/moeshakmusic/releases/tag/v6.0.0)** دانلود کن:

- **`MoeshakMusic-v6.0.0.apk`** — حدود ۹۲MB — هر ۳ معماری (arm64-v8a, armeabi-v7a, x86_64 — ویندوز با WSA هم همین)
- **`MoeshakMusic-v6.0.0-slim-arm64.apk`** — حدود ۴۰MB — فقط arm64 (گوشی‌های مدرن)
- **`MoeshakMusic-v6.0.0.aab`** — باندل پلی‌استور

> فایل‌های نصبی داخل ورک‌اسپیس/ریپو نگهداری نمی‌شوند؛ فقط روی صفحهٔ ریلیز گیت‌هاب.
> تاریخچهٔ کامل تغییرات: [CHANGELOG.md](CHANGELOG.md)

## نصب
1. APK را دانلود کن
2. روی گوشی بازش کن (اجازه «نصب از منابع ناشناس» یا نصب از مرورگر)
3. ورود: سریع‌ترین راه **QR** (تلگرام ← تنظیمات ← دستگاه‌ها ← اتصال دستگاه)

## بیلد از سورس
```bash
git clone https://github.com/moranasrabad/moeshakmusic
cd moeshakmusic
./setup-abis.sh          # دانلود TDLib jniLibs (اگه در ریپو نبود)
gradle assembleRelease   # یا -Pslim برای فقط arm64
# خروجی: app/build/outputs/apk/release/app-release.apk
```

## نسخه‌های قدیمی
در صفحهٔ [Releases](https://github.com/moranasrabad/moeshakmusic/releases) گیت‌هاب.
