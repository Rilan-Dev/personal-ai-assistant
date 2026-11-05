# Chat Notification Shade Action Implementation Plan

> **For agentic workers:** This task is implemented natively in the current session under the owner's explicit request to fix the remaining flow and make the tests pass.

**Goal:** A direct chat request to open Android's notification shade produces one clearly labeled owner approval and, only after approval, invokes Android's Accessibility global action without reading notifications.

**Architecture:** Extend the existing deterministic owner-intent parser and phone-side `AgentHarness` tool registry. Keep the side effect on Android behind exact intent validation, the existing one-use approval journal, a live Screen Assist binding, and a native `GLOBAL_ACTION_NOTIFICATIONS` call. Do not add a new planner loop or provider credential.

**Tech Stack:** TypeScript/Node test runner, React Native bridge, Kotlin AccessibilityService, Maestro, Android ADB, GitHub Actions signed release-test workflow.

**Spec:** `docs/testing/NEXA_AGENT_GOAL_ACCEPTANCE.md`; repo constraints in `AGENTS.md`.

## Global Constraints

- The model never supplies authorization; match the exact single-action owner request at the phone.
- Require Screen Assist to be actively bound and a fresh owner approval before dispatch.
- Never read or disclose notification contents.
- Keep outbound messages disabled and do not send any message during testing.
- Work on `Development`; use workflow-dispatch-only CI and the pinned release signer.
- Update `docs/project-control/WORKLOG.md` and `NEXT_ACTION.md` with evidence and unresolved gates.

## Review Focus

- Exact polite notification-shade request maps to the narrow native action; unrelated/compound/negated requests do not.
- Invalid arguments, missing Screen Assist, and missing owner approval fail closed.
- Android returning false or failing to expose SystemUI is not reported as a completed open.
- Maestro's active UiAutomation suppression of Accessibility is treated as a test-environment conflict, never bypassed by weakening service assertions.
- Notification contents remain unread and absent from logs/provider observations.

### Task 1: Define and test the chat owner intent

**Files:** `packages/agent-contracts/src/intent-policy.ts`; `packages/agent-core/test/natural-device-intent.test.mjs`; `packages/agent-core/src/android-planner.ts`; `packages/agent-core/src/direct-provider.ts`; `packages/agent-core/src/remote-planner.ts`.

- [ ] Add failing tests proving exact shade requests are admitted and mixed, negated, and “read notifications” prompts are rejected.
- [ ] Run focused tests and confirm the new assertions fail for the missing tool.
- [ ] Add `device.open_notification_shade` with empty input to the exact intent parser and the existing planner allowlists.
- [ ] Run the focused tests, then the full Node test and typecheck suites.

### Task 2: Bind the tool to the approved native action

**Files:** `apps/mobile/src/native/androidTools.ts`; `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt`; `ScreenAssistService.kt`; Android instrumentation tests.

- [ ] Add tests for empty arguments, active Accessibility binding requirements, and one-shot approved execution.
- [ ] Connect the tool only through the existing `AgentHarness` side-effect approval and Android journal.
- [ ] Add a native bridge operation that requires foreground Nexa and a bound service, calls `GLOBAL_ACTION_NOTIFICATIONS`, and reports only acceptance/open verification without text or notification data.
- [ ] Run Android native compilation/instrumentation and focused JS checks.

### Task 3: Verify physical-device and CI acceptance

**Files:** `.maestro/experimental/10-agent-notification-shade.yaml`; `scripts/ci/manual-actions.sh`; `scripts/test/manual-actions.test.mjs`; `docs/project-control/WORKLOG.md`; `NEXT_ACTION.md`.

- [ ] Run the focused feature path on the phone, approve the visible single action, and verify Android SystemUI shade without inspecting its contents.
- [ ] Run flows 10–13 in order and retain strict bound-service and actual-shade assertions.
- [ ] Resolve any reproducible failures, rerun the ordered set and required source checks.
- [ ] Route debug, emulator, and release-test dispatches to `Development`; leave `android-main-promotion.yml` scoped to `main`.
- [ ] Dispatch one signed release-test build for the verified `Development` revision, install via ADB, and verify installed version and signer.
- [ ] Record the pass/fail evidence and any Maestro UiAutomation limitation accurately.
