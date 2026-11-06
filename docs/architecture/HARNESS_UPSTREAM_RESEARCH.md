# Agentic harness source review — October 31, 2026

## 2026-11-06 — Dynamic Android task ingress source check
- Rechecked LangGraph.js HEAD `4030632d3d58a22601fdd1fa3be35b805a76f6a1`: `libs/langgraph-core/src/graph/state.ts` still exports `StateGraph` with node/edge construction and `.compile()`; MIT license at repository `LICENSE`. The app already uses the installed LangGraph package only in the Node planner. This milestone reuses that existing planning boundary and does not run a second graph in Android.
- Rechecked Deep Agents JS HEAD `e80b3af2c842515cf3af2bb59a1f2be8a46d266c`: `libs/deepagents/src/agent.ts` builds `createDeepAgent` with filesystem, task, subagent and summarization middleware and optional human interrupts; MIT license at repository `LICENSE`. Its generic filesystem and tool loop are not adopted for Android screen actions.
- Compared alternatives for this device task: OpenAI Agents SDK would add a second tool runner; Pydantic AI and smolagents would require a separate Python process; OpenHands focuses on development environments. None supplies Android's current-window identity, Accessibility node grounding or one-shot native action claims. Keep the existing Node planner plus Kotlin executor boundary.
- For the first Chat milestone, reuse Android's existing launcher-label lookup in `NexaPhoneVoiceTools.findLaunchableApp`, but revalidate the exact resolved package in the native Chat bridge. Do not infer an opened screen from an Intent dispatch or introduce cloud upload of the installed-app list.

## Reviewed upstream code (pinned reading snapshots)
- LangGraph.js: https://github.com/langchain-ai/langgraphjs/tree/959f041dfb8f6bbafa6af3ebb4bc20484a9290da
  - StateGraph, Annotation, START/END, and compile/invoke patterns in upstream
    quickstart docs and graph source.
  - LangGraph package imported via npm, NOT copied/vendored.
  - Upstream MIT license applies; package lock records exact installed version.
- Deep Agents JS: https://github.com/langchain-ai/deepagentsjs/tree/26f190d40f44a2bb498db73e9e98b002bb8554fe
  - `libs/deepagents/src/agent.ts` creates the agent with built-in planning,
    filesystem middleware, skills, subagent and interrupt behavior.
  - `libs/deepagents/src/permissions/enforce.ts` implements filesystem path
    permission matching, whose unmatched rule default is permissive.
  - We do NOT enable Deep Agents' generic filesystem/shell tools on a phone.
    Future adoption must wrap every plugin in an explicit deny-by-default
    capability policy, and never treat model instructions as authorization.
- Other candidates (research, not dependencies yet): OpenAI Agents SDK,
  Pydantic AI, OpenHands SDK, smolagents. Evaluate under the same benchmark
  before adopting; do not simultaneously nest several autonomous loops.

## Reused, concretely
The Node-only `services/agent-runtime` imports actual `@langchain/langgraph`
`Annotation`, `StateGraph`, `START`, `END` and calls the compiled graph.
`provider.ts` reuses the `@langchain/openai` LangChain model adapter for
schema-constrained output, instead of reimplementing provider protocol.
The model graph performs one planning/validation step. A separate
request-to-action intent gate prevents a model from converting unsupported
requests into unrelated device actions. It executes NO native
device action. Phone-side `AgentHarness` owns the bounded observe/act/verify
loop and independently checks tool arguments, in-app grants and approval.

## Why a split runtime
React Native/Hermes mobile must not bundle Node-only graph execution or
cloud provider secrets. The graph runs in a trusted backend process; the
mobile client sends opt-in requests to an owner-specified HTTPS API, receives
a single typed plan, and executes its locally allowlisted tools. Cloud model
inputs contain the user's prompt and redacted outcomes, not raw screens or
contacts. A planning-only server cannot make phone UI calls directly.

## Current limitations / open acceptance gates
- No LangGraph persistence or durable checkpointing. Graph steps are stateless;
  no sensitive phone observations are intentionally stored on the server.
  Durable encrypted on-device session storage belongs to the next milestone.
- No real provider call has been verified: tests inject a deterministic model
  to prove the actual LangGraph graph and HTTP transport loop without secrets.
- OpenAI-compatible providers must support structured output; adapt model
  providers as separate plugins and perform provider compatibility testing.
