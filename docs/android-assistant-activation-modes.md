# Nexa Android activation modes

## Android platform boundary

Android provides `VoiceInteractionService` for a user-selected default assistant, but an ordinary installed APK cannot access the protected SoundTrigger/DSP hotword service that permits Google-style indicator-free idle detection. Nexa will not hide microphone privacy indicators or bypass microphone foreground-service policy.

Official references:
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- https://source.android.com/docs/core/audio/sound-trigger
- https://source.android.com/docs/compatibility/16/android-16-cdd
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## Implemented — Phase 1

**Gesture-first (default):** select Nexa under Android's default digital assistant settings. No idle audio recording, no model required, no always-on mic service. An explicit assistant gesture opens the system session, where on-device recognition captures one bounded spoken device command. The green indicator is expected while recording. Results use the existing restricted device-tool allowlist. User must configure a live voice provider separately for conversational AI.

**Continuous wake (experimental):** optional 40MB Vosk model, separately user-enabled microphone foreground service, visible Stop notification, and Android green dot while recording. It attempts to re-arm after a session and may be limited by OS power/background policies.

**Migration:** an existing pre-v2 `wake_enabled=true` flag does not automatically start the microphone after upgrade. The new versioned `activation_mode_v2=continuous` preference is saved only by the user's explicit wake enable action. The mode can be turned off from Setup or notification. Protected hotword detection is reported unavailable to an ordinary APK.

## Phase 2 source implementation — 2026-11-01

**Owner-controlled conversational invocation:** Under Setup, the user can
enable "AI conversation on assistant invocation". This opt-in defaults OFF
and is stored as `nexa_setup.auto_voice_invocation_v1` only through a
foreground, visible Setup action after microphone/notification permissions
and valid OpenAI Realtime or Sarvam Voice Agent credentials are present.

On an explicit Android assistant gesture, owner overlay tap or opted-in local
Hey Nexa wake event, a configured and enabled session requests an Android
microphone foreground service, shows an ongoing Stop notification and the
normal microphone privacy indicator, and opens the direct live speech-to-speech
provider. When this session window hides/dies, its live service stops.
Without the preference/provider, the existing single local command path
remains the fallback. Initial microphone grants, cloud account access and
live-provider charges are not silently assumed by an old install.

**Barge-in hardening:** playback chunks now carry a generation token.
User interruption invalidates the generation, clears queued samples and
synchronizes the audio sink with its flush. The OpenAI transport also checks
server response identifiers so late audio from an interrupted response cannot
play over the next turn. Sarvam interruption still depends on the provider
emitting its documented interruption event; this requires phone/provider
acceptance.

**Validated on connected server:** 199 Node tests, TS compilation and
Expo web export passed at commit `3308d1b`. Native Android Kotlin compile,
instrumentation, actual cloud voice and physical-device acceptance were not
run by these checks; the home Android build server was unreachable and the
alternative Linux server has no JDK/Android SDK/ADB.

## Next phases — not yet delivered

Phase 2: conversational assistant session, voice streaming/provider selection, interruption and speech-end behavior, error/retry handling, explicit device-command approvals.

Phase 3: Android real-device acceptance for OEM/version variations, repeated activations, screen-off gesture, ambient-noise false positive rates, headset/call interruptions, boot/update recovery, battery and thermal impact, and release signing verification.

Phase 4: investigate authorized OEM/System SoundTrigger or protected hotword integration. This is required for true always-on “Hey Nexa” without a continuously visible ordinary microphone indicator.

## Acceptance criteria

1. Fresh install and upgrade from legacy wake setting: default gesture mode is idle and does not record or restart a mic FGS.
2. Selecting Nexa as assistant lets OS gesture invoke a single on-device speech command with normal temporary microphone indication.
3. Continuous wake requires explicit user opt-in; shows mic dot and Stop notification; can be disabled.
4. No concealed audio access, no spoofed privileged permissions and no unreviewed external messages.
5. Source/unit/instrumentation checks must pass; physical-phone behavior remains unverified until tested there.
