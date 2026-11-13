# Nexa × Maestro — bounded scroll settling and signer preflight (2026-11-13)

## Scope and provenance

Branch: `feature/nexa-maestro-scroll-settle-20261113`, based on `Development@efd7b982ea78e8fbdc7f9474afa40f5ece070322` (which already includes merged Maestro PR #117). No production main changes. No new Maestro desktop driver or permission escalation.

The earlier PR #117 added one-time owner-reviewed scroll search and safe hierarchical taps. This increment fixes **post-scroll timing and final-dispatch races**, while preserving the same owner approval, Pause generation and encrypted action journal.

## Changes

1. `NexaTaskScreenObservation.exactVisibleControl` returns a short **ephemeral geometry key** only for a single unique safe target. It is never stored, transmitted, or interpreted as approval.
2. `NexaBoundedScrollSearch.onEvidence` requires two matching observations after an Android-reported scroll event, at least 250 ms apart. An ambiguous label, changed hitbox, missing key, truncated screen or expired token cannot yield FOUND or schedule a next owner approval. No automatic gesture/retry is added.
3. `NexaToolOverlayService` forwards the key, allows four bounded observational polls within the existing 4.5s evidence deadline, and reports settled observation rather than merely an event.
4. `NexaScreenScroll.confirm` validates the unique scrollable node and its currently supported action again **inside** the Pause-fenced final dispatch, using a newly acquired Accessibility window; stale nodes, incomplete trees, changed app/window/bounds, or unsupported actions fail closed. Only one actual `performAction` is possible.
5. `verify-installed-nexa-signer.ps1` contained 11 PowerShell parser errors due to an interrupted legacy certificate predicate. The predicate was repaired without changing its release-signing policy, and a Windows parser check was added to the Node regression suite.
6. The pre-existing Windows Git Bash fake-ADB test timeout was increased from 5 seconds to 15 seconds to accommodate concurrent worker startup. No emulator-only restrictions or actual device commands were relaxed.

## Evidence (Windows BridgeHead development host)

| Check | Result | Notes |
|---|---|---|
| Full Node regression | **460 / 460 PASS** | Exact branch `69492b7d691eb93eff481186b0bf9fb797467100`, `%TEMP%\\nexa-scroll-final-69492b7.log`; concurrency=3 |
| TypeScript typecheck | PASS | `npm run typecheck`, after native-source changes |
| Web export | PASS | `npm run web:export` |
| Android portable Maestro JVM unit tasks and debug/app-instrumentation APK assembly | PASS | `:maestro-portable:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --offline`, source revision `879759d041d7118436f2f183f07dab3bb3f6fb5d` |
| PowerShell release preflight syntax | **0 parser errors** | Dedicated Windows test executes `Parser.ParseFile`; previously **11 errors** |
| Bounded scroll Node source tests | PASS | Includes review/dispatch uniqueness and target-settling invariant |
| Android instrumentation against new APK | **NOT RUN** | Android API-37 emulator appeared unauthorized and its headless process exited. Owner's Infinix APK was not replaced. No synthetic device result claimed for this exact new revision |
| Real third-party scroll, WhatsApp composer, Sarvam live call | **NOT VERIFIED** | Screen Assist and overlay permissions and real authorized app state remain separate owner/device acceptance |
| Release signer comparison on installed phone | **NOT RUN** | The device-level tool invocation was blocked by safety controls; parser and static tests are not substitutes for signer identity verification |
| Docker deployment | Not applicable to this change | This repository's tested target is the Android APK on Windows, not a running personal-ai-assistant container in the inspected home server `/docker` directory |

## Acceptance boundaries

**Development source PASS** for Node regression, TypeScript, web export and Android compilation/assembly. **Physical E2E and production release are NO-GO** until separately verified. No OS permission toggles, personal-app data clearing, contacts/messages, real WhatsApp Send, or production signing-key changes were performed.

Do not merge to `main` or install over an existing user app merely because the source builds. Physical installation requires signer lineage consistency and same-revision tests on an authorized device.

## Next independent gates

- Authorize / restore an Android emulator ADB session (or use an owner-authorized app test install with matching signer) and run the new instrumentation classes against the exact-source APK.
- Validate the same relative/child selection, scroll-stability fence and changed-window cancellation on real external-app UIs without sending messages.
- Finish cross-Chat/Voice/overlay durable task handoff, real composer + recipient grounding, provider-backed live voice and rollback/signing-lineage acceptance.
- Evaluate remaining Maestro source predicates, occlusion and Android IME fallback as strict opt-in adapters; never import unrestricted scripts, second model engine, desktop ADB driver into the APK.
