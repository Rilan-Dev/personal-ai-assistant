# Nexa–ACA Canonical Business Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make Nexa Chat and floating overlay use the same ACA user, organization, agent configuration, business records, and complete business-turn orchestration as ACA web Chat, with Development-only read-only parity as the first release.

**Architecture:** ACA remains the only business backend and database owner. Nexa authenticates through ACA’s existing identity/session system, then calls a versioned server API that enters the same ACA Chat turn pipeline and resolves the selected agent’s canonical provider/configuration snapshot. Android device actions and Nexa’s device journal remain local; no ACA database, provider secret, Redis, RabbitMQ, or Qdrant credential reaches the phone.

**Tech Stack:** ACA TypeScript/Next.js, `@ai-comms-agent/db`, `@ai-comms-agent/agent-core`, ACA Vitest; Nexa React Native/Expo TypeScript, Android Keystore-backed session vault, Node built-in test runner, existing Maestro flows.

**Spec:** `docs/superpowers/specs/2026-11-16-nexa-aca-business-parity-design.md`

**Execution status (2026-11-16):** Tasks 1–5 are implemented and covered by source tests. Synthetic ACA/Nexa parity tests pass. Local Android QA2 build and fail-closed Chat/overlay flows pass. Live Development parity remains pending because this workspace has no isolated ACA Development API origin, identity, database, or service configuration; the current QA APK uses the non-routable `https://aca-unconfigured.invalid` sentinel.

## Global Constraints

- Development and Production remain separate ACA environments; all implementation and QA use isolated Development/test data.
- Make source changes in `D:\RiluRaafu\personal-ai-assistant` for Nexa and a separate `D:\RiluRaafu\ai-comms-agent` Development checkout for ACA; do not write ACA source through the GitHub API.
- Within one environment, ACA web and Nexa use the same canonical PostgreSQL records and the same ACA server-side service plane; no mirrored business database or dual-write sync is added.
- ACA business conversation history follows its current organization-and-agent visibility policy; it is not private to the creating user. Do not claim user-private history or migrate old Nexa-local transcripts.
- Reuse ACA’s existing identity and credential records; browser cookies and Nexa device tokens stay separate and independently revocable.
- The mobile feature gate and email allowlist remain default-deny; do not enable a Production canary in this project.
- Nexa must not connect directly to PostgreSQL, Redis, RabbitMQ, Qdrant, or provider APIs for ACA business turns.
- The server selects provider/model/prompt/policy/tools from the requested authorized ACA agent and pins that configuration revision for the run; ignore Nexa-supplied provider overrides.
- The first release exposes only authorized ACA read tools. Business writes, schedules, WhatsApp sends, voice, reverse device control, and migration of old Nexa transcripts remain separate milestones.
- Preserve current Development worktree changes and keep the Android task coordinator, local harness, owner reviews, and device journal behavior intact.

## Review Focus

- A missing, expired, revoked, or disallowed device session must fail closed and must never fall back to Nexa’s local planner for a business request.
- A caller-supplied organization, agent, conversation, or account outside the authenticated user’s current authorization must be rejected before data access.
- A business write request sent to the first read-only API must stop before any draft, schedule, approval, or external side effect is created.
- A Nexa-local provider setting or Agent A configuration must never change ACA Agent B’s provider, prompt, tool allowlist, memory scope, or active run.
- Missing/malformed API origin, oversized stream events, partial mail coverage, cancellation, and server failure must be visible and must not send business data to another provider.

---

### Task 1: Establish an explicit ACA Development API origin

**Files:**
- Create: `apps/mobile/src/native/acaConfig.ts`
- Modify: `apps/mobile/src/native/acaEmail.ts`
- Modify: `apps/mobile/android/app/build.gradle`
- Modify: `apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt`
- Modify: `.github/workflows/android-main-promotion.yml`
- Modify: `.github/workflows/android-release-test.yml`
- Modify: `scripts/acceptance/nexa-message-feature-qa.sh`
- Modify: `scripts/ci/run-nexa-emulator-acceptance.sh`
- Modify: `scripts/test/nexa-aca-email-source.test.mjs`
- Modify: `scripts/test/nexa-aca-client-boundary.test.mjs`
- Create: `scripts/test/nexa-aca-api-origin.test.mjs`

