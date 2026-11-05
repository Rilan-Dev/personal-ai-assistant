# Worklog

## 2026-10-31 — GitHub source integration
- Verified Rilan-Dev/personal-ai-assistant and cloned its initial main.
- Imported original Phase 1 TypeScript harness/UI/tests from archive.
- Integrated generated Expo SDK 58 native Android and iOS projects.
- Added restricted Kotlin PAADeviceControl app/camera Intents and package allowlist.
- Registered the native ReactPackage; removed unnecessary template Android permissions.
- Added deterministic native action planning, bridge adapter and regression coverage.
- Recorded unsupported AI, voice, accessibility and outbound actions as not implemented.
- Validation and push evidence to be appended when available.

### Verification
- Fresh npm ci: succeeded (490 packages installed; 13 dependency advisories, 2 high).
- npm test: 29/29 passed (harness, native planner and source wiring checks).
- npm run typecheck: passed for shared core and React Native app.
- npm run web:export: passed; Expo Metro generated a static web bundle.
- Android Kotlin compilation: NOT VERIFIED; Linux worker lacks Java JDK and Android SDK.
- iOS Xcode build and real Android physical-device testing: NOT VERIFIED.
- Owner-controlled native launch actions are intentionally disabled until the in-app grant is selected.

### GitHub Actions build infrastructure
- Initial Core tests workflow: SUCCESS on commit 00799b3.
- Initial Android workflow: FAILED before compilation at android-actions/setup-android@v3;
  GitHub Actions log shows obsolete SDK package "tools" unavailable.
- Replaced that setup action with direct SDK manager installation for API 36 and build-tools 36.0.0.
  Android Kotlin compilation remains unverified until a new workflow run reaches Gradle.

## 2026-10-31 — Phase 2 LangGraph planning adapter
- Reviewed upstream LangGraph.js main at 959f041dfb8f6bbafa6af3ebb4bc20484a9290da;
  Deep Agents.js at 26f190d40f44a2bb498db73e9e98b002bb8554fe.
- Added actual @langchain/langgraph StateGraph runtime for model -> policy steps.
- Reused @langchain/openai for schema-constrained provider adapters; server model
  credentials remain in environment (none committed).
- Added a planning-only bearer-authenticated HTTP API bound to loopback by default.
- Added strict server tool allowlist, Zod input checks, redacted observations,
  error sanitization and 32 KB request ceiling.
- Added mobile createRemotePlanner with HTTPS enforcement, timeout and client
  response validation; native chat UI requires explicit remote mode and does
  not persist bearer tokens to disk.
- Corrected Android CI: create ephemeral debug.keystore with keytool in runner
  before APK compilation; no key checked into Git.
- Local npm test: 49/49 passing with real LangGraph graph and simulated
  end-to-end HTTP model -> tool -> observation loop; npm run typecheck passed.
- Expo web static export passed. An actual LLM provider and physical-device
  acceptance remain unverified.
- Added a server/client action-to-user-intent gate: currently only exact supported single-step commands can trigger native app/camera operations; multi-step or unrelated commands fail closed.
- Android CI signing fix and GitHub verification pending commit.

### Provider protocol and APK handoff
- Added a transport-level test using the actual LangChain ChatOpenAI structured
  response SDK against a local OpenAI-compatible mock endpoint.
- No live LLM API credential was used, and no provider model quality was tested.
- Android CI now uploads a short-lived debug APK artifact AFTER a successful
  Gradle compile, enabling actual phone testing; this is not a release APK.

## 2026-10-31 — Durable task checkpoint and one-time approval implementation
- Confirmed GitHub Core tests AND Android debug APK compilation passed on
  commit 97f01a2; debug APK artifact available through the Actions workflow.
- Inspected upstream LangGraph checkpoint base, MemorySaver and replay
  idempotency guidance, plus Deep Agents filesystem permission code.
- Added `DurableActionJournal` TypeScript contract and test-only memory store.
- AgentHarness now stores checkpoints before execution and reserves action IDs
  before dispatch; grants are exact-session, one-shot and cannot be replayed.
- Added Android Keystore AES-256-GCM encrypted session and action journal
  with synchronous preference commits; no plaintext saved to SharedPreferences.
- React Native UI now has Approve once, Decline and owner-initiated Resume
  saved task controls. No automatic execution on cold restart.
- Added concurrency, denial, restart, cancellation and persistence failure
  regression tests; local suite: 62/62 passed.
- New native journal Kotlin compilation and device-process-death testing
  remain pending CI and physical Android acceptance evidence.
- Added a high-risk, **zero-device-side-effect** Android training tool
  `demo.approval_test` and a "Test approval" prompt, so the owner can verify
  one-shot approval and restart behavior without messaging anybody.

## 2026-10-31 — Foreground Android voice adapter milestone
- Verified Android debug APK and Core tests SUCCESS on prior HEAD 89f1a23.
- Reviewed Android SpeechRecognizer/on-device API, RecognizerIntent,
  TextToSpeech and offline voice requirements; recorded upstream references.
- Added Kotlin VoiceControlModule: API 31+ on-device recognition only,
  RECORD_AUDIO check, foreground focus guard, 25-second timeout, stop/cancel
  and app pause/destruction cleanup; no cloud STT fallback or wake word.
- Added offline voice playback restricted to installed non-network TTS voices.
- Added React Native Tap to talk/Stop listening, Read last reply aloud/Stop
  speaking, device/English/Tamil language selection and explicit transcript
  review before the user sends a request.
- 66/66 local automated tests passed; TypeScript verification passed.
- Native Android compilation for this update and physical speech execution
  are pending verification. No real model provider credentials used.
- Added AndroidX Test instrumented Kotlin suite for real Android Keystore
  persistence, ciphertext at rest, one-time claims and unknown outcome
  replay refusal; added API 35 emulator GitHub Actions workflow.
- Emulator execution and results are NOT yet verified; it is an independent
  acceptance path, not proof of device UAT.

## 2026-10-31 — Fix Kotlin React Native voice bridge compile
- Android debug workflow for 980f501 failed in :app:compileDebugKotlin:
  unresolved currentActivity in VoiceControlModule.kt lines 89 and 216.
- Inspected installed React Native 0.88 ReactContextBaseJavaModule.kt;
  its API explicitly directs modules to reactApplicationContext.currentActivity.
- Corrected both foreground checks to context.currentActivity; kept permission
  and no-background policy unchanged. New CI compile required for verification.

## 2026-10-31 — Emulator CI RED/repair
- Confirmed native Android APK **compiled and uploaded successfully** for
  commit 8aae81c (GitHub workflow 41207954456); Core tests passed.
- Emulator job 41207954334 booted API 35 and compiled app code, but
  `:app:compileDebugAndroidTestKotlin` failed at SecureJournalAndroidTest.kt:19:
  cannot directly instantiate abstract ReactApplicationContext.
- Inspected installed React Native 0.88
  `ReactApplicationContext.kt` (abstract class). Test now creates an
  anonymous concrete subclass `object : ReactApplicationContext(...) {}`.
  Emulator rerun is required before marking runtime Android tests accepted.
- Added owner-controlled Android Quick Settings launcher, Android 13+
  request-add consent dialog and manual tile editing instructions on 12.
- Tile opens foreground chat after unlock, never starts microphone or tools.
- Added source regression and emulator permission metadata check.
- Local automated tests: 67/67 passed; web and TypeScript build verified.

## 2026-10-31 — Encrypted journal concurrency + security triage
- Identified that Android journal used a per-module lock, which did not
  serialize claims across two live React Native bridge module instances.
  Replaced with one process-wide Kotlin companion lock. Multi-process Android
  app mode remains unsupported; no assumptions about distributed CAS.
- Checkpoint and latest-session pointer are now written in one synchronous
  encrypted SharedPreferences transaction rather than two commits.
- Added emulator instrumentation for 12 concurrent bridge objects racing
  to claim one native action; exactly one should return true. Emulator
  acceptance remains pending current GitHub CI and is not yet verified.
- npm audit currently reports 12 advisories: two high, nine moderate,
  one low. High-severity findings trace through Expo CLI/Zod 3 toolchain.
  Did NOT force incompatible Expo downgrade or major Zod overrides.
- Added non-blocking security audit evidence artifacts to Core tests,
  with detailed risk notes in docs/security/DEPENDENCY_AUDIT_2026-10-31.md.

### Emulator compiler follow-up
- Emulator 3b5f4ee reached :app:compileDebugAndroidTestKotlin and rejected
  the anonymous ReactApplicationContext because ReactContext has additional
  abstract members, including handleException.
- Reviewed installed React Native 0.88 source and selected its concrete
  test-oriented BridgeReactContext (marked @VisibleForTesting by upstream),
  without initializing a JavaScript Catalyst instance. The journal only
  uses application preferences. Await Android emulator runtime proof.

## 2026-10-31 — Emulator runtime diagnosis
- Checked GitHub emulator acceptance run 41210468241: Android APK and
  instrumentation Kotlin compiled and four journal tests actually executed,
  but all four rejected secure writes/reservations. Cause was hidden by
  generic exception catches in the native journal.
- Added privacy-safe native diagnostics: exception *class name only*, never
  request, key, fingerprint, exception message or stack. Emulator workflow
  captures this restricted logcat on failure; tests include the safe class.
- This is diagnostic evidence, not a claim the journal is fixed. Must inspect
  the new emulator run and resolve underlying Keystore/storage error.

## 2026-10-31 — Android read-only battery capability
- Added Android BatteryManager capacity and charging-state bridge without
  privileged battery statistics, phone identifiers or runtime permissions.
