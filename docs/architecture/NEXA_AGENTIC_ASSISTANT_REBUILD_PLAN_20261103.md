# Nexa AI — agentic personal-assistant rebuild
Date: 2026-11-03
Branch: feature/nexa-agentic-loop-20261103
Target: personal Android assistant (React Native UI, Kotlin native capabilities, optional LangGraph)

## Evidence and priority

The exported custom.db conversation records showed:
- Input fragments ("open the uh" then "Halite motion") led to mistaken literal app lookup.
- A two-part request was not kept as one durable task.
- XOSLauncher supplied readable Accessibility labels but no supported horizontal scroll node.
- "Android accepted scroll" was mistaken for useful task completion; no verified page change.
- A WhatsApp search control seen in one voice session was not available to subsequent reasoning; sessions restarted.
- Tap-related crashing requires AndroidRuntime crash/ANR logs to determine the actual exception; export alone does not prove its cause.

Do not equate source tests, an Android API returning true, or a model assertion with task success.

## Reference architecture

1. **Capture and utterance assembly:** Native offline Hey Nexa wake only; select one authorized live provider. Preserve partial-final STT fragments, resolve filler safely, attach speech turns to one task. Never let tool output itself authorize a new action.
2. **Intent and tool availability:** Map request to intent schema. Gather only permitted device app catalogue, OS permissions, current foreground package, and safe UI observations. Distinguish user intent from model suggestions.
3. **Task planning:** Planner emits bounded typed steps (max 8 for initial rollout) with expected evidence, dependencies, risk classification and fallback. Split compound requests but never split message bodies. For dynamic requests a LangGraph server may *propose* a plan, then the device validates and, where required, the owner reviews before action. The existing @paa/agent-core AgentHarness supports bounded tool loops, a durable action journal, approval and resume; it must be bridged into foreground voice task execution rather than remaining Chat-only.
4. **Permissions:** Evaluate Android runtime grant, user-visible Accessibility enablement, content scope, owner preference and current active task separately. Unsupported permissions are not granted by speech, model, previous consent or a global switch.
5. **Execution:** One currently authenticated step dispatched on device using a narrow registered tool. Prefer Android contracts/Intents and semantic AccessibilityNodeInfo actions, never unverified model-provided X/Y coordinates. Serialize calls, enforce timeouts and cancel on user Stop, session switch, screen change or foreground mismatch.
6. **Observe and verify:** Re-fetch the correct window, compare source window/package, structural metadata and bounded visible labels; read OS outcome where available. Treat uncertain execution as uncertain (no blind retry). Feed verified observation back to planner for next step or minimally necessary clarification.
7. **Memory:** Store conversation turns, task steps, approval choices and safe learned preferences in the Android-private encrypted custom.db. Remember preferred messaging channel for a contact only after the contact identity, channel and delivery outcome have been verified. Save no API keys or raw contacts in unencrypted logs. Future Prisma/PostgreSQL sync is separate, opt-in and authenticated.

**Loop contract:** OBSERVE → PLAN → CHECK_PERMISSION → (REVIEW/PAUSE) → EXECUTE → VERIFY → UPDATE_STATE → NEXT. Errors route to safe clarification or stop; no guessed success.

### Explicit task state

Task: taskId, sessionId, userGoal, createdAt, provider, status, memoryScope, approvedPlanHash, expiry.
Step: stepId, toolId, typedArguments, preconditions, expectedSource, risk, approvalBinding, idempotencyKey, status, attemptCount, evidenceIds.
Status: planned, awaiting_permissions, awaiting_approval, executing, verifying, verified, failed, uncertain, cancelled.
Observation: source package/window, timestamp, bounded content summary, snapshot ID, integrity hash and declared sensitivity.
The executor never moves to the dependent step until its precondition is verified; an Android launch Intent accepted is not enough.

### Permission model

- Ask every time: default for every tool.
- Reuse approved safe actions: per-category Always allow/Allow once/Deny; grants can be revoked in the Voice UI.
- Trusted navigation: for ordinary launcher opening and bounded safe navigation, reduce prompts. It is NOT unrestricted access or permission to send/call/pay/delete. Sensitive read categories require an individual grant; Android runtime permissions always apply.
- High-impact communications, contact attribution, third-party message delivery, payments and OS permissions must have their own approved integrations and consent gates. "Always allow" cannot turn an unknown LLM-generated target or unverified recipient into an action.

The persistent permission policy in this milestone applies only to low-risk approved voice actions and can be reset. It is not an Android role/permission and does not elevate the app.

## Use cases

### "Hey Nexa, open WhatsApp and read my screen"

Task: open WhatsApp → verify com.whatsapp becomes the external foreground app → read bounded non-editable labels with the appropriate authorization → report observations. One user instruction; each dependent step uses fresh window evidence. Current initial deterministic task cursor implements this pattern, but should evolve into a durable task record and be verified on hardware.

