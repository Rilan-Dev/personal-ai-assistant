# Nexa–ACA Business Capability Parity Design

Date: 2026-11-16
Status: Owner-approved direction; revised with the shared identity/data/configuration requirement. Implementation remains behind the scoped plan and Development-only verification gates.

## Intent

Make Nexa a first-class ACA client so an owner can use Nexa Chat, the floating overlay, and later live voice to access the same authorized business-agent workflows and quality of results available in ACA Chat. Within each deployment environment, ACA and Nexa business workflows use the same canonical ACA identity, business database, agent/provider/account configuration, and backend service plane. Development and production remain separate environments; QA starts with isolated Development/test data. Keep Android device control in Nexa’s native, owner-reviewed execution plane.

The first delivery order is Nexa-to-ACA business access. ACA-to-Nexa device control is a later, separately reviewed milestone.

## Current state established from source

- Nexa is an Android-first React Native/Expo application with a local agent harness, optional remote planner, Android-native execution and approval controls, and a separate device journal.
- Nexa’s ACA bridge currently supports a mobile session, authorized organization/agent targets, and a read-only mail summary endpoint. Nexa does not yet expose ACA’s full agent chat, search/filter, scheduling, knowledge, and WhatsApp Business workflows.
- ACA is a private TypeScript monorepo. Its business runtime, email-agent runtime, database adapter, shared contracts, web API and worker are separate packages/apps.
- ACA Chat’s server route uses the ACA agent runtime and email, WhatsApp and schedule tool runtimes. The repository also contains APIs for mail actions, WhatsApp approval queues, agent-scoped schedules, and mobile session/targets/mail-summary access.
- In ACA Development, mobile login reuses ACA’s existing PostgreSQL-backed email/password identity and credential verifier. It creates a `nexa_m1_` device-only bearer session recorded through the same auth-session repository; the browser cookie and device token remain separate, independently revocable sessions for the same ACA user ID. The mobile endpoint is protected by `NEXA_MOBILE_EMAIL_ENABLED` and the `NEXA_MOBILE_ALLOWED_EMAILS` owner canary.
- The existing Nexa integration plan already establishes the key boundary: ACA owns organization identity and business data; Nexa owns on-device execution and Android permissions.
- Nexa also has a local Android database for device conversations, action journals, and local state. That database is not ACA’s business database and must not become a second writable copy of ACA business records.
- The local Nexa Development checkout has extensive existing uncommitted changes. Implementation must preserve them and must not reset, replace, or overwrite unrelated work.

## Goals

1. Give Nexa authenticated, organization- and agent-scoped access to the business capabilities ACA already runs.
2. Have ACA’s server execute business tools so ACA web and Nexa use the same canonical user/org/agent records, business database, connected business accounts, policies, tool registry, and provider configuration.
3. Support concise answers and user-facing progress across Nexa Chat and floating overlay from the first read-only parity milestone; add live voice only after the same business-turn contract works in text.
4. Add read workflows first, then separately gated business writes and schedules.
5. Keep tenant isolation, tool permissions, audit, revocation, idempotency, and human review consistent with ACA.
6. Keep personal Android actions separate and authorized by Nexa’s native executor.
7. Maintain one versioned mobile API and contract test suite so ACA changes cannot silently break Nexa.
8. Keep each agent’s selected model/provider, provider-secret reference, configuration revision, tools, prompts, memory scope, and harness behavior isolated from other agents.
9. Use the ACA Development deployment and synthetic/test accounts for implementation and QA. ACA web and Nexa share that environment’s canonical database; Production data and credentials are not used in development verification.

## Non-goals

- Copying or vendoring the ACA web app, worker, database package, credentials, or whole runtime into the Android bundle.
- Connecting the Android app directly to PostgreSQL, Qdrant, RabbitMQ, Redis, provider databases, or private network services. The ACA backend and workers use the same canonical ACA database and service clusters for both web and Nexa requests.
- Pointing development builds or test scripts at the Production ACA database or using Production business records as fixtures.
- Treating a valid organization membership as blanket authorization for every tool or agent.
- Removing owner review for outbound email/WhatsApp actions or Android-native actions.
- Giving ACA backend services arbitrary Android Accessibility, tap, scroll, text-entry, notification, or permission-grant access.
- Merging private Nexa device conversation history with ACA organization history without explicit identity, visibility, retention, and consent design.

## Approaches considered

### A. Vendor-copy ACA functionality into Nexa

This would duplicate server code and data access in a second product. ACA’s database, worker, provider credentials, and organization services are not appropriate to embed in a phone. The copies would diverge and would create a larger credential and tenant-isolation risk. Rejected.

### B. ACA remains the canonical server; Nexa is a scoped client

