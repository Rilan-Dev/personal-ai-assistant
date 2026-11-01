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
