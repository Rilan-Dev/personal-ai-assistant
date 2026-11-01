# Nexa Android assistant-role and floating-tools usability fix

2026-11-01. Source implementation complete; Android CI and real device acceptance pending.

## Diagnosed issue

When RoleManager reports the assistant role as unavailable, the old UI disables
Choose Nexa as assistant and offers no way to open Android's default-apps
settings. The old native call additionally required window focus.

## Fixed behavior

- A resumed Activity can request the Android assistant role. If the role is
  unavailable or its chooser cannot start, the request falls back through
  Android Voice Input Settings, Default Apps Settings and general Settings.
- A separate always-accessible Android Default apps button is now present.
  Returning from Settings refreshes the role state from Android.
- Added an explicit SYSTEM_ALERT_WINDOW grant flow using the Android Settings
  permission UI. On Android 11+, the app may appear in a list of apps rather
  than an app-specific permission dialog.
- Added a small, draggable, 52dp, nonfocusable Android overlay bubble that
  does not cover the home screen. Expanded controls: Chat, Voice, Tools,
  Stop live voice, Collapse, Hide Nexa. Navigation is one-time user driven.
- No hidden recording, automatic boot startup, screen scanning, arbitrary
  gestures or model-controlled click interface was added.
- Setup now shows 8 steps including the separate optional floating-tools grant.

## Acceptance still required

Test on the actual Android phone: tapping the assistant-role chooser, its
Settings fallback, whether Nexa is listed as an eligible assistant, actual
role status on return, granting overlay access, dragging and dismissing the
bubble, reopening Voice and Tools, lock screen, and safe Stop. The app cannot
force an OEM to select Nexa when the phone's OS does not offer it.

## Wake-word distinction

The current build explicitly reports wakeWordReady false. Neither granting
assistant role nor allowing the overlay implements "Hey Nexa". Continuous
custom wake-word detection requires a licensed on-device detector, explicit
user opt-in, compliant microphone service, audio focus, visible stop/status,
power measurements, and real-device acceptance. Do not claim it works until
measured on the user's phone.

AI device actions remain permission- and approval-gated; the overlay cannot
give the model unrestricted authority over installed applications.