**Interfaces:**
- Produces: `resolveAcaApiOrigin(value: string | undefined, development: boolean): string` and `getAcaApiOrigin(): string`.
- Both Expo JS and Android Keystore validation use `EXPO_PUBLIC_ACA_API_ORIGIN`; Gradle writes that exact origin to `BuildConfig.NEXA_ACA_API_ORIGIN`. An unset Development build uses `https://aca-unconfigured.invalid` and cannot make a real ACA request. Release builds require an explicit origin. Accept HTTPS origins; allow HTTP only for loopback in Development. Reject credentials, path, query, and fragment.
- `acaEmail.ts` obtains its origin from this module for login, targets, summary, and logout; a saved session is valid only for the configured origin.

- [x] **Step 1: Write origin validation tests** in `scripts/test/nexa-aca-api-origin.test.mjs` for HTTPS Development/Production origins, Development loopback, the fail-closed unconfigured sentinel, malformed URL, HTTP non-loopback, embedded credentials, and non-root path. Extend `nexa-aca-client-boundary.test.mjs` to verify the bearer session origin matches the configured host.
- [x] **Step 2: Run the focused test and confirm it fails**

Run: `node --experimental-strip-types --test scripts/test/nexa-aca-api-origin.test.mjs`

Expected: FAIL because `acaConfig.ts` and its exports do not exist.

- [x] **Step 3: Implement origin validation** in `apps/mobile/src/native/acaConfig.ts`; set `BuildConfig.NEXA_ACA_API_ORIGIN` in `build.gradle`; update `AgentJournalModule.kt` to accept sessions only for that exact build origin; and update `acaEmail.ts` so every request and saved-session check uses the same configured value. Keep the Production URL only in `android-main-promotion.yml`; set a non-routable Development host for test-only release artifacts and require explicit Development origins in the physical/emulator acceptance scripts.
- [x] **Step 4: Run the focused test and existing ACA source regression**

Run from the Nexa repository: `node --experimental-strip-types --test scripts/test/nexa-aca-api-origin.test.mjs scripts/test/nexa-aca-email-source.test.mjs scripts/test/nexa-aca-client-boundary.test.mjs`

Expected: PASS; tests use mock/synthetic origins and make no network request.

### Task 2: Reuse ACA Chat’s complete turn handler behind a mobile read-only adapter

**Files:**
- Create: `apps/web/src/lib/chat/chat-stream-handler.ts`
- Modify: `apps/web/src/app/api/chat/stream/route.ts`
- Create: `apps/web/src/app/api/mobile/v1/chat/stream/route.ts`
- Create: `apps/web/tests/nexa-mobile-chat-boundary.test.ts`
- Create: `apps/web/tests/nexa-mobile-chat-tenant.test.ts`

**Interfaces:**
- Produces: `handleChatStreamPost(request: Request, options: { identityAdapter: IdentityAdapter; readOnly: boolean }): Promise<Response>`.
- The existing web `POST` retains same-origin protection and delegates to `handleChatStreamPost(request, { identityAdapter: getIdentityAdapter(), readOnly: false })`.
- The mobile `POST` rejects browser `Origin`, non-JSON, oversized request bodies, and malformed inputs; it delegates with `new NexaMobileIdentityAdapter()` and `readOnly: true`.
- The mobile route is default-deny behind both the existing mobile-session gate and a new `NEXA_MOBILE_CHAT_ENABLED` flag; the session email allowlist remains mandatory.
- Mobile request schema: `{ organizationId: string; agentId: string; conversationId?: string; message: string }`. IDs are selectors only; authenticated ACA authorization remains authoritative.
- The shared handler keeps the existing ACA context/prompt assembly, conversation repository, `prepareAgentConfigurationTurn`, runtime, retrieval, tool authorization, business read tools, response formatting, and SSE event names (`conversation`, `status`, `progress`, `delta`, `done`, `error`).

