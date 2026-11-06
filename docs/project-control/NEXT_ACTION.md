
## 2026-11-06 — Owner device-action pause/rollback source implemented
Implemented on `Development`: a local persistent Pause/Resume control in Settings; a one-way Pause button in floating Tools; monotonic generation invalidation of stale Voice approvals; native blocking of new third-party actions across journaled taps, scroll, text, launches, OS navigation, and flashlight ON. The UI and native cancellation paths preserve existing one-shot approval and no-replay protections.

Still required development: general parent task/step Kotlin scheduling for Chat/Voice/overlay, true observed multi-step Android state transitions, recipient/chat/message identity verification and per-action mandates across every tool, complete owner memory correction controls, release flag/rollback policy integration. Source checks and manually triggered native builds are separate from these feature implementation gaps; do not represent the entire development backlog as complete.

Owner runbook: `docs/architecture/NEXA_OWNER_ACTION_PAUSE_RUNBOOK_20261106.md`.
---


## 2026-11-06 — New implementation milestone and remaining engineering
Implemented in Development: limited safe non-sensitive screen-control observations with a durable Kotlin read claim, and shared Android launch dispatch with exact installed-app revalidation, one-shot no-replay audit and Chat/Voice origin attribution. Chat launch remains human-approved by the existing harness; Voice launch by the visible owner card.
The remaining substantive development is the **general step executor** under one parent task ID: unify observed UI action receipt linkage, recipient/window/field-specific native mandates, general observation-driven multi-step continuation, safe failure and cancellation, voice session continuity, and production kill-switch/rollback. No automatic Send or permission bypass.
Do not promote `main` or claim a release until the exact-commit Kotlin build, real-device behavioral acceptance, signing lineage, and recovery/rollback gates pass.
---

## 2026-11-06 — Dynamic Chat app launch development and emulator gate
- Chat source now accepts a single owner-requested installed app name and Android resolves one exact launcher label at execution time. The Kotlin bridge, 396 Node tests, TypeScript checks, native compile, and focused Android instrumentation passed. A new Maestro flow covers opening Calendar through Chat.
- The current emulator has a locally built debug APK for this source. Debug JavaScript required Metro, and the UI remained blank after bundle load, so the new Maestro flow has not passed. Build and install a signed release-test APK from this exact source, then rerun flow 15 and the ordered suite. Do not count source/instrumentation success as UI acceptance.
- Broader screen observation, dynamic tap/scroll/type, continuous session, messaging send, and cross-interface coordination remain development work. The design and acceptance boundaries are in `docs/architecture/NEXA_DYNAMIC_SCREEN_AGENT_20261106.md`.
- The only current ADB target is emulator-5554. No physical phone is attached. The Sarvam key previously pasted into chat has not been used and needs rotation before a live provider test.

## 2026-11-06 — Main Maestro suite physical continuation
- The corrected main Maestro suite passed together on the API 37 emulator: 13/13 in 9m 8s. All 13 main scripts also passed on the Infinix X6831 across shorter batches and isolated reruns. The four experimental scripts remain 4/4 on both devices.
- A single uninterrupted 13-flow phone run is not green: one attempt was blocked by Infinix App Lock after six passes; another had a temporary Maestro driver startup timeout; the last long run was diverted into the Android notification shade during flow 03 and stalled at a Maestro tap after flow 14's successful relaunch. Flows 03 and 14 then passed together on the phone (2/2, 1m 43s). The observed interruptions did not reproduce as app failures.
- Updated main flow 06 to grant Android's microphone and notification prompts when shown, flow 09 to dismiss Chrome's first-run tips before the real agent search, and flow 14 to assert the current owner-review status label. These are Maestro-only edits; no app source or APK changed.
- A later OEM run can use shorter batches to avoid App Lock and long driver sessions. The remaining product-development gaps below (unified task/step authority, observed multi-step execution and provider-backed live voice) are separate from the green UI script results.
- After the main-suite runs cleared app state, Screen Assist was re-enabled through the real Android Settings flow on the Infinix. The final post-run device check reports Screen Assist enabled and bound and the overlay grant allowed; Nexa was brought back to the foreground.
- Maestro `clearState` reset Nexa's local app data during these runs. The owner must re-enter any personal provider configuration before live Sarvam acceptance; use a rotated key rather than the key previously exposed in chat.

## 2026-11-06 — Experimental Maestro acceptance completed on Infinix
- Development source `7903f9b6b37edfcaa42fd050fc9a666906c22499` is installed as the signed release-test APK on the API 37 emulator and physical Infinix X6831. The ordered experimental suite passed on the phone: WhatsApp owner review, ambiguous messaging-app choice, reviewed generic text input, and Screen Assist setup (4/4, 3m 20s). No message was sent.
- After Maestro exited on the phone, Android reported `ScreenAssistService` enabled and bound and the overlay permission granted. In Nexa Chat, the exact local request `open notification bar` required Approve once and Resume saved task; after those owner steps, `com.android.systemui` became the foreground notification panel. No notification contents were retained in project artifacts.
- Maestro suppresses Android AccessibilityServices while it runs, so the bound-service check and live notification action are post-run checks. Infinix also exposes overlay grants as inline switches and sometimes opens the Screen Assist detail page directly; flow 10 handles the Settings route without assuming a nested overlay page.
- The final YAML also passed all four flows together on the API 37 emulator (4/4, 2m 53s). Maestro-only edits remain uncommitted in `.maestro/experimental/10-agent-notification-shade.yaml` and `.maestro/experimental/config.yaml`. The CLI invocation passes the four YAML files in explicit order because the installed Maestro CLI did not honor config ordering.

