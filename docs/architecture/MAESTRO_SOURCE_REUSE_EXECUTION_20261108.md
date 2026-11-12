# Maestro → Nexa source-reuse implementation ledger

**Status:** Development feature branch only; not certified for autonomous phone workflows.
**Source:** `mobile-dev-inc/Maestro@aaa248d7fd4ca4924444dc027af9c7a62a9be348` (Apache-2.0) through the pinned `third_party/maestro-upstream` submodule.
**Nexa feature branch:** `feature/nexa-bounded-scrollsearch-20261107`, PR #117; base `Development` changes independently.

## Source provenance: compiled versus adapted

| Maestro module | Integration today | What to take next |
|---|---|---|
| `maestro-client/{Point,Bounds,TreeNode,SwipeDirection,ScrollDirection}.kt` | **Directly compiled** from immutable submodule in `:maestro-portable`; 9 portable-library unit tests passed on the earlier physical-development worktree | Keep pinned, fail build if missing, no copied upstream source |
| `maestro-client/Filters.kt` | **NOT compiled**; desktop `Maestro`, `UiElement`, coroutine and CSS-query dependencies | Selectively adapt exact text/id, relation, enabled/focus/checked predicates into `MaestroSafeSelector`; do not import `css` or unrestricted regex |
| `maestro-orchestra-models/ElementSelector.kt` | **NOT compiled**; coupled to JsEngine and YAML model | Adapt a narrow owner-controlled typed selector, never execute provider-generated scripts |
| `maestro-client/ViewHierarchy.kt` | **NOT compiled**; coupled to its desktop Driver and UiElement | Reuse refresh/occlusion concepts with Android's existing Accessibility tree, preserving current window and observation generation |
| `maestro-client/UiElement.kt` | **NOT compiled**; references DeviceInfo | Visibility percentage and relative geometry adapted into the pure selector; test thresholds before use for actions |
| `maestro-orchestra/Orchestra.kt` | **NOT compiled** | Bounded scroll, wait-for-stability, conditions, and step receipts adapted individually to Nexa's one existing coordinator, not a second runner |
| `maestro-android/MaestroInputMethodService.kt` | **NOT compiled** | Evaluate only as an optional owner-enabled IME fallback after verified `ACTION_SET_TEXT` limitations |
| `maestro-client/drivers/AndroidDriver.kt` | **Excluded from APK**; host ADB/gRPC/instrumentation | Keep only in Windows/CLI Maestro test infrastructure |
| `maestro-cli/mcp/*` and `maestro-orchestra/debug/*` | External QA source; not in APK | Use host CLI for real screen inspection, repeatable flows and local-only failure artifacts |
| `maestro-ai/*`, arbitrary JS/YAML, desktop/iOS drivers | Excluded | Do not import a second model engine or privileged execution path |

## Verified existing functionality

1. Git submodule and Gradle source allowlist pinned; no full desktop dependency graph.
2. Basic read-only `MaestroSafeSelector` and native `NexaMaestroTargetSelection` integrated with the original `NexaScreenLabelTap` one-time owner approval, current package/window, Pause generation, permanent action journal and observed-action verifier.
3. Four-direction, owner-confirmed scroll search with max four steps / 90-second task duration on the PR #117 branch; each step requires fresh Android observation and another confirmation.
4. Source and device evidence collected **before** the new relative-selector extension: `npm test` 420/420, root/mobile/runtime TypeScript, Expo export, 9 portable JUnit tests, arm64 debug and AndroidTest APK builds, 11 synthetic physical-phone instrumentation tests (Infinix X6831 / Android 13). These **do not certify** the later branch source.
5. Real-owner Maestro CLI Chat test was blocked while the phone was securely locked; it was not marked passed.

## 2026-11-08 new implementation (branch, awaiting fresh native compilation)

- Pure `MaestroSafeSelector` gains exact `aboveText`, `leftOfText`, `rightOfText`, `childOfText` (true tree ancestry), and explicit 1–100% viewport visibility. Unique, visible, non-editable anchors are required; multiple matches reject. The default remains 100% visible. Existing `belowText`, exact text and resource ID behavior remains.
- The native `NexaMaestroTargetSelection` adapter accepts observation-only anchors while keeping returned indices limited to approved target candidates. Generic unscoped label taps remain unchanged.
- The owner-only `NexaMaestroRelativeTap` grammar accepts four bounded relationships (e.g. `tap Open below Recent`), rejects sensitive/injected requests, and round-trips a strict short-lived overlay action token.
- Floating Chat/Tools routes relative requests into the **existing** owner review and `NexaScreenLabelTap.approvedRelative`. The one-time journal, Pause fence, current-window check, bound hitbox and Android `ACTION_CLICK` remain the only execution path. The relative anchor is re-observed for a unique match at its reviewed bounds **inside** the final dispatch fence.
- Source-first regressions: 5 added pure selector cases, 2 native adapter cases, 3 Android parser cases, and 6 Node integration checks. Source-level integration invariant inspection passed 13/13. An isolated **manually mirrored** pure-Kotlin smoke harness compiled with `kotlinc` and passed visibility, hierarchy, grammar and invalid-action cases. This is not an exact-branch Gradle/Android test.
- Full native `childOfText` use remains **read-only**: current live tap adapter uses the flattened candidate/anchor view and supports four geometric relations, not arbitrary parent/descendant control actions. Do not claim live full-hierarchy automation.

