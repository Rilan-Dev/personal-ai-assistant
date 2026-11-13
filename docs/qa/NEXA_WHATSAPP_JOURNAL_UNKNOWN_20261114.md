# Nexa WhatsApp one-time tap recovery — 2026-11-14

## Concrete defect

The reviewed WhatsApp row-click path reserved its durable encrypted `tap` claim before invoking an optional caller callback and the native UI verifier. If either of these, or any later pre/post-dispatch code, raised an exception, the outer catch returned a generic "WhatsApp changed" message **without settling the claimed journal outcome**. Subsequent dispatch was non-replayable because the durable tombstone was already reserved, but the diagnostic ledger could misleadingly remain `claimed` instead of `unknown`.

## Correction

`NexaWhatsAppChatActions.openApproved` tracks the unsettled action token immediately after a successful one-time claim. All normal exit paths now inspect whether `NexaUiActionJournal.finish` actually persisted their outcome. If an exception occurs after the claim, the handler best-effort cancels the transient UI verifier and settles the journal to **`unknown`**, explicitly requiring manual screen inspection. Failed journal persistence is reported as uncertainty; neither duplicate taps nor send actions are retried.

The existing independent owner-Pause-fenced *fresh external WhatsApp Accessibility tree*, same-target class/geometry/native-source validation, expiry, and **no Send** contract are unchanged.

## Verified evidence on exact source

- Feature SHA: `f6ad35c97633c85d5478ad8015194d4fa70ce73e`.
- Full Node tests: **492/492 pass**, including four targeted journal recovery checks; `%TEMP%\\nexa-whatsapp-unknown-node-f6ad35c.log`.
- TypeScript `npm run typecheck`: **pass**; `%TEMP%\\nexa-whatsapp-unknown-typecheck-f6ad35c.log`.
- Android arm64 app + AndroidTest assembly and portable Maestro JUnit build: **BUILD SUCCESSFUL**, `%TEMP%\\nexa-whatsapp-unknown-android-f6ad35c.log`.
- Isolated `.physicalqa2` APK, versionCode `2026111403` — SHA-256 `C304079D7DF35A83B5332FB245337A5F3BADBAD25F1292665654EF51CE668A62`; AndroidTest SHA-256 `9C7F1AEE3D01DE28C28B80E9513424BB6F18B169D286570A85DED40DDDA24CE2`.
- QA2 certificate fingerprint `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`, same isolated signer as prior physical QA APKs.
- Physical Infinix X6831 / Android 13: ADB connected; WhatsApp and WhatsApp Business installed. **Android Accessibility enabled-services setting was null**, and an independently running Maestro host and `dev.mobile.maestro.test` had Android UiAutomation registered. No real WhatsApp was operated, no Android grant was modified, and the new QA2 APK was not installed over an active test session during this evidence capture.

## Release gate still open

Run `NexaWhatsAppClaimRecoveryAndroidTest` and the combined Android safety matrix in an available isolated device/test slot. Real third-party WhatsApp row selection, independently verified header/composer and owner-reviewed unsent text-entry must be demonstrated after the owner enables Accessibility; never press Send or bypass OS grants automatically. Live voice/wake and production signing/rollback are separate NO-GO gates.

**Only development source/test changes.** No `main`, production services, existing consumer APK, primary phone app data, microphone or platform permissions modified.