Expose versioned, authenticated mobile APIs that call ACA’s existing agent runtime and tool runtimes. Nexa sends a user turn and receives the same server-generated answer/tool events as ACA Chat. The API reuses ACA authorization, data, audit, approval, and worker paths. Nexa keeps device-only actions local. Recommended and selected by the owner’s instruction to use the approach that best preserves ACA behavior.

### C. Extract a shared runtime repository before integration

Move portable contracts and business orchestration into a separately versioned package consumed by both repositories. This may improve source reuse after module boundaries are proven, but it is a larger first step and does not eliminate the need for a secure server API. Defer extraction; first establish parity through the existing ACA service boundary. Extract only tested, portable code later, without moving database or secret-bearing adapters into mobile.

## Target architecture

User request in Nexa Chat, overlay, or later voice
→ Nexa mobile client with the selected ACA organization and agent
→ authenticated, versioned ACA mobile conversation API
→ ACA identity and per-request organization/agent/tool authorization
→ the same ACA agent runtime and business tool runtimes used by ACA Chat
→ the same environment-scoped ACA PostgreSQL data and connected-account records as ACA web, plus that environment’s shared Redis, RabbitMQ, and Qdrant services through server-side adapters
→ streamed answer and structured tool/action events back to Nexa.

Business tools never execute in the Android process. Android device tools never execute in ACA’s worker. If a request needs both business and phone actions, the orchestration must produce distinct, typed steps and each execution plane independently validates and authorizes its own step.

The ACA repository is private. All database and infrastructure access remains server-side. Qdrant and RabbitMQ are internal implementation services behind ACA APIs, not client APIs.

## Canonical identity, data, and configuration

- ACA’s identity/user table, organization memberships, role permissions, agent records, connected business accounts, provider configuration, business conversations, audit records, and synced business data are the single server-side source of truth used by ACA web and Nexa in the same deployment environment. There is no mirrored Nexa business database and no dual-write sync.
- “Same login” means the same ACA user ID, existing ACA credential record/authentication path, organization membership, role, and agent authorization. The current Development path verifies the existing ACA email/password on the ACA server, then issues a separately revocable `nexa_m1_` device token whose hash is stored in ACA’s existing auth-session repository. Browser cookies stay in the browser; do not copy browser cookies to Android or share a bearer token between devices. Do not add a second identity provider or duplicate password store as part of this integration.
- New business conversations initiated from Nexa are stored in ACA’s conversation system and are available in ACA when the existing visibility policy allows it. Historical Nexa-local conversations are not bulk-migrated or exposed to ACA automatically.
- ACA email, WhatsApp Business, and other connected-account credentials remain in ACA’s encrypted credential store. Nexa reads account metadata and executes operations only through APIs; it never receives provider tokens or database credentials. Personal SMS/WhatsApp accounts on the phone remain a distinct device boundary and are not silently linked to ACA Business accounts.
- ACA’s current Chat conversations are organization-and-agent scoped, not private to the user who created them. A member with the existing `organization.read` access can see that agent’s organization conversation history. Nexa-created business messages will follow that same visibility policy; the app must not describe those conversations as private-to-user. Changing this policy requires a separate owner-approved migration and a plan for existing shared history.
- ACA’s provider/model configuration for a selected business agent is canonical in ACA. Both ACA and Nexa business turns resolve the same organization, agent, provider, model, prompt, policy, and published configuration revision. Authorized Nexa users may view or edit configuration only through the same role-checked ACA APIs and review/publish flow; secret values are never returned to the app.
- Every run resolves an immutable agent configuration snapshot and an explicit agent-scoped tool allowlist. No Nexa client preference, global default, or other agent’s provider setting may override it. An update to Agent A must not mutate Agent B’s tools, model, policy, prompt, account bindings, or live run.
- ACA web and Nexa requests use the same ACA deployment and therefore the same environment’s PostgreSQL deployment/schema and server-side Redis, RabbitMQ, and Qdrant service plane. Development and Production have separate credentials/data; both clients share the same environment when using that environment. Namespaces, queue messages, cache keys, vector filters, and idempotency records must include the applicable organization, agent, and configuration/run scope. Existing ACA services remain the only clients with infrastructure credentials.
- Nexa’s encrypted local journal remains for phone-side action approvals, execution evidence, and device-only state. It is not a second business database. ACA business history and data required across devices must be read and written through ACA’s canonical APIs.

### Full turn parity requirement

The mobile API must invoke the same complete ACA Chat turn orchestration used by the web client. Sharing only the agent runtime package or tool handlers is insufficient if prompt/context assembly, conversation state, retrieval, routing, tool gating, summaries, citations, or result formatting remain different. Refactor the web route behind a shared server-side turn service if necessary; keep web-cookie and Nexa-device authentication as separate adapters that resolve to the same authorized principal and turn service. Nexa may render the returned answer and progress in its own UI, but must not create a second business planner.

