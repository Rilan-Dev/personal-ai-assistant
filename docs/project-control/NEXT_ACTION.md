# Next authorized implementation actions

## Completed source milestones
- Expo bare Android/iOS source and React Native assistant UI committed.
- Native Kotlin allowlisted app-launch and camera bridge implemented.
- LangGraph.js imported as real server-side agent orchestration dependency.
- Planning-only authenticated HTTP endpoint with strict JSON/input allowlist.
- Model-planning provider via @langchain/openai; user-entered model credentials
  belong only to the server environment.
- Mobile-side HTTPS planner adapter; user opt-in backend settings kept in memory.
- Integration tests cover LangGraph graph, HTTP bearer, input validation,
  disallowed tool calls, redaction, permission denial, and complete simulated
  model/tool/observation loop.

## Open acceptance gates / next sprint
1. Build a signed DEBUG Android APK from GitHub Actions. Debug key must be created
   fresh during CI and must NOT be committed. Diagnose Kotlin/API build failures.
2. Physical Android test: native bridge available, permission-off blocks actions,
   WhatsApp/camera Intent launch observed, unsupported/not-installed apps fail.
3. Run at least one REAL configured LLM provider test of graph structured output
   via owner-provided secret; never use service credentials from app code.
4. Add encrypted ON-DEVICE task checkpoints and one-shot side-effect approval
   ledger; prove no replay on restart, cancellation or connection loss.
5. Implement provider-neutral streaming/voice orchestration after the checkpoint
   contract stabilizes.
6. Review 13 dependency advisories (including 2 high) before private APK release.
7. Stage MCP plugin registry behind a deny-by-default capability boundary.
8. Preserve provider/GitHub upstream source, commit, licenses and tests in
   docs/architecture/HARNESS_UPSTREAM_RESEARCH.md for all future integrations.

Do not mark iOS, real LLM provider, accessibility control, wake word or WhatsApp
message delivery as verified without genuine evidence.
