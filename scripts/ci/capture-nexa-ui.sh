#!/usr/bin/env bash
# Capture REAL installed Nexa Android UI from the ephemeral CI emulator.
# Never requests or grants Android permissions. No accounts or messages.
set -euo pipefail
root="$(cd "$(dirname "$BASH_SOURCE")/../.." && pwd)"
# A real phone's package data must never be cleared by the CI screenshot kit.
source "$root/scripts/acceptance/require-synthetic-emulator.sh"
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

tap_accessible() {
  local label="$1"
  local slug="$2"
  adb shell uiautomator dump /sdcard/nexa-audit.xml >/dev/null
  adb pull /sdcard/nexa-audit.xml "$out/before-$slug.xml" >/dev/null
  local tap
  tap="$(python3 - "$out/before-$slug.xml" "$label" <<'PY'
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
print(f'Could not locate accessible control: {label}',file=sys.stderr)
raise SystemExit(1)
PY
)"
  read -r x y <<< "$tap"
  adb shell input tap "$x" "$y"
  sleep 2
}
# First-run setup is an important screenshot because the user reported missing grants.
# Android cloud emulators can show a Pixel Launcher ANR directly above Nexa
# while instrumentation, release packaging and UIAutomator compete for CPU.
# Never record the system-error dialog as a successful Nexa screenshot.
# A stock OS "Wait" response is safe; it grants no Android app permissions.
wait_for_nav_ui() {
  local snapshot="$out/readiness-last.xml"
  local status
  local attempt
  for attempt in $(seq 1 20); do
    timeout 20 adb shell uiautomator dump /sdcard/nexa-audit.xml >/dev/null 2>&1 || true
    adb pull /sdcard/nexa-audit.xml "$snapshot" >/dev/null 2>&1 || true
    if [[ -s "$snapshot" ]]; then
      status="$(python3 - "$snapshot" <<'PY'
import sys,xml.etree.ElementTree as ET,re
try:
  nodes=list(ET.parse(sys.argv[1]).iter("node"))
except (ET.ParseError,OSError):
  print("pending")
  raise SystemExit(0)
labels=[((n.get("text") or "")+" "+(n.get("content-desc") or "")).lower() for n in nodes]
if any((n.get("content-desc") or "").strip().lower()=="chat tab" for n in nodes):
  print("ready")
elif any("pixel launcher isn't responding" in text for text in labels):
  for n in nodes:
    if (n.get("text") or "").strip().lower()=="wait":
      match=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.get("bounds") or "")
      if match:
        x1,y1,x2,y2=map(int,match.groups())
        print(f"wait:{(x1+x2)//2}:{(y1+y2)//2}")
        break
  else:
    print("launcher_anr")
else:
  print("pending")
PY
)"
      case "$status" in
        ready) return 0 ;;
        wait:*)
          echo "Android emulator launcher ANR detected; choosing stock Wait option." >> "$out/README.txt"
          IFS=: read -r _ x y <<< "$status"
          adb shell input tap "$x" "$y"
          ;;
        launcher_anr)
          echo "Android emulator launcher ANR has no visible Wait action." >> "$out/README.txt"
          ;;
      esac
    fi
    if ! adb shell pidof "$app" >/dev/null 2>&1; then
      echo "Nexa process exited before the tab UI became visible" >&2
      adb logcat -d -s AndroidRuntime:E > "$out/startup-android-runtime.log" || true
      return 1
    fi
    sleep 3
  done
  echo "The Nexa navigation never became visible; no fake screenshot pass." >&2
  adb logcat -d -s AndroidRuntime:E ActivityManager:E > "$out/startup-android-runtime.log" || true
  return 1
}


# Capture setup and current compact three-tab navigation.
wait_for_nav_ui
capture "01-first-run-setup"
for item in chat voice tools; do
  wait_for_nav_ui
  tap_accessible "${item^} tab" "tab-$item"
  capture "tab-$item"
done
tap_accessible "Open settings" "settings"
capture "tab-settings"
tap_accessible "Open permission setup" "setup"
capture "tab-setup"
# Reveal optional permissions without changing any Android permission state.
# Initial Setup is vertically scrollable; make the expansion control visible.
adb shell input swipe 510 1540 510 820 380
sleep 1
tap_accessible "Show optional permissions and features" "optional-permissions"
capture "tab-setup-optional-expanded"
echo "Captured first-run Setup, 3 bottom destinations and 2 header/shortcut destinations from the signed APK." >> "$out/README.txt"
