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

## New guarded flashlight capability
- Android native device.set_flashlight supports on/off with separate OS
  CAMERA consent, in-app hardware grant, one-time approved session, and
  observed Camera2 TorchCallback before confirming success.
- Real Android device test is required; emulator may lack a flash.
- See docs/architecture/ANDROID_FLASHLIGHT_TOOL.md.
- Prioritize unresolved encrypted-journal emulator failure before any
  signed production release. Diagnostic error-class logs added to CI.

## Real LLM acceptance infrastructure
- Manual workflow agent-provider-acceptance.yml and local
  npm run test:provider-live use AGENT_LLM_API_KEY/AGENT_LLM_MODEL.
- Configure secrets under the protected GitHub environment to run the
  five synthetic planning cases; no real device action or messaging.
- No real provider test has been executed. See
  docs/architecture/REAL_LLM_ACCEPTANCE.md.

## Physical device acceptance kit
- Run py scripts/acceptance/android_device.py --check and --walkthrough on
  an owner-controlled Windows PC with ADB and the current debug APK, or
  use python3 on Linux/macOS.
- Only owner-entered results are recorded; no automatic message sending
  or unprompted microphone/camera access.
- Details: docs/architecture/ANDROID_DEVICE_ACCEPTANCE.md.

## Stable Android debug app signing
- Owner may configure PAA_DEBUG_KEYSTORE_B64 as a GitHub Actions repository
  secret. Without it, CI debug APKs are signed by different temporary keys.
- This permits in-place upgrade and crash/replay acceptance without wiping
  the Android Keystore journal. See docs/architecture/DEBUG_APK_SIGNING.md.
- Debug signing is NOT production release signing.

## Confirmed Android Keystore emulator acceptance
- Root fix 483f5d1: provider-generated AES-GCM encryption IV.
- 8/8 instrumented tests PASSED (run 41228192986).
- 9/9 including ciphertext tamper PASSED (run 41228313667).
- Core journal emulator gate is closed. Confirm the separate extended
  voice instrumentation workflow before claiming full native CI green.
- A real phone, a real LLM provider, and release-security gates remain open.

## Native emulator sign-off
- 8/8 PASSED in run 41228192986 (baseline secure journal).
- 9/9 PASSED in run 41228313667 (AES-GCM tamper case).
- 13/13 PASSED in run 41228773858 (voice foreground safety).
- Native emulator automated gate is closed for the source on e48ae7a.
- Physical Android acceptance and real-provider acceptance are still open.
- User-run physical test kit: scripts/acceptance/android_device.py.

## New Android release-test and direct AI provider support (2026-11-01)
- GitHub Actions workflow: Android installable release-test APK. Confirm
  assembleRelease, minification and apksigner PASS before sharing artifact.
- Under Artifacts, download personal-ai-assistant-release-test-apk, unzip and
  install app-release.apk on the owner's phone. Check SHA-256.
- In app: AI Configuration & API Key > provider/model/key > Test connection >
  Save & Enable AI. Key is encrypted with Android Keystore and never
  included in source or APK.
- Test provider live on phone (not performed without owner's API credential).
- Android emulator: verify new encrypted provider vault save/load/remove.
- Physical testing: verify chat, native tool approvals, battery, flashlight,
  speech and crash recovery. Owner may configure stable test-release signing
  secrets for in-place upgrades.
- Do not describe this signed TEST-release APK as production ready. Open
  dependency advisories and real provider/physical phone acceptance remain.
- Design: docs/architecture/ANDROID_AI_CONFIGURATION.md and
  docs/architecture/ANDROID_RELEASE_TEST_APK.md.
