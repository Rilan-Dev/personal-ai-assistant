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
