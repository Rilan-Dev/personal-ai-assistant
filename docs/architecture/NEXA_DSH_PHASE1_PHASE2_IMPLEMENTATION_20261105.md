# Nexa × DSH — Phase 1 admission and Phase 2 storage
Date: 2026-11-05. Target: Development. GitHub is source of record.

## Phase 1 implementation
- New packages/agent-core/src/task-admission.ts normalizes local Android intent, direct BYOK, and remote LangGraph PlannerDecision into immutable BoundTaskPlan.
- The trusted local registry supplies risk, expected evidence, tool allowlist, owner identity and policy. The model cannot claim permission or verification.
- apps/mobile/App.tsx now wraps the actual Chat planner selection behind NEXA_TASK_V2_ENABLED=false. Existing behavior remains unchanged until a native coordinator rollout is authorized.
- Once explicitly enabled, only native read-only device.status or device.battery_status proposals can produce an owner-created, foreground review record. No tool dispatch occurs in this mode.
- packages/agent-core/test/task-admission.test.mjs covers normalization, unsupported tool refusal, unsafe risk, cancelled planning, failed persistence and ordinary responses.

## Phase 2 implementation
- New NexaTaskStore.kt revalidates every admitted plan inside Android, independently of JS and any provider.
- Valid plans must identify an existing Android-minted, owner review-v1 chat task. They are bounded in size, lifetime and ordered dependencies and contain only exact trusted low-risk empty-argument device reads.
- Global SQLite step identities are new Android UUIDs; model step IDs and dependencies are stored inside encrypted arguments_cipher records.
- Every step and the read_plan_admitted event commit in a single SQLite transaction. Failed or duplicate admission cannot result in a partial runnable plan.
- Native bridge admitOwnerReadOnlyPlan requires a foreground Nexa window. It does not create an execution mandate, grant approval, claim an action or execute an Android tool.
- Existing encrypted custom.db v3 task, step, event, mandate, approval, claim and observation tables are reused without destructive migration.
- NexaTaskStoreAndroidTest.kt covers atomic storage, no mandate, no approval, duplicate/cancelled/expired task denial, unsafe tools and dependency rejection.
- An independent NexaTaskCoordinator.kt native owner-driven battery/storage fact path also exists on Development; it has its own evidence, revocation and timeline. These paths are not yet the general unified coordinator.

## Development still open
1. General native task lifecycle transitions and dependency-aware step scheduling across multiple tools with evidence, approval and crash recovery.
2. One stable task identity across Chat, Sarvam/OpenAI Voice, assistant role and overlay; currently distinct execution paths.
3. Verified, approved integration of existing safe Accessibility navigation and messaging composition without automatic external Send.
4. Owner-visible memory provenance, task corrections, retention and global task timeline across all providers.
5. Maintain current source security checks; do not promote an Android release or force a merge into main without exact commit native acceptance.

## Safety posture
- NEXA_TASK_V2_ENABLED remains false.
- No autonomous messaging, calls, payments, blanket Accessibility permission, OS permission bypass or production deployment.
- GitHub Actions remain manual. Android APK has no Docker Compose service; running ACA containers must not be restarted for this change.
## Follow-up native recovery and source fixes
- Review-only task cancellation now marks any planned/pending/awaiting-approval steps cancelled in the same transaction as the owner cancellation event. A cancelled task cannot leave apparently runnable child steps.
- NexaTaskCoordinator.recoverStaleInFlight reconciles native-fact tasks whose claim remained outcome_unknown beyond a two-minute safety window. It marks task and step uncertain, revokes the native mandate and records recovered_uncertain_no_replay, without reissuing the Android read.
- The existing Tools task timeline recognizes uncertain outcomes and never treats them as verified or automatically repeatable.
- NexaTaskCoordinatorRecoveryAndroidTest.kt verifies one-time recovery and permanent mandate revocation.
- A prior Android CI attempt (#41992588796 on 101ff60c7e98) exposed an unrelated debug-only Kotlin property error in NexaChatListQaActivity.kt. The writable importantForAccessibility property is now used instead of assigning isImportantForAccessibility.
- The new task execution feature remains disabled; full Chat and Voice unification and all consequential Android actions still require native policy and owner approval.

## 2026-11-05 — Encrypted owner corrections, dependency review and unified history

- NexaTaskLifecycle.kt implements compare-and-swap owner corrections using
  plan_revision, encrypted replacement goals, transactional invalidation of
  unclaimed draft steps, and append-only redacted revision events. Corrections
  are blocked for cancelled, mandated, approved, observed or claimed work.
- The foreground-only native bridge now exposes owner review details and
  revision-safe correction; Tools shows step/event metadata without exposing
  encrypted tool arguments or screen/contact contents.
- NexaTaskStore.reviewDependencies decrypts edges inside Kotlin only, returns
  blockers and always sets executable=false; satisfied dependencies never grant
  permission to dispatch a device action.
- NexaUnifiedTaskTimeline.kt provides a unified read-only index across native
  agent_tasks and legacy voice_task_checkpoints. Legacy voice checkpoints
  remain distinguishable and do not count as verified execution evidence.
- normalizeMultiStepPlannerProposal accepts a provider envelope with version
  and steps, binding all authority and IDs from native-trusted context.
  This is safe plan normalization, not generic provider execution.
- Android regression sources were added for optimistic concurrency, legacy
  separation, mandatory denial, dependency blockers, and owner redaction.
- Local source checks at Development 79c1f3a0e82d: 392/392 Node tests and
  TypeScript PASS. Native compilation on later revisions is not implied.

Remaining development: make the legacy Voice cursor, Chat, overlay, and system
assistant share one final Kotlin task coordinator, enforce approvals and
outcome evidence per action, migrate authorized screen/messaging adapters,
and complete controlled memory and rollback integration.

Execution feature flags remain OFF, no auto send, no permission bypass.
The Nexa Android APK is not a Docker Compose service; no unrelated production
containers were modified.
