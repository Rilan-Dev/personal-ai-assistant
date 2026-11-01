# Nexa "Hey Nexa" parity — Android platform feasibility, November 1, 2026

## Requirement
The desired feature is continuous, low-power, screen-off **"Hey Nexa"**
activation, without an ongoing **microphone privacy dot** or a persistent
microphone foreground-service notification. After detection, the assistant
starts a normal user-visible voice conversation with the proper mic indicator.

These are independent capabilities, not one general permission:

1. Always-on wake phrase processing.
2. Exemption from the normal idle recording privacy indicator.
3. Exemption from standard microphone-foreground-service operation.
4. Subsequent normal voice session with user-visible microphone access.

## Verified Android security boundary

Android 17 Compatibility Definition Document (CDD), §2.2.5, requirement
**9.8/H-1-9**, expressly prohibits a **user-installable application** from
providing the protected hotword detection service. Requirement **9.8/H-1-14**
requires a microphone indicator once a successful hotword result is sent to
the voice interaction service. The indicator rules in **9.8.2/H-4-1** allow
specific protected detection paths; those are not generic Android mic grants.

Relevant upstream:
- https://source.android.com/docs/compatibility/17/android-17-cdd
- https://source.android.com/docs/core/audio/sound-trigger
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

`VoiceInteractionService`, `RoleManager.ROLE_ASSISTANT`, microphone runtime
permission, notification permission, accessibility permissions, model downloads,
sound source choices, `AudioRecord`, and foreground service exemptions **cannot**
turn an ordinary downloadable APK into a system-protected detector.
Do not attempt to use hidden `HotwordDetectionService` APIs,
privileged permissions, system-UID impersonation, altered AppOps,
`setMicrophoneMute`, hidden audio sinks, or artificially suppressed
notifications/indicators.

## Verified Nexa source and current behavior

- `NexaWakeWordModule.getStatus()` reports
  `protectedHotwordAvailable=false`, rather than claiming a protected
  system hotword detector.
- `NexaWakeWordService` uses Vosk and ordinary `AudioRecord` for the
  explicit opt-in `continuous` mode. Its start path uses a microphone
  foreground service and explicit persistent Stop notification.
- `NexaWakeWordService.mode()` defaults to `gesture` unless the user
  explicitly opted in via the versioned preference.
- `NexaAssistantService` implements the system-selected assistant
  session. Assistant gesture mode does not open a continuously running
  `AudioRecord` while idle. Once activated, real speech recognition is
  visible to Android, and microphone privacy indication may appear.
- Setup now labels continuous capture explicitly and displays protected
  no-dot hotword as unavailable on the ordinary APK.

The 40 MB Vosk model does not confer DSP access. Model size, quality and
power consumption cannot change Android's platform privilege requirements.

## What can actually ship

**Default — privacy-first:** No idle recording, no persistent mic FGS, no
green mic dot from Nexa while idle. Invoke via Android assistant gesture,
assistant button, Quick Settings or visible UI. "Hey Nexa" voice-only wake
**cannot** function from idle under this mode.

**Optional continuous mode:** Local wake recognition, then assistant session.
It necessarily runs the normal microphone capture/FGS and Android visual
privacy disclosures. It must remain opt-in, with immediate Stop and no
false marketing claims about no-dot behavior.

**Protected OEM/system integration (future):** Explicit OEM integration,
device-specific SoundTrigger DSP capability, proper enrollment, system signing
or authorized platform API, correct privacy/CTS gates and consent. No working
OEM contract, custom ROM, signed system package or protected hotword API
entitlement has been supplied or verified. This cannot be accepted as complete
on a user-installable Nexa APK.

## Important 2026 EU interoperability decision

On July 16, 2026 the European Commission adopted binding Digital Markets Act
interoperability measures for Google's Android features, including
always-on hotword detection for competing AI assistants.

Its official Q&A states an Android 18 implementation deadline of **August 1,
2027**, and concurrent multi-assistant hotword support in Android 19 by
**August 1, 2028**. The decision does not provide an already-shipping,
worldwide API for the present Nexa APK; regional/device/beta eligibility and
developer onboarding must be verified at implementation time.

Official:
- https://digital-markets-act.ec.europa.eu/businesses-portal/interoperability/alphabet-specification-proceedings-interoperability-ai-services_en
- https://digital-strategy.ec.europa.eu/en/news/commission-provides-guidance-google-ai-interoperability-android-and-sharing-google-search-data

Do not introduce guessed Android 18/19 SDK API names or mark an unsupported
device as ready. Revisit the integration only after public platform
documentation, verified Android image/device support and permitted SDK access.

## Explicit go/no-go acceptance requirements

1. In default gesture mode: observe **zero Nexa idle microphone capture**,
   no Nexa microphone notification or green indicator from Nexa during
   quiescence, across boot, upgrade and background transitions.
2. In optional continuous mode: show the correct Android notification and
   indicator while raw microphone capture is active; Stop releases audio
   promptly, including failure cases.
3. Confirm Nexa cannot claim a protected hotword backend on a normal APK;
   verify `protectedHotwordAvailable` and Setup disclosure.
4. Physically test Android assistant gesture, voice start/stop, latency,
   interruptions, audio focus and device/OEM restrictions.
5. For any future protected backend: record specific OEM authorization,
   enrolled wake phrase, OS image/platform SDK, signed build provenance,
   CTS/security approval, battery tests, screen-off/reboot detection and
   expected **post-wake** microphone indicator behavior.

Outcome: **NO-GO for no-dot/no-notification always-on "Hey Nexa" on an
ordinary Nexa APK.** This is a platform capability/authorization gate, not
an outstanding Kotlin/React Native bug that can be solved by hiding UI.
