# Sarvam AI: chat, dictation, spoken answers, managed realtime STS

Date: 2026-11-01
Status: IMPLEMENTED IN DEVELOPMENT SOURCE; NOT LIVE-PROVIDER/PHONE ACCEPTED

## Supported modes

| Surface | Sarvam model/service | Protocol | App transcript |
| --- | --- | --- | --- |
| Interactive Chat | sarvam-105b (editable model ID) | HTTPS POST /v1/chat/completions | User-entered chat and AI answers are displayed |
| Explicit one-turn dictation | saaras:v4 | HTTPS multipart WAV /speech-to-text | One editable user-reviewed result only |
| Explicit read-aloud | bulbul:v4-flash | HTTPS JSON /text-to-speech WAV | Only the already-visible assistant reply is sent for TTS |
| Live conversation | Sarvam managed Voice Agent / Samvaad | Signed WSS full-duplex 16k PCM16 audio chunks | No transcript handlers, no local transcript display/persistence |

The user requested speech-to-speech without transcripts for live conversation.
The native Android live path sends and receives AUDIO only. It ignores all
server text, transcript and tool events. It does not create a JS transcript,
write audio or transcript files, or send any text to the app chat. This does
NOT imply Sarvam's managed agent has no transcription: Sarvam may use its
own speech recognition, dialogue model and TTS internally, and may retain
call recordings/transcripts according to the Sarvam account configuration.
Check the dashboard/data-processing terms before processing sensitive speech.

## Official documents verified

- https://docs.sarvam.ai/api/token-factory/chat-completions
- https://docs.sarvam.ai/api-reference/chat/chat-completions-v1
- https://docs.sarvam.ai/api-reference/speech-to-text/transcribe
- https://docs.sarvam.ai/api-reference/text-to-speech/convert
- https://docs.sarvam.ai/voice-agents/deploy/sdks/react-native

Upstream source inspected from npm: sarvam-conv-ai-sdk 0.0.42, MIT licensed.
Inspected VoiceAgent and SocketManager message contracts and audio interface.
Rather than shipping an unbounded React Native JS audio loop, the Android
Foreground Service uses the verified wire protocol in native Kotlin for
background continuity. The official SDK remains the API reference, and we
do not assume signed-URL layouts or protocol fields beyond that source.

Signed-WSS handshake:
  GET https://apps.sarvam.ai/api/app-runtime/orgs/{orgId}/workspaces/{workspaceId}/apps/{appId}/url?interaction_type=call
  Header: X-API-Key: [owner-entered secret]
  Response: url, reference_id
  Only connect to WSS and hosts within *.sarvam.ai on normal TLS port.
  Add user_identifier (randomly generated per installation in encrypted
  vault), user_identifier_type=custom, interaction_type=call,
  input_sample_rate=16000, output_sample_rate=16000.

Protocol:
  client.action.interaction_start
  server.action.interaction_connected => start native AudioRecord/AudioTrack
  client.media.audio_chunk => raw signed 16-bit mono PCM16, base64,
    sample_rate=16000, format=audio/wav (the SDK's format enum)
  server.media.audio_chunk => decode base64 raw PCM and play through AudioTrack
  server.event.user_interrupt => clear stale output
  server.system.ping => client.system.pong
  server.action.interaction_end => release audio and close the WS

The app does NOT execute Voice Agent tool suggestions or expose Android
native actions to the Sarvam managed session. Android FGS microphone
permission, owner-start, 15-minute hard limit, non-exported service and
persistent Stop notification are preserved.

## Owner setup

1. In Settings > AI Configuration select Sarvam AI, model sarvam-105b,
   and enter a personally managed Sarvam API subscription key. Save it.
2. For full-duplex STS, create and COMMIT a voice agent in Sarvam's
   dashboard. Obtain org_id, workspace_id and app_id.
3. In Settings > Sarvam Voice Agent, enter the three IDs and Save.
   The app stores IDs, a pseudonymous user ID, and the API key inside
   Android Keystore AES-GCM encrypted preferences. Do not paste credentials
   into GitHub issues, chat logs, or a public repository.
4. Go to Voice > Start live voice session. Explicitly approve mic and
   notifications. Speak, listen to replies, interrupt naturally. The
   Android system notification has a Stop action, and a session times out.
5. For ONE user-edited message, tap to dictate; Saaras v4 performs
   transcription and places editable text in the chat composer. This
   is separate from the live audio-to-audio session.
6. To read an existing chat answer aloud, tap Read last reply;
   Bulbul v4 Flash synthesizes WAV audio which plays on the speaker.

## Acceptance boundaries

- No owner's API key, managed Voice Agent IDs or live audio are available to
  automated tests. Tests cover source contracts, secure separation and
  Android Keystore instrumentation with synthetic configuration.
- Native Kotlin compilation, Android emulator tests, release signing and
  a physical phone test remain required before claiming deployment.
- Direct STT/TTS REST path is one-turn, NOT standalone realtime streaming.
  The realtime full-duplex audio is the managed Samvaad Voice Agent WSS.
- Sarvam server calls can incur charges. Networking, VAD, echo cancellation,
  speech interruption, phone calls and background Android OEM restrictions
  must be verified on a real device.
- No transcript/local history of live voice is recorded by app; this is not
  a claim about Sarvam backend data retention.

## Post-release stabilization — native voice cancellation and owner mute
- Fixed one-shot Saaras/Bulbul operation overlap: a cancelled request
  remains locked until the previous worker finishes cleanup. Active
  OkHttp REST calls are cancelled on the owner's Stop/Cancel action,
  rather than merely hiding a result while the network is still
  transmitting or downloading sensitive audio.
- Disabled redirect following on all Sarvam speech REST calls so an
  owner-entered subscription key cannot be forwarded to an arbitrary
  Location from a remote HTTP response.
- Added a bounded AudioTrack playback-head drain before reporting
  finished TTS. A completed audio write is not proof that the speaker
  finished playing. User cancellation takes priority.
- Added owner-initiated microphone mute/unmute controls in Voice and
  in the Android foreground notification. Muted sessions send
  silence PCM16 frames to preserve server voice-turn timing, **not
  recorded human speech**. Android microphone capture continues
  internally while muted; Stop ends microphone recording entirely.
  Raw PCM sending and mute transitions are synchronized.
- There are no new background permissions; the existing owner-started,
  non-exported microphone foreground service remains the entry point.
- Emulator acceptance tests include synthetic WAV PCM16 round-trip and
  malformed PCM format rejection without network credentials.
