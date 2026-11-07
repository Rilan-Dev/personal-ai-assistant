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
