# Nexa — Sarvam managed WebSocket live-voice acceptance

**Date:** 2026-11-02. Status: source-ready; provider-authenticated call and physical Android audio still **unverified**.

## Target user experience

1. Owner explicitly enables background wake listening and live AI invocation. The offline wake detector hears “Hey Nexa” only to launch the compact overlay and the managed live voice foreground service.
2. Nexa decrypts the user's saved Sarvam key + Organization / Workspace / Agent App IDs **on-device**, obtains a signed Voice Agent URL through Sarvam's app-runtime API, and opens **one continuous managed CALL WebSocket**.
3. Nexa sends mono 16-bit PCM at 16 kHz in `client.media.audio_chunk` frames; Sarvam sends `server.media.audio_chunk` PCM and user/agent events over the same socket. Mic and speaker remain active across turns, with interruption, visible mute, Stop, audio focus, and Android notification.
4. Sarvam's managed Voice Agents runtime, not Nexa's one-turn dictation UI, orchestrates ASR → LLM + context/tools → streaming TTS → next turn. A transcript event may be used in memory for the existing strictly limited approval sidecar; it is **not** converted into a separate visible dictation session or a second Chat completion.
5. The user may pause or hear an extended agent response without the old 45-second idle timeout cutting off the call. The client keeps a 120-second inactivity exit and a visible owner-stop option. A 60-minute maximum prevents uncontrolled background recording, consistent with Sarvam's published maximum call length; a particular agent can end calls earlier.

## Saaras model selection — do not conflate protocols

- `saaras:v3-realtime` is the default for Sarvam's **standalone realtime STT WebSocket** (`/speech-to-text-realtime/ws`). `saaras:v4` is available on that same endpoint. The **reference** `Rilan-Dev/ai-comms-agent/infra/voice-relay/src/sarvam-wire.mjs` uses that endpoint with a server relay, separate text/agent runtime, and Bulbul TTS WebSocket.
- Nexa's primary selected **managed Voice Agent** uses the **different** `/api/app-runtime/.../apps/.../url` signed CALL WebSocket. The configured Agent's own server-side STT/LLM/TTS models are chosen in Sarvam Voice Agents dashboard; the documented signed URL does **not** support a caller-selected `model=saaras:v3-realtime` override. Do not silently connect a second raw STT socket or advertise that managed agent recognition has been changed by Nexa client code.
- To require Saaras v3 for the agent, select it in Sarvam Agent Settings where supported and commit/publish that version. If the platform instead routes Saaras v4 automatically, the mobile client cannot override that behavior.

## Authenticated readiness diagnostics

Nexa → Voice → **Verify Sarvam Voice Agent connection** runs a read-only, foreground-owner-initiated HTTPS authorization check from the phone's encrypted saved credentials. It returns only a safe code:
- `AUTHORIZED`: Sarvam returned a signed trusted WebSocket URL + interaction reference. Does **not** prove that the microphone, playback, or conversation works.
- `MISSING_CONFIG`: saved provider/key/Voice Agent fields missing or inaccessible.
- `AUTH`: key invalid, expired, revoked, or wrong workspace grant.
- `AGENT_NOT_FOUND`: exact App ID, workspace/org, or published version was not found.
- `INVALID_AGENT`, `RATE_LIMIT`, `NETWORK`, `PROVIDER_ERROR`, `MALFORMED`, `BAD_SIGNED_URL`: safe bounded failures requiring further diagnostics.

**Never copy a key or signed URL into chat, GitHub, logs, or CI.** A previously exposed API key must be revoked/rotated. Enter the replacement in Nexa's encrypted Settings and copy the complete Agent App ID from Sarvam (avoid shortened UI display labels).

## Distinct security boundary for device actions

Sarvam Voice Agents has a **managed agentic harness** that runs model-callable HTTPS API tools. Nexa's currently implemented `NexaVoiceIntentGate` is a narrower **local transcription-based, permissioned app-launch proposal**, and Sarvam's model does not receive its result. That is not a full managed model→Android action→tool-result loop.

Actual managed tool use requires:
1. Sarvam agent has a published HTTPS API tool pointing to an authorized broker (not directly to a phone).
2. Broker authenticates the unique call/session and device owner; enqueues a narrowly allowlisted tool proposal with bounded idempotency, expiry and an explicit device permission review.
3. Phone only executes an owner-approved action, securely reports the observed result, and the broker returns it as the Sarvam API tool's synchronous response (or a safe pending/timeout result). No unverified “WhatsApp opened” claims; never send messages without an extra explicit confirmation.
4. Agent repeats the managed loop with the **actual tool result** in context. Do not treat `server.event.tool_call` telemetry as a client-side execution request, and do not invent a `client.tool_result` frame.

This broker and agent-dashboard tool registration are **not** implemented or deployed by this change.

## Validation ladder (do not conflate)

- Source tests, TypeScript and CI Android emulator: validate code/permissions/lifecycle under isolated conditions; no real vendor grants.
- On-device signed URL readiness: verify key, IDs and published agent access from a foreground tap, without microphone capture.
- Physical-phone audio acceptance: with the newly rotated key and fully committed agent, start live Voice; confirm WebSocket `interaction_connected`, 30 seconds of bidirectional audio, several user/agent turns, barge-in, explicit Stop/restart, wake transition and Android microphone indicator. Run a tool approval/denial without sending messages, then verify result.
- If the phone crashes: record redacted AndroidRuntime/ActivityManager logcat. One emulator build does not establish OEM-specific microphone and service behavior.

## Sources

- Sarvam Voice Agents SDK: https://docs.sarvam.ai/voice-agents/deploy/sdks/web
- Sarvam managed runtime: https://docs.sarvam.ai/voice-agents/build/run-time
- Sarvam harness: https://docs.sarvam.ai/voice-agents/build/concepts/harness
- Sarvam API tools: https://docs.sarvam.ai/voice-agents/build/tools/https-tool
- Sarvam standalone Saaras realtime: https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/realtime-streaming
- AI Comms Agent reference: `infra/voice-relay/src/sarvam-wire.mjs`, `infra/voice-relay/src/sarvam-relay.mjs`
