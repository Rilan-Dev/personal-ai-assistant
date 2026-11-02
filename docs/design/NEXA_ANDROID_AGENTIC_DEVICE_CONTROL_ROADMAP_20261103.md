# Nexa Android Agentic Device Control — implementation plan and acceptance gates

**Date:** 2026-11-03
**Branch:** `Development`
**Goal:** The owner can say "Hey Nexa", continue a live Sarvam Model API conversation in the translucent overlay, request a useful phone action, review dangerous actions on the *same overlay*, and hear the **actual native tool result** in the conversation.

## 0. Current provider and route (source-verified)

- `Voice → Start live realtime conversation` selects the encrypted **AI Configuration** provider. `sarvam` selects `sarvam-api` on Android: Saaras v3 Realtime STT WebSocket, Sarvam-105B Conversations API, Bulbul streaming TTS WebSocket. `openai` selects the native OpenAI Realtime WebSocket.
- Voice Agents `sk_samvaad_` credentials, organization/workspace and Agent App IDs are not used by this active path.
- Android `VoiceInteractionSession.onShow` previously required `auto_voice_invocation_v1` even for an explicitly armed Hey Nexa hotword. The screenshot shows the precise fallback text from that branch. Fixed: **hotword + armed wake + valid voice credentials/consent** starts live voice; non-hotword assistant gestures retain the extra toggle.
- The system overlay had Approve/Deny controls *below* the main buttons. Fixed: visible approval banner and **Approve once / Deny** before normal shortcuts. No automatic approval.

## 1. Execution architecture

```text
Hey Nexa (owner-armed offline Vosk wake detector)
  → Android VoiceInteractionSession OR protected floating fallback
  → RealtimeVoiceService (foreground microphone + Stop)
  → SarvamApiVoiceTransport
      ├─ Saaras v3 Realtime (continuous PCM16 WebSocket + VAD/barge-in)
      ├─ Sarvam-105B Conversations (bounded in-memory history, tool_calls)
      └─ Bulbul streaming TTS (continuous playback WebSocket)
            ↓
       Native Agent Tool Router / Permission Gate
            ├─ read-only Android status (immediate, verified result)
            ├─ owner-requested exact app match (requires overlay approval)
            ├─ future screen observation (explicit scoped consent)
            └─ future deterministic UI navigation (one approved action at a time)
            ↓
       tool_call → Android result → tool message → model reply → spoken answer
```

**Important**: this is a composed STT → LLM → TTS duplex experience, **not** a single native STS endpoint. The speech conversation stays active; the user does not need to operate separate recognition and speech synthesis screens. The model must never acquire permissions itself.

## 2. Phase inventory

| Phase | Capability | Status | Native boundary | Acceptance |
|---|---|---|---|---|
| P0 | Wake → auto-start live; same-overlay approval | Implemented in Development, native CI pending | Opt-in offline detector + microphone FGS; 60-second approval expiry | Hotword auto-start and approve/deny without opening app |
| P1 | Battery, storage, limited device status | Implemented in Development, native CI pending | Android BatteryManager / StatFs, permission status | Actual percentage/GB returned and spoken |
| P1 | Exact installed app discovery and one-tap open | Implemented in Development, native CI pending | Android MAIN/LAUNCHER package visibility, local label match | Open non-hardcoded app, deny/ambiguous does nothing |
| P1 | Sarvam tool_call → read-only native execute → tool result → model response | Implemented in Development, native CI pending | Strict nested function schemas, one tool call per turn | Correct function call, checked result, subsequent reply |
| P2 | Device info: connection, screen, memory, battery health, storage | Planned | Bounded Android system services (no privileged APIs) | Clearly mark unsupported / permission denied values |
| P3 | Screen observation (Accessibility node tree) | Planned, opt-in | Existing ScreenAssistService; owner-initiated one-time snapshot, redact sensitive fields | Visible-only nodes, no background scanning or uploading without consent |
| P4 | Targeted click, scroll, long press, type (foreground app) | Planned, **policy-sensitive** | Node-bound targets where possible, approved one-action execution | Observed node still matches, one action, observed result, Stop |
| P5 | Home-screen folder discovery and visual icon recognition | Planned | Read accessible labels + on-device image recognition where explicitly authorized | Never guess coordinates; ask owner on ambiguous/hidden icons |
| P6 | Telephone calls, contacts, WhatsApp compose | Planned | ACTION_DIAL default; optional scoped contacts, reviewed message/call confirmation | No actual call/message without explicit intent and review |
| P7 | Plan → observe → validate → execute → verify multi-step tasks | Planned, not automatically enabled | Owner-visible action-by-action approvals / deterministic scripts | No unbounded model autonomy or permission bypass |
| P8 | Physical device compatibility, release QA | Pending | Signed APK + instrumented UI tests + real OEM phone | No crash, wake recovery, encrypted preferences, approvals |

## 3. Tool contracts and permission design

Tool identifiers and proposed permission classes:

- `device.get_battery`, `device.get_storage`, `device.get_status`: safe read-only facts. Immediate response may feed a Sarvam `tool` message after native validation. These are the **only model-callable tools today**.
- `app.find(name)`: package-manager query for the *exact spoken app label*, local results only. Do not request `QUERY_ALL_PACKAGES`. Ask the owner if there are zero/multiple results. No background upload of the installed-app inventory.
- `app.open(target)`: an exact owner request is required; present package label and context on the visible overlay. A tap on **Approve once** dispatches an Android launch Intent. Report `launch requested` rather than claiming the destination screen is visible without verification.
- `screen.observe(scope)`: optional AccessibilityService root window snapshot with a visible disclosure, one-time session token, redaction and limited lifetime. Never scan passwords/OTP, secure windows, bank/authentication surfaces, or the entire UI continually. Android can return `null` for inaccessible/secure content.
- `screen.click(target_node_id)`, `screen.scroll(direction,target_scroll_container)`, `screen.type(target_editable_node,text)`: future single-action executor. Derive target IDs and bounds from a **fresh**, authorized foreground snapshot; verify package/window identity and node content at execution time. Prefer `AccessibilityNodeInfo.performAction(ACTION_CLICK/ACTION_SCROLL_*/ACTION_SET_TEXT)` for exposed nodes. Use `dispatchGesture` only for documented, user-authorized deterministic operations; never blindly tap coordinates generated by the LLM.
- `phone.prepare_call(number_or_contact)`: prefer `ACTION_DIAL`, which leaves the final Call button to the owner. Direct `ACTION_CALL` requires `CALL_PHONE` and an explicit confirmation. Contact resolution requires runtime `READ_CONTACTS`, queried narrowly and locally. No call recording.
- `communication.prepare_message(recipient,text,app)`: preview exact destination and text; obtain an additional explicit send confirmation, then verify result. No silent WhatsApp sends or accessibility bypass of sender confirmation.

### Policy and distribution gate

Google Play's Accessibility API policy prohibits **general-purpose autonomous agents** that initiate, plan and execute screen actions through AccessibilityService. A general voice assistant must not declare itself a disability accessibility tool merely to evade this rule. Nexa must implement **deterministic, owner-directed, narrow operations** with prominent disclosure and consent. For private sideloaded builds, Android's own user-enabled Accessibility and screen-sharing rules still apply; sideloading is not a permission bypass.

Android 14+ MediaProjection requires consent for each capture session; never cache reusable screen-capture grants. Accessibility window content requires an explicitly enabled service with `canRetrieveWindowContent`, and some application UIs intentionally do not expose nodes.

## 4. Agent harness workflow and safety

1. **Listen:** Only after user enables Hey Nexa wake or taps Voice; local wake word stops detecting while provider owns microphone.
2. **Interpret:** Sarvam may reason about owner speech. A function proposal is *data*, not permission; validate schema, current session and original owner request.
3. **Permission gate:** Read-only tools immediately execute. App opening requires a fresh, visible approval; screen access and phone communication require separately scoped permissions and safety review. No auto-grant of Android permissions.
4. **Execute:** One action at a time, timeout and duplicate suppression; never retry send/call actions blindly.
5. **Observe:** Confirm actual Android state if exposed and report truthful status. Return a bounded result to the model as a tool message or direct in-session narrated status.
6. **Continue:** Keep the realtime voice session and bounded RAM-only history alive through tools and interruptions; Stop cancels queued work and microphone streaming.
7. **Privacy:** No NexaHub records, contacts, files, screenshots or installed-app inventory are sent to Sarvam without explicit owner scope. API calls to Sarvam necessarily send speech/audio, limited conversation text and tool summaries; no cloud zero-retention guarantee.

## 5. Native permission & release acceptance matrix

| Permission / capability | When requested | Required test |
|---|---|---|
| RECORD_AUDIO, microphone foreground service, POST_NOTIFICATIONS | Explicit Setup and visible wake/voice opt-in | Live mic indicator, Stop, denial and rearm |
| SYSTEM_ALERT_WINDOW | Owner enables floating fallback in Android settings | Overlay only when enabled; approval visible and tappable |
| Android assistant role | Owner selects Nexa under Default Assistant | Hey Nexa system overlay works or explicit fallback |
| AccessibilityService (ScreenAssist) | Owner enables in Settings; one-shot screen request | Node snapshot redaction, focus/package revalidation, no unattended UI agent |
| MediaProjection | Owner authorizes each specific capture | Consent each capture, revoke handling, secure content excluded |
| CALL_PHONE / READ_CONTACTS (future) | Only when requesting specific calling feature | Denial, wrong contact ambiguity, visible call confirmation |
| Launcher package query | Local, when owner names an app | No broad package permission or inventory upload |

## 6. Tests, release gates and incomplete work

Current source gates: `npm test`, `npm run typecheck`, `npm run web:export`.
Native gates: GitHub Actions debug Kotlin compilation, new instrumentation for native facts + tool schemas, restricted app lookup, foreground service/start/Stop, overlay approval and signed release build.

**Still unproven:** auto-start and overlay approval on the owner's exact OEM phone, a genuine 3-turn Sarvam call after wake, screenshot/icon understanding, arbitrary swipe/type/click actions, outgoing calls, and full autonomous multi-tool execution. CI success does not imply these capabilities work on the phone.

**Do not implement unbounded screen control or call/message sending merely because the model requests it.** Each new capability must pass its Android permission, owner consent, Play distribution, security and device acceptance gates.

## References

- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android AccessibilityNodeInfo: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- Android media projection consent: https://developer.android.com/media/grow/media-projection
- Android common phone call intents: https://developer.android.com/guide/components/intents-common
- Google Play Accessibility policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Sarvam function calling: https://docs.sarvam.ai/api/token-factory/chat-completions