- Registered device.battery_status in the native tool registry, deterministic
  Android command parser, remote LangGraph schema and independent owner-intent
  policy. A multi-step request cannot silently cause a battery read.
- Redacted remote observations include only validated battery percent and
  charging flag; backend done-step overrides hallucinated model percentages.
- Added direct graph policy and mobile-over-HTTP-agent integration tests.
- Device Kotlin compile/real battery UAT remain gated on the new CI build.

## 2026-10-31 — Guarded Android flashlight control
- Added Android Camera2 native flashlight module and Flutter-free
  React Native bridge (no camera image/video capture).
- Separate explicit CAMERA runtime permission prompt, in-app hardware
  capability, and exact journal-backed one-shot approval required.
- Added user-intent grammar supporting "Turn flashlight on/off" and
  "Turn on/off flashlight"; multi-action commands are rejected.
- Native CameraManager TorchCallback must confirm the requested state;
  callbacks are removed on success/failure/timeout, and backgrounding
  interrupts unconfirmed actions without automatic retries.
- Added LangGraph server schema, opt-in remote planning adapter,
  observed-data redaction and deterministic grounded completion.
- Automated graph, model-hallucination, device grant/replay and
  native-source tests added. Android compilation and phone hardware
  acceptance are NOT yet verified for this addition.
- Added AndroidX instrumentation tests for flashlight native bridge: local
  hardware-support query, refusal of hardware access without a foreground
  React Native Activity, and optional CAMERA hardware/permission metadata.
  These tests do not turn on a real LED or grant permissions. Runtime emulator
  results and physical hardware verification remain pending CI.
- Added opt-in real-model acceptance harness, using the actual LangGraph
  graph and a configured OpenAI-compatible provider but zero real device
  tools. Five synthetic cases cover positive tool selection, rejection
  of unsupported message/contact actions, and basic response latency.
- GitHub workflow is manual and uses an environment-scoped secret.
  No real model credential provided or used, so provider acceptance
  remains unverified. See docs/architecture/REAL_LLM_ACCEPTANCE.md.

## 2026-10-31 — Android Keystore emulator root-cause correction
- GitHub emulator run 41216055475 executed eight instrumentation tests; four
  failed journal writes/claims with InvalidAlgorithmParameterException.
- Root cause: Android Keystore AES-GCM uses randomized-encryption-required
  by default. Passing a caller-created 96-bit IV to
  Cipher.init(ENCRYPT_MODE, key, GCMParameterSpec(...)) was rejected.
- Fixed encryption to Cipher.init(ENCRYPT_MODE, key) and used its generated
  96-bit cipher.iv; kept encrypted data format IV + ciphertext/tag and
  authenticated decryption with GCMParameterSpec. Did NOT disable
  Keystore's IV safety property or bypass encryption.
- Added regression guarding no caller-IV encryption / no weakened key spec.
- The next emulator CI run must validate this exact change before claiming
  Android Keystore acceptance. No phone test has been run.

- Added Android instrumentation proving AES-GCM rejects a one-bit tamper to
  ciphertext/tag (JOURNAL_READ), and successfully restores the original
  encrypted record. This is test-only manipulation of a synthetic session,
  not a production journaling bypass.
- Built standalone owner-run ADB physical acceptance kit
  scripts/acceptance/android_device.py with device/package prechecks and
  manual pass/fail/skip outcomes for voice, torch, battery, approvals,
  force-stop replay and lock-screen assistant tile.
- Android phone is not connected to the available remote workspace; this
  kit is not a substitute for actually executing those hardware checks.
- Recorded device-specific acceptance procedure in
  docs/architecture/ANDROID_DEVICE_ACCEPTANCE.md. Reports are gitignored.
- Added four emulator instrumentation tests for native voice: offline-only
  capability reporting, no wake word, microphone denial without a foreground
  Activity, speech playback denial without foreground, and safe idle stop.
  These DO NOT record audio or test actual speech engines and have not yet
  executed on an emulator for this commit.
- Android APK workflow now accepts an optional repository-secret-provided
  PAA_DEBUG_KEYSTORE_B64 owner-owned debug key to allow in-place updates
  without wiping encrypted task journal. Secret is checked via keytool, never
  logged or committed; absent secret retains temporary debug signer.
- Added owner setup instructions in docs/architecture/DEBUG_APK_SIGNING.md.
  The secret has NOT been configured or verified in the user's repository.

## 2026-10-31 — Encrypted Android journal acceptance evidence
- GitHub Actions run 41228192986 succeeded on Android API 35 emulator:
  8/8 instrumented tests, BUILD SUCCESSFUL, 7m32s Gradle verification.
- GitHub Actions run 41228313667 succeeded on Android API 35 emulator:
  9/9 instrumented tests including GCM tamper rejection, BUILD SUCCESSFUL.
- Native Keystore root-cause fix in commit 483f5d1 is now confirmed.
  These results close the *core emulator encrypted-journal* blocker.
- Remaining independent gates: newer voice safety instrumentation,
  actual Android phone, owner-provided real LLM provider and production
  dependency/release-signing review.

## 2026-11-01 — Android AI provider settings and release-test APK milestone
- Added Android AI Configuration & API Key in-app UI: OpenAI, Gemini, Groq
  and OpenRouter; choose model; masked API key; Test connection; Save & Enable;
  Remove stored key. No provider credentials committed or bundled.
- Android PAAAgentJournal now encrypts validated provider settings with
  Android Keystore AES-256-GCM, and supports load/update/clear. Auto backup
  remains disabled. Added native Android instrumentation covering encryption
  at rest, reinitialization, invalid setting refusal and clear behavior.
- Direct mobile BYOK provider adapter sends bounded JSON prompts to fixed
  selected provider HTTPS endpoints. Validates JSON and native tool plans;
  invalid/unrelated actions blocked. No provider executes Android tools.
  Post-action completion is grounded locally to Android observations.
- Kept optional LangGraph backend planning as alternate mode. BYOK does not
  require the user to deploy an agent server for single-turn AI chat.
- Added GitHub Actions Android installable release-test workflow:
  npm ci, test, typecheck, Gradle assembleRelease, dedicated testing key,
  apksigner verification, SHA-256 and APK ZIP upload. Signing defaults to
  ephemeral test-only; owner may supply stable private CI signing secrets.
- Android release is no longer signed with the debug key in Gradle.
- Local automated unit/static suite reached 100/100. Native release
  assembly, Android emulator provider vault tests and physical device
  acceptance require actual CI / owner test evidence; none claimed here.
- Upstream npm audit advisories remain open, and the artifact is intended
  only for controlled physical-device testing, not public distribution.

- Made the AI settings panel independently scrollable for smaller physical
  Android screens, keeping API key field, Test connection, Save and Remove
  controls reachable while the keyboard is visible.
- Provider summary now says AI mode rather than suggesting that saving a key
  proves a successful network connection.

## 2026-11-01 — Repair Gradle script evaluation
- First release-test workflow run 41234974422 and Android debug run
  41234974572 failed while evaluating app/build.gradle line 96.
- Root cause: Groovy DSL method invocation precedence around the new
  versionCode expression; corrected to versionCode((env ?: "1").toInteger()).
  Made versionName an explicit method call as well.
- Release signing now uses an if(file.exists()) block; the absent testing
  keystore does not attempt to pass null as Gradle signing configuration
  during normal debug builds.
- CI rerun is required. No release APK was produced by the failed run.
- Checked current official Gemini and Groq OpenAI-compatibility docs.
  Updated Gemini default to gemini-3.8-flash; updated Groq default to
  openai/gpt-oss-120b because llama-3.3-70b-versatile was deprecated
  for free/developer tiers in August 2026.
- Owner can still edit any provider model name in AI Configuration.
- Direct-model output policy strengthened: a model-only done answer can
  never claim success for an exact supported read-only device request such
  as Check battery level/Check device status. It must propose a native tool,
  then the app reports the locally observed state. Added regression cases.

## 2026-11-01 — All GitHub Actions now manual only
- Removed automatic push and pull_request triggers from Core tests and
  Android debug compile; removed filtered push from Android Keystore emulator
  and main-branch push from release-test APK builder.
- Kept workflow_dispatch only for all five workflows, including already
  manual real-model acceptance.
- Updated optional persistent debug signing condition for explicitly
  dispatched runs on main, preserving private-keystore restrictions.
- Added a regression test enumerating every GitHub workflow and refusing
  any push, PR, schedule, workflow_run, or repository_dispatch trigger.
- No GitHub Actions jobs were manually started for this change. Run them
  only as needed via Actions > workflow > Run workflow.

## 2026-11-01 — Mobile-first UX, assistive screen reader and realtime voice foundation
- Reviewed the owner's physical Android screenshot: header collided with the status bar, a large stack of provider settings and permission cards occupied the chat, voice controls were permanently pinned above the composer, and there was no proper navigation.
- Rebuilt the app with Chat, Voice, Tools and Settings bottom tabs, compact chat composer, dedicated voice studio, separate AI/provider configuration, action approvals in chat and clean permissions controls in Tools.
- Installed Expo-compatible react-native-safe-area-context (~5.10.1), added SafeAreaProvider in app entry and safe-area-aware root for status bar/gesture navigation. Native Android system sans-serif text and improved spacing/touch targets.
- Added owner-triggered Android RealtimeVoiceService using a microphone foreground service and Stop notification. It reads OpenAI BYOK key from Android Keystore natively, uses authenticated WSS to a dedicated realtime voice model, streams 24kHz PCM frames, receives audio response chunks, and has 15-second handshake and 15-minute session time limits. No automatic start or device tools are reachable from speech.
- Added owner-enabled Accessibility ScreenAssistService and bridge: screen-text capture only when explicitly armed for 20 seconds, one snapshot max 2KB, no gestures, no password/editable fields, no disk storage, clear/review and manual-to-chat transfer. Android Accessibility Settings still require owner consent.
- Added native AndroidX instrumentation cases for foreground microphone denial, accessibility service permission metadata and no hidden auto-start. These native cases have not yet been executed by an emulator for the new commit.
- Added source regression for mandatory notification, token isolation, no boot start/gestures, read-once screen limits, safe area, separate tabs. Existing agent permission and journal code kept intact.
- Followed official Android foreground microphone restrictions, AccessibilityService APIs and current OpenAI realtime event contracts. Source architecture: docs/architecture/MOBILE_UX_BACKGROUND_ASSIST.md.
- Automated local unit/static/typecheck/web tests pass. New Kotlin code, OpenAI live audio, Android OEM background continuation and physical-screen accessibility are not independently verified yet. Do not call this production-ready.
- All GitHub Actions remain manual-only; no automatic jobs have been triggered.
- Added Android BackHandler navigation to Chat and keyboard dismissal on
  tab switches; capability settings remain separate from chat composer.
