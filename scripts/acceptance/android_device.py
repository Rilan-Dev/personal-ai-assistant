#!/usr/bin/env python3
"""Owner-run Android acceptance kit. Requires one connected ADB device.

Does not read messages, contacts, photos, mail, audio, or other private data.
Manual outcomes are entered by the device owner; nothing is falsely passed.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import subprocess
import sys

PACKAGE = "dev.rilandev.personalaiassistant"
CASES = [
    ("navigation", "Open Chat, Voice, Tools and Settings bottom tabs. Each occupies its own scrollable screen; no overlapping status bar, tab bar or buttons."),
    ("mobile_keyboard", "On Chat and Settings, open the keyboard, type text, rotate if supported, use Android Back. Verify composer and API fields remain usable."),
    ("settings_key", "In Settings > AI Configuration, verify masked API key, provider/model choices and optional Test connection with your own key."),
    ("permissions_deny", "With CAMERA and RECORD_AUDIO denied, flashlight and voice requests must be blocked."),
    ("permission_grant", "Use in-app buttons to grant CAMERA and RECORD_AUDIO. No automatic OS consent dialog."),
    ("voice_transcript", "Tap to talk; speak harmless phrase. Verify editable input changes; no automatic Send or tools."),
    ("voice_cancel", "Stop listening; background/restore app. Verify microphone not active."),
    ("offline_tts", "Read reply aloud. Offline-only voice plays or fails safely if not installed."),
    ("tile_lock", "Tap Quick Settings assistant tile while locked. Unlock required; no recording."),
    ("battery", "Check battery level; compare result against Android battery status."),
    ("flash_denial", "Without in-app flashlight grant, Turn flashlight on must not execute."),
    ("flash_approval", "Grant flashlight, approve once, resume, verify LED. Skip if phone has no flash."),
    ("flash_replay", "Force stop/reopen after accepted action. Confirm no automatic torch action, and completed task has no Resume control."),
    ("journal_training", "Test approval command. Approve once and Resume: no device action, no replay."),
    ("live_voice_denied", "Voice tab: deny mic/notification access. Live session must fail closed and never start without your explicit Start."),
    ("live_voice_session", "If you have an OpenAI API key, explicitly start live voice. Verify system microphone indicator, audible response and Stop notification. Mark SKIP if no compatible provider."),
    ("live_voice_background", "While user-started live voice is active, switch apps; verify indicator and visible Stop notification, then Stop. Do not expect hidden always-on recording."),
    ("screen_access_off", "Tools > Screen assistant: without Android Accessibility permission, Arm one screen read must be denied."),
    ("screen_capture_once", "Enable screen Accessibility; arm one read, switch to a harmless app, return, review one bounded text snapshot and discard. Never automatically share it."),
    ("screen_sensitive", "Verify password/editable field contents are not captured; if uncertain mark FAIL and report without sharing private screen text.")

]

def adb(*args, timeout=12):
    r = subprocess.run(["adb", *args], text=True, stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, timeout=timeout, check=False)
    return r.returncode, r.stdout.strip(), r.stderr.strip()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--check", action="store_true", help="Check device and APK only.")
    p.add_argument("--walkthrough", action="store_true", help="Open app and ask for real test results.")
    args = p.parse_args()
    if not args.check and not args.walkthrough:
        p.print_help()
        return 2
    if shutil.which("adb") is None:
        print("ADB missing: install Android SDK platform-tools.", file=sys.stderr)
        return 2
    code, devices, _ = adb("devices")
    if code:
        print("ADB could not discover devices.", file=sys.stderr)
        return 2
    authorized = [line for line in devices.splitlines()[1:] if line.strip().endswith("\tdevice")]
    if len(authorized) != 1:
        print("Connect exactly one authorized Android phone; count: " + str(len(authorized)),
              file=sys.stderr)
        return 2
    code, api, _ = adb("shell", "getprop", "ro.build.version.sdk")
    if code or not api.isdecimal():
        print("Could not query Android API level.", file=sys.stderr)
        return 2
    code, installed, _ = adb("shell", "pm", "path", PACKAGE)
    if code or "package:" not in installed:
        print("App not installed. Install current debug APK before testing.", file=sys.stderr)
        return 2
    print("Authorized phone detected; Android API " + api + ". App installed.")
    print("No device serial or personal content is stored by this script.")
    if args.check:
        return 0
    if int(api) < 31:
        print("Android below API 31: strict on-device STT is unavailable.")
    print("Use the newest signed RELEASE-TEST or DEBUG APK. Never uninstall between encrypted journal tests.")
    if input("Open assistant foreground app? [yes/no]: ").strip().lower() != "yes":
        print("Cancelled.")
        return 2
    code, _, _ = adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    if code:
        print("Could not open MainActivity.", file=sys.stderr)
        return 2
    results = {}
    for key, instruction in CASES:
        print("\n[" + key + "] " + instruction)
        while True:
            reply = input("Observed outcome [pass/fail/skip]: ").strip().lower()
            if reply in ("pass", "fail", "skip"):
                results[key] = reply
                break
            print("Enter pass, fail or skip. This script does NOT infer outcomes.")
    now = datetime.now(timezone.utc)
    path = Path("device-acceptance-" + now.strftime("%Y%m%d-%H%M%S") + ".json")
    path.write_text(json.dumps({
        "schema":1, "checked_at_utc":now.isoformat(),
        "android_api":int(api), "package":PACKAGE,
        "evidence":"owner-manually-entered", "cases":results
    }, indent=2) + "\n", encoding="utf-8")
    print("Saved anonymized local report:", path)
    print("PASS",sum(x=="pass" for x in results.values()),
          "FAIL",sum(x=="fail" for x in results.values()),
          "SKIP",sum(x=="skip" for x in results.values()))
    return 0 if all(x=="pass" for x in results.values()) else 1

if __name__ == "__main__":
    sys.exit(main())