## 2026-11-06 — Bound native text approvals implemented
- Text/message field approvals now record the exact reviewed content binding and irreversible one-shot claim together, instead of relying only on a random confirmation token.
- Remaining development: unify legacy Voice and Android screen/messaging tool dispatch under one parent Kotlin task/step authority; add recipient/window/step-specific mandate issuance, verified observation and interruption recovery across multi-tool workflows; complete full personal memory correction/privacy UI and release rollback/switching.
- Safety status: no general unattended Accessibility executor, automatic Send, model-created approvals or main promotion. Source passes Node+TS, but Kotlin CI/device acceptance remains a separate release gate.
---



## 2026-11-06 — Completed partial authorization/memory development
Implemented: encrypted provenance-aware recipient app preference with owner-selected retention, display/forget in Tools, and exact per-request approval binding for reviewed message and generic text field preparation. The native request's exact recipient/label, package and text cannot be changed between prepare and confirm without cancelling the attempt.
Remaining core engineering (not merely testing): unify all approved UI action claims beneath the parent Kotlin coordinator and versioned per-step mandate, replace the independent Voice gesture cursor, add evidence-grounded multi-step continuation for target app/chat and approved field, complete owner-controlled memory corrections and full release-flag rollback.
Critical safety policy: never silently send or submit; approved text insertion only, no automatic replays; Android permission and provider grants required.
---


## 2026-11-06 — Voice privacy and process-recovery source milestone
Canonical audit-only mirroring, process-restart quarantine, and terminal Voice detail redaction are implemented in Development. Existing Android native UI and Voice executor permissions remain unchanged.
Unfinished implementation: consolidate the legacy Voice action executor into Kotlin coordinator step/mandate authority, bind screen/recipient/message fields to exact per-step approvals, add observation-grounded continuation, owner-facing preference provenance/retention, and release switching/rollback. Android native compilation and physical-phone acceptance are still required before any production assertion.
---


## 2026-11-06 — Voice state storage convergence completed; executor migration still open
New implementation: existing Voice compound checkpoints are mirrored atomically into audit-only native v3 tasks; they are never run by the shared coordinator. Context, task identity, step index, goal and topology changes fail closed. Abandoned Voice state becomes uncertain at process restart, without replay.
Remaining SOURCE DEVELOPMENT: replace the independent Voice UI-action cursor scheduler with versioned native step/mandate handling, enforce precise recipient/app/field approvals across all UI tools, add observation-driven continuation (not Intent dispatch), integrate provenance-aware personal memory and retention, and finish release-switch/rollback and branch reconciliation.
Keep consumer release approval gates and device safety boundaries in place. Native Android compile and instrumentation of this new source need matching SHA evidence; Node/TypeScript alone do not certify Kotlin.
---


## 2026-11-05 — Native device-fact coordinator now shared by four origins
Completed source: direct owner Chat/Voice/assistant-role/overlay battery and storage facts now go through NexaTaskCoordinator, with encrypted task, one-shot claim, Android observation, mandate and owner-visible origin. Unsaved handoffs have a native Discard operation.
Remaining implementation: general-purpose safe app/screen/messaging multi-step executor; exact per-action consent and recipient binding; one coordinator for legacy Voice task cursor and model-directed Chat execution; controlled memory provenance and rollback/release integration.
Current scope is low-risk Android facts only. Release flag gates, user-approved send and Android platform permissions must remain intact.
---


## 2026-11-05 — Shared task ingress implemented in source
The same owner review now accepts Chat, Voice (Sarvam/OpenAI), assistant-role and overlay input. Trusted source is retained at explicit Save in encrypted custom.db. No model action is authorized.
Still unfinished DEVELOPMENT: migrate legacy Voice cursor and Chat tools to one native coordinator; general step mandates, approval binding, cancellation and recovery; observed third-party screen navigation and messaging composition; provenance-aware memory; feature flag and rollback integration.
Do not enable execution gates or promote main until source and native acceptance. No auto-send or permission bypass.
---
## 2026-11-05 — New development priorities (release testing separate)

Completed source additions: native owner task correction with encrypted CAS
and draft-plan invalidation; local redacted event/dependency history; combined
native/legacy Voice timeline; bounded multi-step provider plan normalization.

Remaining PRODUCT DEVELOPMENT:
1. Migrate all Chat, Sarvam/OpenAI Voice, overlay and system assistant tasks to
   one task identity and Kotlin final coordinator. Legacy checkpoints currently
   have unified read-only visibility, not shared execution authority.
2. General native step transitions, permission mandates, exact action binding,
   one-shot approval consumption and verified observations for approved tools.
3. Integrate existing safe Android app discovery, screen grounding, navigation
   and reviewed message preparation into that coordinator; no blanket
   Accessibility autonomy or unapproved external Send.
