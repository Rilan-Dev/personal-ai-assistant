# Nexa Maestro source reuse implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or subagent-driven-development. RED/GREEN tests, frequent commits, and release verification are mandatory.

**Goal:** Link the pinned Maestro source into Nexa and compile verified upstream Kotlin directly; adapt reusable command and selector behavior to the existing Android executor.

**Architecture:** Pinned git submodule at third_party/maestro-upstream, narrow Android :maestro-portable source set from upstream, existing ScreenAssistService and NexaTaskCoordinator remain the sole action executor. Maestro CLI remains external for E2E testing.

**Tech Stack:** Kotlin, Gradle Android, TypeScript/React Native, JUnit, Node test runner.

**Spec:** docs/architecture/NEXA_DYNAMIC_SCREEN_AGENT_20261106.md; approved Nexa x Maestro source reuse plan dated 2026-11-07.

## Global Constraints
- Base: local Development 41daba78641a70f27f0a166fe0efb23a922462b6, isolated feature worktree; never alter untracked .maestro tests or unrelated commits.
- Upstream: mobile-dev-inc/Maestro @ aaa248d7fd4ca4924444dc027af9c7a62a9be348 (Apache-2.0), exact source provenance.
- No full Maestro JVM/ADB/GraalJS/Selenium dependency in APK; no second AccessibilityService, planner, executor or audit journal.
- Model output never authorizes actions; native app verifies user grant, package/window, pause fence, one-shot claim.
- Messaging and sensitive actions require specific approval; no automatic sends, calls, purchases, OS bypass or unsafe replay.

## Review Focus
1. Missing upstream clone must fail the build instead of silently copying fallback code.
2. Direct compilation must exclude unapproved upstream dependency transits.
3. Duplicate or stale selector must never cause action.
4. Editable/password/OTP fields need stricter matching than benign controls.
5. Unknown outcomes, permission revocation, pause or process death must prevent next action.

### Task 0: Baseline and ownership
Files: AGENTS.md, docs/architecture/HARNESS_UPSTREAM_RESEARCH.md, docs/project-control/WORKLOG.md.
- [ ] Run npm test, npm run typecheck and :app:compileDebugKotlin or record environmental blockers.
- [ ] Map existing owners of screen observations, taps, scroll, text, voice and durable tasks.
- [ ] Record baseline, upstream commit/license/exclusions, rollback; no new runtime loop.

### Task 1: Pinned source and portable Android module
Files: .gitmodules, third_party/maestro-upstream gitlink, apps/mobile/android/settings.gradle, apps/mobile/android/maestro-portable/build.gradle, module JUnit test.
- [ ] RED: Write upstream model unit tests and demonstrate failure before module sources are wired.
- [ ] GREEN: Include only upstream Point, Bounds, TreeNode, SwipeDirection and ScrollDirection source files and make JUnit tests green.
- [ ] Compile :app:compileDebugKotlin and verify dependency graph excludes desktop-only packages.
- [ ] Commit verified milestone with source pin and ownership notes.

### Task 2: Native grounded selector
Files: NexaMaestroSelectors.kt, NexaScreenLabelTap.kt, native tests.
- [ ] RED: test unique vs duplicate, text/id, secure node, stale foreground package/window and missing target.
- [ ] GREEN: use upstream TreeNode/Bounds in pure selector representation while preserving existing action journal and screen root.
- [ ] Run native build and regression tests; commit.

### Task 3: Bounded scroll and input
Files: existing NexaScreenScroll.kt, NexaTextInputActions.kt and native tests.
- [ ] RED: visible/offscreen/absent, timeout, cancellation, wrong scroll container, ambiguous field.
- [ ] GREEN: bounded observe-scroll-verify; safe exact text entry; no unsafe retry.
- [ ] Verify Android and external Maestro smoke; commit.

### Task 4: One multi-step task coordinator
Files: existing task-plan.ts, task-admission.ts, NexaTaskCoordinator.kt and tests.
- [ ] RED: max 8 steps, dependsOn, verified continuation, pause, stale plans, replay prevention.
- [ ] GREEN: extend trusted allowlist on existing coordinator only.
- [ ] Verify Node, Android and workflow tests; commit.

### Task 5: Voice, chat, overlay and recipient-safe draft
Files: existing bridge and messaging modules, .maestro/nexa E2E cases.
- [ ] RED: verify cross-surface admission, wrong recipient, changed app, no-send.
- [ ] GREEN: same typed tools for every surface, bound recipient and exact unsent text.
- [ ] Verify same SHA emulator APK, user-owned physical phone evidence; commit.

### Task 6: Acceptance and release handoff
- [ ] Run npm test, npm run typecheck, npm run web:export, Android compile and tests, Maestro external smoke.
- [ ] Commit evidence. No main merge, production promotion or release claim without separate acceptance.

**Execution method:** Inline through BridgeHead with Superpowers TDD; keep worktree isolated.
