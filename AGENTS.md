# Engineering agent instructions — Personal AI Assistant

## Scope
This repository is an independent React Native/Expo Android-first AI assistant.
Never fork the entire Expo framework into this product. Android native execution
remains in Kotlin; Node-only LLM orchestration remains in services/agent-runtime.

## REQUIRED upstream reuse and source verification
Before touching any agentic harness / tool-calling / checkpoint / approval feature:
1. Examine current source (not just marketing pages) from
   https://github.com/langchain-ai/langgraphjs
   https://github.com/langchain-ai/deepagentsjs
   and any other proposed upstream framework if it is directly relevant.
2. Document exact upstream repository + commit/tag + relevant source path,
   license, the stable API being reused, and what is NOT adopted in
   docs/architecture/HARNESS_UPSTREAM_RESEARCH.md.
3. Prefer maintained upstream packages. Never copy large upstream
   implementation files without an explicit license/provenance review.
4. Do not layer multiple autonomous loops without evidence they cooperate.
5. Compare alternatives (OpenAI Agents SDK, Pydantic AI, OpenHands, smolagents)
   against the concrete task and test requirements; do not install everything.

## Security invariants
- The backend *plans only*, phone-side executor authorizes and executes.
- Model and external MCP tool output are untrusted data, never security policy.
- Default-deny unknown tool IDs, capabilities and high-risk side effects.
- Validate exact tool arguments again on device. No arbitrary shell, code
  execution, filesystem or raw device automation granted by LLM output.
- Require one-time human confirmation of outbound messages/calls/payments.
- Never infer message delivered or UI target opened from Intent dispatch alone.
- No user data or secrets in Git. User must explicitly opt in to remote planning.
- Preserve privacy: do not log personal screen, notification, contact or calls
  content. Redact tool observations sent to cloud.
- Abort stops queued actions but cannot reverse an already dispatched Intent.

## Acceptance and worklog
- Execute focused automated RED/GREEN tests and relevant core test suites.
- Run npm ci, npm test, npm run typecheck, npm run web:export.
- For native Kotlin changes, build the APK in Android CI and test on a real phone;
  do not mark device acceptance passed without actual evidence.
- For Apple, use Xcode/macOS; do not claim cross-platform equivalence.
- Update docs/project-control/WORKLOG.md and NEXT_ACTION.md on every major task.
- Preserve concurrent Git commits; no force push. Keep changes focused.
