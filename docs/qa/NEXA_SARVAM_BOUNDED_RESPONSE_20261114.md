# Sarvam HTTP response-size safety — November 14, 2026

## Issue

`SarvamApiVoiceTransport.completeModelTurn` previously read an **unbounded** `ResponseBody.string()` from provider HTTP responses, then checked that the decoded result was under 140,000 characters. Error diagnostics in both live transport and `SarvamModelApiReadiness` similarly performed `body.string().take(5000)`. These post-allocation limits did not bound memory on an Android device if a provider or intermediate proxy returned oversized/chunked/decompressed content.

## Fix

New `NexaSarvamResponseBounds.textOrNull(response,limit)` reads **at most limit + 1 bytes** through OkHttp `Response.peekBody` and rejects oversized responses before constructing the bounded String. It handles missing/malformed responses as unavailable, preserves `Response.use` cleanup, and never transmits a device action. Both the owner-tapped key readiness diagnostics and live chat/error code paths use the same bounded reader.

- Live successful model response maximum: **140,000 bytes**.
- Provider-error diagnostic response maximum: **5,000 bytes**.
- Oversized, invalid or empty model response produces the existing safe spoken error, not an unbounded allocation.
- The Saaras STT and Bulbul TTS WebSocket protocols, tool approval, microphone ownership and wake-word logic were **not changed**.

## Verified development and Android physical evidence

| Gate | Result |
|---|---|
| Exact feature source SHA | `becac6295fb42d835f287d04f5475d9549929473` |
| Full Node source/security regressions | **492/492 PASS**, log `%TEMP%\\nexa-sarvam-body-node-becac62.log` |
| TypeScript | **PASS**, `%TEMP%\\nexa-sarvam-body-typecheck-becac62.log` |
| Android arm64 app + AndroidTest + Maestro portable JUnit build | **BUILD SUCCESSFUL**, `%TEMP%\\nexa-sarvam-body-android-becac62.log` |
| Isolated physical QA2 APK package | `dev.rilandev.personalaiassistant.physicalqa2` versionCode `2026111404` |
| Debug QA2 APK SHA-256 | `9990FD94613D3865AC87CAFA0F3989AB4D58B3E8D7EA1D034FD5B672806D072C` |
| QA2 AndroidTest APK SHA-256 | `CDE08AD323225097E802346F987E090274784136C886CA1D138298E27923AAB9` |
| QA2 test signer SHA-256 | `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`, same as previously installed isolated QA2 cert |
| Physical Infinix X6831 Android 13, exact Git SHA identity test | **OK (1)** |
| Physical Infinix X6831 Android 13, bounded synthetic HTTP response tests | **OK (4)**: small success, exact limit, limit+1 rejection, invalid limit |
| Live Sarvam API/STT/TTS, audio, network quota, key, barge-in | **NOT TESTED**, never represented by synthetic HTTP tests |

The QA2 app and test APKs were updated using **in-place `adb install -r -t` only** after signing identity and version-order preflight. No production app, messages, contacts, microphone permission, app-op, background wake activation, `main`, real provider key, or production services were touched.

## Remaining acceptance

An actual owner-initiated readiness check with configured Sarvam Model API key, a voice session on the real device, wake-to-live-voice auto-start, interruption/Stop behavior, and overlay approvals remain separate release gates. A synthetic response-bounds PASS cannot certify provider availability.