- Expanded AndroidX tests: owner-openAI-key-only native voice access and
  a synthetic one-shot in-memory screen snapshot, without querying any
  real outside app or enabling a microphone.
- Updated ADB UAT checklist for bottom navigation, narrow screens, live
  mic/notification, Accessibility arming, safe return/discard and refusal
  to share text automatically.
- Local clean npm ci succeeded; 115/115 source/contract tests passed,
  typecheck passed and Web export succeeded. No new Github Action run was
  automatically started; all five workflows still manual-only.

## 2026-11-01 — Native verification attempt on constrained remote host
- Verified main HEAD 7b9307e and all five workflows remain
  workflow_dispatch-only. The GitHub connector exposes workflow log/artifact
  reads and reruns but NOT workflow_dispatch; an unauthenticated GitHub
  browser session also cannot manually start new runs. No CI event was
  triggered and no previous run was mistaken for the latest revision.
- Installed isolated Microsoft OpenJDK 21 and official Android command-line
  SDK with platform 36/build-tools 36 outside the checked-out repo, in
  /home/techadmin/workspaces/personal-ai-assistant/android-test-toolchain.
  The Android CLI download was verified against the official SHA-256.
- Attempted local Android native build and instrumentation compilation.
  Gradle 9.4.1 started and compiled some Expo/React Native Gradle plugins.
  It did NOT reach :app:compileDebugKotlin or Android test compilation.
  The shared host has 7.5 GiB RAM, no swap, approximately 5 GiB free disk
  after toolchain setup and no /dev/kvm. MemAvailable fell below 0.5 GiB
  during the Gradle run. Stopped the workload and its spawned JVMs to avoid
  affecting active unrelated production services; available memory returned
  above 3 GiB. No passing native build or emulator result is claimed.
- Added a read-only scripts/acceptance/android_build_preflight.py to check
  memory, disk, JDK, SDK platform and KVM before attempting a native build,
  plus automated tests. This prevents using resource-starved shared
  production hosts as Android CI machines in the future.
- Current release-test APK artifact from 7b8787a predates the new Android
  UI and native service changes; do NOT offer it as a validated new build.
- Required next step: authenticated owner manual workflow_dispatch on main
  for Android debug compile, Android emulator acceptance and only after
  success the Android release-test APK. Fetch job logs, fix actual Kotlin
  failures and verify artifact signature. Physical device/STT/Accessibility
  acceptance remains owner-dependent.
- Follow-up preflight regression: initial Python subprocess usage relied on
  capture_output/text flags unavailable on this AlmaLinux Python 3.6 host.
  Replaced with compatible stdout/stderr PIPE and universal_newlines.
  Tested the Java-version path using a synthetic java executable. New
  preflight correctly identifies OpenJDK 21 and Android platform 36 but
  rejects local build due inadequate available RAM/disk.
- Latest full local test suite: 118/118 PASS; TypeScript typecheck PASS.

## 2026-11-01 — Pre-native-CI stability and safety corrections
- Compared current OpenAI Realtime client/server event contracts and
  Android microphone foreground-service requirements against app source.
  Confirmed gpt-realtime-2.1-mini model and 24 kHz mono PCM protocol.
- Fixed real-time interruption bug: mic no longer stops sending while
  model is speaking, allowing server VAD to hear owner interruptions.
  Speech-start events now purge buffered output; Android AEC and noise
  suppression are enabled where supported.
- Added audio-focus management: focus loss or interruption stops
  microphone/speaker and releases audio resources.
- Fixed potential native import error by using
  android.media.audiofx.AcousticEchoCanceler and NoiseSuppressor.
- Added native handler-driven expiry of screen-text snapshots even
  if the user does not reopen the application; no lingering sensitive
  text held indefinitely after the TTL.
- Expanded instrumentation tests for bounded screen snapshots,
  owner discard, and existing denial/metadata cases.
- Strengthened manual-only GitHub workflows: debug native
  instrumentation compilation, source validation before emulator run,
  and source SHA in signed release-test artifact.
- Expanded policy/contract tests. 123/123 local tests and TypeScript
  checks pass; source alone is NOT proof of working Android voice,
  physical Accessibility capture, or a signed new APK.


## 2026-11-01 — Manual CI launch authorization and duplicate-safe dispatcher
- Owner clarified that GitHub Actions should be triggerable manually by
  the assistant WHEN a milestone requires it; only automatic push/PR
  triggers must remain disabled to protect account quotas.
- Inspected GitHub connector action surface: read workflow status/logs,
  rerun old runs, but no workflow_dispatch tool for latest source.
  BridgeHead server has no gh binary and no owner-authenticated gh session.
- Added scripts/ci/manual-actions.sh to dispatch allowlisted workflows
  via authenticated GitHub CLI on main, after verifying HEAD SHA and
  skipping successful/active same-commit runs. Manual release requires
  BOTH Android native compile and emulator success for the exact commit.
  Explicit retry flag is required for failed same-SHA run.
- Added mocked no-network regression tests for dispatch, dry-run, dedupe,
  blocked authentication, and release gates. No new GitHub Actions run
  launched, and automatic triggers remain forbidden.

## 2026-11-01 — Failed manual CI diagnosis and consolidated main-only pipeline
- Reviewed GitHub Actions logs for five owner-launched runs:
  - release-test 41246429246 and emulator 41246447014: compilation failed
    at AgentJournalModule.kt lines 127/143 (constructor context reference
    is not an instance property) and ScreenAssistService.kt line 62
    (recursive Kotlin type inference for clear callback).
  - live provider 41246478040: missing provider secret and model;
    41246896110: secret present, model not configured.
  - core tests 41246493686 succeeded.
- Created Development branch from verified main for further development;
  push commits on Development must never trigger GitHub Actions.
- Fixed Kotlin: stopService uses inherited reactApplicationContext,
  preserving voice revocation when keys are updated/deleted. The
  Accessibility native clear() function explicitly returns Unit, resolving
  the recursive synchronized/handler callback inference problem. Added
  source regression tests for both exact root causes.
- Added android-main-promotion.yml: ONLY this workflow can automatically
  run on main and only for specified source/workflow paths. It does npm
  tests/typecheck, boots Android API 35 emulator, executes Android Keystore
  and replay instrumentation, and ONLY when those pass builds/signs/verifies
  the release APK. Runs on a single runner; concurrency cancels superseded
  in-progress release attempts. No automatic triggers on Development.
- Five original workflows remain workflow_dispatch-only. The
  scripts/ci/manual-actions.sh wrapper can launch main promotion manually
  when authenticated. No credentials or sensitive tokens in Git.
- Provider acceptance now allows owner-specified model via Run workflow,
  with AGENT_LLM_MODEL environment-variable fallback. Preflight verifies
  BOTH secret and model before installing dependencies; never invents a
  static provider model nor logs credentials.
- Source/tests validated locally; native verification is contingent on the
  first controlled promotion of this objective to main. A GitHub Actions
  pass is not physical device or real-provider acceptance.

## 2026-11-01 — Sarvam AI modalities implementation milestone
- Inspected official Sarvam API documentation for sarvam-105b
  (HTTPS chat), saaras:v4 (one-shot STT), bulbul:v4-flash (WAV TTS),
  and Sarvam managed Samvaad Voice Agent signed WebSocket. Reviewed
  sarvam-conv-ai-sdk@0.0.42 MIT source to map native wire messages.
- Added Sarvam to DirectProvider planner and Android Keystore
  validated provider allowlist, with api-subscription-key header,
  JSON-mode response enforcement and Android exact-intent policy.
- Added encrypted Sarvam Voice Agent org/workspace/app IDs and stable
  pseudonymous user ID in Android journal. Saving or deleting provider
  and Voice Agent config stops any live microphone session.
- Added SarvamAudioTransport native OkHttp signed-URL handshake,
  restricted *.sarvam.ai WSS trust boundary, PCM16 16k chunks,
  ping/pong, audio playback, interruption and Stop paths. Live
  voice events have NO transcript/text listeners or device tools.
- Reused existing Android microphone foreground service, permission,
  visible notification Stop, audio focus and 15m hard limit. Kept
  separate OpenAI voice mode available.
- Added Sarvam one-shot, foreground-checked SaaRAS v4 dictation
  (memory-only WAV, owner-reviewed editable result) and Bulbul v4
  read-last-response WAV playback with cancellation.
- Extended Voice and Settings UI with Sarvam Voice Agent IDs, explicit
  no-local-transcript disclosure, chat provider key/model selection,
  session state and clear actions. Added source contract/security
  regressions and Android Keystore instrumented synthetic-ID tests.
