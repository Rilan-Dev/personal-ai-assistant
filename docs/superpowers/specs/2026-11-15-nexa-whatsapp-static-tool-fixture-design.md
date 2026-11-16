# Nexa WhatsApp Reviewed Send Tool and Static Fixture

## Goal

Enable the requested WhatsApp message workflow through Nexa's existing owner-sequenced Android tools, with Send guarded by a separate exact one-time approval. Add a deterministic offline fixture that tests the entire tool sequence without touching a real contact.

## Design

- Add `screen.send_whatsapp` as a dedicated WhatsApp-only tool. It is high-risk, requires a separate one-time owner approval, and is bound to the exact recipient and exact reviewed draft. Do not expose Send through generic `screen.tap_label`.
- The native executor must re-check the exact WhatsApp package, exact visible chat header, exact non-empty composer text, and one unique accessible Send button adjacent to that composer; it must use the existing pause fence and durable no-replay journal before clicking.
- After dispatch, report success only when the same chat header remains, the composer is clear, and the exact message is visible as a conversation bubble. Never claim delivery/read status. If evidence is uncertain, do not retry.
- Store a Sarvam-compatible function-call response with `message.content: null` and a synthetic full owner request using a synthetic contact/message. Expected native tools are `device.open_app`, `screen.open_whatsapp_chat`, `screen.compose_whatsapp`, and `screen.send_whatsapp`.
- Run the owner request through the existing `createOwnerSequentialPlanner` and `AgentHarness`, using fake native outcomes. Verify a separate approval and status/result event per action. A send without a preceding verified matching draft must be rejected atomically.
- Keep contact/message values synthetic and keep provider credentials out of the fixture and repository.

## Boundaries

- The fixture is test-only and never sends a real message. Live WhatsApp acceptance is separate.
- The production send action remains owner-reviewed; model output, screen text, or test data is never approval.
- No device screen, contact database, conversation history, API key, or outbound message is read or changed by the fixture.

## Acceptance

- The provider fixture is accepted as exactly one native Sarvam function call with no direct response text.
- The full owner sequence produces the four expected Android tool IDs and arguments in order.
- Each action pauses for durable approval in the harness and advances only after structured evidence passes; Send evidence never implies delivery.
- A Send request without the exact preceding recipient-bound draft is rejected atomically; no tool executes.
- The focused regression test and root Node test suite pass.
- Android native checks pass, and any physical send test uses a separate explicit owner approval for its exact real recipient/message.