- Device-side UI intent dispatch is not screen verification.
- Android real-device voice, wake-word, accessibility, WhatsApp messaging,
  iOS/desktop system automation are out of scope for this graph milestone.
- Android signing secrets and local app API tokens must never enter Git.
- Remote planner is OFF by default; credentials are memory-only in the app.
- Third-party MCP tools remain unavailable until plugin sandbox and scopes
  are implemented and security tested.

## Upstream source reuse rule for future work
For every future harness dependency or integration, record upstream repo,
commit/tag, relevant source module, package license, purpose, capability
boundary, tests, and rollback plan. Favor maintained packages over
copy-pasted mutable framework internals. Always test tool hallucinations,
prompt injection in tool results, output validation, duplicate side-effects,
denied permissions, cancellations and incomplete observations.

## 2026-10-31 — Checkpoint replay and durable local action claims
- Reviewed upstream LangGraph checkpoint `base.ts`, `memory.ts`, and
  functional API idempotency guidance. A side effect that starts but has not
  been checkpointed can execute again on resume unless independently guarded.
- Reused existing LangGraph planning SDK, but implemented Android-specific
  encrypted reservation because a server graph checkpoint cannot atomically
  reserve a private device action.
- `packages/agent-core/src/journal.ts` defines the interface and a test-only
  in-memory implementation. `AgentJournalModule.kt` provides Android
  Keystore AES-GCM and synchronous commit of one-shot action reservations.
- Cross-platform support does not grant external side effects to the Web/iOS
  demo. Android native execution requires its secure journal.
- No deepagents filesystem/shell autonomy adopted. Phone policies remain
  explicit allowlist, fail-closed.
- Details: docs/architecture/DURABLE_ACTION_JOURNAL.md.

## 2026-10-31 — Android voice adapter source boundary
- Reviewed official SpeechRecognizer, RecognizerIntent, TextToSpeech and Voice
  APIs and prior LangGraph/DeepAgents reference source.
- Reused existing LangGraph planning and local approval/tool loop. STT fills
  editable composer only; new audio never bypasses device capability policy.
- No new autonomous agent harness, generic shell tool, or MCP microphone
  access introduced. Full design in docs/architecture/ANDROID_VOICE_PHASE.md.

## 2026-10-31 — Quick Settings invocation (no new agent loop)
- Reviewed Android TileService / StatusBarManager APIs, Android 14
  PendingIntent requirement, Android 13 user-requested tile API and
  lock-screen unlockAndRun. See docs/architecture/ASSISTANT_INVOCATION.md.
- Reused the existing MainActivity and React Native chat interface.
  No autonomous agent runtime, microphone permission escalation, filesystem
  shell tool, or model-controlled invocation was introduced.
- Default Android assistant VoiceInteractionService is still a separate
  OEM-/OS-dependent integration and has not been implemented.

## 2026-10-31 — Battery tool grounding and narrow action surface
- Reused LangGraph.js source at 959f041dfb8f6bbafa6af3ebb4bc20484a9290da
  and compared Deep Agents.js permissions at
  26f190d40f44a2bb498db73e9e98b002bb8554fe.
- Added one pure-read device.battery_status tool, not a generic Android shell
  or unrestricted device status parser. Empty args, exact owner intent,
  bounded percent, no permissions, minimal redacted observations.
- Completion is generated deterministically from validated Android readings.
  The LLM cannot substitute a fabricated battery value.
- See docs/architecture/ANDROID_BATTERY_TOOL.md.

## 2026-10-31 — Android Camera2 physical-world tool
- Reviewed Android CameraManager.setTorchMode / TorchCallback API contracts,
  especially unreliable availability, external app preemption, state events,
  and mandatory listener teardown.
- Reused LangGraph.js 959f041dfb8f6bbafa6af3ebb4bc20484a9290da's
  policy-controlled graph boundary and the existing device-side durable
  AgentHarness/Journal; compared Deep Agents.js permissions at
  26f190d40f44a2bb498db73e9e98b002bb8554fe.
- Added a narrowly typed device.set_flashlight command, no new agent loop.
  Required Android CAMERA permission + in-app hardware grant + one-shot
  approval + verified Camera2 callback before claiming success.
- Details / limitation: docs/architecture/ANDROID_FLASHLIGHT_TOOL.md.