- Live provider calls and real-device microphone/audio routing cannot
  be tested without owner-configured Sarvam API credentials and a
  committed Voice Agent. Automated tests do NOT prove live STS audio.
- Development branch used; do not trigger any CI for incremental
  commits. Promote only the tested entire milestone to main once,
  then inspect consolidated native emulator and signed release outcome.
- Privacy/transport post-review correction: Android microphone consent now
  names the ACTUAL selected realtime provider, not always OpenAI. The
  Sarvam signed-URL HTTP request forbids cross-domain HTTP redirects and
  requires reference_id as documented by the vendor SDK. Sarvam one-turn
  TTS UI does not claim playback completed when cancelled. Added source
  regressions. Promoted together so a superseded main pipeline can be
  cancelled by concurrency, avoiding duplicate completed APK builds.
- New main promotion run 41254373222 failed native Kotlin compilation:
  SarvamAudioTransport.kt:42 incorrectly referenced the nested
  companion-scoped VoiceCredentials as AgentJournalModule.VoiceCredentials.
  Corrected to AgentJournalModule.Companion.VoiceCredentials, matching
  the actual Kotlin declaration. The other five unresolved member
  references in the log were cascading symptoms of that type failure.
  Added regression to prevent repeating this exact Kotlin error.
- During correction, prevented simultaneous live FGS capture and
  one-turn Sarvam dictation/TTS through UI and native checks.
  Local source/regression suite: 148/148 PASS; TypeScript PASS.
  Native CI re-validation is still required.

## 2026-11-01 — Post-Sarvam voice reliability and privacy hardening
- Verified completed run 41255216719 (commit 0c9812b) on GitHub:
  22 emulator Android tests passed, optimized release compiled, APK
  Signature Scheme v2 verified, release-test APK artifact uploaded
  (artifact 13063621896). Remaining live phone/Sarvam provider UAT
  is intentionally not misrepresented as complete.
- Audited native voice service lifecycle against the user-started FGS
  protocol and official Sarvam speech API reference. Fixed two real
  cancellation defects: late REST work continuing after Cancel/Stop,
  and the next one-shot request being allowed before the first worker
  finished. Kept the per-operation lock until cleanup and cancelled
  actual OkHttp Call objects. Blocked concurrent STT/TTS modes.
- Fixed false TTS completion after pushing bytes into Android
  AudioTrack, now checks playbackHeadPosition with a bounded drain.
  No audio or transcription data is persisted by this integration.
- Added cross-provider live mic mute/unmute from both Android
  notification and Voice screen. While muted, only zero PCM frames
  are transmitted, never microphone content. Mic hardware remains
  active until Stop (clearly explained to user). Serialized
  owner mute transitions and voice-frame send; Stop now prevents
  an additional captured frame from being sent.
- Added emulator WAV round-trip/malformed format regression and
  new source security regressions. npm tests, typechecks and export
  passed on Development. No automatic Actions on Development.
- Node production audit currently reports 2 high vulnerabilities,
  from Expo CLI and transitive Zod v3 (<4.6.6). npm audit's suggested
  fix is to downgrade Expo to SDK 51, which would break the modern
  SDK58/RN0.88 project. Do not blindly apply --force; track
  compatible upstream patched versions, minimize public build
  API exposure, and preserve native app isolation.

## 2026-11-01 — Owner-private contact and approximate location milestone
- Reviewed Android LocationManager getCurrentLocation with permission,
  cancellation and API30 minimum, plus Android ACTION_PICK Phone MIME
  and temporary URI grant. Did not add unrestricted READ_CONTACTS.
- Added OwnerPrivacyModule: opt-in Android phone contact selection,
  single contact URI validation and a single phone-number row query,
  not an address-book scan. No raw contacts in logs/files.
- Added foreground-only approximate location request with Android
  ACCESS_COARSE_LOCATION, current fix not older than 60 seconds,
  one in-flight request, 12-second timeout, two-decimal coordinate
  rounding and no background tracking.
- Added React Native Tools UI, local observation preview, 60-second
  memory expiration, discard control and explicit insertion into
  the editable Chat composer. No automatic model upload or actions.
- Added owner-private observation source regressions and emulator
  synthetic quantization test; physical device location & installed
  Contacts provider acceptance remains necessary.
- All new changes remain in Development until consolidated release
  promotion, preserving the single-main-push CI budget policy.
- Native acceptance gate for voice-hardening main commit bede808
  passed the Android emulator step in run 41258027928; optimized
  release APK build proceeded, and the separate private-tools
  milestone remained on Development while native voice was verified.
- Extended native privacy acceptance: the installed Android package's
  manifest must contain coarse location but no broad contacts,
  precise location or background location permission. One-turn
  Sarvam dictation now stops and rejects any audio upload if the
  visible application loses focus during the microphone capture.
- Additional strict foreground privacy correction: the Android
  LocationManager callback now discards the fix if the owner switches
  away from the app before the one-time result returns. Source test
  asserts this; no background location permission was introduced.


## 2026-11-01 — Nexa assistant role and app discovery (Development)
- Implemented Android default-assistant selection, system-bound
  VoiceInteractionService/SessionService, and a compact system session panel.
  Role and Android microphone/notification permissions remain owner-managed.
- Session panel starts existing Sarvam/OpenAI FGS only on user tap, never at
  boot or on session creation. Stop and status remain user-visible.
- Added a dedicated preflight permission control in Tools & permissions.
  Selecting the role never implies a working custom hotword detector.
- Added GitHub installation lookup limited to com.github.android and
  a user-initiated launch Intent; report launcher page unknown.
- Added emulator instrumentation for assistant service manifest, role status,
  GitHub lookup and rejection of unsupported apps. Source regressions prevent
  unintended privileged-permission grants or always-on audio claims.
- Local tests: 156 passed; TypeScript checks and web export passed.
- Android native compilation, signed APK, assistant gestures and real-device
  Sarvam STS are NOT yet verified for this milestone.
- See docs/architecture/NEXA_BACKGROUND_ASSISTANT.md for gates and limits.

## 2026-11-01 — GitHub launcher navigation and reviewed WhatsApp compose
- Added separate opt-in launcher accessibility service, default-HOME-package restriction,
  exact visible GitHub label, eight horizontal gestures maximum, and timeout/cancel.
- Added status UI, native bridge and instrumentation tests without scanning other apps.
- Added owner-selected WhatsApp contact and exact-message preview; a bounded, encoded
  WhatsApp composer Intent is dispatched only after owner action. No send or delivery claim.
- Read docs/architecture/NEXA_LAUNCHER_WHATSAPP_MILESTONE.md for gates.

## 2026-11-01 — Nexa panel one-shot offline voice commands
- Added explicit Speak a device command button to Android VoiceInteractionSession.
- Uses Android API31+ on-device SpeechRecognizer only; RECORD_AUDIO must already
  be user granted and no live Sarvam/OpenAI session may be using the microphone.
- Fixed allowlist: Open GitHub, Open WhatsApp, Find my GitHub app; 20-second limit.
- App launch request is not evidence of opening, and GitHub icon scan still
  requires the separate Android Accessibility user grant.
- No arbitrary third-party action, cloud transcription, message send or hotword.
- Added source security regressions; native emulator and physical UAT remain gates.
- WhatsApp reviewed draft now rejects national-only phone strings and requires
  an international +country-code contact to avoid ambiguous recipients.

## 2026-11-01 — Explicit Chat to WhatsApp preview routing
- Added local, deterministic, quoted-message parser to agent-core; model output
  cannot trigger it. Any unquoted, chained or ambiguous request falls through
  to the existing safe planner rather than executing a partial send.
- Chat command supplies the exact message only to the Tools review composer;
  alias such as Mom remains a hint, never an inferred WhatsApp recipient.
- User still chooses the single contact in Android picker and taps Send in
  WhatsApp. No background contact scans, LLM tool sends, or delivery claims.

## 2026-11-01 — Android setup UX and stale-install diagnosis
- Compared a supplied screenshot containing the old Personal AI Assistant
  all-in-one UI with current four-tab Nexa UI. No source rollback detected.
- Diagnosed workflow signing fallback: temporary test certificate per run makes
  in-place Android upgrades impossible without the same signer. Independent
  workflow run numbers could also lower the Android versionCode.
- Built new first-run Nexa Setup tab, skip/continue, native local acknowledgement,
  OS permission readback and AppState resume refresh, real system grants, provider
  configuration and independent Accessibility setup. No auto-grants or hidden mic.
- Added Android installed version, code and exact source revision diagnostics;
  both release workflows use a comparable timestamp-based versionCode and embed
  the commit; release artifact records the signer fingerprint for comparison.
- Tests: source UI permission and workflow checks, Kotlin instrumentation suite,
  TypeScript, web export and Android release workflow gates.
- See docs/architecture/NEXA_UI_PERMISSIONS_RELEASE.md for install safety,
  stable signing requirement and physical phone acceptance.
- Follow-up branding audit found Expo app.json name and web name still using
  legacy Personal AI Assistant even though native app_name and JS title were
  Nexa AI. Updated Expo name, web name, version and Screen Assist label;
  kept the existing Android package ID to preserve app identity.

## 2026-11-01 — Permanent Nexa Android release signing
- Root cause: GitHub workflows silently generated a new temporary testing-release
  private key when encrypted signing secrets were absent, breaking in-place APK
  updates. The previous temporary private key cannot be recovered from an APK.
- Created new, owner-controlled 4096-bit RSA PKCS12 certificate (36,500-day
  validity) in an access-restricted VPS directory, with protected credentials.
  Bootstrap refuses identity rotation; no private key or password was committed.
