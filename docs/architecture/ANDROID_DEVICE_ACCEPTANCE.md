# Physical Android acceptance — owner-run test kit

GitHub Android emulator CI validates Kotlin compilation, Android Keystore
persistence, journal integrity and replay claims. It does NOT validate actual
phone flashlight hardware, OEM speech engines, the locked Quick Settings UI,
microphone permission dialogs or the owner's installed language packs.

The local acceptance kit is pure Python standard library plus ADB, without
a third-party testing provider or a new privileged Android agent.

## Setup (Windows, macOS or Linux)
1. From the GitHub Actions Android debug compile workflow of the SAME
   verified commit, download the personal-ai-assistant-debug-apk artifact.
2. Install the APK on an Android phone you control. If a different debug
   signing key causes an UPDATE_INCOMPATIBLE error, uninstalling an older APK
   erases encrypted journal history. Do not uninstall between recovery tests.
3. Enable USB debugging and connect exactly one phone with USB cable.
4. Install Android SDK platform-tools and put adb on your PATH.
5. Run from repository root on Windows:
     py scripts/acceptance/android_device.py --check
     py scripts/acceptance/android_device.py --walkthrough
   On Linux/macOS use python3 instead of py.
6. On Android 12 or newer, use the in-app Test approval, battery, camera,
   flashlight and voice controls. The script prompts for PASS/FAIL/SKIP.

The script does not grant camera/microphone permissions, toggle the flash,
read contacts, capture audio, inspect messages or send communications.
After explicit confirmation it opens MainActivity in the foreground and asks
the owner to record observed outcomes. Reports have no device serial or user
data and are excluded from Git by .gitignore.

## Release gate
- The newest Android emulator workflow and APK build for the same commit
  must pass. An outdated APK does not establish current-build acceptance.
- Owner-entered physical phone results are not independently verified by CI;
  a reviewer must inspect the physical-device evidence before signoff.
- A skipped or failed case is not accepted as a passing release gate.
- Live provider model acceptance is independently gated on user-authorized
  credentials. Emulator/mock checks do not substitute for real model calls.

## Important on-device replay distinction
The UI intentionally does not expose Resume for a fully completed task. After
force-stop and reopening, confirm there is no automatic torch control and no
Resume button for the completed task. To evaluate a truly interrupted claim,
use the emulator action-ledger instrumentation tests, or a controlled custom
debug test harness; do not try to race a subsecond flashlight operation by
force-stopping a production device at random.

## Mobile UX, live voice and screen-read acceptance
The owner test kit now includes all four bottom tabs, keyboard-safe composer,
AI provider Settings, Android back navigation, Live Voice opt-in/denial,
system notification Stop and user-armed screen reading. Never run a live
microphone test in an environment where bystanders expect privacy. The live
stream goes to the owner's OpenAI API and may incur usage fees. During screen
tests choose a harmless public app and do not upload account, OTP or
personal message text to a model or bug report.
