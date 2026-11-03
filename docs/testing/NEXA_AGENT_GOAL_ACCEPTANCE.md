# Nexa user-goal acceptance matrix

Status: **IN DEVELOPMENT**. The earlier eight Maestro Cloud checks are **UI smoke tests**, not evidence of an autonomous voice research assistant. No goal below is accepted without device observations and task-level outcomes.

## Requested owner tasks

| Goal | Required evidence | Source implementation | Automation verdict |
| --- | --- | --- | --- |
| Say "Hey Nexa" while hands-free | Android's real microphone recognizes the phrase, brings Nexa up, transfers audio cleanly, and responds with speech | Offline wake service + Vosk, active indicator and Stop notification | **Not certified by Maestro Cloud**; native microphone-audio injection not exposed by current cloud setup. |
| "Open Chrome and search what is AI" | The owner types/speaks the exact request to **Nexa**; a Nexa native tool is approved; Chrome visibly displays Google search results for that query | New `device.browser_search`, native Chrome intent, approval journal, voice native tool | Requires new signed APK + Maestro `09-agent-chrome-ai-search.yaml` **PASS** |
| "Check the best articles and open it" | Nexa reads new results, compares relevance/credibility with a configured AI model, chooses an accessible actual result, requests approval, opens it, checks changed page | **Not integrated as an unattended multi-turn browser-research planner.** Screen reading and safe label taps are one-action tools | **NO-GO** until provider-backed ranking and evidence-verified click test, never pretend a predefined Maestro `tapOn` is Nexa's choice |
| Scroll up/down/left/right | One explicit owner direction reaches Nexa's Accessibility action; verified foreground content/page changes rather than only dispatch | `NexaScreenScroll`, launcher-only gesture fallback | Needs separate Chrome and launcher E2E evidence; ACTION_SCROLL acceptance alone is not task success |
| Tap a named menu/control | Requested label resolves uniquely in current external app; one approved click; resulting screen observed | `NexaScreenLabelTap`; normalized exact names `MyMotor` ↔ `my motor`, approved overlay input | Needs real external-app E2E evidence |
| Tap a call button | Distinguish read-only **locate** from actually starting a call, identify recipient, require explicit confirmation | Current generic screen tap **blocks** Call/Send/Pay etc | Correct security behavior. Do **not** silently tap Call to force a passing test |
| Open notification bar | Nexa receives explicit owner request, gets one-time approval, uses Android Accessibility GLOBAL_ACTION_NOTIFICATIONS, verifies shade opened and **does not read contents** | `NexaBrowserTaskActions.notificationsApproved` and voice/overlay routes | Needs emulator E2E evidence |
| Go back | Nexa requests one safe Android global Back action after approval and verifies resulting screen | `NexaBrowserTaskActions.goBackApproved` | Needs emulator E2E evidence |

## Truthful testing rules

1. **Nexa must be the actor**. Maestro may enter an owner request, approve a visible proposed action or grant OS permissions, but must NOT run `openLink`, `tapOn`, `swipe` or `inputText` in Chrome to simulate Nexa's execution of the actual goal. Chrome prewarming for first-run consent is an environment setup only and must be identified as such.
2. A real provider is needed for open-ended reasoning. A static top-result selector, scripted browser click or model hallucination is **not** evidence of autonomous article evaluation.
3. A green native wake-state UI is **not** a microphone recognition test. Cloud emulator does not demonstrate Android OEM background/overlay behavior, especially on Infinix/XOS.
4. Every side-effect runs through Android native one-time approval and audit; no auto-send, auto-call, raw XY tap or privilege bypass.
5. A pending or blocked goal cannot be marked complete because a tool returned `android_accepted`.

## Selective run

Manual-only workflow: **Nexa Maestro Cloud UI acceptance (manual only)**, suite `agent-browser`. It uploads the signed release APK corresponding to the same app-runtime revision and records the actual Chrome screen. The original `all` suite also includes the additional browser test after release.

Other objective flows belong in a separate suite until they can be verified on Android 13 and then on the user's physical Infinix/XOS device.
