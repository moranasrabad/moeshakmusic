# 📥 موشک موزیک — اندروید

نسخهٔ سورس فعلی: **v5.10.3** (versionCode 85)

APK آماده در این محیط ساخته نشد؛ بیلد را روی سیستم خودت بزن.

راهنمای کامل، کلیک‌به‌کلیک: **[BUILD-APK.md](BUILD-APK.md)**

خروجی: `app/build/outputs/apk/release/app-release.apk`

```bash
git clone https://github.com/moranasrabad/moeshakmusic
cd moeshakmusic
git checkout arena/01a05311-moeshakmusic
# اگر jniLibs نبود: ./setup-abis.sh
gradle assembleRelease
```

ورود روی گوشی: QR (تلگرام ← تنظیمات ← دستگاه‌ها ← اتصال دستگاه).

تاریخچه: [CHANGELOG.md](CHANGELOG.md)
