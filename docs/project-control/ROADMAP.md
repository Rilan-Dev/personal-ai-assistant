# End-to-end development roadmap

- Phase 0 — Repository, dependency assessment, Expo bare native template, shared contracts. Exit: reproducible tests and a committed workspace.
- Phase 1 — React Native mobile chat, Android Kotlin bridge, iOS starter, Web build. Exit: device-level native tool acceptance and web export.
- Phase 2 — Agentic LLM runtime: structured tool schemas, bounded loop, failure recovery, timeouts, context, checkpoint, resumption, side-effect idempotency. Exit: no unauthorized execution.
- Phase 3 — Push-to-talk STT/TTS, optional Android assistant role and wake-word feasibility trials. Exit: actual on-device spoken command execution; always-on listening never presumed.
- Phase 4 — Android tools: location, flashlight, camera, intents, contacts and notifications under distinct permission gates. Exit: denied-permission and background state tests.
- Phase 5 — AccessibilityService: observe nodes, click, scroll, type, screen state verification, optional MediaProjection, protected-screen restrictions. Exit: observe-act-verify loops.
- Phase 6 — Communications: WhatsApp contact matching, chat verification, draft and explicit approval, send reconciliation, phone/dialer adapters. Exit: no wrong-recipient or duplicate send.
- Phase 7 — Plugin SDK and MCP remote tools: schemas, authentication, per-tool scopes, isolation, revocation, security tests. Exit: plugin cannot escalate Android privileges.
- Phase 8 — Encrypted personal memory, user controls, offline AI model/STT experiments, context compaction. Exit: user-owned and deletable memory.
- Phase 9 — Desktop and cross-device: Windows/macOS client adapters, Linux Tauri, companion web, device pairing, tool routing. Exit: platform-specific acceptance matrices.
- Phase 10 — Quality and security: threat modeling, benchmarks, crash recovery, privacy tracing and test automation. Exit: reproducible safety and performance evidence.
- Phase 11 — Private distribution: signed personal APK, desktop installers, versioned updates, maintenance. Exit: owner-approved real-device UAT.

No phase can be marked fully accepted without appropriate platform test evidence.
