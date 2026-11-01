# Nexa continuous assistant voice session — November 2026

## Architecture after this change

`NexaWakeWordService` remains an opt-in on-device wake detector that uses a
visible microphone foreground service. After a phrase match the service
releases the microphone and opens Android's user-selected
`VoiceInteractionSession`. The new compact voice plate appears at the
bottom of the current screen instead of filling the display with six
oversized buttons.

**Default offline mode:** `NexaVoiceSession` owns a bounded multi-turn
interaction (up to 16 local tool requests). Each individual Android
`SpeechRecognizer` call is only a single recognition turn. After each
successful recognition, the session calls `NexaOverlayCommands.handle()`,
reports the actual result, optionally reads a short result through Android
`TextToSpeech`, and starts a new recognition turn. A 18-second silence
window, recognition timeout, explicit stop/goodbye, app navigation or the
turn budget ends the interaction. On hide, the session cancels recognition,
shuts down TTS, releases the mic and asks the previously armed detector to
resume. A missing offline speech engine remains a detectable blocker, not
a justification for recording secretly or transmitting audio without consent.

**Opted-in managed live STS:** Existing OpenAI or Sarvam voice sessions are
only started automatically after a previously saved owner preference and
encrypted valid provider credentials. `RealtimeVoiceService` sends real
microphone audio over its selected provider connection, handles streamed
responses and interruption, and stays foreground with a persistent Stop
notification and Android microphone indicator. A best-effort 45-second
local speech-activity idle check stops the live call and returns to wake
listening. Noise can prevent an idle detection. A hard session cap still
applies.

## Live tool calls and outcomes

For **OpenAI Realtime only**, the Android live voice service advertises
three function tools: get_device_status, get_battery_level and
request_open_app. When a model returns a function_call item in
response.done, the native `NexaLiveToolPolicy` strictly validates the
arguments, de-duplicates call IDs, enforces a 12-call budget, and executes
only its allowlist. Battery/status are read-only.

Brave, GitHub and WhatsApp launches are proposals, not automatic actions:
the model's request sets a pending approval; the visible assistant plate
offers **Approve app** and **Deny**. The actual launcher intent is only
dispatched after explicit approval; the reported result never claims the
target UI was independently verified. The service sends a function_call_output
item back to the same provider session followed by response.create, allowing
the model to speak the result or request another permitted tool.

The TypeScript `AgentHarness` still implements its own journal,
permissions and approval semantics in the full application. This native
Realtime function gateway does NOT silently run that full agent harness,
does NOT send WhatsApp messages, run arbitrary commands, grant
Accessibility permissions, read screens, or control other applications.
The Sarvam managed transport currently processes audio only and exposes
no application-level tool callbacks; therefore we must not claim the same
tool execution for Sarvam STS.

## Verification gates

- Source/TypeScript tests and regression checks for continuous turns and
  bounded function tool loops
- Android native emulator instrumentation for tool policy, allowlists,
  malformed input, durable silence boundaries, assistant role/services
- Signed release APK build and signing-key identity verification
- Physical phone: compact assistant plate geometry, one-utterance STT/TTS
  reliability, two or more sequential commands, API provider response,
  explicit approval/denial, 45s idle return, repeated Hey Nexa invocations,
  permission revocation, app update, battery and reboot behavior

Android restrictions and OEM microphone policies remain in force. A voice
session that compiles and passes emulator tests is not equivalent to full
physical device acceptance or hardware DSP wake-word parity.