- [x] **Step 1: Write route tests** proving disabled feature gate, anonymous/invalid token, inactive or cross-organization membership, unauthorized agent, paused agent, and read-only write attempts are rejected before any business tool, business-data mutation, workflow, or worker side effect. The canonical ACA chat may still record the user turn and safe refusal.
- [x] **Step 2: Run the focused ACA tests and confirm they fail**

Run from the ACA repository: `pnpm --dir apps/web exec vitest run tests/nexa-mobile-chat-boundary.test.ts tests/nexa-mobile-chat-tenant.test.ts`

Expected: FAIL because the mobile chat route and reusable handler do not exist.

- [x] **Step 3: Extract the current route body into `handleChatStreamPost`** without duplicating ACA orchestration. Keep web same-origin behavior in the web route. Add a bounded JSON mobile adapter using the existing `NexaMobileIdentityAdapter`, current identity repository, authorization checks, and the same agent repository.
- [x] **Step 4: Enforce the read-only capability ceiling on the server** before business-data mutations, tool calls, workflow creation, or worker dispatch. The normal ACA conversation may persist the user turn and a refusal. Remove mutation tools from the mobile effective tool set and reject explicit email/WhatsApp/schedule mutation prompts; do not rely on a UI or model instruction for this boundary.
- [x] **Step 5: Run the focused tests and web typecheck**

Run from the ACA repository: `pnpm --dir apps/web exec vitest run tests/nexa-mobile-chat-boundary.test.ts tests/nexa-mobile-chat-tenant.test.ts`

Then run from the ACA repository: `pnpm --dir apps/web typecheck`

Expected: PASS; the same authenticated principal resolves to the same ACA `user.id`, while all cross-tenant, revoked, and write attempts fail before a business action.

### Task 3: Pin canonical agent configuration and business conversation scope

**Files:**
- Modify: `apps/web/src/app/api/mobile/v1/chat/stream/route.ts`
- Modify: `apps/web/tests/nexa-mobile-chat-tenant.test.ts`
- Create: `apps/web/tests/nexa-mobile-chat-config-isolation.test.ts`
- Read: `apps/web/src/app/api/chat/conversation/route.ts` and `packages/db/src/conversation/repository.ts` to preserve the current conversation visibility contract.

**Interfaces:**
- Each run is bound server-side to `{ organizationId, agentId, conversationId, userId, configurationRevision, runId }`.
- New business messages and assistant results use ACA’s canonical conversation repository and remain visible under the existing ACA conversation visibility policy. Nexa-local history is not copied.
- Provider, model, prompt, tool allowlist, retrieval scope, connected accounts, and policy are read from the selected ACA agent/config revision; request fields cannot override them.

- [x] **Step 1: Add configuration and conversation tests** proving Agent A and Agent B resolve separate snapshots/tool lists, a Nexa-supplied provider override is ignored, a revoked/paused revision is rejected, an authorized same-organization member can see the new conversation under ACA’s existing policy, and an unauthorized organization cannot read it.
- [x] **Step 2: Run the focused tests and confirm the missing invariants fail**

Run from the ACA repository: `pnpm --dir apps/web exec vitest run tests/nexa-mobile-chat-config-isolation.test.ts tests/nexa-mobile-chat-tenant.test.ts`

Expected: FAIL only for parity invariants not yet enforced by the shared handler.

- [x] **Step 3: Use the same ACA conversation and configuration services as web Chat**; add only the smallest missing organization/user visibility check or revision pin. Do not add a parallel business table, provider configuration row, or synchronization worker.
- [x] **Step 4: Re-run focused tests and web typecheck**

Run from the ACA repository: `pnpm --dir apps/web exec vitest run tests/nexa-mobile-chat-config-isolation.test.ts tests/nexa-mobile-chat-tenant.test.ts`

Then run from the ACA repository: `pnpm --dir apps/web typecheck`

