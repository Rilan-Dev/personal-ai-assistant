# Nexa overlay-first local tools (2026-11-01)

## What changed

The floating window is now an actual small **native Android workspace**.
Chat / Tools / Voice tabs switch in the window without opening the main app.
The bubble stays 52dp; expanded panel is 320dp wide, non-modal,
collapsible and draggable. The chat panel has its own editable text input,
small bounded transcript, and local command response.

The main React Native application continues to host the full AI agent
harness, provider configuration and approval workflows. This change does
**not** silently claim that the full AI conversation runs in the overlay.
The explicit Open full Chat / Full Tools / Full Voice controls provide the
fallback when the compact local surface cannot perform an operation.

## Supported local overlay commands

- Help, device/permissions/privacy/background status
- Check battery (Android BatteryManager)
- Check installation of GitHub, WhatsApp, WhatsApp Business
- Explicitly open those allowlisted installed apps; intent dispatch is not
  proof the target appeared
- Explicit GitHub home-screen search with already authorized dedicated
  launcher-only accessibility service, limited to 8 horizontal swipes;
  follow-up launcher status is available
- No WhatsApp sends from the overlay; a request to send a message says
  nothing was sent and directs to reviewed composition
- Voice panel: current permissions, verified on-device wake-word model,
  listener state and realtime voice status; owner may start the **local**
  wake detector or stop the detector/live voice with controls

## Android privacy and limitations

There is no generic permission named "background access" that grants an
AI unrestricted access to the phone. Android's runtime microphone permission,
foreground microphone service restrictions, notifications, optional
SYSTEM_ALERT_WINDOW, assistant selection and Accessibility grants are
independent. Those OS controls cannot be self-approved by the app.

On Android 14+ a background overlay may be blocked from starting a new
microphone foreground service, even when the overlay is visible. The wake
button reports the error and prompts opening Nexa visibly. It must not
pretend an FGS was started. An existing user-started microphone foreground
session can continue subject to Android restrictions and Stop controls.
The current offline wake detector stops after one trigger; it is not an
OEM hardware hotword and is not automatically armed at boot.

An Android system assistant session or the full app still handles long
AI conversations. A separate future integration would embed the full React
Native agent UI into the native overlay host, with auditable tool dispatch
and the same human approval boundaries as full Chat. This change deliberately
does not create an unrestricted automation pathway.

## Acceptance

1. Install the signed APK; grant overlay access in Android Settings.
2. Show bubble, return to home screen, tap to expand. Switching among Chat,
   Tools and Voice must not open Nexa's full-screen activity.
3. Enter "check battery", "check github", "background status" and unknown
   instructions in Chat. Verify a bounded response and no side effects for
   unknown commands.
4. In Tools, check app installation, request GitHub launcher search after
   enabling the dedicated accessibility service, verify at most 8 swipes
   and no icon clicking. Test permission-denied and missing-app paths.
5. In Voice, view the model and permissions, arm only after deliberate tap.
   Confirm microphone indicator and Stop notification where Android allows
   activation, and an explicit failure message where it forbids it.
6. Deny/revoke overlay and mic access; verify no silent permission grant,
   no hidden audio and no message sent.
7. Test minimized overlay keyboard, home-screen touch passthrough, portrait
   and landscape, Android 12–16, multiple OEM devices, lock/unlock.
8. Run native emulator instrumentation, release R8, pinned-signer APK build
   and real device checks. Desktop/source tests are not real-device proof.

## Remaining product acceptance

Full provider-backed Chat in the overlay, secure reviewed actions in the
overlay, conversation-grade voice task execution, durable wake monitoring
and OEM/background power behavior remain outstanding and must not be marked
completed from a source-only test.