## Capability rollout

### Milestone 0 — Baseline and API contract

- Build a feature-parity inventory from current ACA Development source and current Nexa source, including mail search/read/summary, agent chat/tools, schedules, WhatsApp Business, knowledge retrieval, and voice.
- For each capability, record the ACA tool/API, required permission, organization/agent/account scope, side-effect class, Nexa surface, failure behavior, and parity test.
- Verify the ACA Development API deployment, isolated test database/accounts, and service configuration without copying secrets. Confirm ACA web and Nexa resolve the same canonical records within that environment and document which records/namespaces are authoritative for identity, agents, provider configs, connected accounts, conversation history, mail, schedules, WhatsApp Business, and retrieval. Never point this milestone at Production.
- Preserve the existing ACA identity provider and auth tables: same ACA account credentials, same user ID, and separate revocable browser/device sessions. Keep the mobile feature gate and owner allowlist fail-closed.
- Add tests proving ACA web and Nexa resolve the same user/org/agent/configuration IDs and that agent-specific configuration changes remain isolated.
- Add a mobile-facing capability discovery contract. Do not infer capabilities from UI labels or model text.
- Use the existing mobile session/targets/summary routes as the baseline, not as proof that all business workflows are already available.

### Milestone 1 — Read-only ACA Chat parity in Nexa Chat and overlay

- Add an ACA mobile conversation/turn API that authenticates the current device session and calls the same complete ACA Chat turn service, including the existing web-chat context, prompts, retrieval, conversation handling, tool policy, and result formatting.
- Carry explicit organization ID and agent ID on every request; re-check active membership, current agent state, channel/account bindings, policy, and tool permissions at execution time.
- Resolve the same published, immutable ACA agent configuration revision and server-side provider secret reference as ACA Chat. Nexa must not submit an alternate provider/model/prompt/tool registry for a business turn.
- Support server-streamed assistant text, tool-start/result events, cancellation, and stable conversation/run identifiers. Return only minimized, UI-safe tool results.
- Make both Nexa Chat and floating overlay use this same business turn API. Their UI may differ, but a given prompt must reach the same ACA turn service and receive the same business answer/tool outcome.
- Expose every read-only business tool that is already available in ACA Chat and permitted for the selected agent. The parity inventory defines the exact set. The initial acceptance set must include supported email search/filter, thread/read, concise summary with count and sync/partial-coverage metadata, and the ACA Chat read tools for schedules, WhatsApp Business, and knowledge when those tools are registered and authorized.
- Have Nexa render short human-style summaries and source/freshness caveats without replaying raw email bodies or large reports.
- Keep unsupported scopes explicit: no silent handoff to a planner that cannot access the requested ACA business data.
- Keep business writes, schedule mutations, WhatsApp sends, and live voice out of this milestone; they require their later review and acceptance gates.

### Milestone 2 — Business actions and scheduling

- Add typed action proposals for email drafts/replies/sends, schedules/automations, and WhatsApp Business send actions.
- Reuse ACA’s current permission checks, account bindings, policy decisions, approval records, audit events, provider checks, and worker dispatch.
- Show exact recipient, channel, content, timing, and recurrence in Nexa before the owner approves an external action.
- Make approvals one-time, bound to the exact proposal and organization/agent, expire safely, and use idempotency keys. Unknown send outcomes must not retry automatically or claim delivery.
- Recheck permissions and provider readiness when the owner approves and again when the worker executes.

### Milestone 3 — Same business experience in live voice

- Add live voice only through a server-authorized ACA business turn/session contract; keep microphone capture, Android foreground-service lifecycle, device actions, and owner approval in Nexa.
- Preserve one conversation/run identity across entry points only where user identity, organization visibility, retention, cancellation, and audit semantics are explicit.

### Milestone 4 — Cross-repository source convergence

- Publish or otherwise version shared, portable schemas and event contracts for mobile capabilities, conversation events, tool proposals, approvals, and outcomes.
- Add consumer-driven contract tests in both repositories and compatibility checks in ACA CI.
- Consider extracting portable agent policy/intent modules into a private package only after a concrete module can run without database clients, provider secrets, worker APIs, or Android APIs.
- Keep ACA API and its authorization checks as the server-side execution boundary.

### Milestone 5 — ACA-to-Nexa device control

- Treat reverse control as a separate design and implementation project after Nexa-to-ACA business parity is accepted.
- Use paired devices, short-lived scoped commands, online-device presence, signed/authorized task envelopes, and visible owner review in Nexa.
- The phone must independently check Android permission state and the exact native action. ACA cannot grant Accessibility, bypass system consent, or silently tap/send on the owner’s device.

