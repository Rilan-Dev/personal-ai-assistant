# Nexa Android UI, permission setup and APK update diagnosis

2026-11-01 engineering change; source-reviewed, subject to signed Android CI and
physical phone acceptance. Do not infer installed APK revision from the screenshot alone.

## Why the phone appears to show the previous UI

The supplied screenshot labels the application **Personal AI Assistant**, places
AI Configuration, Quick Settings, capability chips and speech controls on the
same scrolling screen, and displays wording that says there is no accessibility
or background microphone support.

The current GitHub Development code has a new Nexa AI title, four original
independent tabs (Chat, Voice, Tools, Settings), assistant role and screen
services, a separate background voice FGS and updated screen help. Therefore
the screenshot is consistent with an older installed APK or outdated runtime
bundle. Git history did not show a UI rollback.

The GitHub test-release workflow can generate a fresh temporary keystore when
repository signing secrets are not configured. Every such APK has a different
certificate. Android therefore rejects installing it over an older APK signed
with another certificate. Separately, the two release workflows historically
used independent github.run_number counters. An APK with a lower Android
versionCode cannot be installed as an update. Neither situation is a React
Native rollback. Both explain why a successfully built APK may not replace the
old installed application.

## Implemented changes

- Dedicated first-run Setup tab with visual status, explicit actions and a
  continue/skip path; once explicitly dismissed it stays dismissed in
  app-local preferences, with a persistent Setup navigation tab and Settings
  shortcut. No permission is automatically requested on launch.
- Seven distinguishable steps for RECORD_AUDIO + POST_NOTIFICATIONS, assistant
  role selection, Screen Assist Accessibility service, Launcher Navigation
  Accessibility service, AI provider, flashlight CAMERA, and approximate
  coarse location. Contact picking does not require blanket READ_CONTACTS.
- Native permission readback is authoritative; AppState resume refreshes
  status after OS screens. The app does not infer grants from intent dispatch.
- Dedicated Android App Settings link for previously denied permissions and
  fallback; owner can still use Chat with optional features declined.
- More readable midnight-dark setup surface, larger controls, explicit status
  descriptions and five-tab navigation; original screen spacing/typography and
  authorization boundaries retained.
- VersionName, versionCode, API level and build Git SHA are surfaced from the
  Android APK rather than inferred from a UI theme or a cloud repository.
- Release workflows embed exact commit SHA, use an epoch-second UTC Android
  versionCode, and include signing certificate fingerprint in build artifacts.
  This fixes independently counted workflow versions. It does not make
  different signing certificates update-compatible.

## Consent boundary

The user alone can accept Android runtime permission dialogs, choose Nexa as
default assistant, and enable each Accessibility service separately. No
automated permission or Accessibility grant, boot-time recording, hidden
location tracking or WhatsApp Send was added. In-app tool authorizations remain
session-only and action-approval controls remain separate.

## Install/update acceptance checklist

1. Read GitHub Actions release job conclusion and download the APK artifact
   for the exact source revision to be tested.
2. Compare the previous and new APK signing certificate SHA-256 fingerprints.
   A matching certificate and increasing versionCode allow an in-place update.
3. If the certificates differ, Android rejects the in-place update. **Do not
   uninstall before exporting/recording your existing API provider settings,
   credentials and necessary app data**, as uninstall clears private storage
   and Android Keystore access. Do not publish a secret or keystore in chat.
4. Provision a persistent, owner-controlled testing-release keystore in GitHub
   Actions secrets: PAA_TEST_RELEASE_KEYSTORE_B64,
   PAA_TEST_RELEASE_STORE_PASSWORD, PAA_TEST_RELEASE_KEY_PASSWORD and the
   PAA_TEST_RELEASE_KEY_ALIAS variable for future update-compatible APKs.
   This should be configured before broadly distributing builds. Do not switch
   production signing identities casually.
5. After installation, open Nexa > Setup and check installed app version/code
   and source revision. If the label still reads Personal AI Assistant, it is
   not the updated app binary.
6. Confirm each OS permission state and the actual opt-in flow on the phone:
   assistant selection, mic/notification denied/granted, two Accessibility
   services independently enabled, app permission settings, cancellation,
   preferences persistence, first-run skip and reopening from Settings.
7. Test on an actual Android device and OEM launcher before declaring release
   accepted. Emulator success does not prove the OEM permission experience.

## Explicit remaining limitations

This does not implement an unattended Hey Nexa wake word or a privileged
accessibility agent that sends WhatsApp autonomously. Always-on microphone
use and third-party app actions have separate device/OEM/consent acceptance
requirements. No real phone acceptance is claimed by source or emulator tests.
