# Nexa AI + AI Comms Agent: shared-product implementation plan
Date: 2026-11-04
Status (2026-11-05): Phase 1 read-only source merged; ACA web deployed with owner-only controls and mobile flag OFF. Signed Nexa release and emulator QA verified at prior SHA; live account/physical-phone parity not accepted. Full shared product phases 2–6 remain outstanding.

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

### Verified progress and release gates — 2026-11-05
**Implemented and verified in source**
- ACA mobile v1 session, organizations/agents and read-only summary API merged to `Development`; current ACA production web image at `d1cda78b` serves HTTP 200 with `NEXA_MOBILE_EMAIL_ENABLED=false` and anonymous mobile targets HTTP 404. No production database migration or unapproved sending was performed for mobile.
- ACA owner-canary allowlist is default-deny; inactive agents blocked even with configuration revisions OFF; mobile responses disclose their actual 50-message detailed-evidence cap. Latest API/web validation: TypeScript PASS; **1592/1592** tests across 247 files PASS. ACA PRs #69–#72.
- Nexa source preserves Android AgentHarness, permission-grant model, WhatsApp review-only flow, voice providers and local journal. Existing signed APK `714b175b` passed native instrumented acceptance and **12/12** default Maestro Cloud flows ([#41804370133](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41804370133)); separate WhatsApp local review-only Cloud test passed **1/1** ([#41848990853](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41848990853)). No actual WhatsApp message was sent.
- Nexa subsequent ACA organization-discovery rollback fix was merged in PR #88; **341/341** source tests and TypeScript checks passed. A new signed APK for that exact source was dispatched as [#41911002876](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41911002876); do not attribute earlier Maestro results to the newer APK without a matching run.

**Not yet accepted / must not be presented as complete**
- Rotate the ACA password exposed in the original chat before broad access. Do not embed the credential in GitHub or the app, and do not enable the owner canary before authenticated acceptance.
- Install the latest signed Nexa APK on a real Android phone, log in with a freshly rotated ACA credential, confirm authorized organization+agent selection, and compare 24-hour and all-history unread counts/report freshness against ACA web. Verify explicit logout, expired/revoked sessions, unauthorized org/agent rejection, offline and retry behavior.
- Actual physical Android voice-provider/microphone sessions, wake-phrase reliability, external-app screen tapping, notification shade, WhatsApp contact selection and provider-approved sends need independent human-observed acceptance. The separate notification-shade Maestro experimental test remains NO-GO; 12/12 default flows do not certify it.
- Unified SSO/PKCE, canonical cross-client chats, shared provider configuration and realtime voice sessions, email/WhatsApp reviewed send APIs, shared product operations and Phase 2–6 are roadmap items, not delivered. Keep ACA PostgreSQL server-side and Android device actions separate.
- ACA host disk free space was under 5 GB after recent image builds: preserve one tagged rollback image and prioritize safe capacity remediation before more production rebuilds.

**Deployment gate:** keep `NEXA_MOBILE_EMAIL_ENABLED=false` until password rotation, authenticated owner-account/mobile mailbox parity, permissions/revocation tests, and signed-APK acceptance are recorded. Only then enable a one-user canary with `NEXA_MOBILE_ALLOWED_EMAILS` explicitly set, verify health and rollback by disabling the flag.
