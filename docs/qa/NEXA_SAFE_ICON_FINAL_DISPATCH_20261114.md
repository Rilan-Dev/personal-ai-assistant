# Nexa generic safe-icon final-dispatch hardening — November 14, 2026

## What was wrong

The existing `NexaScreenActions.confirm` owner-reviewed navigation flow found one safe icon in an Accessibility tree *before* the final Android Pause fence. The callback then used that earlier `AccessibilityNodeInfo` directly. A target could be recycled or its window/position replaced between review and click. The outer exception handler also could leave a claimed journal action without an explicit `unknown` outcome.

This differs from the already hardened WhatsApp-chat tap and unsent composer flows.

## Implemented

- New `uniqueCurrentTarget(root, icon)` walks the current hierarchy, discards password/editable/invisible/disabled subtrees, compares safe label and semantic, exact rectangle, class and resource ID, and rejects **all** ambiguous or incomplete walks (180-node budget).
- Both initial review and final dispatch call the same resolver. Within the existing `NexaNativeActionSafety.dispatchWithFence`, a fresh external root must expose exactly the same package, window and native source as the reviewed icon. Lock screen, interrupted Accessibility connection and owner Pause deny dispatch.
- Native click occurs on the **freshly acquired** node, not the earlier one. An exception after entering a potentially irreversible native click is journaled `unknown`; the owner must inspect manually, and no automatic retry is added. If a broader exception occurs after a durable claim, the handler best-effort records `unknown` instead of silently leaving the claim unfinished.
- Added a synthetic AndroidTest inspection of an actual search ImageButton with negative checks for moved geometry, changed native class and changed resource identity; no click on other applications.
- Added five source regression tests asserting tree completeness, final-fence reacquisition, Pause/lock rejection, uncertainty and synthetic test coverage.
- Corrected a separate existing Windows CRLF-only failure in the Maestro Cloud manual-workflow source regression. Permission-grant and no-automated-app-launch assertions remain unchanged.

## Verified development evidence

| Gate | Evidence | Result |
|---|---|---|
| New focused Node regressions | `scripts/test/nexa-safe-icon-final-fence.test.mjs` at source commit `b2c36e8` | **5/5 PASS** |
| Full Node suite after CRLF test fix | `%TEMP%\\nexa-safe-icon-full-5d694f5.log` | **486/486 PASS** |
| TypeScript | `%TEMP%\\nexa-final-icon-typecheck-b2c36e8.log` | **PASS** |
| Java/Kotlin Android + androidTest assembly and Maestro portable JUnit | `%TEMP%\\nexa-final-icon-gradle-b2c36e8.log`, arm64 QA2 opt-in, source `b2c36e8ff03e61786a3c3894b93ae9c33b6f4dff` | **PASS** |
| Web static export | `%TEMP%\\nexa-safe-icon-web-5d694f5.log` | **PASS** |
| npm dependency audit | `%TEMP%\\nexa-final-icon-audit-b2c36e8.json` | **0 info, low, moderate, high or critical** |
| Physical Infinix X6831 Android instrumentation of this new safe-icon test | QA2 `dev.rilandev.personalaiassistant.physicalqa2` was already installed. The host also had a concurrent `dev.mobile.maestro.test` instrumentation still running, Java PID 812 and no ADB port forwarding. | **NOT RUN** — concurrent test session deliberately not interrupted |
| QA2 signing lineage check | Read-only `adb pull` of installed QA2 APK and `apksigner` on both old and new isolated APKs; certificate SHA-256 digest `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2` matched. New QA2 versionCode `2026111401` is greater than installed `11`. | **MATCH; NO INSTALL/UPGRADE MADE** |

The native source was last modified at `b2c36e8`. The subsequent commit `5d694f5` changes only one Node test's cross-platform newline parsing. The later documentation commit does not change native app binaries.

## Remaining release / owner acceptance

- Run `NexaInputAndIconFixtureAndroidTest.safeIconResolverRejectsStaleClassResourceOrBounds` and all related emulator/device tests against the exact SHA when an isolated Android test environment is not occupied. Never terminate an unrelated running Maestro process or change permission grants.
- With owner-granted Screen Assist and separately visible approval, verify one genuine third-party **safe navigation icon** and a changed/ambiguous/locked/paused negative case without touching sensitive Send/Call/Pay actions.
- Independently complete real WhatsApp recipient/composer acceptance, Sarvam live voice/wake and floating overlay, source-to-binary signing and rollback gates, before promoting the consumer `main` branch.

**This is safe development-branch source hardening, not Nexa production acceptance.** Primary owner app, QA2 installed state, real messages, Android settings and server services remain unchanged.
