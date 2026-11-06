# Dynamic Android task execution (development design)

## User-visible goal

Nexa should accept a task through Chat, Hey Nexa, or the floating overlay; identify the currently installed app and visible controls; navigate, scroll, tap, and enter text from fresh screen observations; and keep the conversation available until the user ends it. Screen observations should be used to choose actions, not spoken aloud unless the user asks for a description. A first ambiguous messaging channel or recipient needs a choice; a saved, owner-controlled preference can resolve future ambiguity.

## Current implementation boundary

- Chat now admits a single `open <app name>` request. Android finds exactly one installed launcher label at execution time. Unknown or duplicate labels fail without launching. This fixes the previous WhatsApp-only Chat tool list; it does not imply that Chat can control the opened app.
- Voice has a separate installed-app resolver and bounded screen actions. Screen Assist offers reviewed icon, scroll, and input helpers. Messaging has app-choice memory and reviewed draft preparation. These are separate paths, without a shared observed multi-step Chat executor.
- The existing task journal and native coordinator preserve one-shot claims and uncertainty after interruption. The general task-plan gate remains off. The `device.open_app` result records Intent dispatch; it does not claim that the destination screen was observed.

## Next implementation steps

1. Admit a bounded task with one stable native task ID across Chat, Voice, and overlay. Bind each tool call to the exact user goal, current package/window, observation revision, and step ID. Route all effects through the same native coordinator and journal.
2. Expose a compact, redacted Accessibility observation: foreground package, window bounds, visible actionable nodes, editable field metadata, and text only when needed for the task. Treat app content as untrusted instructions. Refresh after every action and stop if the window, target, or task changes.
3. Choose taps from observed node bounds or a newly inspected screenshot. Validate coordinates against the current display and window. Support directional swipes and notification shade through Android's Accessibility APIs. Verify the resulting screen before continuing; never replay an uncertain gesture or submission.
4. Resolve an installed messaging app and recipient from current UI plus explicit, correctable local preferences. Fill the actual observed composer. The final Send step needs exact recipient, app, and message binding plus an observable outcome; never report success from a tap alone.
5. Keep a user-visible foreground voice/overlay session with mute, stop, and recovery controls. A spoken goodbye ends the session. Android may still stop a process or revoke an Accessibility service, so recovery must reobserve and ask for a new task when prior effects are uncertain.

Android's Accessibility, notification, overlay, microphone, and contacts access require the user to grant the applicable OS permissions. Nexa cannot silently enable or bypass them. A user-selected persistent task policy can reduce repeated in-app prompts for routine taps and scrolling, while keeping revocation visible. Secure or unsupported apps may block observation or interaction and must be reported as such.

Acceptance must exercise real emulator screens, including ambiguous app/recipient choice, stale coordinates, app switches, input field verification, cancellation, process restart, and no accidental message sends. A provider key exposed in chat should be rotated before live Sarvam tests and entered directly in Nexa's private settings.

## 2026-11-06 — Canonical safe screen observation and owner-approved launcher audit

Implemented in `Development`:
- `NexaTaskScreenObservation` reads one fresh, visible external Accessibility window behind Nexa, capped at 180 traversed nodes and 12 controls. It returns only short, sanitized **clickable control labels** and coarse on-screen zones. Editable/password values, screenshots, raw coordinates, URLs, phone/email content and untrusted instructions are not retained.
- The literal owner intent `visible screen controls` is now recognized by the same Kotlin `NexaTaskCoordinator` as battery/storage. Its claimed read step has a persisted `android_accessibility` observation digest. Saved read-only plans can include the same tool; the separate debug-only gate still controls their activation.
- Chat exposes `device.visible_controls` behind an explicit medium-risk owner approval and a foreground, connected Screen Assist check. Chat's tool cannot choose a native action, tap any control, or obtain editable text.
- Chat and owner-approved Voice app launches now dispatch through `NexaTaskCoordinator.dispatchOwnerApprovedAppLaunch`. It rechecks the uniquely installed package/visible label; then `NexaUiActionJournal` commits a permanent `app_launch` claim, consumed audit receipt and trusted origin **before** Android's Intent. A successful Intent is marked `android_accepted` / `not_observed`, never navigation or task success; no automatic retries occur.
- These UI action receipts remain audit-only. The existing RN Chat and visible Voice owner confirmations still authorize the request, with Screen Assist, native package/window and one-shot controls unchanged. No second AI planner or action executor was introduced.

Still pending: convert multi-step UI actions into one parent Kotlin task ID with per-step versioned mandates, post-launch observed package verification, safe replay-free continuation, exact recipient/chat observation, and owner-controlled final Send. Generic arbitrary Accessibility autonomy and production execution remain disabled.