4. Owner-visible correction provenance, retention and memory controls shared
   across entry points, with interruption recovery and unknown outcomes.
5. Gate activation, rollback implementation and conflict-aware main branch
   reconciliation only after engineering work is actually complete.

NEXA_TASK_V2_ENABLED and reviewed read-only BuildConfig remain OFF.
Testing/CI/device certification is separate from these implementation gaps.

---
## 2026-11-05 — DSH Phase 1/2 development progress

- Added a provider-neutral task proposal admission adapter around the actual Chat planner path for deterministic intent, direct BYOK, and LangGraph suggestions; new execution remains gated OFF.
- Added encrypted native plan admission in existing custom.db v3: native rechecks owner identity/policy, step expiry/dependencies, exact no-side-effect registry, and atomically inserts read-only task steps and a plan event without granting a mandate.
- Owner cancellation atomically invalidates child planned steps. Existing native fact task coordinator now reconciles stale claimed steps as uncertain and revokes their mandate without retrying the Android read.
- Added Node admission and Android plan-storage/recovery instrumented tests.
- Fixed debug-only Kotlin compilation error identified in Actions #41992588796.
- Targeted Node and full TypeScript source checks passed during implementation (389 tests at prior exact commit e25828488ae3). Later source revisions require a fresh exact-commit check; Android native CI for new changes is still needed.
- No unattended sends, general model-driven Accessibility actions, production Docker restart, automatic CI triggers or main branch promotion.

Remaining DEVELOPMENT work before calling general Phase 2 finished: full general-purpose step state transitions/approval and observation APIs across Chat/Voice; one shared Kotlin final execution coordinator for all supported tools; cross-interface stable task identities and complete interruption recovery. Keep flag OFF until these changes are implemented.

---

## 2026-11-05 — Next release gate: unified Nexa task lifecycle

Completed source foundation: bounded non-executable DSH task plan contract, additive encrypted custom.db v3, permanent UI-action claim digests, owner-controlled proposed/cancelled event lifecycle, and one local task review screen in Tools. Manual review history and cancellation are restricted to chat-origin owner policy records.

Still open: current-commit Android native test and signed APK; Maestro save/cancel/relaunch and messaging review flows on matching signed APK; single Kotlin-native task coordinator and step mandates; Chat/Voice/overlay ingress to that coordinator; real authorized provider E2E; physical Infinix Android Accessibility, wake, microphone and process-death validation; signing upgrade, rollback, Play policy and security-advisory closure. Keep execution features disabled and no automatic Send. Do not promote main or treat an earlier green CI run as current native acceptance.

Detailed evidence: docs/qa/NEXA_DSH_OWNER_TASK_REVIEW_ACCEPTANCE_20261105.md.

---
# Current priority — DSH-inspired Nexa implementation

**5 November 2026.** Source inventory and gates: docs/architecture/NEXA_DSH_SUPERAGENT_SOURCE_ADAPTATION_20261105.md

## Verified and implemented in source
- Non-executable, owner-scoped bounded task plan validator; feature remains OFF.
- Additive Android custom.db v3 schema and unique permanent UI-action claims; encrypted proposed-only tasks; native migration and replay tests.
- Local source checks on e9ef608: 377/377 Node tests, TS and Expo export passed. Later native changes require exact-SHA native CI.

## Outstanding work / NO-GO
1. Review CI run #41923776222; conduct one exact-current-SHA emulator test for the later storage changes, fix any Kotlin or instrumentation failures.
2. Implement one trusted native task coordinator, serialized step state, evidence, permissions, approvals, crash recovery and user-visible history before enabling task v2.
3. Bridge Chat, Sarvam/OpenAI Voice, assistant role and overlay through the same owner task identity, with strict model/tool boundaries and no general-purpose autonomous Accessibility behavior.
4. Preserve manual messaging Send, recipient confirmation, privacy and Android settings grant requirements. Add no unattended calls, payments or unsafe external UI control.
5. Pass all native/emulator, physical phone, real-provider, signed in-place upgrade, rollback and Play-distribution acceptance. Do not promote main until verified.
6. Review npm dependency audit's 2 high advisories without unsafe automatic Expo downgrade.
7. VPS Nexa workspace is only for source tests, not a Docker Compose deployment. Leave running production stacks alone.

---

# Current priority — Nexa × DSH integration

**Date:** 2026-11-05  
**Source of record:** docs/architecture/NEXA_DSH_SUPERAGENT_SOURCE_ADAPTATION_20261105.md

## Completed this milestone
- Baseline Development source audit and BridgeHead checkout synchronization; 365 baseline Node tests passed.
- Version 1 non-executable owner-scoped task-plan binder and 12 negative/positive tests (377 source tests after addition).
- Upstream DSH/LangGraph/Deep Agents provenance and Play Accessibility distribution constraints recorded. Task v2 remains disabled.

