# Nexa autonomous Android input, icon and messaging QA — 2026-11-05

## Intended owner experience

A single request can contain one or several low-risk navigation steps. For example,
"Hey Nexa, open Chrome and scroll down and tap Search" is parsed as a bounded
three-step task; each step advances only after Android's observed evidence is
accepted. The existing opt-in **Trusted navigation** mode can reuse approvals for
known low-risk app opening, scrolling and safe navigation icons. Android's
Accessibility permission remains a prerequisite and revocation always wins.

For "Hey Nexa, send message to my Mom that I am in the meeting i will call you
later", Nexa parses the recipient alias and exact text, checks installed
WhatsApp, WhatsApp Business, SMS, IMO and BOTIM, uses an explicitly saved
encrypted per-alias app choice if still installed, otherwise asks for a channel.
A saved app preference is **not** permission to send or evidence of the correct
contact; an owner-approved recipient and final send are separate.

## Why two cloud tests failed previously

Run 41916720191: Android native instrumentation completed **127 tests** and
a disposable standalone React Native APK booted on an Android emulator.
Maestro Cloud reported **2/2 flows failed** after reaching their Tools review
sections: the expected app chooser and generic text-entry review buttons
were below the immediately visible region. This was not proof that typing
failed; the cloud flows had not performed external-app typing at all.

This branch moves the active task review cards to the top of Tools and makes
Maestro scroll to the actual review controls. It also fixes the Android edge
case where an empty EditText can expose its hint as node.text, and performs a
read-only node refresh before claiming a text insertion was verified.

## Concrete regression tests

1. Parser and routing tests: first-use channel ambiguity, explicit choice
   versus remembered choice, missing installed app, no unsafe substitution,
   exact message body, invalid inputs, and no send.
2. Encrypted per-alias memory instrumentation: remember, read, revoke,
   isolation and invalid alias/app rejection.
3. Generic text-field classification: one visible, enabled, empty,
   non-sensitive field with the requested label; never overwrite data,
   password/OTP/payment/recipient fields, or click Send/Submit.
4. New DEBUG-ONLY test Activity with Search and Notes EditTexts, a password
   input, Search and unknown icons, and an inert Send button. UiAutomation
   exercises actual Android AccessibilityNode ACTION_SET_TEXT and ACTION_CLICK
   on this synthetic screen and checks that Send was never pressed.
5. New bounded-autonomy tests: three-step verified task cursor, opted-in
   safe-navigation grants, denial of stale/disconnected Accessibility,
   arbitrary text, sending, calls and sensitive actions.
6. Maestro experimental 12/13: exact owner-requested message and named
   text entry must be visible with working review controls. This is a
   **review UI test**, not proof of real app sending.

## Verified and open

- Latest local clean checkout: 365/365 source tests passed, TypeScript passed.
- **Verified on earlier QA source SHA 57cf84a:** owner-triggered API35 run
  https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41922174301
  completed successfully: **365/365 source tests**, **130/130 Android
  instrumentation tests**, disposable standalone APK signed with a throwaway
  identity, emulator boot/visible UI and **2/2 Maestro Cloud review flows**.
  The Maestro flows verify requested review UI, not live external-app text entry.
- The newer QA branch includes the bounded three-step autonomy test and
  one-installed-app auto-selection; owner-only validation run
  https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41925172179
  was started to test that exact newer source. Latest direct source tests
  **366/366** and isolated current Development merge tests **378/378** pass.
  Do not mark the newer build as native accepted until its run completes.
- Real WhatsApp, IMO, BOTIM and SMS conversation input behavior, actual
  contact identity, physical phone Accessibility grants and per-app
  confirmation have **not** been verified. No physical phone is connected.
- Native instrumentation invokes a test-only Android Activity; it does
  **not** silently grant the Nexa production Accessibility service.
- Unknown/custom icons can be observed conservatively. They cannot be
  universally classified or safely auto-clicked. Destructive, financial
  and external communications actions require explicit user confirmation,
  regardless of navigation trust.
- Never equate a task plan or OS action request with completion. Preserve
  one-shot journal, fresh-screen checks, provider consent and no auto-send.
