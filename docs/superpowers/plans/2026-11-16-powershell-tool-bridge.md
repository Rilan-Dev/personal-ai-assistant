# Nexa PowerShell Tool Bridge Implementation Plan

> **For agentic workers:** Use the native implementer workflow in this session. Steps are tracked here and tested as they are implemented.

**Goal:** Make the root `nexa.ps1` client trigger Nexa's existing native tools over a debug-only local socket without ADB shell commands.

**Architecture:** A Kotlin local abstract socket server forwards JSON requests to a React Native event. The app dispatches one deterministic tool call through its existing AgentHarness/journal, and the server returns a bounded response. A native in-memory snapshot store provides recent indexed clickable targets and visible text.

**Tech Stack:** PowerShell 5.1, Kotlin/Android LocalServerSocket, React Native DeviceEventEmitter, existing TypeScript AgentHarness, Android AccessibilityService.

**Spec:** `docs/superpowers/specs/2026-11-16-powershell-tool-bridge-design.md`

## Global Constraints
- The PowerShell client may invoke `adb forward` only; never ADB shell/input, `am`, or uiautomator.
- Screen reads and side-effecting tools require `-Confirm`.
- All taps, scrolls, and named keys execute through existing Nexa tools and one-shot journals.
- Screen text is returned only to the local caller, bounded, memory-only, and never logged or sent to a model.
- Exclude outbound message/call/payment tools from the PowerShell allowlist.
- Preserve all existing unrelated dirty files and test edits.

## Review Focus
- Malformed HTTP or oversized body is rejected before dispatch — protocol parser test.
- Missing confirmation cannot reach the harness — dispatcher test.
- Stale snapshot IDs/indexes or changed foreground window cannot tap — snapshot-store test.
- Repeated command IDs cannot be replayed — server request test.
- A release build cannot start the local server — Android module test.

### Task 1: PowerShell request contract

**Files:**
- Modify: `nexa.ps1`
- Create: `apps/mobile/src/native/nexaPowerShellControl.ts`
- Test: `scripts/test/nexa-powershell-control.test.mjs`

- [ ] Add failing tests for operation validation, tool allowlist, and confirmation rules.
- [ ] Run focused test and confirm it fails because the helper is absent.
- [ ] Implement pure request validation and safe-tool metadata filtering.
- [ ] Expand PowerShell commands to `status/tools/observe/tap/scroll/key/invoke`; preserve only `adb forward` transport.
- [ ] Run focused tests and PowerShell parser.

### Task 2: Native live screen snapshot and indexed target

**Files:**
- Create: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaPowerShellScreenSnapshot.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/ScreenAssistService.kt`
- Test: Android unit tests for bounds, redaction, expiry and stale-window rejection.

- [ ] Add failing pure tests for index generation, input-field exclusion and stale/expired snapshots.
- [ ] Implement bounded visible text/clickable extraction from the external Accessibility root.
- [ ] Keep snapshots in memory with a 30-second lifetime; bind to package, window and structure digest.
- [ ] Resolve an index to a safe label only if the live screen still matches.

### Task 3: App-owned local RPC and AgentHarness routing

**Files:**
- Create: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaPowerShellBridgeServer.kt`
- Create: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaPowerShellBridgeModule.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlPackage.kt`
- Modify: `apps/mobile/src/native/androidTools.ts`
- Modify: `apps/mobile/App.tsx`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaAgentActionAudit.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaUiActionJournal.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaConversationDb.kt`

- [ ] Add failing parser/dispatch tests for bounded requests, one-use confirmations and disallowed send tools.
- [ ] Implement a debug-only LocalServerSocket, strict HTTP/JSON bounds, one request at a time, timeout, and a deterministic React Native event/response handshake.
- [ ] Dispatch approved calls through the existing AgentHarness, Android tools, and secure journal; use exact capabilities and fingerprints.
- [ ] Add a named key-action tool backed by Nexa's existing global-action journal; support only Back/Home/Notifications.
- [ ] Return structured responses without logging screen content; refuse high-risk send tools.

### Task 4: Device acceptance

**Files:**
- Modify: `docs/architecture/HARNESS_UPSTREAM_RESEARCH.md`
- Modify: `docs/project-control/WORKLOG.md`
- Modify: `docs/project-control/NEXT_ACTION.md`

- [ ] Run focused tests, complete Node test suite, typecheck and web export.
- [ ] Build isolated QA2 debug APK with the bridge.
- [ ] Install only on serial `099344034S003788`; do not touch the emulator.
- [ ] On the phone, verify status, tool list, screen read, one safe indexed tap, one directional scroll, and Back using `nexa.ps1`.
- [ ] Record exact evidence, limitations, APK path and run/test results.