- Pinned the permanent public signer SHA256 in scripts/signing/; both release
  workflows now REQUIRE the same encrypted GitHub Actions secrets, verify the
  public certificate before building and verify the actual APK after signing.
- No fallback release key generation, or silently signed wrong-key APK upload.
  The temporary Android DEBUG test signer remains intentionally independent.
- Added shared long-lived UTC minute-based versionCode calculation, to avoid
  cross-workflow downgrades and the 2038 Unix-second code ceiling.
- Local signer loading and fingerprint check PASS using a JDK17 Docker container.
- Repository administrator still must provision three encrypted GitHub Actions
  secrets, because the connected GitHub app cannot edit repository secrets.
  Until then, CI release intentionally fails closed; stable-key APK not yet built.
- Recovery plan and one-time migration limitation documented in
  docs/architecture/NEXA_PERMANENT_ANDROID_SIGNING.md.
- 2026-11-01 signing follow-up: Authorized home-server GitHub CLI successfully
  created all three encrypted NEXA_RELEASE_* repository secrets via SSH stream
  from protected VPS. CI re-run for e898562 passed signer-load and certificate
  validation; native emulator and APK artifact still running at status check.
- AES-256-PBKDF2 local recovery archive verified; copy to off-host vault is not
  attested, so disaster-recovery readiness remains explicitly incomplete.

## 2026-11-01 — Android default-assistant fallback and compact floating tools
- Reproduced source-level UX issue: Setup disabled Choose Nexa when Android
  reported assistant role unavailable, preventing any role request/fallback.
- Native assistant chooser now requires resumed Activity (not window focus),
  falls back to official Android Voice Input / Default Apps settings and adds
  an independent manual Settings button. Grant is never inferred from launch.
- Added optional SYSTEM_ALERT_WINDOW Android grant flow and a small draggable
  52dp non-modal overlay, owner-toggled and not enabled at boot. Expanded
  controls: Chat, Voice, Tools, Stop live voice, Collapse, Hide. It does not
  acquire accessibility, record in background or operate other apps.
- Setup now tracks eight independent features; real OS permission states
  refresh after returning from Android Settings.
- Real Hey Nexa hotword remains unimplemented, never presented as working:
  no validated on-device keyword-spotting engine/model has been integrated.
- See docs/architecture/NEXA_ASSISTANT_ROLE_OVERLAY_2026-11-01.md.

## 2026-11-01 — Offline Hey Nexa experimental detector
- Introduced Android Vosk 0.3.47 (Apache 2.0) and optional on-device small
  English model (official 40 MB archive SHA-256 pinned and independently
  verified). Bounded HTTPS download, ZIP traversal/size checks, app-private
  atomic install, explicit owner grant and manual start.
- New non-sticky microphone foreground service recognizes local trigger
  phrases with a two-frame gate. It has a visible Android Stop notification,
  no raw audio storage or cloud capture and no automatic boot/background start.
- On detection the service releases the microphone and requests a bounded
  system assistant session if the OEM has selected Nexa as default. The user
  can always tap a fallback notification. Unrestricted AI device control and
  messaging have not been enabled.
- UI: optional model download, local status, arm/stop, refresh. Verification
  and limitations documented in docs/architecture/NEXA_OFFLINE_WAKE_WORD.md.
- Real device accuracy, accent/noise, battery, background OEM and provider
  handoff acceptance remain pending. This is not a trained Hey Nexa KWS model.

## 2026-11-01 — Live conversation on assistant gesture (privacy opt-in)
- Added `auto_voice_invocation_v1`, default false, using the visible Android
  Setup module and a React Native choice. Requires owner-present action,
  provider credentials and relevant microphone/notification grants.
- The selected Android assistant invocation uses direct configured realtime
  speech-to-speech (OpenAI or Sarvam) when explicitly opted in, otherwise
  retains a one-shot on-device command. Session-launched service stops on hide
  or destroy; Android microphone indicator and Stop notification remain.
- Made the native OpenAI playback reject audio from stale response IDs after
  interruption; tagged audio chunks by generation with synchronized flush,
  also applied to Sarvam interruption.
- Added native permission-consent instrumentation and 3 Node source regressions.
- Connected `/home/techadmin/workspaces/nexa-assistant` checkout updated from
  the Development branch. Verified 199 Node tests, TypeScript typechecks and
  Expo web export on connected Linux server.
- Native compile/emulator, owner-supplied cloud provider credentials,
  physical phone reliability, battery/reboot and permanent-key signed APK
  must not be marked verified. Home Android host was unreachable through
  Tailscale; secondary server had no Android SDK/JDK/ADB.


## 2026-11-01 — Owner-reviewed spoken WhatsApp handoff
- Added deterministic Kotlin parsing of explicitly spoken WhatsApp message requests.
  It stores only the contact hint and exact message in memory (60-second TTL)
  and opens the existing React Native Tools review UI.
- Native bridge consumes once; the React Native frontend never infers who Mom
  is, never reads bulk contacts, and never sends WhatsApp messages automatically.
  Existing Android contact picker, country-code check and manual send remain.
- Added Kotlin Android instrumentation and JS source regression tests.
- Reconciled with concurrent explicit-cloud-voice consent changes. The
  combined source passes 202 automated tests and TypeScript checking.
- Dedicated Android compile and emulator CI were triggered; real phone
  recognition, OEM behavior and live WhatsApp acceptance remain unverified.
- See docs/android-voice-whatsapp-review-handoff.md and GitHub PR #7.


## 2026-11-01 — Protected "Hey Nexa" no-dot feasibility audit
- Verified Android 17 CDD 9.8/H-1-9 and H-1-14 against Android
  SoundTrigger, VoiceInteractionService and microphone FGS policies.
- Confirmed the ordinary Nexa APK cannot provide OEM-only protected
  always-on detection, irrespective of model size, assistant role or app code.
- Verified Nexa wake status already reports protectedHotwordAvailable=false;
  the current optional Vosk service uses ordinary AudioRecord with required
  Android indicator and notification. No attempt to suppress them.
- Improved React Native Setup wording so the continuous capture mode is not
  misdescribed as indicator-free "hands-free"; show protected backend as
  unavailable in the installed APK, and explain privacy-first gesture mode.
- Recorded the July 16 2026 EU DMA interoperability decision and deadlines
  for Android 18 (August 1 2027) / Android 19 concurrent hotword
  (August 1 2028); not a currently shipped API or worldwide guarantee.
- Detailed source-level and device/OEM acceptance gates:
  docs/architecture/NEXA_HOTWORD_PARITY_FEASIBILITY_2026-11-01.md.
- Linux verification after UI correction: 202 Node tests passed,
  TypeScript checks passed and Expo web export succeeded at b9258bd.
- Native APK, actual device microphone privacy behavior and OEM
  integration remain unverified; release gate is NO-GO for no-dot
  always-on voice-only activation on ordinary APK.


## 2026-11-02 — Privacy-first voice entry and Google Assistant App Action
- Inspected protected AOSP `VoiceInteractionService` hotword APIs. The DSP
  detector is `@SystemApi`; a normal APK cannot acquire privileged backend
  or suppress Android microphone indicators.
- Implemented Android App Action `actions.intent.OPEN_APP_FEATURE`,
  static "Nexa Voice" launcher shortcut and dedicated `nexa://voice`
  entry point in AndroidManifest and res/xml.
- React Native Linking now routes cold-start or warm-app external voice
  shortcut to the Voice tab WITHOUT automatically starting a microphone.
- Onboarding explains Google Assistant "Hey Google" fallback, Play/App Action
  discovery, and possible loss of Hey Google after switching default
  assistant role to Nexa.
- Added native PackageManager instrumentation and Node source tests.
- Source validation 206 / 206 passed, TypeScript and Expo export passed
  at 3b54f3d. Next gate native Android compile for final SHA and device.
- See docs/architecture/NEXA_PRIVACY_FIRST_ASSISTANT_VOICE_HANDOFF_2026-11-02.md.
- True no-dot "Hey Nexa" from idle remains OEM/Android 18 access dependent;
  source changes DO NOT claim to solve or hide ongoing microphone use.

## 2026-11-02 — Live speech microphone handoff and regression verification
- Owner reported a physical-phone crash when starting live voice with the Hey Nexa wake detector armed, and a Sarvam battery question rejected by the provider-action policy.
- Reviewed current Development source. Deterministic read-only battery/status routing and bounded fallback live-provider activation had already been committed before this pass; installed APK behavior was not verified.
- Corrected a missing microphone handoff: RealtimeVoiceService claims the wake AudioRecord before opening its provider transport and releases that claim after stopping/releasing live capture and playback.
- Wake detector now yields capture, exits its read loop safely during live handoff, avoids reporting an error on intentional handoff, and does not trigger the OEM-assistant fallback timeout for app-started sessions.
- Added scripts/test/nexa-live-mic-arbitration.test.mjs with lifecycle and safety regression assertions.
- Isolated Linux clone on direct-connected AI-Comms server in /tmp/nexa-ai-review-20261102 (production services unchanged): npm ci succeeded; npm test 222/222 passed; npm run typecheck passed; npm run web:export passed.
- Android native compilation and physical-phone crash reproduction NOT verified here: home Android machine unavailable via Tailscale; alternate server lacks Android SDK/JDK/ADB. Do not claim native APK, provider session success, or phone acceptance based on source tests.

