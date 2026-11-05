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
