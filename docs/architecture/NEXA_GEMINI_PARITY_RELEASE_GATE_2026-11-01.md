# Nexa Android — Gemini-style assistant parity and release gate (2026-11-01)

## Supported product contract

Nexa is a normal user-installed Android APK, not Google system software.
It never suppresses Android's microphone privacy indicator or FGS notification.
Hardware SoundTrigger DSP hotword invocation without a continuous visible
microphone session requires authorization from the device OEM/system image;
it is **not available for arbitrary sideloaded or Play-distributed APKs**.

Two independently selected capabilities:

1. **Activation (Android role + optional local wake):** Android assistant gesture
   by user-selected `VoiceInteractionService` is default and does not record
   while idle. Offline Vosk wake is optional and has a visible microphone FGS.
2. **After invocation:** one offline allowlisted device command (default), or
   an opt-in, configured OpenAI/Sarvam speech-to-speech conversation. The opt-in
   is saved from visible React Native Setup, requires current voice permissions,
   and fails closed if the provider's encrypted credentials are unavailable.

The voice provider does not have authority to invoke unreviewed device tools.
Calls/messages remain separately confirmed. Closing the assistant window
terminates sessions started from that window.

## Code implemented on Development

- `NexaSetupModule.kt`, `nexaSetup.ts`, `NexaSetupScreen.tsx`,
  `App.tsx`: visible opt-in, provider and permission checks, typed bridge.
- `NexaAssistantService.kt`: explicit invocation to selected live or local
  mode, no idle AudioRecord, stop session-started conversation on hide/destroy.
- `RealtimeVoiceService.kt`: stale audio guarded by response IDs,
  interruption epochs and synchronized speaker flush. Native AEC/NS remain
  opt-in according to device capability; VAD continues to receive microphone.
- Existing Sarvam audio websocket and OpenAI Realtime websocket remain separate
  from the direct local device-action planner.

## Acceptance matrix (NO-GO until all applicable items pass)

| Gate | Evidence required | Current state |
|---|---|---|
| Node regression | `npm test` on exact source SHA | Passed on connected Linux (199 tests; rerun after final change) |
| TypeScript | `npm run typecheck` | Passed on connected Linux (rerun after final change) |
| React Native export | `npm run web:export` | Passed on connected Linux (rerun after final change) |
| Kotlin/Gradle compile | Android Debug workflow green for the exact SHA | Not yet run |
| Emulator instrumentation | API 35+ native `connectedAndroidTest`; consent rejection and cancellation tested | Not yet run |
| Real provider | Active authorized OpenAI/Sarvam session; speech follow-ups and barge-in | Not yet run (requires real account) |
| Real phone | Assistant gesture, background/lock, repeated invocation, first utterance, 100 triggers and UI indicators | Not yet run (no device reachable) |
| Battery/reboot | Controlled 1-hour standby with opt-in off/on, CPU/wakelock data, reboot/update recovery | Not yet run |
| OEM protected hotword | Signed OEM/system integration documented on supported SKU | Not available to normal APK |
| Signing/release | Same pinned permanent SHA-256 certificate, monotonic versionCode, in-place upgrade retaining app data | Not yet run for this SHA |

## Native test procedure on a device owner controls

1. Run the exact-SHA Android native debug compile and emulator workflows.
2. Install the same source build using the pinned release certificate if
   testing an upgrade; never uninstall a prior key-locked app without a backup.
3. Confirm Setup default: automatic AI conversation OFF; wake mic OFF; green
   privacy dot absent while idle.
4. Select Nexa as the default assistant, grant runtime mic and notification
   permissions, configure OpenAI or Sarvam voice agent, enable automatic voice
   in Setup. Dismiss/activate via gesture twenty times, observe connection,
   talk, interruption, second turn, Stop and privacy dot clearance.
5. Repeat with denied microphone, revoked permission, invalid credentials,
   airplane mode, headset, phone call, lock/unlock, and rapid double-invoke.
   No hidden recording, unreviewed message, crash or stuck microphone allowed.
6. With Vosk opt-in, measure 100 wake triggers under accents/noise with
   device logcat and battery metrics, document false activations and missed
   triggers. Confirm the notification and microphone indicator remain present
   during continuous capture; end via Stop.
7. Reboot and update with the same signing certificate and increased version
   code. Verify settings persistence, notification accuracy and no unexpected
   microphone activation on migration.
8. Sign and publish APK **only after** emulator and real-device evidence.

## Blockers verified in this session

- BridgeHead home Android server was unreachable through the tailnet.
- Alternate accessible Linux host has about 4 GiB available RAM, lacks
  Android SDK platform 36 and Java 17+, and cannot safely run native Android
  compilation without resource/environment preparation.
- CamoFox GitHub browser is logged out, therefore cannot manually dispatch
  GitHub Actions; the GitHub connector available here cannot dispatch
  workflows. Do not use an old workflow artifact as proof for current code.
- Android device/OEM protected hotword and real provider account acceptance
  cannot be invented from source-based tests.

Official platform references:
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- https://source.android.com/docs/core/audio/sound-trigger
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developers.openai.com/api/docs/guides/realtime-conversations
- https://docs.sarvam.ai/voice-agents/deploy/sdks/web