## Still required before activation or production claims
1. Confirm exact HEAD clean Node tests, TypeScript and Expo web export after the narrowing fix.
2. Implement, instrument and safely migrate an additive encrypted custom.db v3 task store with durable action claims, preserving old session/Keystore history and at-most-once semantics.
3. Route Chat and live Voice through one stable owner-minted task ID and one Kotlin final execution coordinator; reject any model-supplied approvals, side-effect retries or unexpected tool IDs.
4. Reuse existing native Accessibility/text/messaging modules with fresh per-step scope, window, policy and explicit approval. Follow Google Play constraints: no unrestricted model-generated Accessibility autonomy.
5. Test all scoped features and signed APK on the exact commit; physically verify OEM wake, microphone, overlay, native Accessibility/service grant, crash recovery and authorized providers; keep external message Send disabled without its own separate acceptance.
6. Only after those safety gates pass, conduct signed canary/rollback and resolve PR #97/main promotion. Do not modify ACA containers or unrelated Docker stacks for this Android change.

**Current status:** Phase 1 source foundation only; Phases 2–8 and physical production certification remain open. Do not treat a green Node suite as device UAT.

---

# Next authorized implementation actions

## 2026-11-01 — Conversational assistant gesture milestone
- Implemented optional, foreground-consented auto-launch of configured
  OpenAI/Sarvam live speech-to-speech from Android assistant invocation.
  Default remains one bounded offline device command; no idle microphone.
- Closing the voice assistant window ends the session-started provider stream.
- Audio playback generations and OpenAI response IDs reject stale post-barge-in
  audio without stopping the user's mic/VAD.
- Local Node/TS/web checks: 199 tests passed, typecheck passed,
  web export passed. Kotlin instrumentation has new consent check but cannot
  be marked passed until an Android runner executes it.
- Android phone/emulator, live account, battery/reboot and release APK signing
  still require their actual test platforms. No protected OEM hotword privilege
  is available to a normal user-installed APK.
- Connected home Android server failed Tailscale reachability; working alternate
  Linux checkout lacked Android SDK/JDK/ADB. Do not promote a signed APK or claim
  final completion from JavaScript tests.


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

## 2026-11-01 — Main-only workflow trigger strategy
- Development branch is the active implementation branch, starting from
  main. All feature edits go to Development; no automatic Actions run for
  Development pushes, even if multiple commits are pushed.
- A SINGLE source-complete, locally tested release candidate is
  fast-forward promoted to main; only the new
  .github/workflows/android-main-promotion.yml automatically fires for
  source/workflow changes to main.
- Main promotion job gates signed APK on (1) npm tests + typecheck,
  (2) Android API35 instrumented tests, (3) optimized release compilation,
  (4) apksigner and SHA256 verification. If an early gate fails, no release
  APK is uploaded.
- Other five workflows stay strictly manual. For an independent manual
  GitHub dispatch, use authenticated scripts/ci/manual-actions.sh;
  no automatic runs on Development.
- Failed builds 41246429246/41246447014: corrected Android Kotlin
  context stopService and Accessibility clear() type inference. The next
  pipeline run must confirm Kotlin debug/test/release compilation.
- Live provider runs 41246478040/41246896110 lacked model configuration.
  Owner may select the LIVE model ID in manual workflow input or set the
  AGENT_LLM_MODEL environment variable; AGENT_LLM_API_KEY secret remains
  in live-agent-provider-tests environment. The workflow now fails fast
  before npm ci if either missing.
- Main pipeline runs automatically only ONCE per finished feature-set
  promotion; avoid incremental main pushes. Keep SHA/provenance matching.

## Sarvam AI: release/acceptance gate
- The source now supports Sarvam chat (sarvam-105b),
  explicit dictation (saaras:v4), read-aloud (bulbul:v4-flash),
  and audio-to-audio managed Voice Agent signed WebSocket in
  Android microphone FGS. User configures API key and committed
  Voice Agent org/workspace/app IDs in encrypted Settings.
- Live app does NOT store/display transcripts. The managed
  Sarvam backend MAY internally generate/store call data;
  verify dashboard retention and user consent separately.
- Before release, run npm ci/tests/typecheck/web export,
  one main promotion with native Kotlin, 21+ instrumentation
  tests and signed release APK. Fix native build/test errors
  on Development first, push a new main promotion only after
  a complete focused correction.
- Owner physical UAT with Sarvam key and committed Voice Agent:
  chat, read-aloud en-IN/ta-IN, STT editable one-turn,
  speaker playback/echo, interruption/barge-in, audio focus
  loss, Stop notification, 15m cutoff, background app
  switching and network errors. Never capture real personal
  speech in automated tests or logs.
- Existing permission and approval policies remain; no
  live Voice Agent tools can call Android contacts, apps,
  messages or accessibility controls.
- Full native integration and live-provider activation remain
  conditional until evidence from manual physical phone test.

## Native Sarvam Kotlin hotfix (2026-11-01)
- Android main run 41254373222 failed at :app:compileDebugKotlin.
  Cause: SarvamAudioTransport refered to AgentJournalModule.VoiceCredentials
  although the type is nested in AgentJournalModule.Companion.
  Fixed companion-qualified reference on Development with regression.
- Latest source passes 148/148 tests and typecheck. Main release
  candidate must be fast-forward promoted once and native Kotlin,
  instrumented tests and signed APK verified. Do not mark STS live
  provider accepted without owner phone/Sarvam dashboard data.

