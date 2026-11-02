# Nexa on-screen icon recognition — staged implementation plan
Date: 2026-11-03
Base: `Development`
Branch: `feature/nexa-screen-icon-recognition-20261103`
Status: initial read-only milestone in development; Android APK/emulator/physical-device acceptance pending.

## Intent and approved design
Build Nexa's icon and screen-control engine while preserving Kotlin ownership of Android capabilities, React Native ownership of UI, existing action approval/journaling, and explicit user permissions. Scope originates in the 2026-11-03 comprehensive design agreed before implementation.

## Ground rules
- No new broad `QUERY_ALL_PACKAGES` permission.
- Never capture passwords or editable subtrees.
- No screen data sent to model/provider without a separate owner gesture.
- No automatic screen clicks, messages, purchases, calls or delete actions.
- No CI autoruns, main/release branch update or production deployment.
- Only package-visible launchable app searches; no bulk inventory or home-screen page claims.
- All icon results are observational, not authorizing execution.

## Task 1 — Define snapshot contract and tests
Files:
- `packages/agent-contracts/src/screen-icons.ts`
- `packages/agent-contracts/src/index.ts`
- `packages/agent-core/test/screen-icons.test.mjs`
Behavior: strict fixed icon vocabulary; sanitized labels, bounds, evidence and package; reject malformed/oversized/forged metadata.
Verify: `node --experimental-strip-types --test packages/agent-core/test/screen-icons.test.mjs`; `npm run typecheck` when full dependencies are available.

## Task 2 — Android native observation
Files:
- `ScreenIconClassifier.kt`
- `ScreenIconScan.kt`
- existing `ScreenAssistService.kt`
Behavior: owner arms 20s window; observe one external app window; cap at 180 accessibility nodes and 64 icons; collect only recognized icon actions or unlabeled ImageButtons, excluding editable/password nodes; one-time consume and 60s expiry. Reuse existing service and Settings consent, no gestures.
Verify: stand-alone Kotlin classifier JVM tests; Gradle `:app:compileDebugKotlin` against actual Android SDK; emulator with mock accessible UI.

## Task 3 — Searchable app catalogue and React Native bridge
Files:
- `NexaAppCatalogueModule.kt`, `DeviceControlPackage.kt`
- `apps/mobile/src/native/androidAppCatalogue.ts`
- `apps/mobile/src/native/androidIconRecognition.ts`
Behavior: search only owner-supplied 2–60 char app name, at most 12 matches; return package and label, never launcher page; validate bridge results; no launch, screenshot or action.
Verify: Android PackageManager visibility on Android 11+; denied/missing apps; React Native typecheck.

## Task 4 — Tools UI and permission language
Files: `apps/mobile/App.tsx`, `AndroidManifest.xml`, `strings.xml`
Behavior: read-only icon inspector, review and discard, searched app names; separate existing text-read flow unchanged.
Verify: mobile UI snapshots/emulator, lifecycle, rotation, background, permission revocation and previous voice/WhatsApp controls.

## Task 5 — Acceptance before interaction
Run baseline `npm ci && npm test && npm run typecheck` and Android Gradle compile, then manual Android emulator and physical-device tests. Confirm 0 unauthorized actions and no screen-provider transmission.
Instruments: examine password fields, secure windows, third-party views with labels, unlabeled ImageButtons, IME, TalkBack, multiwindow, app switching, permission off, missing/native module, stale result, process death.

## Next milestones, intentionally NOT implemented here
1. Opt-in screenshot fallback using OS screenshot authorization and protected-surface handling; never send screenshots without consent.
2. Recognition fusion / confidence scoring, system screenshot vision/OCR, icon-image preview and localization.
3. Fresh-snapshot target resolver with a separately approved one-time action token.
4. Node `performAction` first and tightly constrained gesture fallback, with exact target/window revalidation.
5. Native journal claim and observed-outcome verification; failure/restart idempotency.
6. Agent harness tool schemas, visible overlay approvals, explicit user-requested actions only.
7. Private APK, real-phone acceptance, optional staged rollout.

## Blockers / release gates
- The isolated development branch is not an accepted Android release.
- Kotlin Android module build and full React Native typecheck require project dependencies and SDK.
- No reliable icon-image/screenshot recognition yet.
- No agent-triggered cross-app action enabled; deliberately deferred.
