# ساخت APK موشک موزیک v5.10.3 — مرحله به مرحله

این محیط ابری JDK/SDK کامل ندارد؛ بیلد را روی **کامپیوتر خودت** بزن. امضا از قبل داخل پروژه است (`app/moeshak.keystore`)؛ لازم نیست keystore جدید بسازی.

خروجی نهایی:

`app/build/outputs/apk/release/app-release.apk`

---

## روش ۱ — Android Studio (آسان‌ترین، ویندوز / مک / لینوکس)

### ۱) نصب برنامه‌ها
1. JDK **17** (Temurin یا Oracle) را نصب کن:  
   https://adoptium.net/temurin/releases/?version=17  
   موقع نصب تیک `Set JAVA_HOME` را بزن.
2. Android Studio را نصب کن:  
   https://developer.android.com/studio  
3. اولین بار Studio باز شد: **SDK Manager** → این‌ها را نصب کن:
   - Android SDK Platform **33**
   - Android SDK Build-Tools **33.0.2** (یا جدیدتر ۳۳)
   - NDK لازم نیست (کتابخانهٔ `libtdjson.so` از قبل در پروژه است)

### ۲) گرفتن سورس
اگر Git داری:

```bat
git clone https://github.com/moranasrabad/moeshakmusic.git
cd moeshakmusic
git checkout arena/01a05311-moeshakmusic
```

اگر Git نداری: در گیت‌هاب روی همان برنچ **Code → Download ZIP** بزن و از حالت فشرده دربیاور.

### ۳) کتابخانهٔ تلگرام (jniLibs)
اگر پوشهٔ زیر **سه فایل** `.so` دارد، این مرحله را رد کن:

`app/src/main/jniLibs/arm64-v8a/libtdjson.so`  
`app/src/main/jniLibs/armeabi-v7a/libtdjson.so`  
`app/src/main/jniLibs/x86_64/libtdjson.so`

اگر خالی بود، در Git Bash / ترمینال لینوکس/مک:

```bash
chmod +x setup-abis.sh
./setup-abis.sh
```

ویندوز بدون Git Bash: فایل را از این لینک دانلود کن، در `app/src/main/jniLibs` از حالت فشرده دربیاور، پوشهٔ `x86` را حذف کن:

https://github.com/up9cloud/android-libtdjson/releases/download/v1.8.65/jniLibs.tar.gz

### ۴) باز کردن پروژه
1. Android Studio → **Open** → پوشهٔ `moeshakmusic` (همان جایی که `settings.gradle` است).
2. صبر کن Gradle Sync تمام شود (پایین صفحه «Gradle sync finished»).
3. اگر پرسید JDK: **JDK 17** را انتخاب کن.  
   `File → Settings → Build → Gradle → Gradle JDK = 17`

### ۵) ساخت APK امضاشده
از منو:

**Build → Generate Signed Bundle / APK → APK → Next**

اگر خواست keystore:

| فیلد | مقدار |
|---|---|
| Key store path | `app/moeshak.keystore` |
| Password | `moeshak1404` |
| Key alias | `moeshak` |
| Key password | `moeshak1404` |

variant را **release** بگذار → **Finish**.

یا ساده‌تر از ترمینال داخل Studio (پایین، تب Terminal):

```bat
gradlew.bat assembleRelease
```

اگر `gradlew.bat` نبود (این پروژه wrapper ندارد)، از روش ۲ استفاده کن یا در Studio بزن:

**Build → Build Bundle(s) / APK(s) → Build APK(s)**

چون در `app/build.gradle` هم debug و هم release با همان keystore امضا می‌شوند، APK ساخته‌شده قابل نصب روی گوشی است.

### ۶) پیدا کردن فایل
بعد از موفقیت:

`app\build\outputs\apk\release\app-release.apk`

این را به گوشی بفرست (تلگرام، کابل، درایو).

---

## روش ۲ — خط فرمان ویندوز (بدون کلیک در Studio)

### پیش‌نیاز
- JDK 17 → `JAVA_HOME` مثلاً `C:\Program Files\Eclipse Adoptium\jdk-17...`
- Android SDK → معمولاً  
  `C:\Users\YOURNAME\AppData\Local\Android\Sdk`
- Gradle **7.6.3** یا **7.6.4**:  
  https://services.gradle.org/distributions/gradle-7.6.4-bin.zip  
  از حالت فشرده دربیاور؛ پوشهٔ `bin` را به PATH اضافه کن.

PowerShell (به‌جای مسیرها مال خودت را بگذار):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.13+11"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;C:\gradle-7.6.4\bin;$env:Path"

# فایل sdk
Set-Content -Path .\local.properties -Value "sdk.dir=$($env:ANDROID_HOME -replace '\\','\\')"
# اگر sdk.dir با یک اسلش کار نکرد، همین را دستی بنویس:
# sdk.dir=C:\\Users\\NAME\\AppData\\Local\\Android\\Sdk

java -version
gradle -v

cd مسیر\moeshakmusic
gradle assembleRelease --no-daemon
```

خروجی: `app\build\outputs\apk\release\app-release.apk`

اگر فقط گوشی ۶۴بیتی جدید داری (حجم کمتر):

```powershell
gradle assembleRelease -Pslim --no-daemon
```

---

## روش ۳ — لینوکس / مک

```bash
# JDK 17
java -version    # باید 17 باشد

# SDK
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager "platforms;android-33" "build-tools;33.0.2"

cd moeshakmusic
echo "sdk.dir=$ANDROID_HOME" > local.properties

# اگر jniLibs نبود:
chmod +x setup-abis.sh && ./setup-abis.sh

# Gradle 7.6.4 در PATH
gradle assembleRelease --no-daemon
ls -lh app/build/outputs/apk/release/app-release.apk
```

ایران: اگر `google()` یا `dl.google.com` timeout شد، VPN را روشن کن و دوباره بزن.

---

## نصب روی گوشی

1. APK را به گوشی بفرست.
2. فایل را باز کن.
3. اگر گفت «نصب از منابع ناشناس»: برای همان برنامه (Files / Telegram) اجازه بده.
4. نصب → باز کردن.
5. ورود پیشنهادی: **QR**  
   تلگرام رسمی → تنظیمات → دستگاه‌ها → اتصال دستگاه دسکتاپ → اسکن.

نسخه داخل اپ باید **5.10.3** باشد.

---

## خطاهای رایج

| پیام | کار |
|---|---|
| `sdk.dir` / SDK location not found | `local.properties` بساز با مسیر SDK |
| `JAVA_HOME not set` / کلاس فایل ۵۵/۶۱ | JDK را ۱۷ کن، نه ۸ و نه ۲۱ اجباری |
| `Could not resolve com.android.tools.build:gradle` | اینترنت/VPN؛ مخزن Google باید باز باشد |
| `libtdjson.so not found` / UnsatisfiedLinkError | مرحلهٔ jniLibs را انجام بده |
| `Failed to lock ... tdlib` | اپ را کامل ببند، یک‌بار Force stop |
| APK نصب نمی‌شود «تداخل امضا» | نسخهٔ قبلی موشک را Uninstall کن بعد نصب کن |

---

## چه چیزی در این نسخه است (چک نصب)

- کتابخانه خالی → پیام خالی؛ اگر آهنگ باشد مثلاً «۳۰۰ موزیک»
- کشیدن به پایین = رفرش + خط در لاگ
- اسکن نوار پیشرفت دارد
- Now Playing دیگر با RadialGradient کرش نکند
- کاور موقع پخش پالس می‌زند

سورس همین تغییرات:  
https://github.com/moranasrabad/moeshakmusic/pull/1
