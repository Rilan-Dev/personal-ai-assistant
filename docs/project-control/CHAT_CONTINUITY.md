# Project handoff / continuity

Repository: https://github.com/Rilan-Dev/personal-ai-assistant
Default branch: main

Product: owner-controlled Android-first agentic AI assistant, React Native UI
with Kotlin, optional model backend, eventual iOS/web/desktop clients.

Current phase: Phase 2 agent harness source integration under development.
Architecture chosen:
- @langchain/langgraph StateGraph planning backend (Node only).
- @langchain/openai structured-output adapter (server secrets only).
- Local TypeScript AgentHarness loops and rechecks capabilities before tools.
- Android device bridge is limited to allowlisted app/camera Intents.
- No device messaging, accessibility, voice, wake word, memory or MCP yet.

Next actions and blockers: docs/project-control/NEXT_ACTION.md
Full scope: docs/project-control/ROADMAP.md
Upstream research: docs/architecture/HARNESS_UPSTREAM_RESEARCH.md
Source changes MUST honor AGENTS.md, including upstream source review.

Never mark real-device or real-cloud API tests passed on mocked evidence.
