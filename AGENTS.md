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

## GitHub Actions execution policy (owner authorized; quota protection)
- The owner explicitly authorizes the assistant to MANUALLY dispatch needed
  GitHub Actions jobs when a development milestone or acceptance gate requires
  them. No fresh approval is necessary for each ordinary manual CI/APK build.
- NEVER add automatic push, pull_request, schedule, workflow_run or other
  event triggers. All five workflows MUST remain workflow_dispatch-only.
- Avoid using GitHub Actions for every commit or small change: run local
  checks first, and then dispatch targeted native build/emulator/release
  verification only after a complete, focused implementation milestone.
- Do not start a redundant run: verify HEAD SHA and whether the same workflow
  already passed or is currently running for that commit. Release APK must
  follow successful Android native compilation and emulator acceptance.
- If an authenticated GitHub Actions write channel is available, trigger
  the workflow yourself with scripts/ci/manual-actions.sh and check the
  logs/artifact. Do not repeatedly ask the owner to click Run workflow.
- GitHub's connected app may read logs/rerun old jobs WITHOUT allowing
  workflow_dispatch on the current source. Never rerun an outdated commit
  to pretend the new APK was built. If an authenticated dispatch channel
  is unavailable, report that precise limitation and provide a one-time
  account-connection path (never request credentials in chat).
- Manual runs consume GitHub Actions quotas; do not spam, retry blindly or
  claim completed native builds from Node tests alone.

## Acceptance and worklog
- Execute focused automated RED/GREEN tests and relevant core test suites.
- Run npm ci, npm test, npm run typecheck, npm run web:export.
- For native Kotlin changes, build the APK in Android CI and test on a real phone;
  do not mark device acceptance passed without actual evidence.
- For Apple, use Xcode/macOS; do not claim cross-platform equivalence.
- Update docs/project-control/WORKLOG.md and NEXT_ACTION.md on every major task.
- Preserve concurrent Git commits; no force push. Keep changes focused.
