# Nexa message-field journal and owner-consented WhatsApp inspection — 2026-11-14

## Defects addressed

1. The native reviewed messaging-composer flow could report a successful message-field verification even if the encrypted `NexaUiActionJournal.finish` transaction failed, and could omit a meaningful `unknown` outcome after an exception following a claimed one-shot text-entry token. That made the durable diagnostic status misleading even though the SQLite no-replay claim was already consumed.
2. Real WhatsApp acceptance needs an app-visible, privacy-limited inspection harness; it must **not** acquire privileged Android `UiAutomation` to work around an owner-disabled Accessibility service.

## Corrections

- `NexaMessageInputActions.confirm` now tracks the unsettled claimed token, checks durable `finish` persistence for *every* dispatched/paused path and reports uncertainty if the outcome cannot be written. The verified-contents result is returned as fully journal-backed only when `markObservation` persists; post-claim exceptions settle to `unknown` on a best-effort basis. **No Send action or retry is introduced.**
- `NexaMessageFillClaimRecoveryAndroidTest` tests the real Android encrypted claim store for `message_fill`, including invalid input digests, a claimed→unknown transition and rejection of replay or retroactive success. It uses a synthetic text digest; no messaging app or user contact is accessed.
- `NexaLiveWhatsAppReadOnlyAndroidTest` only allows a deliberately supplied, in-memory recipient hint and *Nexa's own connected, owner-granted* `ScreenAssistService.externalWindowRoot()`. It requires a foreground `com.whatsapp` window, performs read-only unique-row/header classification, and exposes only an aggregate candidate count and whether a header is already verified. No screenshots, raw chat labels, coordinates, text entry, UI automation, background capture, taps, or Send. **This opt-in test is not part of the synthetic suite** and should fail closed when Accessibility is disabled.

## Verified development evidence

- Feature production-native source at `acab56bd4bdea26005741b829a1a4fea781ebe1a`: full Node **504/504 PASS**, TypeScript **PASS**, arm64 QA2 Android debug + AndroidTest + portable Maestro Gradle **BUILD SUCCESSFUL**; logs `%TEMP%\\nexa-fill-live-node-acab56b.log`, `%TEMP%\\nexa-fill-live-ts-acab56b.log`, `%TEMP%\\nexa-fill-live-gradle-acab56b.log`.
- Later branch updates `b14afce`, `8b5575b` and `cdf9923` changed the live **test-only** consent implementation and its Node assertions; no production Java/Kotlin behavior was changed.
- After the consent-gated test rewrite, full Node regression **504/504 PASS** at `cdf992360389eeeb341b30c595175a94ba1ed5d5` (log `%TEMP%\\nexa-composer-consent-node-cdf992.log`); TypeScript **PASS** (log `%TEMP%\\nexa-composer-consent-ts-cdf992.log`). This confirms the final source/test behavior independently of the earlier arm64 build.
- x86_64 Android 37 isolated QA2 app + AndroidTest assembly **BUILD SUCCESSFUL** at `cdf9923` (log `%TEMP%\\nexa-fill-consent-x86-cdf992.log`); package `dev.rilandev.personalaiassistant.physicalqa2` versionCode `2026111409`. App APK SHA-256 `8DAC61BBFCAF0AF23232B3B7AB42AC61CE9D9CA8F9D807BB721E587C52A23A9B`; AndroidTest APK SHA-256 `D974309645D270528F42FC9BE665704D39B7CEC4C6EBD310E8A55DF20DF7FA69`; test signer SHA-256 `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`. Both APKs were successfully installed on **emulator-5554**, not on the owner's phone.
- **Do not claim a clean emulator QA pass.** The 9-class combined run executed **28 tests with 8 failures**, all from UI fixture classes whose Android Accessibility root was obscured by an `Application Not Responding: com.android.systemui` system dialog; log `%TEMP%\\nexa-composer-emulator-matrix-cdf992.log`. The 8-case fixture retry crashed its instrumentation process; a separate focused retry later met the same System UI ANR and was cancelled. The emulator was restarted once without wiping app data but still exhibited unstable System UI. Only the other 20 tests finished without a reported failure in the first run. Real-world WhatsApp navigation and fresh standalone on-device journal acceptance remain **not certified** until a stable device session is available.
- The diagnostic run intentionally did **not** invoke the owner-consent-gated `NexaLiveWhatsAppReadOnlyAndroidTest`, because no owner-granted QA2 Accessibility/foreground WhatsApp combination was present.
- High-severity npm audit: PASS on the working feature code (`%TEMP%\\nexa-fill-audit-acab56b.log`).

## Physical device status

BridgeHead detected Infinix X6831 / Android 13. Early in this session, Android listed an enabled Nexa QA2 Accessibility service; later it reported `enabled_accessibility_services=null`, the default assistant returned to Google, and the foreground screen moved among third-party apps including Google Maps. **No permission/role change, forced WhatsApp foreground switch, microphone capture, contact search or primary APK installation was attempted.**

The owner-consented real WhatsApp recipient/header/composer run is therefore **NOT VERIFIED**. Never substitute privileged ADB/instrumentation screen capture for explicit Android Accessibility authorization.

## Release boundaries

This development fix does not certify live WhatsApp text-entry, model-authorized actions, Sarvam speech streaming/wake word, consumer release signing, rollback, iOS/macOS or production. The primary app's installed signer differs from the pinned permanent release signer; no migration or deletion is authorized. `main` and production services remain unchanged.
