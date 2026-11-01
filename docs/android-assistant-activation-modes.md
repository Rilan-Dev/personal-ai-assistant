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
