#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-/usr/local/android-sdk}"
echo "▸ ติดตั้ง Android SDK ที่ $SDK_ROOT"

sudo mkdir -p "$SDK_ROOT"
sudo chown -R "$(id -u):$(id -g)" "$SDK_ROOT"

# ── ค้นหาเลขรุ่นล่าสุดของ cmdline-tools จาก repository manifest ──
# หลักการ: แทนที่จะ hard-code build number ซึ่งเปลี่ยนบ่อย
#          ให้ query จากแหล่งข้อมูลทางการแล้ว fallback หากล้มเหลว
ZIP_NAME="$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml 2>/dev/null \
  | grep -oE 'commandlinetools-linux-[0-9]+_latest\.zip' | tail -1 || true)"
[ -z "$ZIP_NAME" ] && ZIP_NAME="commandlinetools-linux-11076708_latest.zip"
echo "▸ ใช้ไฟล์: $ZIP_NAME"

cd /tmp
curl -fsSL -o cmdline.zip "https://dl.google.com/android/repository/${ZIP_NAME}"
rm -rf /tmp/ct && unzip -q cmdline.zip -d /tmp/ct
mkdir -p "$SDK_ROOT/cmdline-tools/latest"
mv /tmp/ct/cmdline-tools/* "$SDK_ROOT/cmdline-tools/latest/"
rm -rf /tmp/ct cmdline.zip

export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$PATH"
mkdir -p "$HOME/.android" && touch "$HOME/.android/repositories.cfg"

echo "▸ ยอมรับ license"
yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses > /dev/null 2>&1 || true

echo "▸ ติดตั้ง platform + build-tools"
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0" > /dev/null

echo "sdk.dir=$SDK_ROOT" > "$(dirname "$0")/../local.properties"
echo "✅ Android SDK พร้อมใช้งาน"
