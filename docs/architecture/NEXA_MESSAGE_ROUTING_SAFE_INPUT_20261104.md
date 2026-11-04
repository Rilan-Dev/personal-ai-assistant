# Nexa messaging routing and reviewed text entry — implementation contract

**Source:** `feature/nexa-message-routing-input-20261104` (pending native Android emulator and physical-device acceptance)

## User goal

> Hey Nexa, send message to my Mom that I am in the meeting i will call you later.

This produces a **review-only** request:

- Recipient alias: `Mom` (NOT a resolved device contact).
- Body: `I am in the meeting i will call you later` (original user text, not an LLM rewrite).
- App: unspecified, requiring channel review on first use.
- Send authority: **none**. Nexa does not send messages automatically.

## Source-backed routing order

1. Parse an explicit one-message owner request locally; requests without a reliable body delimiter or containing another action are not executable.
2. If the user explicitly names SMS, WhatsApp, WhatsApp Business, imo or BOTIM, prefer only that app and refuse fallback if it is unavailable.
3. Otherwise, look up the owner's explicit **per-alias remembered** app choice from Android Keystore encrypted preferences. Reconfirm its installation before using it.
4. Otherwise, display the small list of supported, installed channels and ask the user to choose **Use once** or **Remember for this alias**. This is a small allowlist, not a broad installed-app inventory.
5. The owner can change or forget an app preference at any time; remembered channel does not authorize a recipient selection or a send.
6. Contacts are chosen with Android's one-contact picker, followed by visible owner recipient confirmation. SMS/WhatsApp use a reviewed composer link/intent and **the user sends manually**.
7. IMO, BOTIM, and WhatsApp Business have no assumed deep link to a specific recipient. Nexa opens the selected app; the owner navigates to the intended conversation. Only a still-live Accessibility service can find a unique, visible, empty message field, match the window package and exact visible chat title, and propose a one-time insertion.
8. The floating Nexa overlay requires a **second confirmation** before any `ACTION_SET_TEXT`. It rescans the same window, checks field identity and geometry, refuses existing content and unsafe/password/search/recipient fields, consumes the one-shot approval before dispatch and stores only an encrypted outcome category. **No Send click occurs.**
9. On any disconnected service, missing title, ambiguous input field, changed window, expired draft, keystore error or unverifiable outcome, fail closed. No automatic retry or cross-app guess.

## Memory and privacy

- App selection is remembered only after the owner taps **Remember <app> for <alias>**.
- The stored value is an encrypted channel ID. The storage key is a hash of the normalized alias; it stores neither phone number nor message text.
- A preference for `Mom` is not a proof that an Android contact with a different displayed name is `Mom`.
- Nexa does not infer a messaging destination merely because a prior conversation contains "WhatsApp" or another app name.
- Pending text for manual Android field entry exists only in process memory for up to 90 seconds. Window confirmation expires after 25 seconds.
- The spoken Sarvam local command path can hand a recognized user utterance to Nexa's chooser. This does not grant a provider permission to select or send messages.
- Android restrictions (Accessibility enablement, overlay permission, application foregrounding and background app starts) remain user/OS controlled.

## Tests and acceptance gates

- Pure Node tests: local natural-language parser, app resolution, explicit override, missing app, and unsafe/multi-action rejection.
- Android native instrumentation: field-policy and recipient-title safety, expiration/replay of staged text, and recognized Hey Nexa command with preserved body.
- Maestro experimental 12: natural "Hey Nexa Send Message..." opens a local app chooser with exact text; no send.
- Native compilation and emulator testing must pass before merging this feature into Development.
- On a real phone, verify WhatsApp and SMS contact-picker draft, IMO/BOTIM actual app screens, correct empty editable node, Unicode message text and noneditable/ambiguous negative controls. The pending flow must not pass if only the app launch intent was dispatched.
- No production success or all-tools-working claim until live Android permission, actual recipient, text visibility and human delivery review have been observed. The source currently **does not implement an automated final Send**.
