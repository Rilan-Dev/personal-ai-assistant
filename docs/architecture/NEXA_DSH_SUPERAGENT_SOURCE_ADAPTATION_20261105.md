# Nexa × DSH Superagent — source-adaptation and safety baseline

Date: 2026-11-05
Target: Rilan-Dev/personal-ai-assistant, Development
Authority: GitHub for source changes; BridgeHead VPS test checkout for local verification.

## Pinned upstream and license

| Repo | Pinned commit | Inspected source | Decision |
|---|---|---|---|
| Nexa | 865fa166a7292df3585482dea52160ce7e9644b2 (baseline) | packages/agent-core/src/runtime.ts, journal.ts, apps/mobile/App.tsx, Kotlin native modules | Extend the existing native authority, not another executor |
| DSH Superagent | 8c32a9dc5d72c033816c6a8110b37f40cf94fba4 | src/core/model.ts, engine.ts, ports.ts, tickets.ts, src/app/store.ts, effects.ts, outbox.ts, src/policy.ts | Adapt domain invariants (Trigger, Mandate, Ticket, Assignment, Decision, after-commit effects); no bundled runtime |
| LangGraph.js | a1d182f065d5cc8802ef6d5c2edd8ef55a9922b5 | libs/checkpoint/src/base.ts | Study checkpoint and replay; existing backend uses maintained @langchain/langgraph |
| Deep Agents JS | 1b56802c3b6608d139f045ecf1486d53881b42b1 | libs/deepagents/src/permissions/enforce.ts | Do not adopt; unmatched filesystem rule permits access |

DSH LICENSE is MIT (DeepSeek Harness Team, 2026). No DSH implementation was copied in this milestone. Preserve original notices if future source is substantially copied.

## Actual executor and Android tool inventory

1. Chat: apps/mobile/App.tsx creates the TypeScript AgentHarness via packages/agent-core/src/runtime.ts; uses deterministic, direct-provider or planning-only LangGraph adapters and a durable Android Keystore journal.
2. Live voice: RealtimeVoiceService.kt has a separate tool-calling and compound-task path through NexaLiveToolPolicy.kt, NexaSarvamActionTools.kt, NexaPhoneVoiceTools.kt and NexaVoiceTaskPlan.kt. This is not the same task-coordinator lifecycle as Chat.
3. Screen: ScreenAssistService.kt, NexaScreenActions.kt, NexaScreenScroll.kt, NexaScreenLabelTap.kt, NexaScreenVoiceOverview.kt, NexaVisualIconCapture.kt and NexaGuidedNavigation.kt enforce bounded owner-reviewed native actions.
4. Input and messaging: NexaTextInputActions.kt, NexaMessageInputActions.kt, NexaMessagingModule.kt and message-routing.ts support safe field discovery, exact reviewed text, installed messaging channels, app-hint memory and manual composers. They do not authorize external Send.
5. Storage: AgentJournalModule.kt provides encrypted one-time reservations; NexaUiActionJournal.kt holds a bounded 40-item encrypted UI audit; NexaConversationDb.kt contains custom.db v2 encrypted conversations and compact voice task checkpoints. These stores are not a unified transactional task database.

Do not allow both TypeScript and Kotlin to independently own the same Android side effect. The final architecture needs one Kotlin-coordinated task lifecycle and durable non-replay claims.

## First source milestone: non-executable task-admission contract

- New packages/agent-contracts/src/task-plan.ts exposes a bounded plan proposal and a trusted binder.
- The owner request ID, origin and policy version come from trusted local context, not the model.
- Tool risk, expected evidence, enabled status and approval requirement come from a trusted registry. A model cannot supply approval or evidence.
- Exact fields, eight-step limit, earlier-only dependencies, bounded data, expiry and scope are checked; unknown or high-risk tool actions are refused.
- Medium-risk admission is independently opt-in, and any side effect requires approval when later implemented.
- Bound data are copied and frozen. The returned plan is explicitly non-executable.
- NEXA_TASK_V2_ENABLED is false. No runtime pathway has been activated; existing Chat/Voice native execution remains unchanged.
- New packages/agent-core/test/task-plan.test.mjs covers 12 negative and positive admission cases.