## 2026-11-02 — approved adaptive Hey Nexa overlay UI
- Implemented original approved floating-glass UI in **all three** native paths: system assistant panel, offline/wake fallback, and owner-enabled movable Chat/Tools/Voice bubble.
- New `NexaOverlayTheme.kt` shares dark/light translucent surfaces, 44dp actions, waveform and Android 12+ wallpaper-derived system accent tokens. Auto/Dark/Light choice is available directly inside the bubble. No wallpaper pixel capture, screen read, microphone bypass, or newly granted device permission.
- Wake fallback includes safe Battery/Find GitHub/Tools shortcuts, live-provider state feedback, explicit approval/deny row, collapse/expand voice dock, and Stop. Live-provider panel may open without Android offline STT if explicitly opted-in and provider credentials are stored.
- System assistant gesture now uses the same translucent conversation card, concise live status, safe local reads, owner-controlled Live AI and Stop, and a non-dimming system-owned window.
- Tool bubble now has a themed expanded transcript/editor, native waveform and theme control; 52dp draggable/collapsible bubble preserved. Bounded size updated to 360dp x 490dp with display-size caps.
- Added source regressions for three shared surfaces and permission disclosures plus `NexaAdaptiveThemeAndroidTest.kt` emulator coverage.
- 226 local Node tests passed, TypeScript typecheck and web export passed in the isolated server checkout (at commit 15bd9bd); no Android SDK/JDK on that server. Build emulator and actual-phone appearance remain separate acceptance gates.
- Detailed spec: docs/design/HEYNEXA_ADAPTIVE_OVERLAY.md.

## 2026-11-02 — polite Chat intent and Sarvam wake-only live actions
- Owner reproduced Chat failure: “Can you please open my WhatsApp?” was rejected by `requestAllowsNativeTool` because the exact-command grammar did not allow conversational politeness. The provider returned “outside exact request”.
- Introduced `parseNativeOwnerIntent` as the single intent policy for direct-provider and native planner. Recognizes bounded, single-step polite WhatsApp, WhatsApp Business, camera, battery/status and flashlight requests; rejects compound, negated, quoted, unsupported, and mismatched actions. Explicit requests become tool **proposals**; Android AgentHarness grants/approval/journal/native checks remain in force.
- Reviewed Sarvam official SDK 0.0.42: `server.event.transcription` supplies role=user|bot and content. Native CALL client previously ignored user transcripts; now user-role only transcript can propose a local allowed app open (WhatsApp, WhatsApp Business, Brave, GitHub) via `NexaVoiceIntentGate`, with user confirmation before the Intent. Bot text, mixed instructions, retries and unknown actions cannot bypass the gate.
- Changed wake/assistant auto invocation: “Hey Nexa” starts an opted-in configured live voice provider. Removed automatic local command fallback after failed live handshake or absent opt-in. Manual Tap Talk/offline action remains available as separate explicit control. Microphone notification and system indicator remain visible.
- Sarvam local guarded transcript sidecar is NOT remote Sarvam harness tool calling. There is no documented CALL client tool-result return protocol; the managed Sarvam agent cannot yet receive phone tool outcomes. Future bridge must have authenticated per-device command routing and result feedback.
- New Node and Android instrumentation source tests. Isolated Linux checkout: `npm test` 232/232 passed; TypeScript typecheck passed; web export passed. Kotlin compilation, configured Sarvam live-account acceptance and Android phone end-to-end still require native verification.

## 2026-11-02 — repeated WhatsApp failure and live-voice crash follow-up (real screenshot cases)
- Screenshot provided after prior green release showed “Can you please i Open my WhatsApp” and “I want you to open the whatsapp app” rejected by `direct-provider` intent policy. Both were absent from earlier regression tests. Added both exact requests, more natural forms, and negative/compound controls to the shared `agent-contracts` grammar; applied analogous parsing to Kotlin Sarvam transcripts.
- Additional functional gap verified: owner-started Voice tab launched `RealtimeVoiceService` with `EXTRA_ASSISTANT_PANEL=false`, so even an authorized Sarvam transcript could not create pending device actions. Enabled the restricted tool path for visible, owner-started live voice and added RN Voice tab pending approval and Android result display; approval requires a foreground owner tap.
- Live crash investigation found startup safety risks (not a proven phone crash stack): concurrent wake AudioRecord release vs provider AudioRecord startup, exceptions from asynchronous Sarvam HTTP/WebSocket callbacks and PCM transmission. Serialized wake microphone ownership, added a bounded voice capture wait, and contained transport errors in managed callbacks.
- Added native Android emulator smoke instrumentation launching the real foreground voice service with the app visible and no stored provider credentials, plus repeated Stop. This verifies fail-closed service behavior, NOT a real Sarvam account/phone reproduction.
- Node source suite now passes 234 tests; TypeScript typecheck and web export pass in isolated Linux checkout. Android emulator/native compilation/release status still pending CI; real phone logcat still required to establish crash root cause if it persists.
- Requested external 'Test Android Apps' plugin capability is not callable in this tool session. Used direct GitHub source and Android CI instrumentation path instead.

## 2026-11-02 — Sarvam managed continuous voice verification and safe on-phone authorization check
- Owner supplied Sarvam credentials in Chat; the API key is compromised by exposure. No secret was copied into code, logs, CI or MCP terminal calls. Owner must rotate it and save replacement in Nexa's encrypted Android settings.
- Verified Sarvam Voice Agents documentation and current managed `sarvam-conv-ai-sdk` wire protocol against Nexa `SarvamAudioTransport`. The managed CALL signed WebSocket handles internal STT, reasoning, TTS and turn history; `saaras:v3-realtime` is the different standalone STT WebSocket protocol. The referenced `ai-comms-agent` uses separate Saaras + hosted agent + Bulbul streaming relays, which are intentionally not copied into Nexa's managed Voice Agent flow.
- Added a foreground-only, read-only native `SarvamVoiceReadiness` preflight and Voice tab control to validate encrypted user-selected key/organization/workspace/full App ID against Sarvam's signed CALL authorization endpoint, returning safe codes only. No media capture, signed URL, API key or transcript crosses React Native.
- Fixed long-conversation lifecycle: local idle limit now 120 seconds; managed agent audio delivery counts as activity; expanded owner-visible maximum to 60 minutes; grew bounded playback queue to 96 and drops oldest delayed audio rather than forcibly terminating the conversation due to one playback backlog. Owner Stop, audio focus, mute, permissions and native authentication remain enforced.
- Added Kotlin offline instrumentation for malicious signed URLs, safe error codes and no token leaks. Updated existing Kotlin idle gate test; added source tests for readiness, isolation and continuity.
- Isolated reference checkout: `npm test` passed 239/239; `npm run typecheck` passed; `npm run web:export` passed. Native Kotlin/emulator verification and real authenticated microphone/audio acceptance require separate gates.
- Full scope and Sarvam managed-vs-standalone model rationale in `docs/design/SARVAM_MANAGED_LIVE_VOICE_ACCEPTANCE.md`.

## 2026-11-02 — Dedicated Samvaad workspace key and hard private voice gate
- User reported `sk_samvaad_` workspace key being rejected by generic AI Provider card and demanded that Nexa not share private NexaHub/phone data or retain provider conversation data.
- Root mismatch: generic provider card targets Sarvam Model API Chat Completions; a Samvaad Voice Agents workspace key is **not** a Model API key. No user API key was committed or added to CI.
- Added separate Android Keystore AES-GCM `sarvam-voice-key`, typed React Native setter/presence-only bridge, dedicated Voice Agent UI and read-only agent preflight; Chat key validation now explicitly rejects voice-only workspace keys with a useful message. Trims only pasted boundary whitespace before secure save.
- Implemented explicit per-device `sarvam-voice-streaming-consent`, OFF by default. New key resets consent. Clearing or disabling stops service. `openVoiceCredentials` will not fall back to OpenAI if a private Samvaad key exists and consent is denied. No microphone starts merely on key save or auth preflight.
- Dedicated Samvaad calls suppress transcript-triggered native tool proposals and use a fresh random pseudonymous user identifier for each call, without sending NexaHub records, contacts, messages, screen data or files.
- Verified Sarvam docs: managed Voice Agents **do not support zero-day data retention**, and workspace data defaults to indefinite until Owner configures data retention. Nexa cannot force provider-side deletion or override managed dashboard agent configuration. The feature warns explicitly and stays disabled by default.
- Isolated checkout validated `npm test` 243/243; TypeScript and web export passed. Android emulator build/physical-phone/ST S acceptance still require separate verification.
- Details: `docs/design/SAMVAAD_VOICE_ONLY_PRIVATE_MODE.md`.

## 2026-11-02 — Sarvam API-key-only continuous voice migration
- Migrated live voice from signed Samvaad workspace/Agent ID authentication to the owner's Sarvam **Model API subscription key** stored in the existing Android Keystore provider vault. The active route `VoiceCredentials("sarvam-api",key)` does not read organization, workspace, Agent App IDs or the dedicated Samvaad key.
- Compared the AI Comms Agent Development reference `infra/voice-relay/src/sarvam-wire.mjs`, `sarvam-relay.mjs`, `sarvam-tts-relay.mjs` with Sarvam's current Model API documentation. Nexa uses direct provider API calls from Android, not AI Comms sessions, data or deployed services; AI Comms server was used only for an isolated source-test checkout. AlmaLinux home server has not been accessed.
- Implemented continuous Saaras v3 Realtime STT WebSocket `audio_input` + VAD barge-in, bounded in-memory conversation turns with Sarvam-105B Conversations API, and Bulbul v4 Flash streaming PCM over TTS WebSocket. No separate one-shot transcript UI or local transcript persistence. API-only preflight tests both WS authentication paths and a synthetic 105B request without microphone input.
- Native tool gate intercepts explicit owner app-open requests and requires the existing visible one-time Android approval, then speaks actual observed result. Does not claim arbitrary model-controlled phone tools.
- Replaced legacy Settings card with a Sarvam API Realtime Voice card; live initiation requires selected Sarvam AI Model API key and explicit consent (off by default, revoked on model/provider change). No fallback to Samvaad workspace or OpenAI.
- Added native emulator instrumented API URL/PCM config tests, updated regression suite and architecture documentation `docs/design/SARVAM_MODEL_API_ONLY_REALTIME.md`.
- Scratch checkout: Node 244/244 tests, TypeScript typecheck and web export passed before final documentation-only changes; Android Kotlin compilation and native instrumentation still require CI. No exposed API keys written to source/CI, no authenticated speech call was attempted.