## 2026-11-01 — Mobile UX and Android background-assist layer
- Preserved the already reviewed pinned LangGraph.js state-machine planning
  architecture from 959f041dfb8f6bbafa6af3ebb4bc20484a9290da;
  existing policy, permission checks, exact approval fingerprints and device
  journal remain the only path for any native tool execution.
- Rechecked Deep Agents.js's permission design at
  26f190d40f44a2bb498db73e9e98b002bb8554fe. No permissive
  filesystem shell or default allowlist was imported into Android.
- Added only platform integration adapters, not a competing autonomous
  planner: owner-started microphone Foreground Service and user-armed
  one-time Accessibility screen text capture. Live voice never invokes
  arbitrary Android device tools. Screen text is treated as untrusted and
  must be explicitly reviewed in the editable chat composer before Send.
- Official Android microphone FGS/Accessibility and OpenAI realtime API
  contracts: docs/architecture/MOBILE_UX_BACKGROUND_ASSIST.md.

## 2026-11-01 — Sarvam managed Voice Agents protocol and direct provider
Upstream implementation reviewed: npm sarvam-conv-ai-sdk@0.0.42,
MIT license, downloaded using npm pack and read actual
dist/modules/socket-manager.js, voice-agent.js, utils/message.js,
types/types.js and interfaces/react-native.js. Official reference:
https://docs.sarvam.ai/voice-agents/deploy/sdks/react-native

The native Android implementation reuses verified signed-URL,
PCM16, client.media.audio_chunk, server.media.audio_chunk,
interaction_connected and interruption message contracts.
The SDK's RN audio interface is JavaScript-mediated and has a
react-native-audio-api native peer; a native Android foreground
service was chosen instead so the user-started microphone is
managed by Android independently of the React Native screen.
The app does not install the upstream SDK or assume its
transcript/telemetry callback is privacy-safe: inspected upstream
sources show raw message logging on some parse errors. The native
integration intentionally excludes transcript and text handlers.

Other official model documents:
https://docs.sarvam.ai/api/token-factory/chat-completions
https://docs.sarvam.ai/api-reference/speech-to-text/transcribe
https://docs.sarvam.ai/api-reference/text-to-speech/convert

Chat uses Sarvam-105B JSON-mode completions through the existing
exact-intent Android AgentHarness and approval journal. Speech
dictation/TTS are separate explicitly user-invoked utilities,
not a new autonomous planner. The full-duplex Voice Agent does
not receive Android app or private-device tool capabilities.

## 2026-11-02 — natural-language tool policy and Sarvam live speech bridge

- Verified current LangGraph.js upstream HEAD `959f041dfb8f6bbafa6af3ebb4bc20484a9290da`, MIT. The repository already imports `@langchain/langgraph` StateGraph in `services/agent-runtime/src/graph.ts` (planning-only, no native phone execution). No extra graph runtime is added.
- Verified current Deep Agents JS upstream HEAD `296c10e2516c8b28fa795cd2cd39f37ab376dce2`, source `libs/deepagents/src/agent.ts` (agent construction/human-in-the-loop middleware), MIT. Do **not** install its filesystem/browser/shell tools onto a phone; those are not an Android permission boundary.
- Reviewed Sarvam `sarvam-conv-ai-sdk@0.0.42` (MIT), `dist/types/text.d.ts`, `dist/types/types.d.ts`, `dist/modules/socket-manager.js`. The SDK defines `server.event.transcription` with `role: "user"|"bot"` and `content`. No documented client `function_call_output` for managed CALL sessions was found; the `server.event.tool_call` event is not evidence that a mobile action may be executed. Reused only the officially typed transcript event.
- Alternative frameworks compared at the prior checkpoint (OpenAI Agents SDK, Pydantic AI, OpenHands and smolagents) remain unnecessary here. We have not nested another autonomous model loop.
- Chat now accepts polite single-step owner requests using shared `parseNativeOwnerIntent` to route the request through the existing mobile AgentHarness approval/Keystore journal; model-suggested tool IDs remain independently checked against original owner intent. Compound requests, mismatched arguments and unrecognized applications remain blocked.
- Sarvam realtime remains the owner-selected managed voice turn loop. Nexa listens for user-role transcripts and can **independently propose** a limited app-open action via `NexaVoiceIntentGate` and show Android approval. Bot transcripts, multi-action speech, unknown tools and unsupported phone actions never execute. This is a client-side guarded transcript sidecar, not a complete managed Sarvam model function-tool integration; Sarvam cannot be told the native result without a separately configured, authenticated agent API-tool/webhook bridge.
- Sarvam official documentation (reviewed November 2026): https://docs.sarvam.ai/voice-agents/build/concepts/harness and https://docs.sarvam.ai/voice-agents/build/tools/https-tool. Native mobile actions cannot be made directly from server-to-server Sarvam webhooks without an authenticated per-device command channel and owner-controlled approval.
- No new dependency or new cloud-held private device authorization introduced. The Android listener stores no transcripts, limits repeated requests and actions per session, requires an actual user approval tap to launch an app, and reports Intent dispatch as a request, not verified foreground navigation.


