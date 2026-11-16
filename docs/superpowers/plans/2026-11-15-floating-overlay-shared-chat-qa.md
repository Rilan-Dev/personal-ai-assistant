# Floating Overlay Shared Chat QA Plan

> **For agentic workers:** Implement natively in the current workspace. Preserve all existing uncommitted changes.

**Goal:** Fix stale React state in floating Chat and validate ACA mail routing plus a safe screen action through the overlay.

**Architecture:** Keep `NexaOverlayChatRelay` as a one-request handoff into the existing React Chat `send()` function. Maintain a stable ref to the newest `send` callback; do not add a second planner, mail client, or permission path.

**Tech Stack:** React Native/TypeScript, Kotlin Android overlay service, Node `node:test`, ADB emulator QA.

**Spec:** `docs/superpowers/specs/2026-11-15-floating-overlay-shared-chat-design.md`

## Global Constraints

- Emulator only; physical device serial `099344034S003788` must not receive commands.
- Screen actions remain one-time and user-reviewed; never send a message or mutate mail.
- Never log or retain ACA message bodies, subjects, provider credentials, or captured screen text.
- Keep provider credentials out of the repository; do not reuse the removed Sarvam test key.
- Do not represent a QA debug APK as a release-signed build.

## Review Focus

- ACA/provider state changes after the overlay listener mounts: event dispatch must use current state.
- Main Chat busy/listening/active task state: reject or report busy without replay.
- Expired overlay token or missing React runtime: fail visibly and execute no action.
- Screen scroll observation cannot identify a unique safe target: fail closed, no retry.
- No saved AI provider: ACA uses the deterministic bounded fallback; no excerpt goes to a model.

---

### Task 1: Fresh callback regression

**Files:**
- Modify test: `scripts/test/nexa-overlay-screen-action-context.test.mjs`

- [ ] Add a test requiring the persistent overlay event listener to dispatch through a ref that is updated to the current `send` callback each render.
- [ ] Run `node --test scripts/test/nexa-overlay-screen-action-context.test.mjs` and confirm the new assertion fails because the current listener calls its captured `send` closure.

### Task 2: Latest Chat callback handoff

**Files:**
- Modify: `apps/mobile/App.tsx`
- Modify test: `scripts/test/nexa-overlay-screen-action-context.test.mjs`

- [ ] Create a stable `useRef(send)` after `send` is defined, assign `.current = send` during every render, and invoke `.current(request, undefined, id)` from the overlay listener.
- [ ] Rerun the focused regression and `npm test`; the focused test and complete suite must pass.

### Task 3: Emulator overlay acceptance

**Files:**
- Evidence: `.qa-results/` metadata and screenshots with no private email content.
- Update: `docs/project-control/WORKLOG.md` and `docs/project-control/NEXT_ACTION.md`

- [ ] Build and install the current x86_64 QA2 debug variant on `emulator-5554`, preserving app data.
- [ ] Send “Summarize unread emails from the last 24 hours” through the floating overlay. Confirm the current ACA session is used; record only status, counts, scope, sync freshness and partial coverage.
- [ ] Compare the same prompt in ACA web Chat. Do not copy email subjects or bodies into output or notes.
- [ ] Use the overlay shared planner for one downward scroll in Android Settings, approve that exact action once, then verify an observed screen change and stop.
- [ ] Run `npm run typecheck`, `npm run web:export`, `git diff --check`, and relevant emulator checks; record the exact build and any unresolved provider/realtime-voice limitations.
