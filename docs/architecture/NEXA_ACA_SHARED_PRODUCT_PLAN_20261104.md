# Nexa AI + AI Comms Agent: shared-product implementation plan
Date: 2026-11-04
Status: Phase 1 source implementation on isolated feature branches; NOT deployed or accepted in production.

## Verified starting point
- ACA repository: Rilan-Dev/ai-comms-agent (Development), deployed frontend https://aca.starcitytechnical.com/.
- Nexa repository: Rilan-Dev/personal-ai-assistant (Development). React Native chat/setup; Kotlin Android tools, encrypted local journal, wake/overlay, native and Sarvam/OpenAI voice.
- ACA holds organization membership/roles, registered agents and email account bindings, email sync DB, unread and rolling mail reports, conversations, provider credentials, approved send workflows and WhatsApp Business integrations.
- Nexa has a distinct on-device action/approval harness, local conversation DB and BYOK provider settings.
- Existing ACA email fast paths require active membership, email.read, agent tool registration, relevant active policy/channel bindings, and report source evidence. Reports use locally synchronized records; they are not proof the mailbox is fully current.

## Integration principle
**ACA is the authoritative business-data/identity backend; Nexa is an additional client.** Keep two separate execution planes:
1. Business actions run via ACA's authenticated organization+agent API with ACA permissions, audit, idempotency and human approvals.
2. Device actions run through Nexa's existing AgentHarness, permissions and Android native adapters. The remote business service MUST NOT gain unrestricted device tool access, and the mobile app MUST NOT connect directly to PostgreSQL.

## Phase 1: email summary in Nexa (source implemented, acceptance pending)
Backend, default disabled:
- NEXA_MOBILE_EMAIL_ENABLED=true is required to expose /api/mobile/v1 endpoints.
- POST /api/mobile/v1/session: ACA account login -> 12-hour device-only bearer token; separate from browser cookies and BYOK keys.
- DELETE /api/mobile/v1/session: revoke that device token.
- GET /api/mobile/v1/targets: list authorized organization + agent choices.
- GET /api/mobile/v1/mail/summary?organizationId=UUID&agentId=UUID&unreadOnly=true&inboxOnly=false&window=all: all-history unread report (up to 50 detailed messages).
- GET /api/mobile/v1/mail/summary?...&window=24h: rolling last-24-hours report (up to 50 detailed messages).
- Uses existing getMailReportEvidence and formatters; user, organization, agent, policy, account bindings, revocation checks retained. No email write API was added.
Mobile:
- Settings > AI Comms Agent · Email lets user enter ACA account credentials once, select authorized org/agent, disconnect.
- Password is never persisted. Device token encrypted with Android Keystore AES-GCM under a separate key; not the provider key.
- Read-only chat requests like “Summarize unread emails from the last 24 hours” route to this HTTP client; other requests, approvals, native screen tools, live voice transport and BYOK provider settings continue to follow original paths.
- Requires an Android APK rebuild and native runtime verification; the code is not in installed production APKs until merged and released.
- For confidentiality, full email text exists only in the response and visible Nexa chat (which is also locally archived by Nexa); do not feed it to arbitrary model providers by default.

### Acceptance gates BEFORE enabling flag / deploying
- Password rotated: credentials accidentally pasted in chat must no longer be in use.
- User can login with Android test build; no session cookie leaks; logout, expiry, revoked membership and lost device behave properly.
- User A cannot view organization B's agents or messages; user with missing email.read, missing tool allowlist, removed account binding, paused agent is blocked.
- Agent A cannot query Agent B's linked mailboxes; mismatch is rejected.
- 24h vs no-time-limit unread reports match ACA web (counts, metadata, sync freshness and partial coverage).
- No auto-draft/send or WhatsApp mutation can occur from read-only route. Native tools remain operational.
- Test under offline state, token expiry, network loss, repeated taps, logout and device rotation; avoid showing sensitive text in crash logs.
- Run Android compile/emulator/Maestro acceptance AND authorized real-account tests; verify existing device, screen, overlay and wake workflows.
- Feature flag stays OFF until gates pass. Never add production secrets to GitHub.
- Private test environment first, canary to owner only, compare metrics and rollback by disabling the flag/releasing previous APK.
- Confirm backend tests and mobile source tests. Distinguish these from actual provider and physical Android testing.

## Phase 2: common user/organization identity
- Move from password-based v1 device exchange to OIDC Authorization Code + PKCE (system browser), device-bound refresh session, per-device revocation, OAuth scopes.
- ACA organizations remain canonical; mobile may select several orgs but queries and resources always scoped to explicit active org.
- Strong tenant isolation with integration tests; privilege revocation at request/action execution time. Migrate accounts through explicit account linking, not email-string implicit merges.

## Phase 3: unified business chat and history
- Build versioned client API for ACA conversations/messages/threads, streaming and tool event envelopes; authorize organization+agent, support resume/cancel, durable audit, optimistic concurrency and read-only annotations.
- Keep Nexa device-only encrypted chat journal separate, or sync permitted user messages with explicit consent; no direct database table sharing from Android.
- Do not merge historical sessions without a reversible identity/org mapping and per-message visibility.

## Phase 4: voice and provider configuration
- ACA maintains provider configurations, but does not send raw organization provider API keys to phones.
- Server-minted ephemeral voice session authorization / relay where provider supports it; enforce org/user/agent scope, per-session limits, provider-specific credentials, consent, and encryption.
- Nexa retains Android microphone/foreground/assistant/wake orchestration. Voice transcript -> authenticated ACA business turn, while tool calls for Android stay device-side. Never silently replace Sarvam/OpenAI selections or key.
- Test microphone capture, websocket reconnect, cancellation, tool permission boundaries, privacy logging, TTS and end-to-end real provider sessions.

## Phase 5: drafts, email sends and WhatsApp
- Expose scoped server orchestration: read -> prepare draft -> show review in both clients -> one-time human approval -> send -> verify outcome and audit.
- Reuse ACA mail permissions, idempotency, sender identity, provider capability checks and approval records; Nexa's WhatsApp device handoff remains a distinct flow from ACA WhatsApp Business.
- Never auto-send. Handle duplicates, expired approvals, revoked roles, missing provider grants, partial failures, cross-org recipient and contact collisions.
- Roll out per capability after independent UAT and rollback.

## Phase 6: converged product and operations
- Single product identity and org switcher, shared business inbox, tasks, commitments, reminders, audit, provider setup; one mobile and one web frontend.
- Shared PostgreSQL is accessed by ACA services only; schema migrations/backup/recovery protected. Avoid bulk DB copying or dropping Nexa's local state.
- Continuous contract tests for mobile API compatibility, migration safety, background voice/device permissions, tenant-security and approval flows.

### Current verified evidence
- ACA web typecheck passed; 19 focused Vitest tests passed (15 existing unread reports and 4 new mobile boundary tests).
- Nexa root/app/agent-runtime TypeScript typecheck passed; full Node test suite 338/338 passed.
- Android APK compilation, emulator/physical phone tests, authenticated live ACA login/mailbox tests, production deploy and feature toggle were NOT performed.
