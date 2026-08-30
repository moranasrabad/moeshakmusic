# 📥 دانلود آخرین نسخه اندروید

## MoeshakMusic v5.9.5 (نسخهٔ فعلی)

فایل APK در ریشهٔ ریپو نگهداری می‌شود (جایگزین هر بیلد):
**`MoeshakMusic-v5.9.5.apk`** — حدود ۹۲MB — هر ۳ معماری (arm64-v8a, armeabi-v7a, x86_64)

> تاریخچهٔ کامل تغییرات: [CHANGELOG.md](CHANGELOG.md)

## نصب
1. APK را دانلود کن
2. روی گوشی بازش کن (اجازه «نصب از منابع ناشناس»)
3. ورود: سریع‌ترین راه **QR** (تلگرام ← تنظیمات ← دستگاه‌ها ← اتصال دستگاه)

## بیلد از سورس
```bash
git clone https://github.com/moranasrabad/moeshakmusic
cd moeshakmusic
./setup-abis.sh          # دانلود TDLib jniLibs
gradle assembleRelease   # یا -Pslim برای فقط arm64
# خروجی: app/build/outputs/apk/release/app-release.apk
```

## نسخه‌های قدیمی
در کامیت‌های گیت (هر نسخه APK خودش را در کامیت عنوان‌دار دارد — تاریخچه CHANGELOG را ببین).
