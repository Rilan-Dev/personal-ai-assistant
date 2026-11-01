# Nexa incremental launcher and WhatsApp controls

Status: implementation on Development. Requires a new signed APK, emulator instrumentation
and physical-phone acceptance. These controls are not connected to Sarvam STS or a hotword
listener, and they do not autonomously send WhatsApp messages.

## Owner-only GitHub app icon discovery

1. On Nexa Tools, check GitHub installation by allowlisted Android package com.github.android.
2. Explicitly enable the separate **Nexa Launcher Navigation** accessibility service in
   Android Accessibility settings. This is distinct from the existing read-once Screen Assist.
3. In the Nexa app, tap **Find GitHub icon**. The native module checks that the current
   activity is visible, the GitHub app is installed, a default HOME launcher is known,
   and the navigation service is connected.
4. Android opens the HOME launcher. The service permits inspection of the currently active
   accessibility root only if its package equals that exact default HOME launcher.
5. If a visible accessibility element has the exact text or description 'GitHub', the service
   records `found`. Otherwise it can swipe horizontally up to eight times (four in each
   direction) with a 25-second overall timeout. It never clicks the icon.
6. Returning to Nexa shows the status (`found`, `not_found`, `wrong_screen`,
   `gesture_blocked`, `timeout`, or `cancelled`) and number of swipes.
   Results expire after 60 seconds. There is no icon coordinate, screenshot or copied
   launcher content retained. Home screens inaccessible to Android's accessibility tree
   may not support this feature; folder-contained icons may not be visible.

Android's launcher can reorganize icons, so any detected page is only an immediate,
user-visible observation, not a permanent mapping. No arbitrary app, remote agent or
screen coordinate may request a gesture.

## User-reviewed WhatsApp draft

1. In Nexa Tools choose **Select one phone contact**, using the existing Android picker.
   The contact name/phone appear only in volatile app state with existing expiration.
2. Type and review a message of at most 500 characters.
3. Tap **Open reviewed WhatsApp draft**. The native module checks the foreground activity,
   validates the international dialable number, confirms WhatsApp Messenger is installed,
   constructs an HTTPS wa.me link with encoded text and restricts ACTION_VIEW to WhatsApp.
4. The result is **Intent dispatched, messageSent:false, verification:not_observed**.
   Only the user may confirm the recipient and press Send inside WhatsApp. Nexa does NOT
   claim message delivery. There are no background sends, retries or hidden tool calls.

This is not yet natural-language matching of 'Mom' against contacts, unattended
conversation inspection, voice approval, WhatsApp Send button automation, or receipt
verification. Those capabilities require separate precise permissions, a verified
recipient-to-chat binding, one-time human approval, idempotent send journal and
physical app-version acceptance tests.

## Security boundary and SDK source

- Uses Android framework AccessibilityService, GestureDescription,
  AccessibilityNodeInfo, PackageManager and Uri.Builder, not injected shell/ADB.
- Launcher navigation is a distinct, owner-enabled accessibility service protected
  by android.permission.BIND_ACCESSIBILITY_SERVICE. This new service has
  canPerformGestures=true; original ScreenAssistService remains read-only.
- No QUERY_ALL_PACKAGES, READ_CONTACTS, SMS, WhatsApp notification scraper,
  screenshot, boot receiver, background location or hidden microphone grant.
- URL query parameters are escaped by Uri.Builder, not string concatenation.
- No contacts, messages or screen contents are logged or sent to the AI planner.
- Voice Agent tool catalog, generic LLM planner and MCP integration are unchanged.
- Android role, microphone, accessibility and contact picker permissions are OS-owned,
  explicitly user-controlled and cannot be granted by developer scripts.

## Release acceptance

- Source: npm test, npm run typecheck, npm run web:export.
- Emulator: launcher manifest/metadata and state-machine tests, ensure external apps
  cannot start either native module while no activity has focus, ensure no message send.
- Native: compile debug and release variants with CI and verify signed APK v2/checksum.
- Physical Android: confirm Accessibility grant, various launchers/folders, home swipes,
  timeouts, AppState resumption, cancellations, landscape, lock screen, gesture denial.
- Physical WhatsApp: verify valid E.164 country code, recipient, multiline message,
  cancellation, no-handler/no-install, no automatic send, no duplicate; never send a
  real message to a third party as a test without the owner's active approval.
- Background wake-word 'Hey Nexa' remains a separate implementation and acceptance gate.
  sherpa-onnx open-vocabulary KWS is an upstream research candidate, but an
  actual customized model, CPU/battery profile, microphone FGS lifecycle and
  physical device evaluation must pass before enabling always-on recording.


## Opt-in assistant-panel one-shot voice command
The compact VoiceInteractionSession now has Speak a device command and Cancel
spoken command controls. Android on-device SpeechRecognizer (API31+) is used
only after a tap and a previous explicit RECORD_AUDIO grant, never while Sarvam
or OpenAI live speech is active. An exact deterministic allowlist opens GitHub,
opens WhatsApp, or requests the bounded launcher scan. Session dismissal and
20-second timeout cancel the recognizer. No cloud transcript, arbitrary UI
navigation, third-party message send or always-on Hey Nexa activation.

A reviewed WhatsApp draft additionally rejects contact phone numbers without
an explicit leading +country code; the owner must correct such numbers in the
phone contact record before the app can prepare the WhatsApp link.
