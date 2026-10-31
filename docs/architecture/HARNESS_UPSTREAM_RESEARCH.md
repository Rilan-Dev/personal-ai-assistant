# Agentic harness source review — October 31, 2026

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
