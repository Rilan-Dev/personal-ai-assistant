# Next authorized implementation actions

## Implemented in source
- Expo SDK 58 Android/iOS starter, React Native UI and Web demo.
- Kotlin Intent launch tools (camera, WhatsApp apps) and explicit grants.
- LangGraph.js planning-only backend; bounded device-side agent loop.
- Keystore AES-GCM encrypted local journal, one-time approvals and at-most-once
  action reservations, owner-initiated task resume.
- Android on-device foreground push-to-talk (API 31+), explicit RECORD_AUDIO,
  25-second bound, cancel and lifecycle cleanup. No cloud STT fallback.
- Opt-in offline TTS selecting installed non-network voices, Stop speaking.
- Transcript populates editable chat composer, never auto-executes tools.

- User-controlled Android Quick Settings tile and optional system add
  dialog (Android 13+), with manual addition available on Android 12.

## Open gates
1. Current commit: Core tests, Android APK and emulator instrumentation;
   rerun emulator with fixed abstract ReactApplicationContext test setup.
2. Physical Android acceptance: Quick Settings tile from locked/unlocked,
   mic permission denial/allow, offline packs,
   cancel/background stop, voice playback, native device tool gates and
   secure journal recovery after forced process death.
3. Add Android instrumented tests for encrypted journal and audio lifecycle.
4. Test a real configured LLM provider with owner's credentials (not in Git).
5. Explore wake word and Android assistant role with OEM restrictions; do not
   promise always-on third-party assistant invocation.
6. Plan user-managed encrypted history retention and voice privacy controls.
7. Review 13 npm vulnerabilities (2 high), before production distribution.
8. MCP/agent tools need deny-by-default plugin authorization.

Architecture evidence: docs/architecture/ASSISTANT_INVOCATION.md,
 docs/architecture/ANDROID_VOICE_PHASE.md and
docs/architecture/HARNESS_UPSTREAM_RESEARCH.md.
Do not mark physical phone, iOS, true wake word, or WhatsApp sending verified.

## Android emulator instrumentation
- GitHub Actions `android-emulator.yml` runs the new AndroidX instrumented
  `SecureJournalAndroidTest.kt` Keystore/approval/replay suite on API 35.
- Fix any CI compilation, runner, keystore or emulator failures before
  claiming native journal verification. Still need genuine phone testing.

## Latest safety improvements
- Process-shared Android journal monitor prevents claim races across bridge
  instance recreation; session snapshot and latest pointer saved atomically.
- GitHub Core tests now include an informational npm audit report artifact.
- There are 12 unresolved advisories (two high) predominantly in the Expo
  dependency toolchain. Review docs/security/DEPENDENCY_AUDIT_2026-10-31.md.
  No unsupported major-dependency downgrade was applied.

## Read-only native battery tool
- Registered device.battery_status (current percentage + charging boolean)
  with exact user-intent validation and no special Android permissions.
- Remote LangGraph response is deterministically grounded to observed
  Android battery data. See docs/architecture/ANDROID_BATTERY_TOOL.md.
- Complete emulator/physical Android validation for new Kotlin method; do
  not equate model mock tests with device acceptance.
