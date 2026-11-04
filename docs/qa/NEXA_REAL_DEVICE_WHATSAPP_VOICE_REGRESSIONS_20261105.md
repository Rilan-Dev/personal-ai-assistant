# Nexa real-device WhatsApp/voice regression repairs — 2026-11-05

## Uploaded evidence (owner export; avoid private details in source)

The owner's `nexa-conversations-1793829928331.json` export showed three task records. Two attempts to open a requested WhatsApp conversation were cancelled at step 0. In another session the app reported a successful read-only WhatsApp screen observation containing the desired chat row, followed by a rejected tap on the speech-recognized contact label. Several Sarvam conversation requests returned HTTP 400 without useful sanitized error classifications. Later the assistant incorrectly claimed that no on-device text-entry capability existed. The separate Android process-exit export listed `user_requested` reasons, not classified crashes/ANRs.

Do **not** commit real chat labels, contacts, phone numbers, personal transcript text, API keys, message payloads, screenshots, or raw provider error responses into test fixtures or GitHub.

## Repairs

1. `NexaWhatsAppChatIntent` recognizes the whole owner request **open WhatsApp → find a named contact → open conversation** as a two-step native task. The existing encrypted task checkpoint, foreground package verification, active Android Accessibility grant, and reviewed tool approval are retained.
2. `NexaScreenLabelTap` deduplicates repeated clickable ancestor nodes instead of counting every duplicate child text label, preventing false multi-match results. For an explicitly requested WhatsApp contact it permits one small ASR spelling difference only if the live target is otherwise unique; unrelated guesses and ambiguous rows are still rejected.
3. `NexaWhatsAppChatActions` reads bounded visible UI metadata locally, selects only one uniquely grounded chat row in `com.whatsapp`, claims the encrypted one-time action journal, clicks once, and waits for a **fresh WhatsApp conversation header plus message composer** before reporting the chat open. Android accepting a click alone is not proof. No message read, text insertion or Send operation occurs.
4. After a successfully checkpointed chat task, Nexa explains that the input box is available for **separately reviewed draft entry**. Native message input already supports an explicitly staged exact draft and independent confirmation; no message is sent automatically.
5. `NexaSarvamChatError` turns safe error categories (bad parameter, tool-schema incompatibility, context limit, authentication, throttling) into diagnostics without propagating free-form provider bodies. An HTTP 400 never triggers arbitrary replays. **Only a confirmed tool-schema rejection permits one text-only model fallback**; provider action execution is disabled in that fallback.
6. Sarvam and OpenAI voice guidance now acknowledges existing owner-reviewed text-entry capability instead of claiming all typing is unsupported.

## Verification gates

- Source and TypeScript checks on feature branch: `npm ci`, `npm test` (378/378), `npm run typecheck` passed at SHA `abe241f`.
- New API35 synthetic instrumentation: task grammar, one-letter matching, dangerous/ambiguous name exclusion, actual accessible chat-list rows, duplicate labels, header/composer proof, no Send, and privacy-safe Sarvam error classification. Only nonexported debug-only synthetic screens and invented aliases are used.
- Manual owner-triggered GitHub Actions: https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41930376493 (result pending until verified).
- **Not established by source/emulator tests:** real phone's current APK version, physical Android Accessibility grant, actual WhatsApp contact/chat DOM on the owner's device, live Sarvam-key-backed conversation API response, real message entry in WhatsApp/IMO/BOTIM, or end-to-end approved delivery. Those still require separate authorized phone acceptance before production release.

## Security boundaries

No auto-send, payment, external share or call. A matching chat is a navigation target, not a validated communication recipient or permission to send. Never choose among two plausible contacts; no retries after uncertain clicks or edits. User may revoke native permissions and persistent safe-navigation preferences.