## Native voice reliability release-gate follow-up
- Next consolidated main promotion includes hardening: owner mute/
  unmute in notification and UI (zero-frame transmission), cancelled
  REST calls, no overlapping STT and TTS workers, playback-head TTS
  completion check, strict redirect prohibition, and WAV emulator
  regression. Re-run the native emulator and release-signing workflow.
- The first signed Sarvam-enabled release-test APK already passed at
  0c9812b / run 41255216719. Do not send earlier APK as the build
  that includes these newer reliability changes.
- Owner's real-device STS speech tests and live Sarvam account
  credentials cannot be simulated or considered completed. No
  unapproved ambient mic/screen/background access.
- Compatible Expo SDK58/Zod vulnerability remediation and production
  stable signing remain separate release-security gates; audit fix
  --force is forbidden because it downgrades Expo incompatibly.

## Owner-private Android tools acceptance
- Scope: explicit single-contact picker and coarse location (Android
  11+), not a new autonomous permissionless location/contact tool.
- Run Android instrumentation for native module registration and
  coordinate quantization, then signed APK build and verify v2 signature.
- Owner verifies Contacts provider selection, no READ_CONTACTS grant,
  cancellation, permission-denied location, accepted coarse location,
  60-second UI expiration, and reviewed-only Chat composition.
- No live credentials, real contacts or user locations may appear in
  committed test fixtures, logs, screenshots, or worklogs.


## Nexa background-assistant acceptance gate (2026-11-01)
- First run consolidated Android Kotlin instrumentation and signed APK CI for
  the new role/session sources. Do not claim a pass from Node tests.
- Owner must choose Nexa in default-assistant settings; confirm supported
  gesture invokes compact session above another app and explicit Start can
  continue audio in background with microphone and Stop notification visible.
- Verify actual phone (OEM power policy, audio focus, Android 14+/15/16 mic
  restrictions), Sarvam live account, network reconnect behavior, mute/Stop.
- Separate future work: verified on-device wake-word engine, bound voice-tool
  event channel, consented screen actions, launcher-page discovery and
  WhatsApp compose/recipient confirmation/send outcome reconciliation.
  These remain **not implemented**, rather than unverified features.
- Preserve the current no-silent-recording, default-deny tools and one-time
  approval policies while implementing those later capabilities.

## Launcher and WhatsApp acceptance (2026-11-01)
- Run Android emulator tests, native Kotlin compilation and signed APK on exact commit.
- Physical launcher gesture, package labels, home folder and screen-resume acceptance pending.
- WhatsApp contacts and draft link must be tested with owner-selected fixtures; no
  automatic Send or delivery verification was implemented in this milestone.
- Hey Nexa always-on custom wake word and voice-device tool routing remain separate
  open engineering gates requiring offline KWS and real phone privacy evaluation.

## One-shot voice command acceptance
- On the owner phone select Nexa as Android default assistant and invoke the
  compact panel. Tap Speak a device command, grant microphone as requested in
  Settings, and verify Open GitHub, Open WhatsApp and Find my GitHub app.
- Confirm voice input cancels on Dismiss, while live voice is active, when
  recognition fails, or after the 20-second bound. No unattended wake word.
- Validate native compilation/instrumentation before labeling as accepted.

## Chat WhatsApp review acceptance
- On Android, type a message request with exact quoted text and confirm Tools
  opens with that message prefilled but no contact auto-selected.
- Confirm ambiguous/unquoted or chained requests do not create draft state.
- Test contact picker, international +country-code validation, WhatsApp handler
  and user-driven send on a physical device. Do not simulate third-party delivery.

## Android setup UI and update installation acceptance
- Verify CI Android emulator compilation + instrumentation and signed release
  artifact for exact change commit. Source-only success is insufficient.
- Check installed app title, versionCode, BuildConfig source Git revision on
  owner phone; preserve encrypted API keys before considering uninstall.
- Confirm current APK and new APK signer SHA256 match to update in place;
  if temporary signers differ, use an owner-controlled persistent signer for
  subsequent releases. Never ship or reveal keystore/password through chat.
- Physically test all seven Setup actions, Android Settings return, revoked
  runtime grants, separate Accessibility enablements, one-time contact/location,
  skip/reopen flow, and OEM-specific gesture/permission experience.

## Permanent release signer production gate (2026-11-01)
- Owner repository admin: authenticate GitHub CLI on the authorized VPS and
  run scripts/signing/upload-secrets-to-github.sh exactly once. The script
  writes all three encrypted repository secrets without printing them.
- Backup the private signing key and credentials to secure OFF-HOST storage;
  encrypted GitHub Actions secrets are not a recoverable backup.
- Trigger a new release workflow; require identical pinned certificate in
  signed APK artifact, native build/instrumentation pass and versionCode increase.
- Install a stable-signed APK, then prove a subsequent APK signed with that same
  identity upgrades without uninstalling or data loss. Prior temporary-signer
  APKs cannot be upgraded directly if their old private key is unavailable.
- Never revert to a per-run signing key or replace the pinned fingerprint.
- GitHub encrypted secrets now provisioned, so do NOT regenerate or replace
  the permanent key. Verify current release run 41296332100 to green and
  compare output APK signer fingerprint to public pinned SHA256.