## Proposed additive database v2 → v3 migration (NOT YET IMPLEMENTED)

| Table | Role |
|---|---|
| agent_tasks | task/session/source/status, encrypted goal, policy revision |
| agent_task_steps | indexed steps, encrypted typed arguments, dependencies and required evidence |
| agent_task_events | append-only state transitions and redacted reason |
| agent_mandates | scope, actor, policy version, expiry and revocation epoch |
| agent_step_approvals | exact one-shot step/target/input binding |
| agent_action_claims | durable idempotency/anti-replay identity independent of bounded diagnostics |
| agent_observations | evidence category and digest; no default raw screen content |

Preserve v1/v2 user records and Keystore keys; unfinished legacy tasks must not automatically resume. Claims are committed before actions; unknown outcomes prohibit blind retry. Test database upgrades, tampering, process death, signing continuity, concurrent callers and native restart before release.

## Android distribution and deployment constraints

Official Play Accessibility policy: https://support.google.com/googleplay/android-developer/answer/10964491 .
Official Android AccessibilityService guidance: https://developer.android.com/guide/topics/ui/accessibility/service .
General-purpose Play apps may not use Accessibility to autonomously initiate, plan and execute actions or decisions. Narrow deterministic human-defined automation and eligible disability accessibility tools have different conditions. Nexa declares isAccessibilityTool=false; review disclosures and consent. Do not bypass OS permission, lockscreen, settings, microphone controls or third-party app authorization.

VPS test checkout: /home/techadmin/workspaces/personal-ai-assistant/github-worktree . This Android APK is not a Docker Compose service. The VPS also runs live unrelated containers; no restart or speculative Docker build is authorized by a Nexa source change. Disk baseline ~7 GB available, 93% utilized.

Keep the existing manual GitHub Actions workflow policy; do not add Development push triggers or promote an unaccepted Android release.

## Phases and gates

| Phase | Scope | Status |
|---|---|---|
| 0 | Pin source, inventory executor paths, baseline Node tests, policy review | Source baseline verified, release/hardware checks separate |
| 1 | Versioned task plans, secure admission, negative tests | Source implemented, feature disabled |
| 2 | Encrypted v3 database and durable native single-writer claims | Open |
| 3 | Shared Chat/Voice task routing and planner adaptation | Open |
| 4 | Unified native mandates and approval revalidation | Open |
| 5 | Observed native task continuation, Android evidence | Open |
| 6 | Messaging/contact/field integration without unapproved send | Partially present; unified path open |
| 7 | Owner task history, memory provenance, retention | Partially present; unified path open |
| 8 | Same-SHA signed build, native emulator, Maestro, real provider, phone tests, rollback | Open |

## Evidence and certification boundaries

Before edits, baseline Development HEAD was 865fa166a7292df3585482dea52160ce7e9644b2 and VPS npm test passed 365/365. First Phase 1 run: focused tests 12/12 and full suite 377/377 passed, Expo web export passed. Typecheck exposed a raw.stepId narrowing issue and was addressed in follow-up commit ce12c334629c628be7fbb07075016d4e860c48bf; fresh typecheck is required to confirm the fix.

Earlier signed APK run https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41917330496 passed on a different source SHA. PR https://github.com/Rilan-Dev/personal-ai-assistant/pull/97 remains open. Source tests do not certify OEM device, actual Accessibility binding, authorized real voice provider or messaging outcomes.

Release requires explicit evidence: current source CI, native signed APK and instrumented tests when relevant, full per-scenario Maestro, same-signature upgrade, real-phone Screen Assist/overlay/mic and process-death tests, and approved rollback. No live APK or unrelated production service is modified in this milestone.
