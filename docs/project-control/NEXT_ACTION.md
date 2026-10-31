# Next authorized implementation actions

## Source implemented
- React Native/Expo SDK 58 Android/iOS, Web demo, Kotlin Intent bridge.
- LangGraph.js planning-only Node backend and strictly typed provider adapter.
- On-device bounded tool loop, independent grants, approval checks.
- Android Keystore AES-GCM durable session checkpoint and action journal;
  claims are committed before dispatch and cannot be replayed.
- Owner-only Approve once / Decline / Resume saved task UI.
- Safe high-risk “Test approval” demo action for on-device acceptance; no message sent.
- No automatic outbound communications or background microphone.

## Open acceptance gates
1. GitHub Android CI compiles AgentJournalModule and uploads debug APK;
   GitHub Core test pipeline passes. Capture exact run evidence.
2. Physical phone: verify permission-off, camera / WhatsApp launch, cold
   restart recovery, persistence and keystore loss/corruption behavior.
3. Android instrumentation: process kill mid-action must not duplicate
   action. One-shot approval must not be reusable after crash or retry.
4. Real LLM provider API with owner-provided credentials, no embedded keys.
5. Push-to-talk speech recognition with explicit mic permission and cancel;
   then TTS and Android voice assistant invocation feasibility.
6. Privacy controls and encrypted data retention; preserve action tombstones.
7. Address 13 npm advisories (2 high); no release until reviewed.
8. MCP tools, new device operations and messaging behind explicit safe gates.

Source analysis: docs/architecture/HARNESS_UPSTREAM_RESEARCH.md.
Full design: docs/architecture/DURABLE_ACTION_JOURNAL.md.
Continue source reviews and append worklog evidence; do not mark real-device
acceptance or WhatsApp message delivery without direct evidence.