## Nexa default assistant, overlay and wake-word device gates
- Complete signed Android emulator CI against the new role fallback and
  overlay native module; then install the confirmed APK on the owner phone.
- Confirm Nexa appears in Android Default apps -> Digital assistant app on the
  actual OEM, or capture system RoleManager/VoiceInteractionService diagnostics.
- Owner must explicitly grant Display over other apps in Android Settings,
  enable bubble in Nexa Setup and check screen readability, tool navigation,
  drag, collapse, hide, user stop and overlay revocation.
- Hey Nexa custom wake-word is still not working. Require a licensed offline
  KWS model, background microphone platform compliance, battery/privacy tests
  and hands-free real phone acceptance. Never equate role selection with KWS.

## Offline Hey Nexa acceptance
- Verify signed CI native test and check permanent-key APK certificate.
- On physical phone: validate downloaded model and Hey Nexa microphone FGS,
  notification Stop, offline detection, default assistant handoff and failure
  fallback; capture battery/accuracy/false wake observations. Use model tests
  with English (India) and Tamil-accented English voices.
- Consider a dedicated licensed/accent-calibrated keyword spotting model after
  quality baseline. Keep assistant gestures, permissions and tool approvals
  independent. No claims of fully accepted always-on behavior before testing.


## Owner-reviewed spoken WhatsApp action acceptance
- Verify Android debug compile and connected emulator instrumentation on the
  integrated PR head, then merge PR #7 only after native gates pass.
- Test on the owner phone: explicitly invoke Nexa, dictate a contact hint and
  message, see the React Native Tools preview, select a phone contact, and
  manually send through WhatsApp. Verify no claim of automatic delivery.
- Confirm expiry after 60 seconds, one-time consumption, cancellation, process
  death clearing, Android permission denials and natural-language accents.
- Keep live provider sessions separate from device tools until explicit
  user-reviewed tool approval and proper physical-device testing exist.


## 2026-11-01 — Indicator-free "Hey Nexa" feasibility decision
- No general APK-only solution exists for continuous Hey Nexa while
  suppressing Android microphone dot and microphone foreground notification.
- Maintain privacy-first assistant gesture as default (no idle microphone),
  with explicitly opted-in, privacy-disclosed continuous Vosk wake mode.
- Show protected hotword as unavailable on ordinary installations.
- Protected SoundTrigger/HotwordDetectionService integration requires
  authorized OEM/system access, validation and real target device.
- Track EU DMA Android 18 hotword interoperability (deadline 2027-08-01)
  and Android 19 concurrent hotwords (deadline 2028-08-01); verify
  actual developer API/regional rollout before attempting integration.
- Reconfirm with native instrumentation and phone: idle gesture mode no
  recording, continuous mode foreground notification + indicator,
  Stop/reboot/update migration, post-wake voice privacy behavior.
- See docs/architecture/NEXA_HOTWORD_PARITY_FEASIBILITY_2026-11-01.md.


## 2026-11-02 — Google-assisted Nexa Voice shortcut
- Added native App Actions/launcher "Nexa Voice" static shortcut, routed to
  visible React Native Voice tab by `nexa://voice` with NO microphone startup.
- Google Assistant may fulfill "Hey Google, open Nexa voice" when it remains
  default and the App Action is registered in Google Play / supported preview.
  No Google voice invocation success has been proven on a phone.
- Run current-SHA Android native compile, emulator instrumentation and
  shortcut deep-link tests. Run real phone assistant App Action acceptance,
  verify idle microphone dot absent and active conversation indicator present.
- Never claim "Hey Nexa" indicator-free hardware wake is available to
  the sideloaded APK or suppress platform indicators.

## 2026-11-02 — Live voice crash and Gemini-style session acceptance
- Development fix: wake AudioRecord is yielded before app-started live microphone capture, and resumed only after capture and playback teardown. Regression assertions and 222 source tests pass on isolated Linux checkout.
- Existing Android Setup offers Enable live AI after assistant invocation; this opt-in and configured Sarvam/OpenAI voice credentials are required for live-provider handoff from Hey Nexa. Otherwise the fallback deliberately uses offline bounded commands.
- Android native instrumentation, signed release APK, and physical-phone tests of starting/stopping Sarvam and OpenAI STS remain unverified until evidence is collected.
- On physical phone: capture redacted ADB logcat AndroidRuntime fatal exception and foreground-service errors (redact credentials, contacts, transcripts); verify wake-to-live transition, audio focus, rearm after inactivity, repeated sessions, approval prompts, and battery read result.
- Preserve visible microphone permissions, wake opt-in, one-time side-effect approvals and bounded agent tools. Do not bypass Android background capture restrictions or assert messaging delivery from Intent dispatch.

## 2026-11-02 — adaptive assistant overlay release acceptance
- Run main promotion Android emulator native Kotlin build and signed APK verification for the adaptive theme. Do not equate Node tests with successful native compilation.
- On the actual phone, validate Home-screen visibility and touches around the overlay; Android dark/light switching; Android 12+ wallpaper accent after wallpaper change; Auto/Dark/Light cycle; layout on small screens; system gesture; wake fallback; opt-in Sarvam/OpenAI live session; Stop; expand/collapse; and action approve/deny UI.
- Confirm Android system microphone dot and notification are not suppressed. Recheck no screen-content sharing without user consent, and no automatic outbound message send.
- If native CI detects Kotlin or instrumentation defects, repair in Development before describing the APK as installable.

