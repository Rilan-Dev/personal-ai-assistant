# Nexa offline "Hey Nexa" wake listening — engineering acceptance

Date: 2026-11-01
Status: native implementation and source regression; **release CI and physical Android phone acceptance required**. No unattended message-sending was enabled.

## Design

The user explicitly downloads an approximately 40 MB small English Vosk model.
The official model URL is
https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
and the SHA-256 (verified on VPS) is
30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498.
Vosk Android 0.3.47 and this model are Apache 2.0; the download is verified,
bounded and unpacked within app-private storage with ZIP traversal protection.
The model is not bundled with the APK or uploaded to cloud storage.

Only after the user grants Android microphone and notification permissions and
taps **Enable Hey Nexa listening** does the app start the foreground microphone
service from a visible activity. Audio is decoded on-device by Vosk at 16 kHz;
no raw microphone audio is persisted, sent over a network, or exposed to JavaScript.

The 2-frame phrase gate accepts "hey nexa" and some phonetic variants to work
around vocabulary restrictions of the generic English ASR model. That is *not*
a purpose-trained, calibrated custom wake-word model. Its true-positive and
false-positive performance, accents, noisy-room behavior and battery impact are
**unverified**. Do not market this as a robust production voice assistant
until independent real-device measurements meet an agreed quality threshold.

After a recognized phrase, the service releases its AudioRecord and stops.
It posts a user-clickable notification. When Nexa has been selected as the
Android default VoiceInteractionService, it additionally requests a compact
system assistant session; this auto-starts a single bounded on-device command
transcription (not continuous cloud ASR), using the native session's existing
fixed allowlist. The service does not autonomously click arbitrary third-party
apps, read private messages or send WhatsApp. A user can stop the active
listener in the Android notification or Nexa Setup at any time.

There is **no boot-start**, background auto-arming, silent microphone capture,
perpetual React Native polling, or other Android permission bypass. The user
must arm again after one activation. Android 14+ while-in-use microphone rules
are respected by requiring a visible app when starting the FGS. Android can
stop or revoke the microphone service at any time.

## Acceptance plan

1. GitHub Actions Android instrumentation must compile Kotlin, resolve Vosk,
   execute the pure two-frame phrase tests, and verify a signed release APK
   using the permanently pinned Nexa signing certificate.
2. On a physical device, test the 40 MB model download, hash validation,
   cancel/retry, network loss, storage exhaustion and offline restart.
3. Test explicit mic and notification grants, backgrounding Nexa, the Stop
   notification, mic privacy indicator, OEM battery optimization and Android
   microphone privacy toggle. Denying/revoking access must prevent restart.
4. With default assistant role selected, say "Hey Nexa", verify the assistant
   session appears over the current screen without navigating to a full-screen
   application. Speak "find my GitHub app" and verify only the pre-authorized
   limited launcher flow. Check cancellation and negative paths.
5. With default assistant role unavailable, verify the notification opens
   Nexa without claiming to have already processed a spoken command.
6. Measure trigger reliability/false positives against actual accents, noise,
   lock screen, headphones and battery usage before production enablement.
7. Check notification/service lifecycle when offline, when audio focus changes,
   after restart, and during existing Sarvam/OpenAI live voice sessions.
8. Confirm no autonomous communications and no unauthorized background controls.

## Remaining known limitations

This solution provides an experimental on-device wake-phrase pathway, not an
OEM-integrated always-on hardware hotword. Unprivileged third-party apps
cannot assume always-on hotword privileges. It still needs physical phone
quality, battery, privacy and Android-version validation. The generic model
may fail to transcribe the proper noun Nexa on some devices/accents.

## Source and synthetic validation details

On the VPS, an independent Python Vosk runtime loaded the official unmodified
model. Its lexicon does not contain the proper noun **nexa**, so the native
grammar uses the model's in-vocabulary phonetic alternatives "hey next a"
and "hey nexus" instead. An eSpeak synthetic sample of "Hey Nexa" decoded as
"hey next a". A synthetic "Hey next" also temporarily decoded that phrase,
but its *completed* result was "hey next". The detector was tightened to
require a stable matching phrase **and** completed utterance before activation.
Synthetic "Hey Alexa" and "Hello Nexa" were also rejected by the phrase gate.

These synthetic checks do not establish real-world activation accuracy.
When Android has not selected Nexa as default assistant, the fallback wake
notification uses its own high-importance channel so a visible alert can be
shown, subject to the user's system notification preferences.

## Release build R8 compatibility correction

GitHub Actions run 41303066805 compiled and completed the native Android
instrumentation gate but failed its release R8 task: JNA's unused desktop-only
java.awt.Component signature was missing on Android. R8 remains enabled.
The release rules now explicitly preserve JNA/Vosk reflection symbols and
suppress **only** this unavailable desktop type. Successful release assembly,
signed APK verification and actual device acceptance must follow this change.
