# Nexa spoken WhatsApp draft — owner-reviewed native-to-React handoff

## User experience
After expressly invoking the assistant using its gesture, owner-enabled wake phrase
or floating panel, the owner can say: **“Send a WhatsApp message to my mom
saying I will call you later.”** Android's on-device recognizer must support
the language and microphone permission.

Nexa handles only a narrowly anchored, local grammar. It preserves the
dictated text, queues a temporary in-memory object and requests the **React
Native Tools** screen. This is not an automatic contact lookup or WhatsApp send.

In Tools, the text is prefilled and the speaker's contact label is displayed
as an **unverified hint**. The owner must choose the exact phone contact using
Android's contact picker, confirm the number with country code, review or edit
the text, and explicitly open the WhatsApp composer. The user must then press
**Send in WhatsApp**. Nexa does not verify delivery or claim a sent message.

## Security and privacy
- The native queue stores only the contact hint and message for **60 seconds**.
- It is memory-only, clears when consumed, and does not survive process death.
- It cannot choose a contact, read private address book data, or send any message.
- Parsing is deterministic; cloud AI responses cannot invoke this shortcut.
- Unrecognized/ambiguous speech falls through to existing safe local handling.
- On failure to open the app, the queued message is discarded.
- Both the Kotlin assistant overlay and floating command panel use the same
  parser. Main UI and review controls remain React Native + TypeScript.

## Acceptance
Source regressions cover one-shot and timeout behavior, absence of automatic
send, React Native handoff, and preserved provider permission restrictions.
Android instrumentation covers parser examples, single consumption and expiry.
A real Android device must verify the speech recognizer, assistant handoff,
app foreground routing, contact picker, WhatsApp composer, and no-send
behavior. Device-only acceptance cannot be inferred from emulator/source tests.

## Broader limitations
Kotlin remains responsible for microphone and system integration. The normal
five-tab frontend is React Native. Physical wake reliability, OEM lock-screen
and battery constraints, autonomous messaging, exact launcher page recognition
and physical-device send verification are **not** proven by this milestone.
