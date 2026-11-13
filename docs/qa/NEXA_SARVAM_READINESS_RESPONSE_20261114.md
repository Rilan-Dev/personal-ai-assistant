# Sarvam one-time readiness: verify the actual response structure — 2026-11-14

## Defect

The owner-tapped `SarvamModelApiReadiness.check` performed STT and TTS WebSocket handshake probes and two synthetic `/v1/chat/completions` requests. It **treated any successful HTTP status as an accepted model response**, even a 200 with an error object, missing `choices`, null/blank `message.content`, truncated completion or an unexpected `tool_calls` array. The UI could falsely claim model/tool-schema readiness without a model response actually proving the schema works.

## Change

- New pure `NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion` checks exactly one `choices[0].message`, `role=assistant`, a bounded nonblank text content, no tool calls and no `finish_reason=length`.
- **Both** the first synthetic conversation request and the subsequent request containing Nexa's actual model tool declarations must satisfy the validator, using the pre-existing `NexaSarvamResponseBounds` 140 KB byte limit.
- Synthetic requests still say only `Reply OK only`, use no phone screen content, run no tool, and record no audio. Unsupported or malformed HTTP 200 now produces `MODEL_RESPONSE` or `MODEL_TOOL_RESPONSE` instead of `AUTHORIZED`.
- The successful readiness copy distinguishes WebSocket handshake success from real audio acceptance. It does **not** claim a live STS conversation, wake-word or overlay acceptance.

## Test record

- Native implementation source revision: `af5289a843c46bfa95f6aa2681f130e073d008d0` (later `b906cd8` updates only a legacy Node source assertion).
- Focused Node source tests: **3/3 passed** at native source SHA.
- Initial full Node suite: **498/499 passed**, one legacy source-regex test required a now-obsolete `if(it.isSuccessful)probeToolSchema()`. Its test was updated in subsequent commit `b906cd8` to require validation before the next probe; **fresh full regression after that commit still requires a recorded run**.
- TypeScript check was completed in the isolated worktree; confirm final execution log when reconnecting.
- Android arm64 Gradle build for native implementation source was in progress when BridgeHead's Windows tailnet connection became `PEER_UNREACHABLE`; **do not claim native compile or physical Android tests as passed without checking the complete build log**.
- Physical Infinix has Android 13, WhatsApp installed and an independently managed Maestro test session that may occupy Android's single global UiAutomation slot. Do not terminate that session automatically or change Android security permissions.

## Acceptance

Once BridgeHead reconnects: fetch latest feature branch into the clean detached isolated worktree, rerun complete Node suite, verify native source blobs match the built APK, finish/redo Android Gradle assembly if needed, run `NexaSarvamProbeResponseAndroidTest` plus source-identity in the separately signed `.physicalqa2` package. Real Sarvam BYOK/authenticated voice, wake and overlay remain separate owner-consented checks.

No production services, main branch, real provider key, consumer app or platform permission changed.