## 2026-11-03 — Hey Nexa automatic live voice, overlay review, model-callable device facts
- Investigated user-supplied screenshot: native system assistant displays "Enable live AI after assistant invocation and configure Sarvam in Nexa Setup." Code gated even an armed Hey Nexa hotword behind `auto_voice_invocation_v1`. Changed only the hotword case to require an explicitly armed offline wake detector and encrypted provider credentials+voice streaming consent; generic assistant gestures still require their separate toggle. Mirrored behavior in the floating fallback.
- System assistant moved one-time **APPROVAL REQUIRED · Approve once / Deny** actions ahead of shortcuts; remains visible without opening the full React Native app. A pending request expires after 60 seconds; no model-driven approval.
- Implemented `NexaPhoneVoiceTools`: local battery percent, StatFs storage usage, device status, and exact owner-requested app label resolution from Android-visible launchers. No broad package listing permission. Dynamic open target dispatch occurs only after the owner taps native approval. Ambiguous or absent labels produce a spoken clarification, not a guessed tap.
- Implemented `NexaSarvamReadTools` with Sarvam Chat Completions nested function schema. `SarvamApiVoiceTransport` now performs a bounded (one tool) function_call → Android validated read-only result → tool message → follow-up model reply loop. Actual phone actions remain in the native owner-speech/approval sidecar; not yet a general agentic UI harness.
- Added native emulator instrumentation for device facts and restricted model function schema, plus source regression coverage of wake path, approval order and explicit permission gate. Android native compilation/emulator and physical device remain separate release gates.
- Full multi-phase roadmap and Android policy restrictions: `docs/design/NEXA_ANDROID_AGENTIC_DEVICE_CONTROL_ROADMAP_20261103.md`.
- Isolated non-Alma checkout source tests `npm test` 247/247, TypeScript and web export passed. No access to the AlmaLinux home server and no changes to the deployed AI Comms Agent runtime.

## 2026-11-03 — final follow-up safety and continuity fixes
- Fixed the floating fallback's legacy two-minute timeout incorrectly ending a live provider conversation. Live voice is now governed by the service's own bounded idle and maximum session limits.
- Native app discovery now queries MAIN/LAUNCHER without MATCH_DEFAULT_ONLY, so legitimate launcher apps are not excluded, and normalizes spoken "app" suffixes. No broad app inventory permission.
- Pending voice action approvals now expire automatically after 60 seconds and return an explicit denied/expired result instead of leaving the conversation permanently blocked. Neither expiry nor callback can execute an app launch.
- Sarvam read-only function tools now validate the **current owner utterance** before returning battery/storage/device-status data to the provider. A model-originating tool call alone is not authorization to disclose phone facts.
- Added Android instrumentation coverage rejecting unrequested read tools. Source tests and TypeScript passed for the follow-up fixes. Native CI must still confirm Kotlin, emulator instrumentation, signing and APK.

## 2026-11-03 — Sarvam passed authorization but Voice UI incorrectly displayed OpenAI
- Inspected user screenshot and GitHub Actions run 41515725696. Sarvam preflight result confirms two WebSocket handshakes and one synthetic Sarvam-105B Conversations response; it **does not** start microphone or change the saved provider.
- Root cause of misleading UI: Voice card chose Sarvam vs OpenAI using legacy `sarvamVoiceKeySaved` (dedicated Samvaad workspace key), whereas live voice uses encrypted `activeProvider` from Android Keystore. An API-only Sarvam key correctly lacks Samvaad workspace credentials; old code incorrectly showed OpenAI despite the active Sarvam model provider.
- Updated Voice card to show provider selected for next session and, for a running call, the **actual native service provider**. Correct Sarvam pipeline wording: Saaras v3 Realtime WS + Sarvam-105B Conversations + Bulbul streaming TTS WS, no workspace ID and no OpenAI key. OpenAI wording appears only for a deliberately saved OpenAI provider. No fallback or silent provider switch.
- Updated app microphone disclosures: local Hey Nexa permission describes offline wake detection, while live requests accurately disclose selected Model API/Realtime provider. Provider selection defaults to Sarvam for a clean installation, while saved Android Keystore selection remains authoritative.
- GitHub run 41515725696 failed `NexaPhoneVoiceToolsAndroidTest.sarvamModelFunctionSchemaIsReadOnlyAndArgumentsAreValidated` at line 51; 68/69 emulator tests succeeded. Root cause: Kotlin raw string regex had `\\b` rather than `\b` around storage/battery words; fixed in `NexaSarvamReadTools.kt`.
- Added `nexa-voice-provider-identity.test.mjs` to ensure provider UI never uses legacy workspace-key flags and native status never returns provider API secrets. Also fixed TypeScript's strict narrowing of icon-vision response `unknown` to a local string before parsing; no change to vision inference behavior.
- Source test suite 259/259, typecheck, and web export passed on isolated AI Comms server checkout. **Native Android re-run and signed APK remain separate acceptance gates.** No API keys used in CI or committed; neither AlmaLinux home server nor AI Comms production services changed.

## 2026-11-03 — Screen-access/voice-tool crash hardening and single-use screen actions
- User reported that Sarvam voice cannot inspect/operate the foreground screen, and a battery-percentage voice request crashed Nexa. No physical-device AndroidRuntime trace was supplied: the exact crash root cause is therefore **not proven**.
- Found missing routing: `RealtimeVoiceService` Sarvam `ownerRequest` only recognized app and some device-fact intents, leaving existing `wantsScreenOverview`, `locateIconIntent`, and Accessibility screen actions unreachable. Now recognizes explicit owner screen-read, icon overview, icon location, four-way scroll, and navigation-icon tap intentions.
- Implemented `NexaPhoneVoiceTools.wantsVisibleText`, `scrollDirection` and `clickSafeIcon`; not a broad autonomous model action. New screen commands create the same visible per-operation `Approve once` / `Deny` request, with 60-second expiry. The screen text approval explicitly discloses sharing bounded labels with the selected voice provider.
- Added `NexaScreenVoiceOverview.readVisibleTextApproved` (current external application, at most 130 nodes, 16 short labels, never editable/password fields, no full screenshot), `scrollApproved` (fresh target, verified one-time Accessibility ACTION_SCROLL_* up/down/left/right) and `tapSafeApproved` (search/menu/settings/back/close only, matched against current Accessibility window and native action journal). Sensitive send/call/delete and arbitrary x/y gestures remain disabled.
- Prevented a native vendor BatteryManager/StatFs exception from crashing the realtime WebSocket: device facts now catch exceptions, and `SarvamApiVoiceTransport.onFinal` catches owner-tool callback exceptions before they escape. The exact OEM crash still requires redacted logcat verification.
- Added native instrumentation covering voice screen grammar and battery no-crash, and source checks for one-time on-overlay approval, sensitive field exclusion, no generic gesture injection and four-way scroll. Updated obsolete background-assist test to reflect previously introduced scoped `flagRetrieveInteractiveWindows`.
- Source tests 271/271, TypeScript typecheck and web export passed on an isolated AI Comms Agent server checkout; native Android CI, signed APK, and physical phone acceptance remain separate release gates. Did not access AlmaLinux home server or modify AI Comms running services.

## 2026-11-03 — Screen-control finishing pass before main release
- Extended bounded `NexaScreenScroll` using native `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT/RIGHT` in addition to vertical scroll actions; no XY gesture dispatch. The floating Tools overlay now offers Scroll left/right with a separate Confirm scroll button.
- Added natural polite owner-command normalization (e.g., "Could you please scroll down?" / "Can you read my screen?") without relaxing exact command or per-operation approval policies.
- Fixed the launchable-app filter's raw Kotlin regex: `\\n` and `\\b` incorrectly blocked valid labels containing `n` and failed proper word boundaries. Added emulator assertions for valid app names and compound unsafe requests.
- Screen voice router now reports `Screen Assist is disabled` immediately if Android Accessibility has not been user-enabled instead of presenting an approval that could never execute.
- Added `NexaScreenVoiceRoutingAndroidTest` and `nexa-live-screen-voice-routing.test.mjs`, covering owner intent, denied unsafe clicks, four-way scroll, missing permission, crash containment and bounded text capture.
- Final isolated source checks: 271/271 Node tests, typecheck, web export passed. Native Android CI still required; no physical phone logs were provided to prove the specific source of the reported battery crash.


## 2026-11-05 — DSH source-admission foundation (Development)
- Audited current source HEAD 865fa166a7292df3585482dea52160ce7e9644b2 with GitHub; synced VPS test checkout via BridgeHead git pull --ff-only. No direct server source edits.
- Baseline npm test 365/365 passed on exact audited revision.
- Added packages/agent-contracts/src/task-plan.ts: strict bounded, policy-scoped, immutable, non-executable TaskPlan proposal binding. New gate is disabled; no Android actions or prior user flows were changed.
- Added packages/agent-core/test/task-plan.test.mjs with 12 cases. Initial focused 12/12 and full 377/377 pass; Expo web export passed. TypeScript narrowing initially failed and was corrected in commit ce12c334629c628be7fbb07075016d4e860c48bf; repeat exact-commit check before claiming certification.
- Pinned DSH MIT, LangGraph and Deep Agents source review, documented the existing duplicated task lifecycles, Android Play Accessibility constraints and exact future migration/release requirements in docs/architecture/NEXA_DSH_SUPERAGENT_SOURCE_ADAPTATION_20261105.md.
- Production Android acceptance and DSH task execution integration remain explicitly NOT ACCEPTED. VPS free disk ~7 GB; no unrelated Docker restart, no live redeploy, no main promotion.

