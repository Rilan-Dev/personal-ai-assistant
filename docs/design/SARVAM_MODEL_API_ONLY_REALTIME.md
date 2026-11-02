# Nexa API-key-only realtime voice migration

**Date:** 2026-11-02. **Branch:** Development → main after source validation. **Acceptance:** native Android CI and actual key-authorized physical phone session pending.

## Architecture and constraints

Nexa no longer requires Samvaad Voice Agent organization, workspace or Agent App IDs for live voice. The owner selects **Sarvam AI** under the standard Chat Model API provider and saves a `sk_` subscription key in Android Keystore. Realtime live voice uses that same Model API key; it rejects `sk_samvaad_` workspace-only keys.

The managed Samvaad transport remains as unused legacy source code, not selected by `openVoiceCredentials` or the new user-facing Voice flow. The owner-facing Settings card describes direct **Sarvam API Realtime Voice**.

### Continuous in-app audio conversation

1. Android foreground microphone service is started by an explicit user action or opted-in Hey Nexa wake trigger, with visible recording indicator and Stop.
2. `SarvamApiVoiceTransport` establishes one continuous **Saaras v3 Realtime** STT WebSocket (`wss://api.sarvam.ai/speech-to-text-realtime/ws`) configured for PCM16 mono 16 kHz, `language_code=auto`, fast partials, VAD turn boundaries. Audio packets use `{event:audio_input,audio:base64}`. The service responds to user barge-in using `vad.speech_start`; final transcripts are never displayed in Chat or written to disk.
3. Each final owner utterance is passed through the Android allowlisted native-action gate. Approval-required actions wait for a real owner tap; the observed result, not model inference, is fed into the in-memory conversation and spoken back.
4. Other utterances use the same key against **Sarvam-105B Conversations** (`POST https://api.sarvam.ai/v1/chat/completions`). Per-session user/assistant turns are bounded to twelve and live in RAM only.
5. The answer streams over the **Bulbul v4 Flash** TTS WebSocket (`wss://api.sarvam.ai/text-to-speech/ws`, output `linear16` 16 kHz), played directly to Android AudioTrack. Speech interruptions clear local playback and close/reinitialize the active TTS stream. No separate one-shot STT/TTS UI is part of live mode.
6. Keepalives, bounded turn memory, capped audio backlogs, 120-second idle termination, maximum one-hour user-visible session, microphone mute, audio focus, owner Stop and exceptions retained from the native service.

**Technical truth:** This is a low-latency STT → model → TTS conversation coordinated locally on Android. It is **not** a single documented native speech-to-speech model/WebSocket, and not the hosted Samvaad Voice Agent harness. Only Saaras recognition and Bulbul synthesis maintain streaming WebSockets; LLM calls occur on utterance boundaries.

### Reference review and server access boundaries

Compared with `Rilan-Dev/ai-comms-agent`, Development branch:
- `infra/voice-relay/src/sarvam-wire.mjs`: source of documented Saaras realtime WS protocol, `audio_input`, `transcript.final`, `vad.speech_start`, keepalives, and `saaras:v3-realtime` default.
- `infra/voice-relay/src/sarvam-relay.mjs`: server-side HMAC and user-session authentication are tied to AI Comms accounts and its private knowledge-base IDs; Nexa **does not** reuse those sessions or upload private Nexa data to that server.
- `infra/voice-relay/src/sarvam-tts-relay.mjs`: source for streaming Bulbul config/text/flush/audio response and barge-in. No remote deployment changes were made.

All scratch validation used only the **AI Comms Agent** BridgeHead server. **AlmaLinux home server was not accessed or modified**; no user approval had been given for it. No code or Docker changes were made to running AI Comms Agent services.

### Privacy and security

- Key remains in local Android Keystore AES-GCM; no API keys in APK, repository or GitHub CI. Exposed keys pasted into Chat should be rotated before production use.
- Voice streaming requires a separate explicit per-device consent. Replacing the Model API key revokes that consent. If consent is absent, **neither Hey Nexa nor Voice tab** can establish a Sarvam API session. No automatic fallback to OpenAI or managed Samvaad.
- The transport only sends microphone PCM to Saaras, short recognized utterances and limited conversation context to Sarvam-105B, and response text to Bulbul. It does **not** send NexaHub files, contacts, photos, screen or messages to any provider. No conversation transcript is saved in Nexa journal or Chat.
- Sarvam Model API processing, billing, provider logs or retention policies cannot be controlled solely from the phone. Cloud processing is not local/private inference; zero vendor retention must not be promised without enforceable vendor terms.
- App actions are separately checked against explicit owner speech and require one-tap review. No autonomous outbound messages or arbitrary API-driven phone control.

### Validation plan and honest limitations

- Node source tests + TypeScript typecheck + web export must pass.
- Android Kotlin compilation, emulator instrumentation (native Keystore, microphone FGS and codec configuration), permanent signing and release APK must pass GitHub Actions.
- Only the owner can validate authenticated speech streaming on their phone using a **newly rotated**, on-device key. Use Voice → Verify Sarvam API realtime connection to test two WS handshakes plus one synthetic model call with *no microphone*. Real acceptance requires 3+ turns, 30s response, interruption, Stop/restart and no crashes.
- Exact provider credentials and physical device logcat have **not** been tested/obtained in this source-only phase. The user should never paste a replacement key into Chat or CI.

Sources:
- https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/realtime-streaming
- https://docs.sarvam.ai/api/token-factory/chat-completions
- https://docs.sarvam.ai/api/api-guides-tutorials/text-to-speech/streaming-api/web-socket
- https://docs.sarvam.ai/api-reference/authentication
