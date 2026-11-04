# Nexa generic input box discovery and owner-reviewed text entry

## Goal

Examples:
- "Hey Nexa, find the input boxes on my screen."
- "Hey Nexa, type project update into the Search box."
- "Fill the Notes field with Meeting starts at 2 PM."

Nexa must identify a single visible editable field in the opened app, then type the user's exact requested text only after review. It must not click Send, Submit, Enter, Pay or save automatically.

## Implementation

1. packages/agent-core/src/text-entry-intent.ts parses user text into a field label and exact text for review, rejecting missing labels, multi-action requests and sensitive targets.
2. Sarvam and OpenAI owner transcriptions pass through NexaVoiceTextIntent.kt, a deterministic parser. They reach React Native's Tools review, not an unreviewed action executor.
3. React Native shows the exact text, target field label and a Prepare approved text entry button. Android Accessibility and floating overlay permissions require manual user grants.
4. The owner navigates to the intended external app. Find visible input boxes performs a bounded read-only scan of accessible nodes; it displays only safe empty field names, never field contents or screenshots.
5. Find input box requires exactly one matching, visible, enabled, empty, editable, non-password node that supports Android ACTION_SET_TEXT. It uses genuine Accessibility hints, descriptions or resource IDs, not coordinates.
6. Approval captures the external app package, window ID, field resource ID, class and bounds and expires after 25 seconds. Confirm type text rechecks the same app and field, then commits an encrypted one-time action claim before requesting ACTION_SET_TEXT.
7. A subsequent local check can verify the exact value. Ambiguous/sensitive nodes, lost permission, changed windows, expired text and journal failures refuse or report uncertain outcomes without retries.
8. No Send, Submit, keyboard Enter, clipboard, contact scraping, provider-driven typing or automatic app permission grants are implemented. Staged text remains only in process memory for up to 90 seconds.

## Exclusions and limitations

Password, passcode, OTP, recovery, banking/card/payment, recipient, phone/email address, transfer and submit controls are excluded. Existing field text is not overwritten. An inaccessible/custom UI widget is reported as unavailable. In unusual applications, text changes can have side effects; those must be separately accepted before use.

## Tests and acceptance

- Pure Node tests check exact text, named-field parsing and unsafe/multi-action denial.
- Kotlin instrumentation tests check field policy, expiry/replay, disconnected Accessibility and spoken handoff.
- The experimental Maestro flow at .maestro/experimental/13-reviewed-generic-text-field.yaml checks the review UI only.
- Physical-device acceptance is still required to demonstrate real external-app ACTION_SET_TEXT, correct visible text and no Send/Submit. Emulator policy tests do not prove proprietary app compatibility.

This feature remains under PR #86, not merged into the stable application or released until native, Maestro and real-device gates are met.