## 2026-11-05 — DSH Superagent task-admission baseline (source only)
- Inspected DeepSeek DSH prototype 8c32a9dc5d72c033816c6a8110b37f40cf94fba4: src/core/model.ts, tickets.ts, ports.ts, engine.ts, src/app/store.ts, effects.ts, outbox.ts, src/policy.ts. MIT licensed; no source copied.
- Inspected LangGraph.js a1d182f065d5cc8802ef6d5c2edd8ef55a9922b5 libs/checkpoint/src/base.ts; existing maintained LangGraph planning dependency remains authoritative on the server.
- Inspected Deep Agents JS 1b56802c3b6608d139f045ecf1486d53881b42b1 libs/deepagents/src/permissions/enforce.ts. Unmatched filesystem rule returns allow; NEVER import it as Android authorization.
- The existing Chat AgentHarness and Kotlin RealtimeVoiceService both own different task/approval paths. The first adaptation adds only a non-executable versioned task-plan binder to packages/agent-contracts/src/task-plan.ts. Android native execution still owns policy/approval.
- Default disabled: NEXA_TASK_V2_ENABLED=false. Models cannot inject approval/risk/evidence or an unrelated owner request. High-risk tools are denied and medium risk needs separate opt-in.
- Full inventory, policy sources and proposed encrypted v3 migration: docs/architecture/NEXA_DSH_SUPERAGENT_SOURCE_ADAPTATION_20261105.md.
- Do NOT copy DSH Node workers into Hermes, introduce generic shell/MCP control, or layer another live autonomous loop.

## 2026-11-05 — Native owner review ingress
- Adapted the previously reviewed DSH task-intake patterns in original Kotlin; no upstream code copied and no duplicate agent loop.
- Chat, Sarvam/OpenAI Voice, Android assistant role, overlay and wake fallback now share the native owner review queue.
- Native origin and exact request are bound to a one-use expiring token; only foreground owner Save creates a review-only encrypted task.
- No new authority is given to models or provider tools. General Voice, Accessibility and messaging execution remain separate and gated.

## 2026-11-06 — Voice checkpoint continuity, no alternate executor

Upstream source checked before this implementation:
- LangGraph.js checkpoint source `libs/checkpoint/src/base.ts`, pinned
  `1631fd895723c4553345ceb5d9fc425b68bda959`, MIT project.
  `CheckpointTuple.pendingWrites` and `WritesSortKey` keep ordering
  metadata; they are not Android OS side-effect authorization.
- Deep Agents JS `libs/deepagents/src/permissions/enforce.ts`, pinned
  `5f42f2df3c814bebbac445c759a34f96c10bc036`, MIT project.
  Its unmatched filesystem rule returns allow; Nexa does NOT reuse this
  as a phone permission or native step grant.
- DeepSeek DSH Superagent Prototype `src/core/engine.ts`, pinned
  `8c32a9dc5d72c033816c6a8110b37f40cf94fba4`, MIT.
  The transactional assignment and event model inspired this adaptation;
  no DSH source was copied, and no worker runtime is installed in Android.

Implementation: `NexaConversationDb.saveTask` now writes its existing
Keystore-encrypted Voice checkpoint and an AUDIT-ONLY
`agent_tasks`/`agent_task_steps` projection in one SQLite transaction.
The projection's policy `voice-checkpoint-audit-v1`, tool identifier
`legacy.voice.checkpoint`, and absence of mandates, approvals or native
observations mean it cannot be executed by the final coordinator. Voice
progress is not represented as independent screen or business verification.
Updates reject stale step indexes, mismatched sessions, changed plan
arguments and attempts to reopen terminal tasks; conflicting task policies
roll back both writes. The unified timeline coalesces the two records by
task ID and continues to show the Voice source as unverified.

