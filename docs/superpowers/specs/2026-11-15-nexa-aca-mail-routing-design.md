# Nexa ACA Read-Only Mail Routing Design

Date: 2026-11-15
Status: implementation authorized by the user's existing instruction to proceed without repeated confirmations.

## Existing behavior

Nexa Chat has a dedicated read-only ACA connection. For recognized summary prompts it calls the ACA mobile mail-summary endpoint directly. ACA's mobile endpoint and web Live Chat use the same agent-scoped getMailReportEvidence query and summary formatters, so supported prompts read the same synchronized mailbox data. The mobile endpoint intentionally returns at most 50 messages and marks larger reports partial; ACA Live Chat can return up to 500.

The mobile prompt parser currently recognizes only a narrow set of explicit 24-hour and unread prompts. An email read request outside that narrow set falls through to Nexa's general LLM/device planner even though that path has no ACA mailbox tool. This can produce an unsupported or misleading answer. The web Live Chat has additional scope parsing, but its browser session/history is not currently shared with Nexa.

## Chosen change

Keep the existing server-side, read-only ACA mobile API and its account, organization, agent, permission, tool-allowlist, and mailbox-binding checks. Do not send mail content to Sarvam or another provider.

1. Interpret ordinary “recent/latest emails” read requests as a rolling 24-hour summary, matching ACA's rolling-window semantics.
2. Classify other explicit email read/report requests separately from unrelated prompts. If Nexa cannot safely express the requested scope through the current mobile API, answer with the supported read scopes and state that no mailbox query was run. Do not pass unsupported email read/write requests to the general planner.
3. Preserve the user's wording in the local Nexa transcript, but do not add an ACA server conversation or sync history in this change.
4. Preserve read-only behavior. Draft, send, reply, forward, delete, archive, and other mutations remain unsupported here.

## Why this boundary

It reuses ACA's existing authoritative mailbox query and authorization path while avoiding a second chat runtime, browser-cookie dependency, shared-history migration, or exposure of email bodies to Nexa's configured model provider. “Same ACA Chat” therefore means the same authorized synchronized data and report semantics for supported summary scopes, not the same ACA conversation thread.

## Verification

Add tests before implementation for recent-email normalization, unsupported-scope interception, write-intent refusal, and normal non-mail prompt routing. Run the focused Nexa ACA tests, the project test suite, and typecheck. Separately, compare the exact 24-hour prompt in authenticated ACA Live Chat and Nexa only after the user signs in privately; record counts, scope, sync freshness, and partial status without copying message content into logs.
