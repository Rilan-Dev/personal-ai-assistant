# Nexa Samvaad workspace key — voice-only privacy mode

**Updated 2026-11-02.** This document distinguishes Nexa-controlled data selection from Sarvam-controlled runtime data handling. Do not claim that a cloud-managed Voice Agent offers zero retention.

## Bug and architecture correction

The **AI Provider** card configures Chat Completions on Sarvam's Model API endpoint and accepts a Model API subscription key and model name. A `sk_samvaad_` workspace key belongs to **Voice Agents**, which use a different signed CALL WebSocket. The Chat test fails with an authentication response even though a voice-workspace key may be valid.

- Nexa now rejects Samvaad keys in the Chat Model API card with an explicit explanation.
- Nexa now displays a separate **Samvaad workspace key (voice only)** field in the Sarvam Voice Agent card. The private value is encrypted by the existing Android Keystore AES-GCM store under a distinct key. It is not written to GitHub, logs, the React Native chat provider JSON, the app journal history, or user-facing read methods.
- Voice Agent IDs and the voice-only key are independent of all Chat model settings. Read-only Verify Sarvam Voice Agent connection uses those on-device credentials, without microphone capture and without requiring streaming consent.
- A new voice key revokes prior streaming consent. Clearing the key or disabling consent stops the live Android foreground service. The private voice key is never returned to JavaScript.
- A dedicated voice key takes precedence. If streaming is not explicitly allowed, `openVoiceCredentials` returns null rather than switching silently to an old chat/OpenAI key.

## Privacy boundary

- **Default: audio streaming disabled.** Nexa requires the owner to activate **Allow Sarvam voice streaming**, after showing that Sarvam can retain conversation contents.
- When authorized, the mobile client sends realtime microphone PCM and the managed Voice Agent call parameters required for authentication and routing. It does **not** send NexaHub database records, contact lists, messages, files, screen captures, camera content, or broader device inventory to Sarvam.
- In dedicated private-voice mode, returned transcript events do not initiate Android tool actions or write to Nexa Chat or a transcript log. Device actions stay on-device; the managed Sarvam Voice Agent itself still recognizes speech internally and may retain provider-side call records.
- Existing Chat, local phone tools, encrypted owner journals, and other app permissions are distinct. NexaHub is not a dependency of the Samvaad voice transport.
- No code changes remotely modify agent prompts, tools, workspace data retention, or Sarvam dashboard settings. Those provider-side settings **can affect** the managed agent's behavior; they cannot be overridden by a mobile key or STS WebSocket.
- Sarvam officially documents call recordings, transcripts, and traces. Zero-day retention is NOT offered for Voice Agents (although it is offered for Model APIs), and unconfigured workspace retention defaults to indefinite. The owner must change Organisation → General model-training preference and Workspace → Data retention, Voice Agents override. Retention settings are not retroactive.
- Therefore **true zero-provider-storage cannot be guaranteed with managed Samvaad Voice Agents**. If that is an absolute requirement, leave audio streaming disabled and use a suitably privacy-contracted/self-hosted voice stack instead.

## Acceptance checklist

1. Insert a *rotated* workspace Voice Agents key in the separate Samvaad key field; save the three agent IDs. Check presence (boolean) and confirm no key printed in RN/logcat.
2. Run read-only Verify Sarvam Voice Agent connection. AUTHORIZED means only a signed URL was issued. Do not record/play audio as part of readiness.
3. With streaming opt-in OFF, attempt to start Voice and Hey Nexa: ensure no Sarvam microphone packets, no hidden alternative provider fallback, and clear in-app explanation.
4. With informed opt-in ON, explicitly start a bounded microphone call; verify multi-turn STS, Stop, rearm, no recorded transcript in Nexa, and no device tools triggered by remote transcription.
5. Confirm that deleting the workspace key/consent interrupts the call and that it cannot auto-start on the next wake event.
6. For real provider-side retention, verify the Sarvam dashboard settings directly with the organization owner and establish an acceptable contract; Nexa cannot inspect or enforce these flags from the documented CALL protocol.
7. Confirm the Android emulator, Kotlin compilation, signed APK and physical phone tests before releasing as complete.

Sources:
- https://docs.sarvam.ai/api/platform/data-retention
- https://docs.sarvam.ai/voice-agents/monitor/call-recordings
- https://docs.sarvam.ai/voice-agents/api/analytics/overview
- https://docs.sarvam.ai/api/platform/faq
