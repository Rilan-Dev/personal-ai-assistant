# Architecture decisions

## ADR-001: Independent app, not Expo framework fork
Use official Expo SDK 58 bare-minimum native template inside an independent monorepo.
Keep native Android/iOS folders committed. Expo is upstream dependency only.

## ADR-002: Typed and bounded agent harness
Shared TypeScript contracts and policy-gated execution live in packages/.
The model proposes tools but never directly executes shell or Kotlin.
Inputs, capability grants, action budgets, duplicate call IDs and approval
fingerprints are checked by the executor.

## ADR-003: Least-privilege Android Kotlin adapter
Initial tools launch only allowlisted WhatsApp/WhatsApp Business packages or camera
via system Intents. Dispatch does not prove the destination screen opened.
No accessibility, messaging, SMS, notification, background microphone or shell access
exists in this phase.

## ADR-004: Cross-platform architecture
Android has Kotlin native controls; iOS will have permission-limited Swift adapters.
Web via React Native Web; Linux desktop via Tauri later; Windows/macOS native desktop
later. A shared UI does not imply identical OS capabilities.

## ADR-005: Explicit consent and approval
App launch is owner-toggle gated. Android OS permission controls remain independent.
Outbound message/call/payment actions require exact action approval and no automatic
retry after an uncertain outcome.

## ADR-006: Open-source candidates
Evaluate LangGraph/Deep Agents for durable orchestration and MCP for plugins later.
Current first device tests use a deterministic command planner to verify a safe bridge.

## ADR-007: Public repository safety
Repository visibility is PUBLIC as created by the owner. Never commit API keys,
private contacts, signing keystores, personal location or raw phone notifications.
Cloud services require encrypted runtime storage and explicit user consent.

## ADR-008: LangGraph planning server, local execution
LangGraph.js runs only in the Node backend and returns ONE next-step decision.
The React Native AgentHarness executes device actions locally after independent
input, capability and approval gates. A model must not obtain arbitrary device
control merely by requesting a new tool ID. The backend is planning-only and
default-bound to loopback with bearer authentication. Remote access requires
an owner-controlled HTTPS gateway and per-device token, entered at runtime.
No API token is included in built source, and no raw screen content is sent.
No in-server durable checkpoint exists yet; encrypted device checkpoints need
a separate privacy-aware design and acceptance tests.