Expected: PASS with one canonical business record and isolated per-agent configuration snapshots.

### Task 4: Add the typed Nexa ACA streaming client

**Files:**
- Create: `apps/mobile/src/native/acaChat.ts`
- Create: `scripts/test/nexa-aca-chat-stream.test.mjs`
- Modify: `apps/mobile/src/native/acaEmail.ts` only to share the validated origin/session request helpers where appropriate.

**Interfaces:**
- Produces: `streamAcaChatTurn(session: AcaSession, input: { organizationId: string; agentId: string; conversationId?: string; message: string; signal: AbortSignal }, onEvent: (event: AcaChatEvent) => void): Promise<void>`.
- `AcaChatEvent` is a discriminated union for the ACA `conversation`, `status`, `progress`, `delta`, `done`, and `error` SSE events. Reject unknown/oversized/malformed payloads and stop on abort.
- Requests send the secure device bearer and opaque organization/agent/conversation selectors only; no provider/model, provider secret, infrastructure credential, or tool-allowlist override is sent.

- [x] **Step 1: Write stream parser and request tests** for valid split SSE frames, multiple events, malformed JSON, unknown event, oversized event, 401/403/404, abort, and exact request scope.
- [x] **Step 2: Run the focused test and confirm it fails**

Run: `node --experimental-strip-types --test scripts/test/nexa-aca-chat-stream.test.mjs`

Expected: FAIL because `acaChat.ts` is absent.

- [x] **Step 3: Implement the bearer-authenticated stream client** using `getAcaApiOrigin()` and the existing Keystore-backed `AcaSession`; never persist stream text outside the current user-visible chat history policy.
- [x] **Step 4: Run the focused stream and origin tests**

Run: `node --experimental-strip-types --test scripts/test/nexa-aca-chat-stream.test.mjs scripts/test/nexa-aca-api-origin.test.mjs`

Expected: PASS without contacting ACA or a model provider.

### Task 5: Route business prompts through ACA in Chat and floating overlay

**Files:**
- Create: `apps/mobile/src/native/acaBusinessIntent.ts`
- Modify: `apps/mobile/App.tsx`
- Modify: `scripts/test/nexa-aca-email-source.test.mjs`
- Create: `scripts/test/nexa-aca-business-routing.test.mjs`
- Delete only after no imports remain: `apps/mobile/src/native/acaMailAiSummary.ts` and `scripts/test/nexa-aca-email-ai-summary.test.mjs`.

**Interfaces:**
- Produces: `classifyAcaBusinessIntent(message: string): { kind: "business_read" | "business_write" | "unsupported_business" } | null`.
- Explicit ACA business prompts route to the server before the general Nexa planner. First release accepts supported read tasks and returns a clear “not enabled in this read-only release” result for writes; it never falls through to the local planner.
- Explicit Android/device tasks and personal SMS/messenger tasks retain their existing owner-reviewed Nexa flow. Mixed business+device tasks stop with a clear request to handle each side separately in this milestone.
- Nexa Chat and overlay continue to use the existing shared `send()` function; no second overlay model or executor is introduced.

- [x] **Step 1: Write routing tests** for email search/filter/summary, schedules, ACA WhatsApp Business, knowledge reads, business writes, Android app-open/scroll/tap, personal SMS/WhatsApp drafts, mixed requests, no ACA session, and overlay using the exact same `send()` callback. Assert business requests never invoke `activeProvider` or `AgentHarness`.
- [x] Include a regression assertion that the ACA connection/settings copy no longer promises to send email excerpts to Nexa’s saved provider.
- [x] **Step 2: Run the focused test and confirm it fails**

Run: `node --experimental-strip-types --test scripts/test/nexa-aca-business-routing.test.mjs`

Expected: FAIL because canonical business intent routing does not exist.

- [x] **Step 3: Implement deterministic business routing in `send()`**; call `streamAcaChatTurn`, render the ACA result/progress, preserve cancellation, and use ACA’s server-selected provider. Keep the local provider and dynamic Android harness on the device-task path only. Remove the local-provider ACA email synthesis path.
- [x] **Step 4: Run focused routing, overlay, and ACA mail regression tests plus TypeScript checks**