## Identity, privacy, and failure behavior

- Use ACA’s existing identity provider, credential records, auth tables, and login verifier for browser and Nexa. In Development, preserve the existing mobile password-login endpoint and device-only `nexa_m1_` token behavior while the full business API is introduced. Keep the browser cookie and mobile bearer separately revocable; never put the password or browser cookie in persistent Android storage. Any later move to OIDC/PKCE requires a separate ACA identity design and migration, not a parallel account system.
- Never store an ACA password, provider API key, database credential, Qdrant credential, or RabbitMQ credential in Nexa.
- Do not expose an ACA provider secret through settings metadata, streamed events, logs, or mobile caches. Keep business provider configuration server-side and resolve it by the selected agent and published revision.
- Scope every request to the authenticated user, active organization, agent, channel, and account binding. Do not accept caller-supplied scope as authority.
- Revoke cached Nexa selections and stop active runs when the server reports session, membership, agent, tool, or account-binding revocation.
- Treat email, documents, WhatsApp content, retrieved records, and tool outputs as untrusted data. They cannot change permissions or authorize actions.
- Do not persist raw email or WhatsApp content in diagnostics. Apply size limits, redaction, safe rendering, and bounded response streaming.
- Surface unavailable/disabled capability, stale sync, partial results, authorization denial, expired session, network failure, cancellation, and uncertain action outcome as distinct states.

## Acceptance gates

- A task parity matrix covers every capability in the agreed first release and maps it to ACA’s existing web behavior.
- ACA web and Nexa resolve the same canonical ACA user, organization, agent, connected-account, provider-configuration, and business-conversation records; no mirrored business database or dual-write is introduced.
- Browser and mobile sessions are separately revocable but map to the same authenticated ACA user and current organization/agent authorization.
- Nexa receives no provider secrets or infrastructure credentials. Tests prove Agent A configuration updates do not affect Agent B, and each run remains pinned to its authorized agent configuration revision.
- Shared Redis, RabbitMQ, and Qdrant usage remains server-side and organization/agent scoped; no mobile client connection or credential is introduced.
- The same supported read-only business prompts in Nexa Chat and the floating overlay reach the same ACA turn service; parity checks cover email search/filter/read/summary and the permitted ACA read tools for schedules, WhatsApp Business, and knowledge.
- ACA web Chat, Nexa Chat, and Nexa overlay use the same complete server-side turn orchestration. Tests fail if one client silently switches to a reduced planner or omits context/retrieval/tool-policy steps.
- Automated contract tests prove Nexa and ACA agree on request/response/event schemas, tool permissions, error semantics, limits, and version negotiation.
- ACA integration tests prove cross-organization, cross-agent, unbound-account, revoked-user, disabled-agent, missing-tool, expired-session, and stale-approval requests are rejected.
- Read tests compare the same synthetic/test-account email prompts in ACA Chat and Nexa for scope, count, source coverage, freshness caveat, and concise-answer metrics without copying private message bodies into artifacts.
- Action tests prove exact proposal binding, approval expiry, idempotency, permission recheck, no automatic retry after uncertain dispatch, and accurate outcome reporting.
- The same tests cover Chat and overlay before voice is enabled; voice requires independent Android/provider acceptance.
- No production capability flag or public release is enabled until the exact source revision passes source tests, typecheck, Android build, API contract tests, emulator/Maestro acceptance, authorized test-account UAT, rollback check, and privacy/security review.

## Constraints and implementation decisions

- Work in Nexa’s existing Development checkout and preserve its current uncommitted changes.
- ACA’s Development repository is not present as a local checkout; inspect its authenticated Development source and create a safe Development checkout before implementation. Keep any local ACA stack/database synthetic and isolated from Production.
- Keep mobile traffic HTTPS-only and use ACA’s existing server deployment; no new public database or queue endpoint.
- Make the ACA API origin an explicit build/environment setting. Tests and Development builds must use an isolated ACA Development endpoint; Production origin is only supplied by its release configuration.
- Use the existing ACA mobile feature gate as the starting rollout control, but add per-user/per-organization canary and rollback gates for broader capabilities.
- Do not update package dependencies or move code between repositories until the API contract and dependency/license review identify a concrete need.

## Open implementation decisions left to the builder

- Exact stream protocol and whether the existing chat stream can safely accept the Nexa mobile bearer session through a dedicated adapter.
- Which email search/filter scopes are safe and stable enough for the first API contract.
- Which business tool outcomes may be returned to Nexa versus rendered/summarized server-side.
- Whether Nexa’s local transcript remains device-only by default or receives an explicit opt-in link to an ACA canonical conversation.

No production API behavior, schema, permissions, or Android controls are changed by this design draft.