This is an incremental convergence of task STORAGE and observability,
not migration of legacy Voice execution ownership. General screen/messaging
steps and shared native scheduling remain separately gated. No new
dependency, database schema version, cloud provider privilege or Android
permission was added.

## 2026-11-06 — Chat notification shade action
- Re-read current LangGraph.js source at commit `abda1bf1a74d60be563e27a293d0c9cd4e8a3215`, MIT license: `libs/langgraph-core/src/graph/state.ts`, `StateGraph` builder and `.compile()` API. The backend graph API is unchanged; this narrow phone action uses the existing `AgentHarness`, not a new graph or agent loop.
- Re-read current Deep Agents JS source at commit `e80b3af2c842515cf3af2bb59a1f2be8a46d266c`, MIT license: `libs/deepagents/src/permissions/enforce.ts`, including `decidePathAccess`. Its filesystem permission rules do not authorize Android Accessibility operations and are not adopted.
- Existing stable API reused: local `AgentHarness.run()` returns `awaiting_approval` for tools with `hasExternalSideEffect: true`; Android's existing one-use journal and owner approval remain authoritative.
- New behavior stays in the existing exact-intent allowlist, React Native native-tool registry, and Kotlin `AccessibilityService.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)` adapter. No framework code is copied, no new dependency is added, and no alternative harness is proposed because this request is a single typed Android action.
- Capability boundary: empty arguments, exact single owner intent, bound Screen Assist, foreground Nexa, one-time approval, and no read of SystemUI notification text. The result says Android accepted the request; actual shade visibility is verified by the device UI flow.

### Voice audit retention and startup behavior
- Process-start reconciliation marks abandoned Voice checkpoints uncertain; it does not infer external app success or automatically replay a gesture.
- Terminal voice audit goal/steps/evidence are eligible for explicit foreground owner privacy redaction. Both encrypted representations are cleared in one SQLite transaction, while identity and event/claim evidence remain for non-replay and incident history.
- A minimal metadata flag indicates redaction in Tools without exposing private Voice text.

## 2026-11-06 — Android-only observed app destination, no harness substitution
- Re-inspected LangGraph.js `langchain-ai/langgraphjs@585b78bbc8249014c6d092ff0e38699679b33034`, `libs/langgraph-core/src/graph/state.ts` (StateGraph execution/checkpoint imports; MIT), and Deep Agents JS `langchain-ai/deepagentsjs@2160d187f5d5c330ed83e575ad0f406e98bdada3`, `libs/deepagents/src/agent.ts` (agent/middleware composition; MIT).
- Neither upstream runtime can prove an Android Intent's destination package is visible. We adopt no new packages and copy no upstream code; the existing Kotlin `NexaUiActionJournal` and Android Accessibility service remain the evidence and permission boundaries. Retained rollback: remove the delayed observation call while keeping permanent claimed launch receipts. Tests cover exact-package classification, wrong-kind rejection and no replay.

## 2026-11-07 — Android owner-pause/approval race fence

Source reviewed at the implementation boundary:
- LangGraph.js, `langchain-ai/langgraphjs@03981062e9010b7454b8e9e290455d9d96306db9`, `libs/langgraph-core/src/graph/state.ts`, MIT: graph compilation and state execution remain backend planning concerns, not device-side authorization or Android Accessibility dispatch.
- Deep Agents JS, `langchain-ai/deepagentsjs@2160d187f5d5c330ed83e575ad0f406e98bdada3`, `libs/deepagents/src/agent.ts`, MIT: middleware and agent construction do not provide native Android owner approvals, app/field identity attestation, or one-time journal atomicity.
- Existing on-device `NexaUiActionJournal` and Kotlin action controllers remain authoritative. No upstream code was copied, no new dependency or second agent loop was added.

Implementation boundary: an owner-created UI review captures the native persistent Pause generation. The final one-time Android gesture/Intent uses the same native synchronization boundary as Pause and refuses a stale generation, including Pause followed by Resume. This mitigates a check/dispatch race without introducing Send, arbitrary coordinates, model approvals, automatic retries or a general autonomous device executor. Outcome receipts still distinguish Android dispatch from observed UI changes and verified business results. Rollback: revert only this bounded generation-fence milestone on Development after independent approval; preserve the durable no-replay journal and existing approvals.
