# Nexa Screen Assist — runtime acceptance and Maestro diagnostic boundary

## Evidence basis (2026-11-04)

- Production-signed APK pipeline [#41713277085](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41713277085) succeeded: native emulator tests, release build, release signature and APK artifact.
- Actual Maestro Cloud [#41716296413](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41716296413) **failed** on `No visible element found: "Nexa Screen Assist connected.*native screen actions ready"`. This happened before the requested notification shade operation; an APK build pass did not validate Accessibility execution.
- The near-simultaneous Maestro Cloud [#41716297109](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41716297109) was stopped/cancelled, not a passing end-to-end run.
- Android's documented `UiAutomation` default behavior suppresses independent Accessibility services unless `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` is used. Maestro's public Android driver obtains `instrumentation.uiAutomation` with default flags. Therefore these Maestro runs may be incapable of testing whether Nexa's real Screen Assist service remains live during automation. **This environment interference is strongly supported by source, but not yet confirmed with device-side `dumpsys accessibility` from the failed Cloud test.**
- Android 13+ may also restrict Accessibility enablement for side-loaded APKs. Owner must grant any restricted setting through the actual Android Settings UI; Nexa must not bypass the OS.

## Source hardening in this branch

1. On a resumed app, query Android's real Screen Assist connection state repeatedly for a bounded window (approximately six seconds), stopping when connected, disabled, app backgrounds, or the retry limit expires. Never guess that the service is ready.
2. On service unbind, invalidate live readiness only for the currently active service instance; avoid leaving stale Ready or clearing a replacement instance.
3. Retain the test's strict requirement for visible notification shade evidence. Add snapshots at permission-not-ready states instead of converting an objective failure into success.

## Owner-controlled physical-device acceptance

Run on the installed **signed APK from the exact source commit**. Do not reinstall with a different signing key or erase `custom.db`. Connect the phone to Windows over an authorized USB debugging session.

1. In PowerShell, from the repository root, run `powershell -ExecutionPolicy Bypass -File scripts/acceptance/nexa-screen-assist-device.ps1 -Phase Before` (or `-Serial <device-id>` when more than one phone is attached). This collects **read-only** local service, activity and crash evidence.
2. On the phone, open **Nexa → Setup → One-time screen assistance → Open Accessibility**. Enable **Nexa Screen Assist** and confirm through Android. If Android says **Restricted setting**, only if you trust the signed APK: go to **Android App info → More options → Allow restricted settings**, authenticate as required and return to enable the service. Do not use `adb shell settings put secure`, app-ops, root or test-only permission injection.
3. Return to Nexa Setup. Check the real status: **Nexa Screen Assist connected · native screen actions ready**. Press **Refresh status** if needed. A disabled or disconnected status is NOT a pass.
4. With permission legitimately enabled, open the floating Nexa bubble, request **open notification bar**, review and tap **Confirm requested action**. Verify the expanded Nexa window disappears and the actual Android System UI notification shade appears. **Android accepted** alone is not proof.
5. Close the notification shade, try the intended user-visible screen-reading and tap/scroll actions on an ordinary app, and confirm the correct target changed. Capture any repeatable crash with logcat. Avoid sensitive content and do not send messages or calls during this test.
6. Run `powershell -ExecutionPolicy Bypass -File scripts/acceptance/nexa-screen-assist-device.ps1 -Phase After`. Compare before/after service binding and crash evidence. The files remain local unless the owner chooses to share them.

This is separate from Maestro smoke tests for onboarding, navigation and UI labels. **Never declare real Screen Assist acceptance PASS from a suppressed-service Maestro environment**, a source grep test or a successful APK build. If the actual phone still fails after a confirmed live service, inspect device-native stack traces, app foreground/background events and UI action verifier logs before changing permissions or overlay code further.

## Release gate

**Source passes:** npm tests, TypeScript and native build. **Real user flow:** remains NO-GO until native Accessibility connected, owner-approved action executed, result visually observed, zero crash evidence, and the target device verified. Prevent automatic message sends and preserve the existing user consent gates.
