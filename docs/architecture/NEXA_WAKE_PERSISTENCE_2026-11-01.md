# Nexa Android wake listener / floating assistant bug review

Date: November 1, 2026. Branch: feature/nexa-wake-persistence-overlay-20261101

## Source-level root causes identified

1. **Oversized overlay.** The native window used WRAP_CONTENT for its expanded
   height and grew with large transcript and rows of development controls. The
   scrolling text and long explanatory labels obscured most of the launcher.
2. **Natural app-open request rejected.** Only exact command strings were
   recognized by NexaOverlayCommands. The actual spoken request "can you please
   open my brave app" did not normalize to an allowlisted action, and the Brave
   package was not visible in Android 11+ package queries.
3. **Wake listener was one-shot.** On a recognized phrase, the detector set
   running=false, released AudioRecord, removed its FGS notification and
   called stopSelf(). The user had to return to Setup to arm the detector.
4. **Wake setting was not durable.** The prior module offered start/stop but did
   not persist owner opt-in. Reboot or package replacement lost armed state.
5. **Privacy comparison was misleading.** Vosk requires real microphone samples.
   Android 12+ shows a microphone-use indicator and Android requires a visible
   foreground-service notification for background microphone capture. This is
   not equivalent to a privileged low-power SoundTrigger/DSP hotword path.

## Implemented changes

- The overlay now uses a bounded 310dp-wide / 340dp-tall scrollable panel and
  a crisp monochrome Nexa assistant spark as its collapsed bubble glyph.
  Chat keeps a short recent transcript; documentation clutter is reduced.
- A fixed natural-language prefix normalizer handles "Can you please open my
  Brave app?" while preserving the existing restricted tool grammar. The
  explicit Brave package is added to Android's package visibility list.
- The user-armed wake FGS now keeps one Model loaded and loops over short
  Recognizer turns. It pauses and releases AudioRecord during the compact
  assistant session and re-arms after VoiceInteractionSession.onHide or
  VoiceInteractionService.onShowSessionFailed.
- Wake enablement is persisted in application-private SharedPreferences. An
  explicit disable in the app, overlay or notification clears the saved
  preference. A stopped or interrupted service is not falsely described as
  currently listening just because the preference is enabled.
- After BOOT_COMPLETED or MY_PACKAGE_REPLACED, a receiver can show a
  user-visible reminder for previously enabled users. It DOES NOT attempt to
  create a microphone foreground service at boot. Opening the app visibly
  causes the bridge to resume listening when Android grants access.
- The service still posts a non-silent, accurate status notification with a
  Stop action and never blocks the Android green privacy indicator.

## Android platform constraints (not bugs we may bypass)

Android 12+ requires a microphone privacy indicator during microphone usage:
https://source.android.com/docs/core/permissions/privacy-indicators

Android 14+ applies while-in-use permission checks to microphone foreground
services, and boot receivers cannot launch microphone FGS:
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

VoiceInteractionService and optional always-on hardware hotword pathways are
system/OEM controlled. Merely becoming the default assistant does not enroll
an arbitrary third-party wake phrase on the phone's SoundTrigger DSP:
https://developer.android.com/reference/android/service/voice/VoiceInteractionService

An owner can configure wake listening to be enabled persistently, but an
ordinary installed app cannot guarantee silent hands-free recognition on
every boot, after arbitrary process kills, or without Android's microphone
disclosure. Do not market this update as indistinguishable from Gemini's OEM
integrated "Hey Google" recognition.

## Acceptance matrix

- Automated source: regression tests for retained wake state, notification-only
  boot receiver, constrained local Brave launch and compact UI.
- Android native instrumentation: durable preference true/false; no default
  opt-in; denied/unknown local commands; ensure permanent signing certificate
  unchanged on release.
- Physical phone (required): microphone start, "Hey Nexa" recognition, system
  assistant interaction, rearm after dismiss, multiple triggers, Stop action,
  disabled state after app update, reboot recovery prompt, denied permissions,
  battery optimization and lock screen. Inspect Android green dot and Stop
  notification. Measure false positives and power impact on the actual OEM.
- The icon update covers the overlay, notification glyphs and a new midnight-
  purple adaptive launcher icon on Android 8+; earlier versions retain the
  existing fallback raster icon.

## Remaining platform/product scope

A genuine Google-like hardware DSP always-on hotword requires a device/OEM
enrolled and enabled detector, and device-specific evidence. Provider-backed
chat inside the overlay and arbitrary phone-wide AI control are separate
features and not silently enabled. The source implementation's behavior
must still be verified on the actual phone.