Run from the Nexa repository: `node --experimental-strip-types --test scripts/test/nexa-aca-business-routing.test.mjs scripts/test/nexa-aca-email-source.test.mjs scripts/test/nexa-overlay-interactive.test.mjs`

Then run from the Nexa repository: `npm run typecheck`

Expected: PASS; Chat and overlay share ACA business turns while Android tasks retain the original harness and owner-review boundaries.

### Task 6: Verify isolated Development parity and update the operating record

**Files:**
- Modify: `docs/superpowers/specs/2026-11-16-nexa-aca-business-parity-design.md`
- Modify: `docs/project-control/NEXT_ACTION.md`
- Modify: `docs/project-control/WORKLOG.md`
- Create: `apps/web/tests/nexa-mobile-chat-parity.integration.test.ts`
- Create: `.maestro/nexa/17-aca-business-chat-fail-closed.yaml`
- Create: `.maestro/nexa/18-aca-business-overlay-fail-closed.yaml`
- Add only synthetic fixtures under `apps/web/tests/` and `scripts/test/fixtures/`.

**Interfaces:**
- ACA Development web and Nexa call the same test deployment, test identity, test organization, test agents, and synthetic business fixtures.
- No test sends a request to `aca.starcitytechnical.com`, uses a real mailbox or Production database, or performs a real outbound action.
- ACA Development API origin is injected at build/test time. Live parity is not claimed unless the local Development service and isolated database are actually available.
- Maestro business acceptance uses a disposable ACA Development account and synthetic backend fixture; credentials are supplied only by the local runner, never stored in YAML, source, notes, or logs.

- [x] **Step 1: Add an end-to-end parity test** that submits the same synthetic read-only prompt to ACA web Chat and Nexa’s mobile route, verifies the same authorized user/org/agent/config revision and canonical conversation IDs, checks concise coverage-aware output, and confirms no write or external send tool ran.
- [x] **Step 2: Run the isolated ACA and Nexa source/API acceptance suites**

Run from the ACA repository: `pnpm --dir apps/web exec vitest run --config vitest.integration.config.ts tests/nexa-mobile-chat-parity.integration.test.ts`

Run from the Nexa repository: `npm test`

Expected: PASS against synthetic/mocked data only; no Production API or database request.

- [x] **Step 3: Run local typechecks, Android build, and fail-closed UI acceptance**

Run from the Nexa repository: `npm run typecheck`

Run from the Nexa repository: `npm run web:export`

Run from the Nexa repository in PowerShell: `.\apps\mobile\android\gradlew.bat -p .\apps\mobile\android :app:assembleDebug :app:assembleAndroidTest --no-daemon`

Run from the Nexa repository: `maestro test --device emulator-5554 .maestro/nexa/17-aca-business-chat-fail-closed.yaml .maestro/nexa/18-aca-business-overlay-fail-closed.yaml`

Local result: all tests pass on the isolated QA2 emulator build using the non-routable ACA origin sentinel. This proves routing and fail-closed behavior, not a live ACA request.

- [ ] **Step 3b: Run live Development parity** after an isolated ACA Development API, test identity, database, and synthetic fixtures are configured. Verify the same web/mobile user, org, agent, configuration revision, and canonical conversation using a Development origin only. Do not use Production.
- [x] **Step 4: Update `NEXT_ACTION.md` and `WORKLOG.md`** with passed evidence, unresolved service/deployment limits, and later gates for writes, scheduling, live voice, and reverse control.

---

## Deferred projects

- ACA-reviewed email/WhatsApp sends, schedule mutations, approvals, idempotency, and worker dispatch.
- ACA live voice parity and persistent cross-entry conversation sessions.
- Shared agent/provider configuration administration UI and role-checked configuration edits. The first release reads canonical published configuration only.
- ACA-to-Nexa remote device control.
- Migration or exposure of old Nexa-local conversation history.

