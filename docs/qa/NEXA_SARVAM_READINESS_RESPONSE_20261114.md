# Sarvam one-time readiness: verify the actual response structure — 2026-11-14

## Defect

The owner-tapped `SarvamModelApiReadiness.check` performed STT and TTS WebSocket handshake probes and two synthetic `/v1/chat/completions` requests. It **treated any successful HTTP status as an accepted model response**, even a 200 with an error object, missing `choices`, null/blank `message.content`, truncated completion or an unexpected `tool_calls` array. The UI could falsely claim model/tool-schema readiness without a model response actually proving the schema works.

## Change

- New pure `NexaSarvamProbeResponse.hasNonExecutingAssistantCompletion` checks exactly one `choices[0].message`, `role=assistant`, a bounded nonblank text content, no tool calls and no `finish_reason=length`.
- **Both** the first synthetic conversation request and the subsequent request containing Nexa's actual model tool declarations must satisfy the validator, using the pre-existing `NexaSarvamResponseBounds` 140 KB byte limit.
- Synthetic requests still say only `Reply OK only`, use no phone screen content, run no tool, and record no audio. Unsupported or malformed HTTP 200 now produces `MODEL_RESPONSE` or `MODEL_TOOL_RESPONSE` instead of `AUTHORIZED`.
- The successful readiness copy distinguishes WebSocket handshake success from real audio acceptance. It does **not** claim a live STS conversation, wake-word or overlay acceptance.

## Verified test record

- Native source/build revision: `af5289a843c46bfa95f6aa2681f130e073d008d0`. Commits `b906cd8` and `f707814` changed only a legacy Node source-regex test and this document: GitHub compare verified **no Android source/test blob changes**.
- Focused Node source regressions: **8/8 PASS** after the stronger probe assertion replaced the stale HTTP-200-only expectation.
- Complete Node suite at feature head `f707814`: **499/499 PASS**; Windows isolated log `%TEMP%\\nexa-readiness-full-f707814.log`.
- TypeScript at feature head: **PASS**, `%TEMP%\\nexa-readiness-typecheck-f707814.log`.
- Arm64 isolated QA2 Android app + AndroidTest APK assembly and portable Maestro JUnit: **BUILD SUCCESSFUL** (2m), `%TEMP%\\nexa-readiness-gradle-af5289a.log`. BridgeHead disconnected *after* the command began; direct inspection of the completed local Gradle log after reconnect confirmed success.
- Isolated QA2 package: `dev.rilandev.personalaiassistant.physicalqa2`, versionCode **2026111406**, updated **in place** after matching the existing signer. App SHA-256 `759CE602105C3458978B27FF140F677B59D7F38720733A5C2342C8233DF5FA8E`; AndroidTest APK SHA-256 `F9890702AF86C516B48212366A08F1D62F34FD4E05428543D85022764173BD16`. Signing certificate fingerprint `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`, matching prior QA2 signer.
- Physical Infinix X6831 / Android 13 instrumentation: **OK (5 tests)** — exact installed Git SHA identity (1) plus synthetic Sarvam response structure/fail-closed cases (4). Log `%TEMP%\\nexa-readiness-physical-af5289a.log`.
- The initial 498/499 Node run on the previous test revision was caused by one outdated assertion expecting the old behavior; the replacement is now tested in the **499/499 PASS** run above.

## Remaining release acceptance

The tests used synthetic HTTP response objects. They did **not** authenticate to Sarvam, call a paid/remote conversation endpoint, capture microphone audio, enable wake listening, send a model-proposed phone tool, access a real WhatsApp chat or validate the actual OEM overlay.

Real owner-mediated Sarvam API credentials, WebSocket audio and barge-in, on-device wake-to-live auto-start, Android Accessibility permission, exact recipient/composer verification and consumer signing/rollback **remain unverified**. The primary app, `main` branch, provider keys, Android permission settings and production services were unchanged.
