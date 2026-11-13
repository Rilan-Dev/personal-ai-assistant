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
- Current branch head and fresh final whole-suite, x86 emulator Kotlin compilation and instrumentation results must be checked before merging. Never treat an in-progress build as passed.
- High-severity npm audit: PASS on the working feature code (`%TEMP%\\nexa-fill-audit-acab56b.log`).

## Physical device status

BridgeHead detected Infinix X6831 / Android 13. Early in this session, Android listed an enabled Nexa QA2 Accessibility service; later it reported `enabled_accessibility_services=null`, the default assistant returned to Google, and the foreground screen moved among third-party apps including Google Maps. **No permission/role change, forced WhatsApp foreground switch, microphone capture, contact search or primary APK installation was attempted.**

The owner-consented real WhatsApp recipient/header/composer run is therefore **NOT VERIFIED**. Never substitute privileged ADB/instrumentation screen capture for explicit Android Accessibility authorization.

## Release boundaries

This development fix does not certify live WhatsApp text-entry, model-authorized actions, Sarvam speech streaming/wake word, consumer release signing, rollback, iOS/macOS or production. The primary app's installed signer differs from the pinned permanent release signer; no migration or deletion is authorized. `main` and production services remain unchanged.
