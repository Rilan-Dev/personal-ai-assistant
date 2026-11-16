# Nexa WhatsApp Reviewed Send Tool Implementation Plan

> **For agentic workers:** Use this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add a narrowly guarded WhatsApp Send tool and static regression data for the complete user-requested tool sequence.

**Architecture:** A JSON fixture supplies a Sarvam function-call response and a synthetic full owner-sequenced WhatsApp message request. The Node harness tests the four exact tools with mock native outcomes. Android verifies the exact recipient/draft/button, claims the durable journal under owner approval and the Pause fence, dispatches once, and reports only visible sent-message evidence.

**Tech Stack:** Node.js test runner, TypeScript agent-core modules, Kotlin Accessibility service, Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-11-15-nexa-whatsapp-static-tool-fixture-design.md`

## Global Constraints

- Keep Android planning on the client and execution behind the existing native allowlist, durable approval, pause fence, and evidence checks.
- Use exact tool IDs and add only a dedicated exact-recipient WhatsApp Send capability; never use generic coordinate/label taps to send.
- Use synthetic contact/message values and no secrets.
- Do not interact with the physical device while Nexa has an active audio session.
- Never treat a sent-message bubble as proof of delivery; uncertain outcomes are not retried.

## Review Focus

- Direct provider prose instead of a function call — assert `content: null` and one tool call.
- Wrong app/recipient/text or call order — compare full IDs and inputs against the fixture.
- Missing/malformed native evidence — ensure the harness does not advance.
- Approval/replay — approve each durable step once and assert one execution per action ID.
- Send without a preceding verified exact draft — reject the entire plan and assert no tool executes.
- Wrong/ambiguous Send button, edited text, changed recipient/window, journal failure, Pause, or uncertain post-send UI — refuse or report uncertain without retry.

## Task 1: Owner-Sequence Send State

**Files:**
- Modify: `packages/agent-core/src/owner-sequence.ts`
- Modify: `packages/agent-core/test/owner-sequence.test.mjs`
- Modify: `scripts/test/fixtures/nexa-whatsapp-draft.json`
- Modify: `scripts/test/nexa-whatsapp-static-tool-fixture.test.mjs`

**Interfaces:**
- Consume: `ToolCall`, `AgentHarness`, and the exact prior `screen.compose_whatsapp` arguments/evidence.
- Produce: one terminal `screen.send_whatsapp` call bound to the same recipient/text as the verified draft.

- [x] Extend the static fixture/test first and verify RED: the current owner sequence rejects Send.
- [x] Extend the parser/evidence gate for exactly one terminal send after a verified matching draft.
- [x] Run focused agent-core tests and verify GREEN.

## Task 2: Native One-Shot WhatsApp Send

**Files:**
- Modify: `apps/mobile/src/native/androidTools.ts`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaAgentActionAudit.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaMessageInputActions.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaUiActionJournal.kt`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaConversationDb.kt`
- Test: `apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaMessageInputPolicyAndroidTest.kt`

**Interfaces:**
- Consume: exact session/call/fingerprint, recipient, message, active Screen Assist service.
- Produce: sent-message evidence only when the recipient remains verified, the exact composer text is cleared, and the exact text is visible in the conversation. Delivery status remains unverified.

- [x] Add red native policy tests for Send target qualification and sent evidence.
- [x] Add native Send execution with same-window revalidation, durable one-shot claim, Pause fence and no retry.
- [x] Add Send to the native action-audit allowlist with exact two-field argument binding.
- [x] Compile the app and Android instrumentation sources. Instrumentation execution remains pending until the phone is safe to use.

## Task 3: Full Verification

**Files:**
- Update: `docs/project-control/WORKLOG.md`
- Update: `docs/project-control/NEXT_ACTION.md`

- [x] Run focused tests, full `npm test`, typechecks, web export, Kotlin main/test compilation, and whitespace validation.
- [ ] Build/install once on the physical Infinix after voice capture ends; run the authorized workflow with the app's exact recipient/text review.
- [x] Record exact outcomes and remaining acceptance gates below; no real Send action was exercised.

## Verification — 2026-11-15

- Static Sarvam-compatible provider response has `content: null` and a native tool call; the synthetic WhatsApp workflow runs through `AgentHarness` with a separate approval for every tool and status evidence for each step.
- Focused agent/static/native source checks: 26/26. Full Node suite: 589/589. Root/mobile/runtime TypeScript checks: passed. Expo web export: passed. `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin`: passed. `git diff --check`: passed.
- The fixture uses only `Nexa QA Contact` and synthetic text; no real chat was opened and no message was sent. Physical installation and on-device Android instrumentation were not run while Nexa had an active voice session.
