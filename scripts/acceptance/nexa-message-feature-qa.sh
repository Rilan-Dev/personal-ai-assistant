#!/usr/bin/env bash
# Disposable GitHub emulator only; no production release key or real phone.
set -euo pipefail
ROOT="$(cd "$(dirname "$BASH_SOURCE")/../.." && pwd)"
ANDROID_DIR="$ROOT/apps/mobile/android"
cd "$ANDROID_DIR"
echo "Native feature source: $(git -C "$ROOT" rev-parse HEAD)"
test -f ./gradlew
bash ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace

# Debug builds need Metro. Only a bundled APK can run RN on Maestro Cloud.
# Ephemeral PKCS12 identity is not the real production signing certificate.
export PAA_TEST_APK_KEY_ALIAS="paatest"
export PAA_TEST_APK_STORE_PASSWORD="$(openssl rand -hex 24)"
export PAA_TEST_APK_KEY_PASSWORD="$PAA_TEST_APK_STORE_PASSWORD"
KEYSTORE="$ANDROID_DIR/app/testing-release.keystore"
trap 'rm -f "$KEYSTORE"' EXIT
keytool -genkeypair -storetype PKCS12 \
  -keystore "$KEYSTORE" -alias "$PAA_TEST_APK_KEY_ALIAS" \
  -storepass "$PAA_TEST_APK_STORE_PASSWORD" \
  -keypass "$PAA_TEST_APK_KEY_PASSWORD" \
  -keyalg RSA -keysize 2048 -validity 3 \
  -dname "CN=Nexa Disposable Emulator QA,O=Personal AI Assistant,C=US" -noprompt
bash ./gradlew :app:assembleRelease --no-daemon --stacktrace \
  -Pandroid.enableMinifyInReleaseBuilds=false \
  -Pandroid.enableShrinkResourcesInReleaseBuilds=false
APK="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
test -s "$APK"
APKSIGNER="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name apksigner | sort -V | tail -n1)"
test -n "$APKSIGNER"
"$APKSIGNER" verify "$APK"
sha256sum "$APK"
adb install -r "$APK"
adb shell am force-stop dev.rilandev.personalaiassistant || true
adb shell pm clear dev.rilandev.personalaiassistant || true
adb logcat -c || true
adb shell am start -W -n dev.rilandev.personalaiassistant/.MainActivity
sleep 8
if ! adb shell pidof dev.rilandev.personalaiassistant >/dev/null; then
  echo "::error::Disposable Nexa app process exited after launch."
  adb logcat -d -s AndroidRuntime:E | tail -n 90
  exit 1
fi
adb shell uiautomator dump /sdcard/nexa-startup-ui.xml || true
adb pull /sdcard/nexa-startup-ui.xml ./nexa-startup-ui.xml || true
adb shell screencap -p /sdcard/nexa-startup-ui.png || true
adb pull /sdcard/nexa-startup-ui.png ./nexa-startup-ui.png || true
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path("nexa-startup-ui.xml")
if not p.exists():
    raise SystemExit("Unable to inspect emulator startup UI")
root=ET.fromstring(p.read_bytes())
labels=[n.attrib.get("text") or n.attrib.get("content-desc")
  for n in root.iter("node")
  if n.attrib.get("text") or n.attrib.get("content-desc")]
print("EMULATOR_STARTUP_UI_LABELS:",labels[:35])
assert any("Nexa" in x or "Make your assistant yours" in x for x in labels), \
    "Nexa initial UI not visible after standalone APK startup"
PY
echo "Disposable bundled feature APK passed emulator startup inspection."