## 2026-11-05 — DSH-inspired native task storage and UI replay hardening
- GitHub Development contains additive custom.db v3 database schema (v1/v2 upgrades), task/step/event/mandate/approval/observation relations and FK constraints. Existing encrypted conversations and voice checkpoints are retained.
- New native task proposals are stored AES-GCM encrypted, rejected on duplicate ID and never executable from this API.
- Old 40-entry encrypted UI-action journal now reserves permanent SHA-256 token tombstones in SQLite before actions; audit eviction cannot cause a duplicate UI claim.
- Android tests added for isolated v1/v2→v3 migrations, all task table/FK checks, encrypted proposed tasks and denial of replay after >40 ledger events.
- CI #41923323315 failed before Gradle on obsolete v2 source assertion; scripts/test/nexa-next-agentic-milestone.test.mjs fixed at e9ef608. Local 377/377 Node tests, TS, web export passed on e9ef608. CI #41923776222 was requested on e9ef608; check final native status. Later storage changes need new native test on exact SHA.
- Read-only npm audit --omit=dev found 10 advisories (2 high, 7 moderate, 1 low), mostly Expo. Avoid automated major Expo downgrade without compatibility/security review.
- All autonomy remains disabled, no live Android APK rollout, no main promotion, no other Docker services touched. Physical phone/providing real Android OS grants requires separate authenticated owner acceptance.

## 2026-11-05 — DSH task review UI and privacy isolation

Confirmed older signed Android workflow 41926075781 passed on 0724292f. Implemented encrypted local owner task proposals, atomic proposal/cancel event history, and a foreground-only manual review UI. Consolidated duplicate screens and bridges to one Tools panel without displacing urgent messaging approvals. Restricted native history queries and cancel operations to manual chat-origin policies, with safe read/cancel of older preview-only records. Added tests for encrypted goals, one-shot cancellation, spoofed policy refusal and native restart persistence. Added manual Maestro task-proposals suite. Source verification at a68dd26b5: Node 380/380, TypeScript and web export passed; later native updates require exact-SHA Android CI. No device-side production promotion, external Send or service restart was performed.

## 2026-11-05 — Maestro proposal flow fix

Maestro Cloud run 41936403045 passed 12/13 flows. The only failure was the exact-match assertion for proposal safety text embedded in a wrapped paragraph. Exposed the disclosure as a dedicated accessibility-labeled element and updated the flow assertion; added a source regression test. Focused test, TypeScript checks, and Expo web export pass. Full Android signed build and Maestro rerun still required for this revision.

## 2026-11-05 — Task lifecycle development milestone

- Added owner-correctable encrypted task goals with compare-and-swap revision
  checks and preservation of append-only event provenance.
- Editing a proposal atomically invalidates its tentative read-only steps.
  Existing mandates, observations, approvals and claimed steps fence edits.
- Added native encrypted dependency inspection with non-executable status,
  foreground-only owner step/event history and one local review panel.
- Added a combined native and legacy Voice task index while clearly labeling
  legacy checkpoints as not independently verified task outcomes.
- Added bounded multi-step model proposal normalization with local risk and
  task ownership; provider outputs still cannot grant permission.
- Local Node 392/392 and TypeScript passed at 79c1f3a0e82d. Android
  compilation and physical device acceptance remain separate.
- GitHub Development only, no main promotion, automatic sending, or Docker
  service mutation.

## 2026-11-05 — Shared task review entry points
- Committed native one-use owner task review handoff from Voice, Android assistant-role, overlay and wake fallback.
- React Native owner Save persists the trusted native origin with encrypted task identity; no action or message is sent.
- Extended owner correction, cancellation, privacy redaction and history for all native review sources.
- Added native regression tests for token replay, expiry, forged goals and cross-origin save/cancel.
- Local source verification at be04a843c050: Node 392/392 PASS and TypeScript PASS. Later commits need exact native compilation.

## 2026-11-05 — Android coordinator shared safe-read milestone
- Routed direct owner battery/storage requests from assistant role and overlay to NexaTaskCoordinator, joining Chat and Sarvam/OpenAI Voice read authority.
- Added origin-aware mandate checks, revocation and native timeline validation for role/overlay task rows.
- Removed a duplicate direct BatteryManager path from the overlay command dispatcher.
- Added explicit owner Discard for unpersisted origin-bound task review tokens; no task or message is created by discard.
- Added Android instrumentation test source for cross-origin native fact runs and secure discard; production Android behavior still needs exact-SHA native evidence.
- Kept external message Send and unrestricted screen automation disabled; no server Docker restart or main release promotion.

## 2026-11-06 — physical QA and release build continuation
- Physical Infinix X6831 (Android 13/API 33) was connected; Screen Assist and Launcher Navigation were both enabled and bound. Approved one four-flow Maestro test batch; no actual messages are to be sent.
- A local debug build fails in Windows Ninja native C++ codegen due to long generated safe-area-context object paths. The D: project fixes stale OneDrive autolinking when its generated cache is refreshed, but still hits the Windows path limit.
- The task changes were carried forward onto the newly advanced `origin/Development`; the phone remains attached.
## 2026-11-06 — Durable Voice checkpoint projection into the canonical Android task ledger
- Added NexaLegacyVoiceTaskMirror as a non-executable audit adapter for existing NexaVoiceTaskCursor checkpoints. The original encrypted voice_task_checkpoints write and a matching agent_tasks/agent_task_steps audit projection now commit in ONE custom.db v3 transaction.
- Added immutable plan/goal and session checks, monotonic step index, terminal state fences, and rejection of cross-policy task-ID reuse. A bad update rolls back both stores.
- Only trusted native Android Voice maintains the actual interaction cursor. The projection has no mandate, per-step approval, native observation or dispatch privilege and never becomes an executor.
- Unified task history deduplicates voice_task_checkpoints with native mirror rows but continues to label them legacy_voice_checkpoint, with zero independently verified steps.
- On Android application process start, abandoned nonterminal Voice checkpoint rows are marked uncertain in both stores; the old cursor cannot be revived or automatically replayed.
- Added Android instrumentation cases for atomic persistence, no mandate, collisions, monotonic updates, terminal non-reopen, and restart quarantine.
- No new database schema version, network dependency, API key, broad Android tool permissions, automated send or production Docker deployment.

## 2026-11-06 — Chat notification shade intent gap

- Root cause: the existing overlay/voice notification action already required owner approval and called Android Accessibility, but the React Native Chat deterministic intent gate, direct-provider allowlist and native tool registry did not expose it. Chat therefore returned the generic unsupported-command response.
- Added RED/GREEN tests for exact owner wording, direct/deterministic planning, rejecting read/compound/negated requests, and proof that AgentHarness awaits one-time approval before execution.
- Added `device.open_notification_shade` to the exact owner intent and planner allowlists. The Android tool has empty arguments, medium risk and an external side effect, so it uses the existing journal/owner approval. Kotlin checks foreground Nexa and a bound Screen Assist, then reuses the existing notification shade global action. It reports acceptance only and never reads notification contents.
- Added Android instrumentation for the fail-closed no-service result and recorded current LangGraph.js / Deep Agents.js source commits and MIT licenses in `docs/architecture/HARNESS_UPSTREAM_RESEARCH.md`.
- Corrected the manual Actions dispatcher to target `Development` for debug, emulator and release-test jobs while preserving `main` for the explicit main promotion workflow. Regression tests verify both refs.
- Verification: 395/395 full Node tests, TypeScript, Expo web export and manual dispatcher tests 9/9. `npm ci` reported 10 advisories (1 low, 7 moderate, 2 high); no dependency upgrade applied.
- Windows local Gradle stopped in React Native CMake because Ninja rejects a generated object filename over 260 characters. GitHub Android native compile/instrumentation and signed workflow build are pending. `gh` is not currently installed.
- Maestro 11–13 previously passed individually. Flow 10 and the direct Chat action still need physical acceptance on the refreshed APK; no messages were sent and no Sarvam key was used.

## 2026-11-06 — Terminal Voice task privacy controls
- Added foreground-only owner action to redact terminal Voice task goals, step texts and last evidence in BOTH encrypted checkpoint and canonical task stores, transactionally.
- The redaction leaves task identity, lifecycle event history, step ordinals and permanent no-replay action claims intact; no tool or provider is invoked.
- Added an explicit redaction marker to metadata-only task timeline (no decryption needed to list), and a Tools button visible only for mirrored terminal Voice tasks.
- Extended instrumentation source for redaction idempotency, removed private content, and preserved audit provenance. Running or pending Voice tasks cannot be erased through this endpoint.

## 2026-11-06 — Guard compound Voice continuation with canonical state
- Added a fail-closed Kotlin mayContinue predicate for matching task ID, owner Voice session, native audit policy and pending step ordinal.
- Wired the guard into RealtimeVoiceService before each legacy multi-action step; changed, completed or aborted tasks stop without further action.
- Kept all existing native approval, Accessibility window targeting, journal claim and owner-send requirements. No new automatic action capability.
- Added instrumentation coverage of the continuation-denial cases.
