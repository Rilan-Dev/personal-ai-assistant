#!/usr/bin/env bash
# Capture REAL installed Nexa Android UI from the ephemeral CI emulator.
# Never requests or grants Android permissions. No accounts or messages.
set -euo pipefail
app="dev.rilandev.personalaiassistant"
out="artifacts/nexa-ui-screenshots"
mkdir -p "$out"
echo "Source revision: ${GITHUB_SHA:-local}" > "$out/README.txt"
echo "Device: Android emulator; fresh synthetic data; no permissions granted" >> "$out/README.txt"
echo "Screenshots here are emulator renders, NOT physical-phone/OEM acceptance." >> "$out/README.txt"
if [[ "$#" -ne 1 || ! -s "$1" ]]; then
  echo "Pass the exact bundled, signed release APK" >&2
  exit 1
fi
release_apk="$1"
sha256sum "$release_apk" > "$out/captured-release-apk-sha256.txt"
# Previous instrumentation APK uses a disposable DEBUG signer.
# This is a synthetic CI emulator: uninstall it before installing RELEASE.
adb uninstall "$app" >/dev/null || true
adb install "$release_apk" > "$out/install.txt"
adb shell pm path "$app" > "$out/installed-package.txt"
adb shell am force-stop "$app" || true
adb shell pm clear "$app" >/dev/null
adb shell am start -W -n "$app/.MainActivity" > "$out/launch.txt"
sleep 5
if ! adb shell pidof "$app" >/dev/null; then
  echo "Nexa did not survive startup" >&2
  adb logcat -d -s AndroidRuntime:E > "$out/startup-android-runtime.log" || true
  exit 1
fi

capture() {
  local name="$1"
  # Use adb exec-out, not a screenshot mockup or a prior build's captured image.
  adb exec-out screencap -p > "$out/$name.png"
  adb shell uiautomator dump /sdcard/nexa-audit.xml >/dev/null
  adb pull /sdcard/nexa-audit.xml "$out/$name.xml" >/dev/null
  test -s "$out/$name.png"
  test -s "$out/$name.xml"
}

select_tab() {
  local tab="$1"
  adb shell uiautomator dump /sdcard/nexa-audit.xml >/dev/null
  adb pull /sdcard/nexa-audit.xml "$out/before-$tab.xml" >/dev/null
  local tap
  tap="$(python3 - "$out/before-$tab.xml" "$tab tab" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
label=sys.argv[2].lower()
for node in root.iter('node'):
  if node.get('content-desc','').lower()==label:
    bounds=node.get('bounds','')
    match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',bounds)
    if match:
      x1,y1,x2,y2=map(int,match.groups())
      print((x1+x2)//2,(y1+y2)//2)
      raise SystemExit(0)
print(f'Could not locate accessibility tab: {label}',file=sys.stderr)
raise SystemExit(1)
PY
)"
  read -r x y <<< "$tap"
  adb shell input tap "$x" "$y"
  sleep 2
}

# First-run setup is an important screenshot because the user reported missing grants.
capture "01-first-run-setup"
for item in chat voice tools setup settings; do
  select_tab "${item^}"
  # Deterministic, flat filenames; no accidental nested directories.
  capture "tab-$item"
done
echo "Captured five tab screenshots and first-run setup from the installed APK." >> "$out/README.txt"
