# 🚀 موشک موزیک — Moeshak Music

<div dir="rtl">

**پلیر موزیک اندروید متصل به اکانت تلگرام شما** — موزیک‌ها، ویس‌ها و فایل‌های صوتی داخل چت‌ها و کانال‌های تلگرام را اسکن، استریم و پخش می‌کند. بدون سرور واسط، مستقیم با TDLib.

برند: **تیم موشک** — [moeshakteam.ir](https://moeshakteam.ir)

</div>

---

## ✨ امکانات

- **ورود به تلگرام**: شماره تلفن / کد تأیید / رمز دومرحله‌ای / **QR Code**
- **اسکن عمیق**: انتخاب ۵۰ / ۱۰۰ / ۳۰۰ پیام یا **همهٔ چت** با پیشرفت زنده
- **استریم مستقیم**: پخش بدون دانلود کامل (بازه‌های ۵۱۲KB با `DownloadFile`/`ReadFilePart`) + fallback دانلود کامل با **کش دائمی**
- **کتابخانه**: تب‌های TRACKS / PLAYLISTS / FAVORITES / DOWNLOADS / CHANNELS
- **پلی‌لیست‌های سفارشی** و **فیوریت** (با دانلود خودکار تراک‌های فیوریت)
- **کانال‌ها**: لمس طولانی روی کارت کانال ← «اسکن کامل این کانال» یا «دانلود کامل این کانال»
- **پخش پس‌زمینه**: سرویس با نوتیفیکیشن + مینی‌پلیر
- **ویژوالایزر زنده**، تم روز/شب، **۷ رنگ اکسنت** قابل انتخاب
- **دوزبانه**: فارسی / English
- ساید‌منو، لاگ زنده با ضبط کرش، مدیریت پروکسی

## 🏗 معماری

```
app/src/main/java/
├── ir/moeshakteam/moeshakmusic/
│   ├── App.java                 # اپلیکیشن + رنگ اکسنت سراسری
│   ├── data/                    # Tg, Prefs, Track, DownloadStore, PlaylistStore
│   ├── td/                      # TdClient, TdCodec — لایهٔ ارتباط با TDLib
│   ├── player/                  # PlayerManager, PlaybackService, TdlibDataSource
│   ├── ui/                      # MainActivity + فرگمنت‌ها و آداپترها
│   ├── util/                    # Ui, AccentUtils
│   └── viz/                     # VisualizerView
├── org/drinkless/tdlib/TdApi.java   # کلاس تولیدشدهٔ رسمی TDLib
└── io/github/up9cloud/td/JsonClient.java  # کلاینت JSON نیتیو
```

- **TDLib 1.8.65** به‌صورت prebuilt از [`setup-abis.sh`](setup-abis.sh) دانلود و در `app/src/main/jniLibs/` قرار می‌گیرد.
- موتور اسکن با **raw JSON** کار می‌کند (بدون reflection) — پایدار در برابر تغییر کدک.
- `TdlibDataSource` برای ExoPlayer بازه‌های ۵۱۲KB را sync می‌کند.

## 📥 دانلود APK + تاریخچه تغییرات
- [ANDROID.md](ANDROID.md) — آخرین APK و نصب
- [CHANGELOG.md](CHANGELOG.md) — تغییرات همهٔ نسخه‌ها

## 🔧 بیلد

**پیش‌نیازها:** JDK 17 · Android SDK (platform 33 + build-tools 33.0.2) · Gradle 7.6.4 (AGP 7.4.2)

```bash
# ۱) دانلود کتابخانهٔ نیتیو TDLib (لازم!)
./setup-abis.sh

# ۲) کلیدهای API تلگرام (apiId / apiHash)
# keys.properties را در ریشهٔ پروژه بسازید:
#   apiId=...
#   apiHash=...

# ۳) بیلد نسخهٔ release
gradle assembleRelease            # هر ۳ ABI: arm64-v8a, armeabi-v7a, x86_64
gradle assembleRelease -Pslim     # فقط arm64-v8a (حجم کمتر)
```

خروجی: `app/build/outputs/apk/release/app-release.apk`

**امضا:** `app/moeshak.keystore` — alias: `moeshak` (تنظیمات داخل `app/build.gradle`)

## 🔑 نکتهٔ کلیدها

`keys.properties` شامل `apiId`/`apiHash` تلگرام است و در `BuildConfig` بیک می‌شود. این ریپو **خصوصی** است و کلید امضا + کلیدهای API عمداً داخل آن نگهداری می‌شوند تا بیلد روی هر ماشین قابل تکرار باشد. اگر ریپو را عمومی کردید، حتماً اول این فایل‌ها را حذف/rotate کنید.

---

<div dir="rtl">

ساخته‌شده با ❤️ توسط **تیم موشک**

</div>
