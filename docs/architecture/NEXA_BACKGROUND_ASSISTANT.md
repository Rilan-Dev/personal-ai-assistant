# Nexa Android system assistant: background voice milestone

Status: source implemented on Development; Android emulator and real-phone acceptance required.

## User experience and activation

1. Owner opens Nexa's **Tools & permissions** page and taps **Prepare microphone & notifications**.
   Android supplies normal runtime consent; denying any permission leaves audio inactive.
2. Owner taps **Choose Nexa as default assistant**. Only Android's ROLE_ASSISTANT selection UI
   can grant the role; a request-result string is not treated as approval. Recheck status on return.
3. Owner uses the phone's supported system assistant invocation gesture/button. If Android
   dispatches to Nexa, a compact **Nexa AI** VoiceInteractionSession appears over the
   currently visible app, with Start, Stop, Open Nexa, and Dismiss actions.
4. **Start live voice** makes a user-initiated request for the existing microphone FGS.
   The FGS checks RECORD_AUDIO, obtains encrypted provider configuration and connects to
   the selected Sarvam managed Voice Agent or OpenAI realtime audio session.
5. The Android microphone status indicator and Stop notification remain visible while
   audio is active, including when the session panel is dismissed. The existing 15-minute
   limit, provider handshake timeout, audio-focus interruption stop and network failure stop apply.

## Non-features / important distinctions

- This milestone does NOT implement an always-on local "Hey Nexa" wake-word detector.
  ROLE_ASSISTANT makes the system invocation entry point eligible on supported Android
  builds; it does not grant Google's or an OEM's hardware hotword enrollment.
  Hotword detection requires a continuously monitoring local audio component while enabled.
- Android 14+ limits background microphone FGS creation. This implementation only starts
  via an explicit visible user action or the recognized assistant session. It does not
  schedule boot receivers, hidden recording or unsolicited background starts.
- No ambient screen capture, arbitrary Android gestures or invisible UI scraping.
  Existing ScreenAssistService remains read-once and cannot click or scroll.
- Sarvam speech-to-speech retains the audio-only client path. The user-facing app does not
  store/display transcripts. Provider-side behavior is subject to the owner's account.
- Sarvam Voice Agent is not currently connected to the Android tool registry. Spoken
  device-control commands and WhatsApp message delivery are NOT executed through STS.
  Adding authenticated voice tool calls requires a separate validated device-side
  intent policy, a trustworthy approval bridge and end-to-end verification.
- An installed app's launcher page, position and folder are NOT available through Android
  PackageManager. The GitHub check reports only the presence of allowlisted
  com.github.android, and can dispatch a launch Intent. Do not claim any home-page index
  or verified foreground app state.
- No WhatsApp messages are sent, and there is no autonomous contact matching or screen
  operation from this release. Outbound messaging requires a verified recipient,
  exact message preview, one-time owner confirmation, and a separate delivery status gate.

## Security and permissions

- `NexaAssistantService` is exported with `BIND_VOICE_INTERACTION`, meaning Android,
  not an arbitrary app, is allowed to bind it. The service itself remains lightweight and shares the app process, so the
  voice session can read live status. `NexaVoiceSessionService` is exported but
  protected by the same system-only binding permission. The mandatory
  `NexaRecognitionService` placeholder rejects all requests and opens no
  microphone; Nexa must not be selected as the system speech recognizer.
- Role status API returns `wakeWordReady:false`. Session never starts microphone from
  `onShow` and does not run or install a wake-word model.
- No `QUERY_ALL_PACKAGES`, `READ_CONTACTS`, background location, boot receiver or
  broad accessibility actions were added. The GitHub app check allows only named packages.
- Stop is always available from the voice session panel and the persistent notification.
  A task cannot infer successful launch, message send, or audio connection from Intent
  dispatch alone. Owner must approve any future outbound communication.

## Acceptance plan

Android emulator:
- Confirm service manifests/voice-interaction XML parse and are discoverable.
- Check assistant-role result returns available/selected flags and false wake-word readiness.
- Verify assistant session never auto-records; no credentials and denied mic block Start.
- Verify app package lookup returns accurate package and `homeScreenPage:unknown`.
- Ensure unknown app IDs are rejected; settings/service remain unexported as designed.
- Compile and run Kotlin Android tests; verify signed APK artifact and checksum.

Physical device:
- Select Nexa via Android's default assistant selector; invoke by gesture.
- Observe compact panel on top of another application without opening Nexa full screen.
- Grant/deny RECORD_AUDIO and POST_NOTIFICATIONS and verify stop/error states.
- Verify foreground FGS and notification persist when switching apps, locking/unlocking
  where permitted, and that mute and stop work. Measure audio focus/network loss behavior.
- Test Sarvam voice with a configured account without posting private speech into logs.
- Compare Android 13, 14, 15/16 OEM/background battery restrictions on actual hardware.
- Check GitHub presence/absence and launch request; do not claim icon page discovery.
- Confirm locked-screen and unsupported-assistant devices fall back to explicit app/tile.

Release policy: no public claim of working hands-free wake word or automated WhatsApp
messaging until the corresponding real-device acceptance cases pass.
