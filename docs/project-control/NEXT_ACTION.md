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

## GitHub Actions execution policy — manual-only
- ALL five GitHub Actions workflows now have ONLY workflow_dispatch.
- Git commits, PR creation and branch updates do not launch CI jobs.
- Launch one workflow explicitly from GitHub Actions > select workflow >
  Run workflow when testing/building is needed. Existing runs previously
  queued by earlier commits are not automatically cancelled by the change.
- Keep the regression test scripts/test/workflow-manual-only.test.mjs green
  whenever adding or changing workflows.

## 2026-11-01 mobile UX / background assist next acceptance gates
- Mobile UI migrated to Chat / Voice / Tools / Settings bottom tabs with safe-area margins and dedicated controls. See docs/architecture/MOBILE_UX_BACKGROUND_ASSIST.md.
- New Android realtime voice FGS and owner-armed one-time Accessibility screen text observer exist in source; they are experimental and NOT independently native compiled or physically accepted yet.
- Run LOCAL npm ci, npm test, npm run typecheck, npm run web:export and verify no regression. Then manually run GitHub Actions Android debug compile and emulator instrumentation; workflows must stay workflow_dispatch only.
- Fix new native compile errors and rerun emulator only when necessary. Then manually build release-test APK and test on Android owner device.
- For voice UAT: configure owner's OpenAI key in Settings; verify microphone and notification prompts, WSS session, speaker, network denial, Stop from notification, timeout and background/lockscreen behavior. No recording without Start.
- For screen UAT: enable Accessibility from Tools; arm, switch to an outside app, return, review/discard; verify no password/edit text, no auto model upload and no gestures.
- Before implementing always-on assistants, contacts, camera analysis and location, define OS-permission-specific owner controls, privacy, Google Play policy and test gates. No hidden surveillance or unrestricted auto actions.
- Real provider integration, physical phone acceptance, dependency audit and Play release remain open. Do not claim Gemini-level OS privileges without corresponding Android role/OEM tests.

## 2026-11-01 native acceptance blocker / manual CI instructions
- Remote shared host cannot safely compile the new native app: 7.5 GiB
  total RAM, no swap, ~5 GiB free disk and no /dev/kvm. Two constrained
  Gradle attempts were stopped after memory fell below a safe threshold.
  Do not retry Android compilation on live production servers.
- Before any host-side build, run:
  python3 scripts/acceptance/android_build_preflight.py --mode compile
  python3 scripts/acceptance/android_build_preflight.py --mode emulator
- GitHub has suitable hosted Android runners, but current GitHub connection
  does not expose workflow_dispatch; browser does not have authenticated
  GitHub identity. Preserve manual-only triggers on ALL five workflows.
- Owner action: GitHub > Actions > Android debug compile > Run workflow
  (main); then Android encrypted-journal emulator acceptance > Run workflow
  (main); then Android installable release-test APK > Run workflow (main).
- Inspect latest run's actual HEAD SHA, compile Kotlin/test code, verify
  emulator tests, build release and apksigner. Do not reuse previous APKs.
- After passing Actions, perform physical phone live-provider audio and
  outside-app one-time accessibility observations under owner consent.

## Android hardened implementation acceptance — 2026-11-01
- Current source has additional VAD interruption, AEC, noise suppression,
  native audio-focus and proactive screen-text TTL improvements. Commit
  must be validated using an authenticated manual GitHub Actions run.
- Native debug workflow now runs :app:assembleDebug and
  :app:assembleAndroidTest; emulator workflow runs npm tests and typecheck.
- Release-test artifact will include release-test-source-sha.txt,
  release-test-sha256.txt and release-test-verification.txt.
- Verify current source SHA in the artifact equals the intended commit.
- Physical acceptance must check Android calls/audio focus, spontaneous
  interruption, echo/feedback, low-bandwidth network, Notification Stop,
  and screen text expiring without returning to the app.
- Do not present passing local Node tests as Android Kotlin compilation.
  The GitHub connector still cannot call workflow_dispatch and the
  browser does not have a signed-in owner session.


## Owner approval for selective GitHub Actions dispatch (2026-11-01)
- Owner clarified: only automatic on-push/PR triggers are prohibited. The
  assistant IS authorized to manually launch needed GitHub Actions workflows
  after a completed objective, and may do so without asking permission for
  each ordinary build. Do not treat manual-only as disabled CI.
- Five workflows remain workflow_dispatch-only: Core, Android debug,
  Android emulator, Android release-test and real-provider acceptance.
- Preferred authenticated CLI: scripts/ci/manual-actions.sh WORKFLOW_NAME,
  requiring GitHub Actions write access through gh auth. Its checks prevent
  same-commit duplicate runs and require passing native/debug + emulator
  gates before spending release-test APK minutes.
- If GitHub CLI or its authenticated session is unavailable, state this
  as an access limitation, not as a user requirement to manually launch
  every run. No extra credentials should be checked into source or pasted
  into chat. Work/browser mode with authenticated GitHub is another option.
- Existing runtime GitHub connector exposes read/rerun, not workflow_dispatch.
  The connected BridgeHead host currently lacks an authenticated gh CLI.
  Current redesigned APK has NOT been rebuilt or physically accepted.