## 2026-11-02 — action-routing/native release acceptance
- Run Android main promotion (Kotlin compilation + emulator instrumentation + signed APK), fix all native errors before claiming installable build.
- Install updated APK on owner's phone and verify Chat “Can you please open my WhatsApp?” enters the existing native approval flow and reports Intent dispatch without claiming destination screen verified.
- Enable Sarvam Voice Agent IDs plus explicitly opt-in to Live AI after assistant invocation, arm Hey Nexa; verify automatic transition to managed STS (no intermediate keyword-only command mode), user transcription reaches local approval, and approval opens only the requested app.
- Confirm app denial, offline provider, unsupported phrases, duplicate transcripts, background microphone restrictions, screen content privacy, and returning to wake mode after Stop all fail safely.
- For **actual Sarvam agentic tool calling**, design a Sarvam managed API-tool webhook + authenticated device broker, per-session pending action channel and signed phone result feedback; do not claim this exists merely because local transcript approvals work.

## 2026-11-02 — mandatory acceptance after repeated owner failure
- Run native main-promotion GitHub Actions once against exact tested source SHA. Verify Kotlin compilation, emulator instrumentation including NexaLiveVoiceStartupAndroidTest, and signed same-signing-identity APK artifact. If failing, fix and retest before suggesting installation.
- Owner to install latest build and check Settings → Installed app source revision equals expected SHA. Re-run EXACT Chat screenshots, accepting one-time app launch approval; confirm Android only reports an Intent dispatch unless foreground destination verified.
- Start Sarvam Voice tab and separately Hey Nexa. Validate handshake, 30s conversation, interruption, Stop/restart, pending app approval and denial, Android notification/microphone indicator, and no process crash.
- Capture redacted `adb logcat` AndroidRuntime Fatal exception (exclude audio samples, credentials, contacts) if owner's OEM phone still crashes; emulator smoke testing alone cannot explain device-specific failure.
- Do not call Sarvam local transcript path full managed tool-calling; separate authenticated Sarvam cloud API tool broker and model-result feedback remain unimplemented.

## 2026-11-02 — Sarvam real-device acceptance gates
- Promote exact tested Development SHA to main (no force), run GitHub Actions Android native compilation and instrumentation, signature verification and APK publication; do not declare an installable APK before success.
- Revoke/rotate owner-exposed Sarvam API key; enter rotated key only in Nexa Settings. Copy the *full* Voice Agent App ID from Sarvam dashboard; verify a committed/published version exists. Do not put account secrets in any GitHub issue/CI input/chat log.
- Install the newly signed APK. From Nexa Voice tap **Verify Sarvam Voice Agent connection**. `AUTHORIZED` means only that Sarvam issued a signed CALL URL; `AUTH` means key/workspace access mismatch; `AGENT_NOT_FOUND` means full IDs/version not accepted.
- If authorized, start one managed session and test multiple spoken turns with 30s+ replies, barge-in, mute/unmute, intentional silence under/over 120s, Stop/restart, Hey Nexa wake handoff, and Android notification privacy. Capture redacted AndroidRuntime fatal exception if the owner's phone still crashes.
- Dashboard model choice: use Saaras v3-realtime where supported by managed Voice Agents configuration; do not fabricate a per-session signed CALL URL `model` override. The separately documented STT URL can default to v3 for a future **custom** pipeline but would require a distinct audio/LLM/TTS integration.
- Actual Sarvam managed tool execution remains NO-GO until an authenticated HTTPS tool broker, owner-scoped device action queue with approval, Sarvam agent tool registration, and observed result return to model are built and tested. Local transcript-triggered app-open approval is not a full managed tool-result loop.

## 2026-11-02 — Samvaad privacy / workspace key device acceptance
- Verify Android Kotlin compilation and emulator instrumentation against latest revision (with `sk_samvaad_` field and consent gate); validate a newly signed APK, don't assume npm tests are sufficient.
- On phone, paste a newly rotated Samvaad workspace key into **Sarvam Voice Agent (live STS)** (not generic Chat AI Provider). Save complete org/workspace/app IDs and verify a read-only signed CALL URL check; no audio start on save/verification.
- Confirm streaming consent OFF blocks **both** Voice tab and Hey Nexa invocation, with no fallback to OpenAI/model-API credentials. Explicit ON only after owner accepts cloud voice processing and retention, then test 3-turn conversation and Stop.
- Check app does not emit NexaHub data, files, screen, contacts, device inventory or transcript-based tool actions to Sarvam. Confirm random user identifier per call.
- Do not assert zero Sarvam retention; provider itself documents Voice Agent retention at least one day and default indefinite when unset. Do not mutate remote dashboard or privacy/training controls on behalf of the owner. Recommend Owner disable model-training consent and choose shortest Voice Agents retention where permitted before accepting cloud use.
- Exposed API keys must be rotated again directly in dashboard; do not paste them into GitHub or chat.