### "Hey Nexa, message Mom: I'm on the way"

Target task state:
1. Resolve contact alias through an explicitly authorized Android contact picker or prior verified binding; don't guess "Mom" from names in screen text.
2. Enumerate available messaging routes (SMS, WhatsApp, other launchable apps) as permitted; prefer a previously verified channel mapping if user consented to remember it.
3. Ask for channel only if unresolved/ambiguous; preserve message text across that clarification.
4. Open authorized messaging composer and prepare exact recipient + content. Use a supported integration; a WhatsApp ACTION_VIEW draft is not a send API.
5. Require explicit final user authorization where integration or policy requires. Verify provider/OS delivery evidence where accessible; do not claim "sent" from opening a composer or UI tap.
6. Record successful verified channel/contact routing preference and an audit event in encrypted custom.db. Never store an unverified sent state as a preference.

Current milestone does NOT implement unattended WhatsApp delivery or automatic recipient selection. Android's personal WhatsApp app does not expose a general guaranteed consumer send API to arbitrary third-party apps.

### "Hey Nexa, call Dad"

Resolve exactly one permitted contact, choose the correct number/SIM if required, and use ACTION_DIAL for a reviewed call or ACTION_CALL only with Android CALL_PHONE permission and proper contact confirmation. Report dialing and connected states separately. Do not use a blind tap on a green icon as proof of a placed call. Emergency calls remain under Android dialer control.

### "Hey Nexa, answer the incoming call"

Use a dedicated telecom path. Validate that a call is currently ringing and a compatible role/permission exists. Android TelecomManager.acceptRingingCall() requires ANSWER_PHONE_CALLS but is deprecated since API 29; a complete default-dialer InCallService is the modern role-based route, and requires substantial UI and lifecycle implementation. NotificationListenerService requires explicit user access and does not automatically provide telephony control. Do not pull down notifications and click a guessed call icon without verifying a ringing call and permitted method. A feature gate and physical-device testing are mandatory.

### Driving

A driving profile should provide short speech feedback, reduce visual interaction and distractions, respect car/phone audio focus, never attempt to bypass a lock screen, and avoid workflows requiring typing while driving. Always expose voice Stop. Handle phone-call audio overlap explicitly.

## Engineering phases and acceptance

| Phase | Deliverables | Gate |
|---|---|---|
| A — stability and trace | Tap/accessibility crash logging, background non-blocking device actions, action evidence improvements, STT filler recovery | Native Android compile + real Infinix crash/ANR log replay |
| B — first multi-step loop | Two-four explicit navigation commands in one spoken request, verified app switching, safe XOS horizontal launcher swipe | Open WhatsApp → read WhatsApp as ONE task on physical phone |
| C — permissions UX | Owner-set Ask every time, remembered safe category approvals, trusted navigation, Allow once/Always allow safe/Deny, revoke | UI tests, permissions survive restart, high-risk actions still blocked |
| D — general planner | Shared model-planner/tool-registry contract; bounded task graph and Android native bridge; observation reuse and interrupts; persistent task checkpoint | Repeated sessions + replay prevention, adversarial model-output tests |
| E — communication tools | Contacts consent and exact recipient resolution; channel selection; WhatsApp reviewed composer + SMS; separate official call/answer APIs and integrations | No false send/call claims; no wrong recipient; phone role/UAT |
| F — memory and cloud optional | Verified contact-channel preferences, native history export, optional authenticated Prisma/Postgres sync | Encryption, consent, data deletion, independent restore and audit |
| G — release acceptance | Build native APK, emulator tests and physical owner UAT (Android 13 XOS, WhatsApp, SIM calls) | No release promotion without verifiable evidence |

### Android / distribution constraints

Current Google Play Accessibility policy explicitly disallows autonomous planning-and-execution workflows driven by the Accessibility API for ordinary general-purpose apps; some verified accessibility tools have a separate eligibility path. Only use the API in permitted, disclosed, user-approved ways and prefer official platform APIs. Android's Accessibility gestures require canPerformGestures=true and owner enablement. An "accept all risk, never ask anything" mode cannot bypass these obligations. This task's partial code introduces no automatic external message sending or phone answering.

## Status for this branch

Implemented in source: initial deterministic compound task cursor and verified app-transition continuation, user permission modes and always-safe grant/revocation UI, serial off-main-thread approved screen actions, speech-fragment hold and bounded app-name correction, XOS/default-launcher horizontal gesture fallback and stronger page-change evidence, plus source/native instrumentation tests.

Not yet established: native Android compile, physical-device crash elimination, fully model-driven multi-step tool loop in live voice, outbound messages/calls, incoming-call answering, and durable contact-channel preference learning.

Reference: LangGraph state/checkpoint/interrupt and Android AccessibilityService/Telecom/InCallService official documentation. No device or provider outcomes should be invented.
