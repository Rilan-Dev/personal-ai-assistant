# Nexa — voice entry without an idle Nexa microphone

## Goal and supported result (Android 2026)

Nexa must not record while idle in its privacy-first mode. A user who wants
voice-first launch now can use the system assistant gesture, the Nexa Voice
Android launcher shortcut, or supported Google Assistant App Actions.

**"Hey Google, open Nexa voice"** is a candidate natural phrase for Google
Assistant when Google remains the default assistant and App Actions are
registered for the installed release on Google Play. The phrase is recognized
by Google's own assistant, not by Nexa. Google's interpretation and the
shortcut availability depend on device, locale, Play registration and user
settings; **no successful voice wake has been verified** on a phone.

A standalone "Hey Nexa" from an idle phone **cannot be supported without an
ongoing microphone foreground service on the ordinary APK**. Android reserves
protected hotword detection for authorized system/OEM implementations.
Neither the Google App Action nor any launcher shortcut grants these rights.

## Source changes

- `res/xml/nexa_shortcuts.xml`: static launcher shortcut plus
  `actions.intent.OPEN_APP_FEATURE` inline feature inventory for "Nexa voice".
- `AndroidManifest.xml`: launcher Activity metadata and a tightly-scoped
  `nexa://voice` VIEW URL.
- `App.tsx`: listens for initial and subsequent Android React Native Linking
  URLs; selects the Voice tab for the exact `nexa://voice` route. Does not
  call `startOwnerLiveVoice`, enable Vosk recording, grant a permission,
  or send messages when merely receiving an external URL.
- `NexaSetupScreen.tsx`: tells owners about the external Assistant handoff
  and the potential conflict with Nexa as the default assistant.
- Native package-manager instrumentation and Node source checks verify
  the shortcut metadata and navigation boundary.

## How to verify

1. Install the exact SHA APK through approved Android CI, using the long-lived
   signing certificate. Avoid replacing a stable-signed build with a test
   certificate when the user's data should survive.
2. Stop continuous wake mode from Nexa Setup. Confirm no Nexa persistent
   microphone notification, idle `AudioRecord`, or green mic dot due to Nexa.
3. Long-press the Nexa AI launcher icon and tap **Nexa Voice**. The app must
   display the Voice tab without silently starting a recording.
4. With USB debugging and one authorized phone, also test:
   `adb shell am start -a android.intent.action.VIEW -d nexa://voice dev.rilandev.personalaiassistant`.
   Confirm Voice tab both from cold start and while app is already open.
5. To attempt the voice shortcut from Google: keep Google Assistant/Gemini as
   default, publish or preview the App Action with the supported tools and
   correct Google account/locale, then say "Hey Google, open Nexa voice".
   It can open Nexa but does not grant instant “Hey Nexa” recognition.
6. In the separate Nexa-default-assistant configuration, use the Android
   gesture or button to invoke Nexa's `VoiceInteractionSession`. Do not
   assume Google hotword detection remains active after this role switch.
7. During a real conversation, confirm normal Android mic indicator,
   active Stop notification, speaker interruption, and cleanup on Stop.
8. Test denied permissions, terminated app, screen lock/unlock and reboot
   separately. Never mark an on-phone test passed without observed evidence.

## Privilege boundary and future work

AOSP `VoiceInteractionService.createAlwaysOnHotwordDetector` is a protected
`@SystemApi` tied to privileged hotword permissions and enrollment; reflection,
manifest permission declaration, privileged UID spoofing or app-op tampering
cannot make this a normal distributable feature.

EU DMA interoperability measures published July 16, 2026 require comparable
access in Android 18 by August 1, 2027, with concurrent hotword by Android 19
by August 1, 2028. Developer API, geographic rollout and supported hardware
must be verified when available. This is not a current grant.

**OEM route:** obtain written authorization/device SKU/system image, protected
wake-phrase enrollment and signing details, integrate through publicly
documented/vendor-provided SDK, then validate CTS/CDD/privacy behavior.
Nexa can already present an Android assistant session once invoked. It must
not claim protected hotword is active before an actual trusted backend is
connected and tested.

## Official sources

- https://developer.android.com/develop/devices/assistant/overview
- https://developer.android.com/reference/app-actions/built-in-intents/common/open-app-feature
- https://developer.android.com/develop/devices/assistant/action-schema
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/voice/VoiceInteractionService.java
- https://source.android.com/docs/compatibility/17/android-17-cdd
- https://digital-markets-act.ec.europa.eu/developer-portal/interoperability/alphabet-specification-proceedings-interoperability-ai-services_en

Status: Source implementation. Native compile, Play App Actions registration,
live Google invocation, OEM hotword integration and phone acceptance require
independent evidence.
