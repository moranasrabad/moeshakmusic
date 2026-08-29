#!/bin/bash
# دانلود کتابخانه‌های پیش‌ساخته TDLib 1.8.65 (پیش‌نیاز بیلد) — تیم موشک
cd "$(dirname "$0")"
curl -sSL -o /tmp/jniLibs.tar.gz https://github.com/up9cloud/android-libtdjson/releases/download/v1.8.65/jniLibs.tar.gz
rm -rf app/src/main/jniLibs && mkdir -p app/src/main/jniLibs
tar xzf /tmp/jniLibs.tar.gz -C app/src/main/jniLibs --strip-components=1
rm -rf app/src/main/jniLibs/x86
find app/src/main/jniLibs -name '*.so' -exec du -h {} \;
echo "DONE — حالا: gradle assembleRelease"