## 2026-11-02 — API-only continuous voice acceptance gates
- Promote exact validated Development revision to main and run GitHub Android native compilation/instrumentation/release signing. Fix any Kotlin compile/test failure, do not claim APK until green.
- Install only a signed APK from a successful native CI build. In Nexa Settings select Sarvam AI, enter a **newly rotated** Model API subscription key, save, then explicitly enable realtime voice streaming. Do **not** enter Samvaad workspace/Agent IDs.
- Run Voice → Verify Sarvam API realtime connection (STT & TTS WS handshakes + synthetic LLM request, no audio) then real 3+ turn conversation, 20+ second speech, barge-in, mute, Stop/restart, wake-to-live and phone tool approval/denial. Capture sanitized device logcat if the voice service crashes.
- This is compositional STT→LLM→TTS using two continuous vendor WS plus per-turn LLM REST, not a single native speech-to-speech provider endpoint. No provider-side zero-data-retention guarantee is possible merely from app settings.
- Do not access or execute on the AlmaLinux home server until user gives explicit approval. No production changes to AI Comms server needed.

## 2026-11-03 — Android companion acceptance
1. Promote exact tested Development revision without force. Run native Android CI: Kotlin compilation, full emulator instrumentation including NexaPhoneVoiceToolsAndroidTest, signed APK. Fix failures before install.
2. On user's phone, confirm installed git revision and permissions; run Hey Nexa with voice key+consent enabled but assistant-gesture auto toggle OFF. Overlay must show Connecting → Listening automatically, without Live AI tap. Test also disabled consent, which must refuse recording.
3. Ask spoken battery percentage and free storage; confirm truthful Android values. Ask to open an installed **non-hardcoded** app by its exact label; verify Approve once / Deny on the overlay, 60-second expiry, and no false claim of screen navigation. Compare with WhatsApp app open.
4. Test Sarvam model function-calling for read-only battery/storage from paraphrases; verify model receives validated tool result and continues conversation. Agent app opening still uses local owner transcript gate.
5. Do not claim screen OCR, icon recognition, arbitrary swipe/tap/type, phone calls or full autonomous harness are implemented. Follow the phased design doc, explicit permission and Play Accessibility policy constraints.
6. Capture redacted AndroidRuntime stack trace if OEM phone crashes or native callback fails. No credential, screen content or raw audio in logs.
## 2026-11-06 — Chat notification shade action

- Chat now has a narrow `device.open_notification_shade` intent route. It must pass exact owner-intent validation, one-time AgentHarness approval, foreground Nexa and an actively bound Screen Assist service. The action never reads notification text.
- The ordered native gates for `1469be4` built successfully but emulator instrumentation found five failures. The reviewed-text digest receipt and owner-read-plan test fixtures are fixed locally; all three Android gates must now be rerun sequentially against the new Development SHA.
- A local Maestro CLI is available from a temporary install. After the signed release-test artifact is built, install it on physical Infinix X6831 and run the four experimental flows in order. Keep real notification-panel visibility and bound-service assertions strict; no messages are to be sent.
- Local Windows Gradle remains limited by Ninja's 260-character C++ codegen path. Do not promote to `main` or call the release-test APK a public release.
## 2026-11-06 — Post-launch observation implementation gate
- Source milestone: exact package-only observed app destination; separate from mere Intent acceptance and never proof of task success.
- Verify **this exact Development SHA** with focused Android instrumentation and release-test UI acceptance on emulator and Infinix phone; test cold launches, denied Accessibility, changed foreground app, pause, and no unintended messaging.
- General observed multi-step coordinator, exact recipient/composer and message-send review, cross-interface recovery, and signing/rollback certification remain OPEN. Do not mark these complete from a single evidence milestone.

## 2026-11-07 — Signed APK and device QA status
- Signed release-test APK from Development source `fbda03447d44072b1124f294fed22f810038086b` (version `0.3.1800066237`) is installed on the API 37 emulator and physical Infinix X6831. Its SHA-256 is `3f9ee0f0d326957037068016f799e9f0b9dfd4f19884dc1549547139425cfd3d`.
- The updated ordered main Maestro suite passed on the emulator, 14/14 in 17m 57s. The ordered experimental suite passed there, 4/4 in 4m 6s. An isolated Screen Assist setting flow passed again afterward.
- On the Infinix, Screen Assist was enabled and bound after APK upgrade. A live Chat request to open the notification bar completed after owner approval and resume; Android System UI appeared in the UI hierarchy. This verifies the action on the installed APK, independently of Maestro.
- Physical-phone Maestro remains unverified on this APK: its `dev.mobile.maestro` driver closes during `deviceInfo` before any YAML step. ADB restart, forced driver reinstall, and freeing Chrome/Clock memory did not restore it. The phone showed only 82 MB free with heavy swap at the failure. Do not count the 14 immediate runner failures as app flow failures.
- Next: restore a stable phone test-driver session and run the main and experimental suites on the Infinix. The general observed multi-step agent, silent screen observation for action tasks, arbitrary app interaction, automatic messaging, and persistent voice session are still source-development gaps; the green Maestro flows do not prove them. A Sarvam key pasted in chat was not used; rotate it before live provider testing.