## Milestone gates still not satisfied

| Gate | Remaining requirement |
|---|---|
| Latest Android compilation | Reconnect Windows BridgeHead; pull feature revision safely into the isolated worktree, run `npm test`, typecheck, `:maestro-portable:testDebugUnitTest`, `:app:assembleDebug` and `:app:assembleAndroidTest` against the new exact commit |
| Physical-device tests | Owner unlocks the phone normally; verify floating Chat, relative control selection, wrong/duplicate anchors, changed package/window, Pause, no-send, bounds drift and cancellation |
| Hierarchy/occlusion | Supply an ephemeral redacted true Accessibility tree to the existing selector and support safe parent/descendant constraints in the live action path |
| Scroll settling | Finish PR #117 live actual third-party app scroll, reliable target visibility and motion-settled evidence; no automatic retry after uncertain effect |
| Unified task continuity | One durable task coordinator with step-level evidence across main Chat, Voice and floating overlay, rather than parallel bounded guides |
| Messaging | Correct recipient/channel/composer verification in an actual external app; no automatic Send, payment or call |
| PR integration | Reconcile newer `Development` commits, finish exact-branch regression tests and retain draft status until release acceptance |
| Release | Manual owner-run same-SHA APK, user acceptance, rollback/signing/provenance gates before promotion |

**Rule:** A simulator or synthetic instrumentation pass never proves a third-party personal-assistant workflow succeeded. Do not run Maestro `clearState: true` / blanket permission denial flows on the owner's personal device. Sensitive screen/recipient artifacts stay on the owner's host.

## 2026-11-13 verified hierarchy milestone — supersedes historical pending gates above

**Branch:** `feature/nexa-bounded-scrollsearch-20261107`, draft PR #117.
**Latest source:** `ca0bf0356247c12f0f9cad0e460f7da9a3c4d4c1` (includes all Development changes through `6e40cbe0051893aa7b949f47183bde9ff65e5c87`).
**Exact Android-tested source:** `988fcee0c79a0b6ef3f6fff13318784e942a4026`. The subsequent merge changed only `scripts/test/nexa-release-lineage-preflight.test.mjs`; no compiled Android/Gradle source changed. Do not claim the tested APK was built from the later commit.
**Pinned upstream:** Maestro `aaa248d7fd4ca4924444dc027af9c7a62a9be348`. All five directly compiled portable model files remain unchanged.

### Implemented and verified now

- New `NexaMaestroAccessibilityHierarchy` creates a bounded (180 nodes, 24 levels), ephemeral, redacted Android Accessibility tree and exposes only safe, already-grounded target hitboxes and explicit owner-named anchors; no action capability or extra screen-data transport.
- `NexaMaestroTargetSelection.chooseFromTree` binds the original Maestro selector's true ancestor path to a **single** previously approved candidate, matching exact hitbox and resource ID. A nearby sibling is not a child. Ambiguous, missing, stale or partially visible targets fail closed.
- Owner grammar now accepts `tap Open inside Recent` in addition to `below/above/left of/right of`. The `child` relation uses native hierarchy rather than the flattened geometric selector. Malformed and sensitive commands cannot downgrade to a generic tap.
- Contextual actions require a current, matching hierarchy **twice**: at reviewed selection and again within the final `NexaNativeActionSafety.dispatchWithFence` before `ACTION_CLICK`. Exact package/window, target bounds, unique anchor, one-time approval and encrypted action journal remain authoritative. No auto-click, auto-send, screenshot data export or side-effect replay was added.
- Bounded scroll search from PR #117 remains four separately approved scrolls / 90 seconds with journal/evidence, not an unrestricted Maestro workflow engine.
- Security regression: on Windows, the emulator-only destructive-QA guard's tests use the installed Git Bash and an isolated fake ADB in that shell; **the real connected phone is never the target of those simulated destructive actions**. Additional Development signer-lineage test merged.

### Evidence, date: 2026-11-13 (Asia/Dubai)

