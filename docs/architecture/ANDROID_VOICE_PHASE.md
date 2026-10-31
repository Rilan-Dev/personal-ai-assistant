# Android Phase 3a — Foreground push-to-talk and offline voice

## Official Android references and upstream reuse
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/speech/RecognizerIntent
- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://developer.android.com/reference/android/speech/tts/Voice
- https://github.com/langchain-ai/langgraphjs
- https://github.com/langchain-ai/deepagentsjs

Reuses the existing LangGraph.js planning backend, device-side agent harness,
React Native -> Kotlin bridge and capability checks. The audio module is not
a second autonomous planner and does not expose the microphone to any model,
MCP plugin, or remote tool. Platform native speech APIs avoid new external
audio framework dependencies; all source is first-party bridge code.

## Behavior
- Android API 31+ only: on-device SpeechRecognizer, no automatic cloud
  transcription fallback. Check service availability before mic permission.
- Tap to talk prompts for RECORD_AUDIO only upon explicit user interaction.
- App must have foreground window focus. No background audio service.
- A single recognition session ends on result, timeout (25 seconds),
  cancellation, app pause, or destruction. Recognizer is destroyed.
- Transcript is editable chat text. NO automatic Send or tool execution.
- Read last reply aloud requires explicit user press and selects only a
  TextToSpeech Voice marked as not requiring network and already installed.
- Stop speaking interrupts playback; TTS shutdown on host destruction.
- Languages: phone default, en-IN, ta-IN (support depends on installed packs).
- Web/iOS do not have this Android-native bridge. No custom wake word.

## Privacy boundary
The audio bridge does not store recordings or contact the agent server.
If the user reviews the transcript and presses Send with an opted-in remote
backend, the text is sent as an ordinary command to that backend.
A network-dependent recognizer or TTS voice is never selected as fallback.
OS keystore checkpoint retention is separate from speech audio handling.

## Physical-device UAT required
1. Current CI APK: deny microphone grant; verify zero recognition.
2. Grant; speak a command; verify it only fills the editor, no tool action.
3. Press Send; verify existing app-launch grant still applies.
4. Stop listening, background/return, pause/restart: no automatic listening.
5. Test supported local languages and absent language packs.
6. Read reply, stop playback, missing offline voice -> controlled error.
7. Re-run journal recovery, one-time approval and side-effect replay checks.
Device test unavailable during source implementation; CI compilation is not UAT.

## 2026-10-31 emulator evidence
GitHub Actions run 41228773858 passed 13/13 Android API 35
instrumentation tests on commit e48ae7a, including four VoiceControlAndroidTest
checks: no wake word/offline-only advertised, foreground gate for microphone,
foreground gate for speech output, and idle cancellation cleanup.

These tests intentionally do not activate actual speech recognition or play
audio. A real Android phone with installed speech/voice packs is required for
those features, separate from emulator acceptance.
