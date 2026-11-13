# Nexa safe-icon structural-container physical QA — November 14, 2026

## Reproduction: real Android regression, not just a static warning

After Development PR #120 added final owner-Pause-fenced Accessibility-node reacquisition, the new synthetic test `NexaInputAndIconFixtureAndroidTest.safeIconResolverRejectsStaleClassResourceOrBounds` was executed against the exact-source isolated QA2 APK on the real **Infinix X6831 (Android 13)**.

- First attempt failed because another ongoing Maestro instrumentation owned Android's global UiAutomation service. We did **not** interfere with that live session.
- After Maestro naturally ended, the test actually executed and failed **`AssertionError: The uniquely identified search icon must be observable`** in the positive case. This was a real regression, not a missing permission or a passing test.

## Root cause and narrowly scoped correction

The newly introduced `NexaScreenActions.uniqueCurrentTarget` prematurely executed `continue` on nodes that were `!isEnabled`, preventing recursion into their descendants. Accessibility structural/decor containers may be disabled even while their child buttons are enabled and actionable.

The corrected resolver **still excludes invisible, password and editable subtrees**. It now traverses a disabled *structural* container, but only accepts the final target when **that specific node** is independently visible, enabled, clickable, uniquely matched, and has the exact approved semantic, class, resource ID and bounds. The final owner Pause fence, fresh-root same-source requirement, encrypted one-time journal and unknown-no-replay handling are unchanged.

New and existing source regressions explicitly prevent reinstating the accidental structural-container pruning.

## Independently verified source / APK evidence

| Check | Result |
|---|---|
| Fixed isolated Git feature SHA | `e282dbbe797595b4010e138db1aecd839e7ea54f` |
| Full Node tests | **488/488 pass**, log `%TEMP%\\nexa-structural-node-e282dbb.log` |
| Android native + AndroidTest + Maestro portable Gradle | **BUILD SUCCESSFUL**, arm64, `%TEMP%\\nexa-safe-icon-structural-e282dbb.log` |
| QA2 app package/version | `dev.rilandev.personalaiassistant.physicalqa2`, versionCode **2026111402** |
| New APK SHA256 | `18CBED24706EB106BC79626B03E1F9DA6DF0C0519FF20BE3B28570A7AEDF9A08` |
| New AndroidTest APK SHA256 | `1890EAEAF00417AA97E9E113ABE26C690A01DD39D7EDD1E66AB2A856F7EE2288` |
| Installed/new QA2 signer SHA256 | `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2` — MATCH |
| Exact-source installed-app identity instrumentation | **OK (1 test)**, `nexaExpectedRevision=e282dbb...` |
| Previously failing safe-icon positive and negative targeting test | **OK (1 test)** on the physical phone |
| Combined physical tests: input/icon + synthetic WhatsApp + safe icon policy + Pause | **OK (16 tests)** on the physical phone, log `%TEMP%\\nexa-safe-icon-physical-e282dbb.log` |
| User's primary `dev.rilandev.personalaiassistant` consumer app | NOT INSTALLED/CHANGED/RELEASED in this test |
| Real third-party app tap/WhatsApp Send/Sarvam microphone | NOT ATTEMPTED or CERTIFIED |

The QA2 APK upgrade used **in-place `adb install -r -t` only**, after checking new/installed signer matches and version code advances. No package uninstallation, app-data clearing, Android security grant, microphone activation, account contact reading or real message Send occurred.

**Exit gate:** owner-approved real third-party safe-icon navigation, nontrivial UI change observation and negative Pause/ambiguity cases still require evidence. This synthetic PASS establishes the new bounded target resolver and OS-backed test fixture, not fully autonomous Android control or consumer release readiness.