| Gate | Actual result | Exact verification |
|---|---|---|
| Entire Node runtime / integration / safety regression | **454/454 PASS**, zero failures | `ca0bf03`; Windows log `%TEMP%\\nexa-node-ca0bf03.log` |
| Focused relative/child selection source regressions | **8/8 PASS** | `988fcee`, `node --test scripts/test/nexa-maestro-relative-overlay.test.mjs` |
| Windows emulator-only QA guard | **7/7 PASS** | `a4adeda`, isolated fake ADB, physical phone protected |
| TypeScript, all workspaces | **PASS** | `988fcee`, `npm run typecheck` |
| Expo web export | **PASS** | `988fcee`, `npm run web:export` |
| Portable Maestro Kotlin unit suite | **PASS** | `988fcee`, `:maestro-portable:testDebugUnitTest` |
| Android app and instrumentation Kotlin compile | **PASS** | `988fcee`, `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` |
| App and Android instrumentation APK assembly | **PASS** | `988fcee`, `:app:assembleDebug :app:assembleAndroidTest` |
| Owner-authorized Infinix X6831 (Android 13), bounded scroll / hierarchy / parser / adapter suite | **20/20 PASS** | Installed **matching standard package** `dev.rilandev.personalaiassistant` and runner `dev.rilandev.personalaiassistant.test`; output `OK (20 tests)`, no failures. Windows log `%TEMP%\\nexa-physical-core-988fcee.log` |
| Same device, synthetic WhatsApp chat row fixture | **3/3 PASS** | Output `OK (3 tests)`; `%TEMP%\\nexa-physical-whatsapp-988fcee.log` |
| Built debug APK integrity | **SHA-256** `1617e92cfed6ed0796dc9623138e1cdc0cab6a505310d8fd7ee6e61f8e3da1db` | `988fcee`; both APKs installed in place with `adb install -r`; no clear/uninstall, no OS permission mutation |
| Real third-party hierarchical tap and multi-step workflow | **NOT VERIFIED** | Device Screen Assist not enabled, no overlay permission granted; do not bypass user settings |
| Production release/signing/rollback acceptance | **NO-GO** | Separate owner review and manual full acceptance required |

**Test-runner trap corrected:** An older `.debug.test` runner on the phone did not contain the new hierarchy test and returned an instrumentation failure despite an ADB zero exit. We inspected APK and installed dex hashes, selected the new standard-package runner, and required the explicit `OK (20 tests)` summary. An instrumentation return code of `-1` alone is insufficient evidence.

### Comprehensive source-reuse gap inventory (latest)

| Maestro source/capability | Current use | Remaining work |
|---|---|---|
| `maestro-client/{Point,Bounds,TreeNode,SwipeDirection,ScrollDirection}.kt` | **Five upstream files compiled directly** | Retain immutable hash, provenance, license and source allowlist |
| `Filters.kt`, `ElementSelector.kt` | **Only safe, deterministic selector ideas adapted** | Strict checked/enabled/focus/id predicates, typed bounded selector model; never introduce arbitrary regex, JS or YAML effects |
| `ViewHierarchy.kt`, `UiElement.kt` | **Real native ancestor hierarchy now integrated**; visibility thresholds pure | Live occlusion, nested scroll-context, layout stability, safe detached-node handling, more robust bounds drift signals |
| `Orchestra.kt` | **Bounded owner-approved scroll concept adapted** | Wait-until-visible with stable-settle evidence, deterministic conditional step receipts and cancellation across Chat/Voice/overlay |
| `MaestroInputMethodService.kt` | **Not reused** | Evaluate optional owner-enabled IME only when exact native field identity and `ACTION_SET_TEXT` limitations are demonstrated |
| `AndroidDriver.kt` / ADB and gRPC driver | **Deliberately excluded from APK** | Remains host-side Maestro QA; never add privileged second device executor |
| CLI/MCP and debug reporters | **Test tooling only** | Run owner-unlocked real third-party UI smoke and retain privacy-safe local-only evidence |
| Maestro AI/JS/YAML and full desktop/iOS execution | **Not imported by design** | Build Nexa-owned typed, allowlisted command orchestration if justified; do not import unrestricted scripts or duplicate model/driver |
| WhatsApp/SMS/contact and other app flows | Nexa's own read-only/approved primitives; synthetic fixture tests passed | Real verified app, recipient, channel, composer, effect and no-send owner acceptance |
| Sarvam live task coordination and persistent task evidence | Existing Nexa infrastructure, not proven by this Maestro PR | Single shared task checkpoint, pause/timeout, cancellation and receipt across voice, chat and floating surface |

**Release status:** SOURCE REGRESSION PASS; NATIVE COMPILE PASS; SELECTED PHYSICAL SYNTHETIC INSTRUMENTATION PASS; REAL CROSS-APP ACCEPTANCE **NO-GO**; PRODUCTION **NO-GO**. Keep PR #117 draft and avoid promoting to `Development`/`main` until those separate gates are established. The main Windows checkout's unrelated working changes, installed app data, OS permissions and production services/databases remain untouched.
