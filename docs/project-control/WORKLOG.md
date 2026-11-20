# Worklog

## 2026-11-18 — v1818 Maestro coverage and local Settings read

- Fixed three Maestro harness cases found during emulator QA: flow 09 recovers from Chrome's transient ANR during browser prewarm; flow 14 scrolls toward the local safety disclosure in the correct direction; physical QA flow 16 handles the delayed Nearby Devices runtime prompt without granting it.
- On `emulator-5554`, all 19 `.maestro/nexa` flows (01–12, 14–20) and experimental flows 10–13 passed individually on QA2 v`0.3.2026111818`. Cloud permission recovery, physical QA setup flows 00–02 and 16, and QA2 floating-bubble setup also passed individually. Standalone JUnit reports are retained under `.maestro/results/qa2-v1818-x86_64/` where enabled.
- The ordered five-flow core-suite run did **not** pass as a batch. Flow 05 passed; Maestro's Android device server then died during `viewHierarchy`/`deviceInfo`, yielding four `DeviceServerDiedException` harness errors. Those four flows each passed in later standalone runs. Keep this transport failure visible; do not label the batch green.
- Re-ran the Screen Assist Settings setup flow and stopped only Maestro's driver packages afterward. Android reported the QA2 Screen Assist service enabled and bound, with no crashed service.
- Direct cross-app check on the exact v1818 APK: armed one local read in Nexa Tools, launched Android Settings, and verified the privacy-safe log metadata `package=com.android.settings nodes=120 captured=true length=419`. Returned to Nexa and used Discard; the UI confirmed “Screen text discarded from memory.” The text was not displayed, stored in the project evidence, or sent to AI. Metadata-only record: `.maestro/results/qa2-v1818-x86_64/settings-read-metadata.txt`.
- Follow-up runner diagnosis: the five-flow grouped batch remains blocked by Maestro's Android device server dying after flow 05. Separately, a later Flow 19 attempt reached Android Settings but logged an ANR in `Settings$AccessibilitySettingsActivity`; the failure hierarchy showed the switch still off. Flow 19 now handles the Settings recovery prompt and retries only if the switch remains off. The updated flow passed 1/1 in 2m31s (`final-screen-assist-retry-20261118/report.xml`). The owner-safe Chat battery smoke passed 1/1 in 55s (`physical-debug-battery-20261118/report.xml`).
- After the recovery rerun, Maestro's driver packages were stopped and ADB again reported QA2 Screen Assist enabled and bound, with no crashed service.
- APK SHA-256: `87A6C7957119116E98254CDE027C48911C682408BDBE862AC408CF08C0684D9D`. This remains a local debug QA APK. No physical device, mailbox, external message, or Sarvam credential was used.
- Remaining acceptance is the floating-Chat LLM initiating a safely owner-approved external tap/scroll, live provider-backed ACA/email and realtime-voice checks, stable grouped Maestro transport, and release signing.

## 2026-11-17 — Native scroll evidence reconciliation on physical Infinix

- Root cause: Android returns scroll_event_observed for an accepted scroll event without asserting the final screen position. The Chat/direct planner previously fell through to a generic failure message, even when Android had accepted the requested direction. The planner now reports event-only evidence accurately; structural-change evidence is distinguished from a scroll event. The owner-sequence classifier accepts both native statuses and legacy evidence.
- Regression coverage exercises up/down/left/right in Android planner, floating Chat completion, and owner-sequence evidence. Focused tests passed 3/3; full npm test passed 653/653; root/mobile/runtime typechecks passed; git diff --check passed with only line-ending warnings.
- On Infinix 099344034S003788, the debug bridge reported NexaAppTools, rawAdbInput=false, and a live engine. Through Nexa's native bridge, Android Settings was opened and one confirmed down-scroll changed the normalized snapshot from 15 to 12 lines (23 line differences). Android reported scroll_event_observed, businessOutcomeVerified=false. A second down gesture at the list boundary produced no normalized content change; Nexa did not claim the page moved.
- From the Home launcher, one confirmed left scroll returned structural_change_observed and changed normalized text from 53 to 52 lines (97 differing lines). The right-scroll attempt first failed closed on STALE_SCREEN_SNAPSHOT; after a fresh observation, the foreground changed to another app and no scroll was issued there.
- Metro on port 8081 was active and the device reverse was present; no APK rebuild was required for the TypeScript planner change. The real floating-Chat/Sarvam request was not rerun through the overlay in this session, so that end-to-end response remains pending. No emulator or message-send action was used.

## 2026-11-17 — QA2 dual-architecture rebuild and ordered screen-control QA

- Rebuilt QA2 v`0.3.2026111708` (`2026111708`) as a self-contained dual-architecture debug artifact. Local and emulator-installed APK SHA-256 match: `DDF05F44C0813AD58FABF460D4782C53D2BC43940620A320D9094DA071D8DCD9`. Embedded JS bundle SHA-256: `0F5968FB88AEC4219332885B935DF6B021FD070D79129D8C363CF516A4C4367A`. ZIP inspection confirmed `assets/index.android.bundle`, `lib/arm64-v8a/libreactnative.so`, and `lib/x86_64/libreactnative.so`. This supersedes the earlier intermediate v1708 APK hash noted below; the earlier arm64-only build crashed on the x86_64 AVD and was replaced.
- Ran the ordered Maestro batch on `emulator-5554` with an explicit UDID: Flows 05, 17, 18, 19, 16 passed 5/5 in 4m 53s. Report and per-flow logs/screenshots are in `.maestro/results/qa2-v1708-dualarch-batch-final-20261117/`. No flows were run on the connected physical Infinix.
- Maestro temporarily held Android UI Automation, so Screen Assist was not bound while the driver was active. After the suite, stopped only `dev.mobile.maestro` and `dev.mobile.maestro.test`; Android then listed QA2 Screen Assist under `Bound services`, with no crashed service. Nexa MainActivity remained foreground; screenshot `.maestro/results/qa2-v1708-dualarch-post-suite-screenassist-enabled.png` shows the Enabled badge.
- On this exact APK, a one-shot local read of Android Settings succeeded (`package=com.android.settings`, `nodes=31`, `captured=true`, `length=16`). Captured text was never displayed or sent to AI. Nexa showed “Screen text discarded from memory”; metadata-only log `.maestro/results/qa2-v1708-dualarch-settings-read-metadata.txt` and screenshot `.maestro/results/qa2-v1708-dualarch-current-screen.png` record the result.
- Re-ran local source verification: `npm test` 645/645; `npm run typecheck` passed for root, mobile, and agent runtime; `npm run web:export` passed. `git diff --check` passed after these documentation edits (line-ending conversion warnings only).
- Still unverified: AI-initiated owner-confirmed semantic tap/dynamic scroll on an external app; live provider-backed ACA email synthesis and realtime voice routing; and release-signed/public APK. ACA test flows stop at the fail-closed connect-account gate (`https://aca-unconfigured.invalid`); no email was read. No message was sent and no Sarvam key was used.

## 2026-11-17 — QA2 emulator Screen Assist status and regression verification

- Traced the intermittent Flow 16 “Enabled” failure to Maestro’s Accessibility driver delaying Android’s Screen Assist bind until the flow exits. Two live runs showed `service connected` and `getStatus connected=true` immediately after Maestro cleanup. Flow 16 now checks control reachability during Maestro; the actual bound-service check is performed after the driver exits.
- Extended the Tools status refresh to a bounded two minutes so it sees Android’s delayed rebind. Fixed the stale Android Accessibility setup banner: it now clears only after native status reports connected, without erasing capture/review feedback.
- Built and installed QA2 v`0.3.2026111708` (`2026111708`) on `emulator-5554`; APK SHA-256 `A5357D6F4619C2E2A9C6C834E37A9634C28DC708C2718566ACBCDE85EEFD3F04`. Verified the embedded bundle hash exactly matches generated JS (`054762BEE55C67CB4CBCF54BBC45B98B547FBEB7941D127E9F34920D6DE49AE2`). Debug QA artifact only; not release-signed.
- On the exact v1708 install, Maestro Flows 05, 16, 17, 18, and 19 each passed 1/1. After the clear-state overlay/ACA flows, reran Flow 19 then Flow 16. Post-Maestro Android reports Accessibility enabled, QA2 Screen Assist bound, and no crashed service; the native bridge logs `connected=true active=true`. Final screenshot: `.maestro/results/qa2-v1708-final-screen.png` shows the Enabled badge without the stale setup notice.
- After a cold emulator restart, Maestro 2.12.1's normal driver reinstall path timed out. Installed its bundled driver APKs from the local `maestro-client.jar`, reran Flows 19 and 16 with `--no-reinstall-driver`, then force-stopped only `dev.mobile.maestro` and `dev.mobile.maestro.test` to release UiAutomation. Android subsequently reported Screen Assist under `Bound services`, no crashes, and the refreshed Tools badge showed Enabled.
- Verified one-shot external-window capture on Android Settings and Chrome `about:blank` using metadata-only logs: Settings `captured=true`, 419 characters; Chrome `captured=true`, 9 characters. The captured strings were never displayed or sent to AI. Nexa's Discard action cleared each buffer; screenshots `.maestro/results/qa2-v1708-external-settings-read-discarded.png` and `.maestro/results/qa2-v1708-chrome-read-discarded.png` show the discard notice. Evidence lines are in `.maestro/results/qa2-v1708-screenassist-external-read.txt` and `.maestro/results/qa2-v1708-screenassist-chrome-read.txt`.
- `npm test` passed 638/638; root/mobile/runtime typechecks, web export, and `git diff --check` passed. The source-map bundling task once produced a truncated intermediate map; an isolated forced bundle rerun produced complete valid maps, after which the APK assembled successfully.
- ACA Flows 17/18 reached only the local connect-account gate because this APK uses `https://aca-unconfigured.invalid`; no emails were read. No message was sent and no Sarvam key was used. The physical Infinix was not used.
- Remaining: AI-initiated, owner-reviewed external tap/scroll; live ACA mail/provider synthesis; ACA realtime voice route; and release-signed/public APK build.

## 2026-11-08 — Native one-shot external actions and verified multi-step continuation (Development)

- Reconciled Development after the owner's security update/force-push and preserved startup supply-chain hardening. The prior source and Voice/WhatsApp reviewed-draft work is retained; `main` was not changed.
- Added `NexaApprovedExternalAction.kt` and migrated reviewed Chrome, camera, SMS, WhatsApp direct drafts, manual messenger launch and launcher handoffs to exact resolved Android components, permanent `app_launch` journal claim, owner Pause/Voice Stop dispatch fence, and no automatic retry when uncertain. Back and notification-shade gestures now use `nav_back` / `nav_notifications` audit kinds and recheck their window or service inside the same fence. Launcher swipes check unchanged launcher context at final dispatch.
- Enabled **native-only** exact visible-package observation (bound to the original owner Pause generation) after an accepted Chat app launch, allowing the existing bounded sequential planner to advance only when the actual destination is observed. Removed the older separate React/JS package reread because it could elevate a `not_observed` result after owner Pause/Resume. Unverified launches stop further tool execution.
- Added Android synthetic no-launch/no-gesture tests and JavaScript action-journal and native-observation boundary regressions; adapted legacy assertions to preserve the explicit no-Send guarantee. The pinned upstream Maestro source is compiled through the existing portable module; missing submodule inputs now cause a clear Gradle preflight error instead of misleading Kotlin import failures.
- Exact verification checkout SHA `a2c4ec378e9385f68edd2d77fbea83148830ba29`: **421 Node tests passed, zero failed; TypeScript and web export passed; Android app and AndroidTest Kotlin compilation, debug APK + test APK assembly, and Maestro portable JVM unit tests passed**. Built artifacts are disposable locally signed DEBUG binaries; no signed release promotion or deployment. Instrumentation tests on a running phone/emulator **not** completed: Windows/Infinix BridgeHead unavailable and Linux API35 emulator crashed at startup (139).
- Current dependency audit: 2 high, 7 moderate, 1 low; Expo 58/CLI/Zod3 and glob upgrades require evidence-based compatibility testing. No unreviewed forced Expo downgrade, automatic Send or deployment of any production service. General parent Kotlin executor across Voice/Chat/overlay, live Sarvam managed-tool acceptance, physical device UI/voice, signing and rollback remain NO-GO. Details and owner acceptance gates: `docs/project-control/NEXT_ACTION.md`.

## 2026-11-07 — Multi-tool owner-goal AgentHarness integration (source development)
- Implemented a bounded sequential **Chat** planner in `packages/agent-core/src/owner-sequence.ts`: up to six exact literal owner commands, including installed-app launch, safe visible-label tap, directional scrolling, exact reviewed text field insertion and device diagnostics. Refuses unsafe/ambiguous partial plans; preserves quoted text; no Send or Submit action. Each step has a stable call ID within one existing Android-journal-backed `AgentHarness` session.
- Existing Chat approval/Resume UI and native `AgentJournalModule` now pass an exact approval fingerprint to Kotlin. `NexaAgentActionAudit.reserveReviewedUiStep` requires the already-consumed one-shot audit receipt, exact session/call/tool/arguments, an unexpired native approval and the unchanged owner Pause generation. It records one irreversible `chat_native_ui_reserved` event before any external Accessibility gesture. Legacy independent screen/gesture journal still enforces its own one-shot claim. No new executor service, raw screen recording or provider-created authorization.
- Reused existing `NexaScreenLabelTap`, `NexaScreenScroll`, `NexaTextInputActions`, and `NexaUiActionVerifier` from a new Android Chat bridge. A native result is required before the next step: exact visible launched package for app launch, non-sensitive UI-change evidence for tap/scroll, and exact text visible in one uniquely grounded empty safe field. Observed UI change is **not** recipient verification or business success.
- Added Chat Tools opt-in capabilities for reviewed `device.interact` and safe `device.observe`, synthetic Node harness/policy tests and native mandate replay/pause tests. First integrated stage `3497c47`: 405/405 Node tests, TypeScript and Kotlin+Android-test compilation **passed**. Later security-hardening stage at `1617875`: 405/405 Node and TypeScript **passed**. Final native compilation on the security-hardening SHA and real device runtime remain separate gates.
- Scope limitations: this is NOT a free-form autonomous LLM-authored multi-app task planner, not all overlay/continuous Voice actions route through this Chat session, and foreground Android/Screen Assist may prevent continuation across switched app windows. Never treat a dispatch as completed navigation or delivery. No production Docker, DB, signed APK, `main` promotion, or message send was authorized.


## 2026-11-07 — Exact-source Android debug APK assembly and emulator boot diagnosis
- The isolated AlmaLinux checkout fast-forwarded to Development `d8a458c84da2`, including the new Pause generation fence and the concurrent Maestro QA changes. On that source, `npm test` passed **399/399**, `npm run typecheck` passed and `npm run web:export` completed.
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` passed at `70eab05` (153 actionable tasks); after the initial debug APK assembly exposed an absent *local* `app/debug.keystore`, a disposable isolated **debug-only** key was generated without touching CI/release credentials. The repeat `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` then reported **BUILD SUCCESSFUL** in 1m 42s (243 tasks).
- The resulting local debug/test APK hashes were `8bbfdbde160cfa0e5dfd9644da3162068cb3b0e051a7d29260ad7e4fc37af435` and `f49bcd3adfdde2e66872916f94086efd4711bd37c09f3442dc03bb1557aedcd5` respectively. Neither is a CI-signed consumer release artifact.
- An Android API 35 emulator was launched twice on the home server, first with SwiftShader indirect graphics, then with graphics/Vulkan disabled. Both native emulator processes exited **139** before boot completed. ADB never retained a booted device; therefore the new `NexaNativeActionSafetyAndroidTest` **was not executed on a device**. The earlier signed Infinix/device acceptance recorded in the other entry applies to an older source commit and does not certify this milestone.
- No source files, Docker containers, production databases, `main` ref, or external messages were changed by these local build/boot operations. The disposable debug key remains outside Git, and no release authorization is implied.


## 2026-11-07 — Generation-scoped reviewed Android action dispatch
- Implemented and committed the native owner-Pause generation fence across reviewed external text input, exact message-field insertion, icon taps, semantic label taps, scrolls and launcher swipes, uniquely grounded WhatsApp chat rows, and approved app launching.
- The final Android effect uses `NexaNativeActionSafety.dispatchWithFence` under the same native lock as Pause, preventing dispatch after a stale approval (even after Resume). It supplements, never replaces, the existing permanent one-shot SQLite action claim. No message Send, model-granted approval, multi-step auto executor or new permission was enabled.
- Added `NexaNativeActionSafetyAndroidTest.pauseAndResumeInvalidateEveryStagedNativeActionFence` and Node source-contract regressions. On combined Development `d8a458c84da2`, 399/399 Node tests, TypeScript and Expo web export passed; app and instrumentation-test Kotlin compilation previously passed on `70eab05e1f83` (BUILD SUCCESSFUL, 153 tasks).
- Fresh QA clone cannot assemble a debug APK without its untracked debug keystore; this is a local test-signing requirement, not a source compile failure. A disposable debug-only signing identity is used for the isolated rebuild; real release signing remains CI-only. The exact-build APK and new on-device test result must be recorded separately. No `main` promotion, no production Docker mutation or third-party message delivery.
- Upstream code review pinned LangGraph.js `03981062e9` and Deep Agents JS `2160d187f5`; no upstream implementation code copied and no additional agent execution loop was added. Remaining general-purpose Android task scheduling, observed multi-screen execution, provider-backed voice and current-SHA device/release certification remain separate.
## 2026-11-07 — Physical Infinix exact-build Maestro acceptance completed
- On Infinix X6831 with signed APK `d8a458c84da2c232377c0a559d766c81ea2c1096` (version `0.3.1800066544`), the fourteen main flows passed together in 12m 44s and the four experimental flows passed together in 3m 29s. No message was sent.
- Maestro 2.12.1's phone driver initially died at `deviceInfo`. Extracting `maestro-app.apk` and `maestro-server.apk` from its local `maestro-client.jar`, installing both with ADB, and running `maestro test --no-reinstall-driver` made the driver stable. Flow 01 passed individually before the ordered suites.
- After the suites, later `clearState` calls had disabled Screen Assist. Reran experimental flow 10 last through Android Settings; then stopped the lingering `dev.mobile.maestro` process. Android reported Screen Assist enabled and bound, overlay grant allowed, and Nexa foreground. Maestro's active driver temporarily suppresses other Accessibility services, so the post-run bound check is the acceptance evidence.
- These 18/18 UI results certify the bounded flows on `d8a458c`, not the newer Development commits pushed concurrently. Broad autonomous navigation, actual third-party text insertion/send, and persistent provider-backed voice remain separate feature gates.

## 2026-11-07 — Exact-current signed build installed and exercised on Infinix
- Rebasing concurrent Development safety-fence commits preserved the Maestro QA edits. Workflow `42278234419` succeeded for `d8a458c84da2c232377c0a559d766c81ea2c1096`; artifact `13534667449` passed SHA-256 and release-certificate checks. APK hash `7d7d1fb0e5d14b4712e5dff315c7d767265fbef65885f88545f1e97a20c0179d`, version `0.3.1800066544`.
- Installed this exact APK on Infinix X6831 via ADB without clearing app data. Android reported Screen Assist enabled and bound. From Chat, `Open notification bar` required Approve once and Resume; the resulting UI hierarchy belonged to `com.android.systemui`. No notification text was stored. After enabling Nexa's App launching switch in Tools, `Open Clock` required Approve once and Resume and the observed foreground activity became `com.transsion.deskclock/.DeskClock`.
- Maestro's phone-side `dev.mobile.maestro` driver repeatedly died at `deviceInfo` before any YAML command, including after ADB restart, driver reinstall and closing test-related background apps. Severe phone memory pressure was observed (68–82 MB free during failures). These are test-runner startup failures, not app flow verdicts.
- An API 37 emulator booted with working DNS, but host free memory fell below 1 GB; it went offline during installation of the new APK. The 14/14 main and 4/4 experimental emulator passes remain evidence for the earlier `fbda034` APK, not the new `d8a458c` build. Full suite acceptance for the exact new APK remains open.
- No external message was sent. Continuous observed multi-step app control, automatic recipient/compose/send, and persistent live voice were not proven by these tests. The exposed Sarvam key was not used.

## 2026-11-07 — Current signed APK passed emulator suites; upgraded physical phone
- GitHub Actions signed release-test workflow `42231566298` succeeded for Development `fbda03447d44072b1124f294fed22f810038086b`. Installed version `0.3.1800066237` on emulator and Infinix; the APK artifact hash matches `3f9ee0f0d326957037068016f799e9f0b9dfd4f19884dc1549547139425cfd3d`.
- Fixed emulator DNS and reran all fourteen main Maestro flows together: 14/14 passed in 17m 57s. Four experimental flows passed together: 4/4 in 4m 6s. Chrome is stopped after its search result to prevent delayed activity transitions; the email gate corrects an observed duplicated IME character before sending its local request; installed-app acceptance uses Clock to avoid Calendar's account setup.
- On the Infinix, ADB install -r succeeded without removing app data. Screen Assist was enabled and bound. Used the phone UI to send `Open notification bar`, approved once, resumed, and observed `com.android.systemui` in the UI hierarchy. No notification content was stored and no external message was sent.
- A full phone Maestro attempt failed all fourteen flows in under one second because the Maestro device server died at `deviceInfo`, before executing YAML. An isolated retry after ADB restart and driver reinstall failed the same way. The phone was under severe memory pressure; force-stopping Chrome, Clock and the stale test driver raised free memory temporarily but did not fix startup. This is a phone test-harness failure, not a verified app regression.
- The experimental scripts cover messaging app choice and reviewed input preparation only. They do not verify autonomous app navigation, actual third-party input insertion, sending, or continuous voice. Sarvam live provider tests were not performed with the key exposed in chat.

## 2026-11-06 — Dynamic Chat app lookup and emulator build
- Observed the installed signed APK reply to `Open Calculator` with a fixed native-tool list and no launch. Changed Chat intent admission, direct-provider schema, bridge validation, and Android launch execution to resolve a uniquely named installed launcher app instead of a fixed WhatsApp list.
- Added an exact-label Android instrumentation case and a Calendar Maestro acceptance flow. Native debug compilation and APK/test-APK assembly passed; the test APK passed five focused instrumentation checks. Full Node suite passed 396/396 and TypeScript checks passed.
- Corrected two stale source tests that expected Screen Assist to stay bound while Maestro ran. The experimental flow grants the OS setting; the bound-service and real shade checks run after Maestro releases Accessibility.
- Installed the new local debug APK on emulator-5554. Metro connection was repaired, but React Native stayed on a blank screen after logging bundle load, so Calendar UI acceptance is still open. The previous signed release-test APK was removed from the emulator to install debug. No message was sent and no Sarvam key was used.

## 2026-11-06 — Main Maestro phone and emulator acceptance continuation
- The main 13-flow Maestro suite passed together on Android API 37 emulator (13/13, 9m 8s) after three test-script corrections. Every flow also passed on the physical Infinix X6831 across short batches and isolated reruns; the final repeat of flows 03 and 14 passed together (2/2, 1m 43s).
- Flow 06 handles Android's runtime microphone and notification dialogs before asserting offline Hey Nexa listening. Flow 09 tolerates Chrome's first-run tips dialog during browser prewarm while still checking the real owner-approved search result. Flow 14 now checks `Awaiting owner review · no action started`, matching the installed app's review-only state.
- The uninterrupted physical 13-flow attempts were interrupted by Infinix App Lock, one Maestro driver startup timeout, and later a notification-shade diversion plus runner tap stall. The affected scripts passed on the phone in shorter runs; no app crash was observed in the checked AndroidRuntime fatal log.
- No external message or email was sent. These edits change only Maestro scripts and project status notes; the signed installed APK still matches application source `7903f9b6b37edfcaa42fd050fc9a666906c22499`.
- Restored the Infinix Screen Assist grant after the clear-state tests via the owner-visible Android Settings flow. Final check: service enabled and bound, overlay permission allowed, Nexa foreground.
- The clear-state flows reset Nexa's local app data. Live Sarvam testing was not performed; future provider acceptance needs owner configuration with a rotated key.

## 2026-11-06 — Experimental Maestro flows passed on physical Infinix
- Ran all four experimental Maestro flows in explicit order on Infinix X6831: 4/4 passed in 3m 20s. WhatsApp review, ambiguous message channel choice and generic text input remained unsent; the fourth flow granted Screen Assist through Android Settings.
- Adapted flow 10 to Android 17's full-control dialog and Infinix's direct Screen Assist detail route. Infinix lists overlay grants as inline switches; the production Nexa overlay grant was already on. Maestro temporarily suppresses other Android accessibility services, so the post-run check verified Screen Assist enabled and bound plus overlay permission allowed.
- Outside Maestro, entered `open notification bar` in Nexa Chat on the physical phone. The action required Approve once and Resume saved task; after both owner steps, Android's `com.android.systemui` notification shade became foreground. Temporary screen dumps were removed; no notification content was kept in project artifacts. No messages were sent and no provider key was used.
- The same signed release-test APK for source `7903f9b6b37edfcaa42fd050fc9a666906c22499` is installed on the emulator and Infinix. GitHub Actions debug compile `42138469241`, Android API 35 acceptance `42139418393`, and signed release-test build `42140849123` passed. This is a Development release-test APK, not a public store release.
- The final cross-device YAML also passed all four flows together on the API 37 emulator (4/4, 2m 53s). Maestro-only changes to flow 10 and its experimental config remain uncommitted.

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

## 2026-11-06 — Owner memory retention and exact cross-app text binding
- Extended encrypted Android recipient-to-messaging-app choice records to version 2 with owner_confirmed provenance, savedAt and expiry (1–365 days); legacy owner choices remain readable until explicitly replaced/forgotten.
- Added React Native metadata validation, provenance and expiry display, and owner-selectable 30/90/365-day retention for remembered app channels; Use once and Forget remain separate.
- Remembered channels still do not establish recipient identity or authorization to send messages.
- Closed an approved-text substitution gap: existing one-shot Message composer and generic text-entry approvals now bind the exact app, recipient/field label and message body in an ephemeral structured SHA-256 digest checked again after the draft is consumed at confirmation.
- Unmatched or replaced drafts fail closed before ACTION_SET_TEXT or the durable action claim. Existing Accessibility, screen-window, empty-field and recipient checks are preserved.
- Added Android instrumentation negative cases for different recipient/app/message and field/text payload replacement.
- No automatic Send, call, payment, arbitrary screen executor, main release promotion, or production Docker mutation.

## 2026-11-06 — Content-scoped durable Android UI action claims
- Extended the existing one-transaction permanent action claim and UI audit receipt to carry the exact reviewed-input SHA-256 digest for message and generic text entry.
- Both controllers now provide their ephemeral reviewed fingerprint at the irreversible claim boundary; unbound text-entry calls are rejected. A consumed `agent_step_approvals` audit row retains the binding without storing raw text or granting additional Android permissions.
- Added synthetic Android instrumentation checks for missing/malformed binding rejection, consumed approval identity, target digest, no replay, and no Send.
- All native on-device UI target, empty field and owner approval checks continue to run before the claim, and no content is sent to a model.
- Node 395/395 and TypeScript PASS at 79e77304c433; Android native instrumentation pending exact-commit results.

## 2026-11-06 — Android instrumentation follow-up
- Exact-SHA emulator run `42119945280` exposed five instrumentation failures on Development `1469be4`: three read-plan fixtures used owner goals that native policy correctly rejects, and two text-action outcome checks compared the reviewed-input digest to the generic gesture digest.
- Fixed the audit receipt to retain the reviewed text digest encrypted with the action arguments and validate that exact digest on outcome. The audit stores no raw field contents. Updated plan fixtures to use owner-authored battery/storage requests and verify that correction invalidates the prior draft.
- Current-source JS checks on `1469be4`: 395/395 tests, TypeScript, and web export passed. The new native fixes still require the sequential Android debug, emulator, and signed release-test gates on a fresh exact Development SHA.

## 2026-11-06 — Shared Kotlin app-launch dispatch and redacted screen observation
- Added fresh read-only Accessibility control metadata through `NexaTaskCoordinator`, shared with existing device facts. Chat can request `device.visible_controls` with owner approval; no text-field values, screenshots, coordinates, screen gestures or external submissions.
- Added owner-trusted `app_launch` to the existing permanent Android claim, consumed per-step audit receipt and original Chat / Voice provenance. Kotlin revalidates installed app label and package immediately before Intent dispatch.
- Routed both Chat and Voice approved app launches through the same native dispatch gate. Intent acceptance is explicitly unverified; unknown outcomes cannot be retried automatically.
- Added synthetic Android policy/claim tests and retained the existing Keystore-backed custom.db v3 schema. No new privilege, server database, Docker deployment, production send or `main` promotion.
- Node 396/396 and TypeScript passed after the safe screen-read implementation. Final Android compilation must be checked for the exact current SHA.

## 2026-11-06 — Durable owner emergency stop and pause-revocation generation
- Added `NexaNativeActionSafety`: local persistent owner pause flag plus monotonic Pause generation. Pausing blocks newly claimed external actions, and a Voice approval offered before Pause cannot be consumed after Resume.
- Enforced native stop at the UI claim, legacy Voice step receipt, Chat launch/camera/browser/shade, Voice tool dispatch, overlay app launch/launcher navigation, Android global Back/notification, and flashlight ON paths. Turning flashlight OFF remains possible.
- Added foreground unlocked owner Pause/Resume in Nexa Preferences and a one-way Pause button in the floating overlay; the overlay shortcut opens owner safety settings. Pausing clears staged UI click/scroll/message/text targets and RN in-memory pending reviews.
- No Android Keystore or custom.db migration; irreversible action tombstones and approved-send requirements remain intact. Existing general task execution feature flags remain disabled.
- Added owner-pause Android instrumentation source and Node source assertions. Kotlin runtime/physical phone and rollback gates still require exact-commit acceptance.
- Recovery/limitations: OS actions already dispatched cannot be undone; the stop prevents new work but does not prove completion or implement the missing generalized task scheduler.
- Runbook: `docs/architecture/NEXA_OWNER_ACTION_PAUSE_RUNBOOK_20261106.md`.

## 2026-11-06 — One-shot post-launch Android package evidence
- Added a package-only, delayed native Accessibility observation after an already approved app-launch Intent. It upgrades a claimed audit receipt only once and only for an exact visible destination package; no raw screen content is kept.
- Added Android instrumentation regressions for matching/changed/unavailable packages, one-time receipts, persistent audit events, and non-launch isolation. Full Kotlin compilation, emulator observation and physical-phone approval remain independent verification gates.
- No sending, generic execution, production services, or build triggers were enabled.

## 2026-11-07 - Maestro pinned-source and selector integration (isolated)
- Implementation worktree `feature/maestro-source-reuse-20261107` based on local Development `41daba78641a70f27f0a166fe0efb23a922462b6`. Original Development checkout retains its own untracked `.maestro/nexa/test_maestro.yaml`; no main merge or production deployment.
- Source link: `third_party/maestro-upstream` pinned to mobile-dev-inc/Maestro commit `aaa248d7fd4ca4924444dc027af9c7a62a9be348` under Apache-2.0. `scripts/maestro-source-check.ps1` checked the SHA, clean upstream and five-file allowlist; deliberate wrong-SHA test failed.
- New `:maestro-portable` Android library compiles the **original upstream** `Point.kt`, `Bounds.kt`, `TreeNode.kt`, `SwipeDirection.kt` and `ScrollDirection.kt` into ignored Gradle outputs; no copied upstream code in Nexa or dependency on Maestro host-side ADB, iOS, GraalJS or Selenium tooling. Existing Nexa AccessibilityService remains the only device execution service.
- Implemented a **read-only** selector over those upstream models: unique exact text/resource ID/relative-below lookup, package+window identity and bounded viewport/secure-field rejection. A rejected unknown enabled state was verified RED then GREEN.
- Evidence: portable JUnit started 3/3, then 8/8 and subsequently 9/9; Nexa Android app and Android instrumentation-test source Kotlin compilation passed with the portable library dependency. This is NOT proof that any on-device UI action was performed.
- Isolated `npm ci` restored the project-locked dependencies: `npm test` subsequently passed 399/399 after initial 382/399 from an unrelated parent node_modules dependency mismatch. CI Android workflows now fetch the submodule explicitly.
- Remaining: adapt selectors into the existing journaled tap path (without a parallel click executor), bounded `scrollUntilVisible`, generalized one-parent-task multi-step scheduling, voice/chat/overlay action ingress, real-device/Android emulator acceptance and safety/policy review. No automatic outbound sends or arbitrary app automation has been enabled.

## 2026-11-07 — Maestro selector connected to one existing Nexa tap path

- Isolated worktree `feature/nexa-maestro-controls-20261107` from local `Development` commit `baa94513084ce4cdf8d3f1d73174ffe930dabcc6`. Original checkout remains unmodified. The pinned upstream Maestro gitlink is still `aaa248d7fd4ca4924444dc027af9c7a62a9be348`, Apache 2.0.
- Added `NexaMaestroTargetSelection.kt`: converts only pre-filtered Nexa Android accessibility tap candidates into the five directly reused upstream Maestro Kotlin model classes, invokes the existing `MaestroSafeSelector`, and returns a unique candidate index or rejects ambiguous, secure, offscreen, disabled, changed-package/window candidates. It DOES NOT create a second service, executor, agent loop or journal.
- Extended `NexaScreenLabelTap.kt` to use that selector for non-editable candidates, keeping the existing explicit `targets.size!=1` rejection and the existing search-field focus exception. Before native one-time ACTION_CLICK it requires fresh matching foreground package/window, stable bounds and pause fence. No automatic retry or external message send.
- TDD: native instrumentation compilation RED (adapter absent), then Android main and androidTest Kotlin GREEN. Source policy tests initially exposed missing one-target guard (2 failures), restored guard and reran focused checks 7/7 GREEN. Complete Node suite 406/406 GREEN. TypeScript and web export GREEN.
- Android debug+androidTest APK assembled successfully with `-PpaaInstallDebugAlongsideRelease=true -PreactNativeArchitectures=arm64-v8a`. Verified .debug package ID, owner-matching SHA-256 signing certificate, APK SHA256 `CE84782E1C5053B4B9AE46FE6074000142B3B45AB7AFC420B2E8F7B14C66D0B3`. Phone was updated via `adb install -r -t` without uninstall/clearState. New `NexaMaestroTargetSelectionAndroidTest` passed 4/4 on physical Infinix X6831 Android 13.
- External Maestro CLI 2.12.1 on physical device passed a real Nexa Chat/Check battery flow after explicitly starting Metro and `adb reverse tcp:8081 tcp:8081`. This proves the PRIOR installed debug APK supported the battery task. An attempted floating-chat flow failed to locate `Show floating bubble` in Tools; the accessible hierarchy showed Tools features but no such control, so no floating-chat claim is made.
- Following the new APK upgrade and on-device synthetic test, the .debug package later disappeared while `.qa` and base package variants appeared. A later Maestro rerun correctly failed `Package ... .debug is not installed`; Accessibility service settings were null and overlay permission reset/default. No further install or permission bypass was attempted due possible concurrent device workflow. Exact-new-APK real-app UI effects therefore remain unverified.
- Added `.maestro/physical-debug/01-chat-battery.yaml` with `clearState:false` plus a documented runbook. Do not upload personal UI hierarchy/screenshot/logcat artifacts. Debug keystore and APK are ignored and not committed.
- Still incomplete: iterative scrollUntilVisible and per-scroll user approval, generic target grounding in the live screen snapshot (beyond prefiltered tap labels), durable cross-app multi-step planner/executor, recipient-safe verification, voice/overlay continuity, recovery and on-device owner acceptance. Maestro desktop CLI/ADB/UiAutomation cannot and should not be transplanted wholesale into Nexa's non-root APK.

## 2026-11-13 — Debug app recovery on Infinix
- Fixed the malformed in-progress JSX in `apps/mobile/src/components/NexaSetupScreen.tsx` by removing the unfinished camera-test button and its unused imports; retained the existing setup actions.
- `npm run typecheck` passed; `git diff --check` passed.
- Rebuilt `:app:assembleDebug` with `-PpaaInstallDebugAlongsideRelease=true` successfully and installed package `dev.rilandev.personalaiassistant.debug` on physical Infinix X6831 (ADB serial `099344034S003788`). Existing base package was preserved.
- Started Expo Metro and launched the debug package. ADB confirmed the device/application launch event and there was no matching fatal exception in the sampled logcat. Accessibility dump showed no bound services; enabled-service list contains the debug package but no Screen Assist binding. Therefore cross-app control is NOT verified in this run.
- Force-stopped the debug app as requested. No emulator started. No messages sent.
- APK SHA-256: `pending record`.

## 2026-11-13 — Maestro Android recovery and cross-app launch
- Root cause for Flow 10 navigation: the setup ScrollView bottom inset (30 px) was smaller than the sticky footer, so the optional-feature card could be present in the accessibility hierarchy while physically covered by the footer. Increased `NexaSetupScreen` content bottom padding to 160 px. Focused Maestro probe confirmed the optional feature card scrolls into view and expands to expose “One-time screen assistance.”
- Updated experimental Maestro flows to avoid blanket permission mutation that asks Android 17 to grant unsupported `ACCESS_LOCAL_NETWORK`; Flow 10 now targets the side-by-side debug build and handles an already-expanded optional section. Flows 11–13 continue against the stable QA app target because their existing selectors are validated against that layout. No message was sent.
- Full experimental workspace on the wired Infinix: 4/4 passed in 3m58s (Flow 10, WhatsApp owner review, ambiguous recipient review, named text-field review). Later repeat on debug-specific mixed targets showed the three review flows are package/layout dependent; final 4/4 result is from the stable package targets except Flow 10 debug.
- Additional existing `15-agent-installed-app-launch` scenario ran against the updated debug build. It requested “Open Clock,” presented `device.open_app` approval, resumed, and Maestro observed “World Clock”; no external communication occurred.
- Post-Maestro ADB accessibility dump: Screen Assist enabled, bound, and no crashed service. Maestro was force-stopped after the checks. Emulator remained off.
- `npm run typecheck --workspace @paa/mobile` and `git diff --check` passed. Debug APK SHA-256: `C393B1B1B0B113FE09B3339B82EBC844F11440D7366E9C3094CECB165E6DC68C`.
- Remaining acceptance: direct live Screen Assist read/scroll/tap on a third-party app is not yet evidenced by this suite; the confirmed cross-app action so far is approved app opening to Clock.

## 2026-11-13 — Screen-read continuation
- Tried to continue on physical Infinix serial `099344034S003788`; no emulator was started. Accessibility was initially off. Android Settings enabled the first same-label Screen Assist entry, which resolved to the `physicalqa` package. `dumpsys accessibility` then showed the service bound and no crash.
- ADB lost the Infinix after the Settings step. Restarting ADB and checking Windows present devices did not restore the Android transport. The connected USB composite device exposed HID keyboard/mouse interfaces only. No external-app screen read or Maestro flow was run after this disconnect.
- Reviewed `NexaScreenVoiceOverview.readVisibleTextApproved`: it requires the active service, reads one external window, excludes password/editable/invisible nodes, bounds labels/output, and returns an explicit read-only result. The implementation review is not runtime evidence. No messages were sent and no screen contents were retained.
- Updated `scripts/test/nexa-accessibility-connection-setup.test.mjs` to match the current Maestro setup flow's `extendedWaitUntil` plus consent tap instead of the removed `assertVisible` step. Focused test passed 7/7.
- Verification: `npm test` passed 415/415; `npm run typecheck` passed; `:app:assembleDebug :app:testDebugUnitTest` succeeded (`testDebugUnitTest` has no JVM source tests); `:app:assembleDebugAndroidTest` succeeded and compiled Android instrumentation tests.
- Built current local debug APK at `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk` for package `dev.rilandev.personalaiassistant.debug`, version `0.3.2026111301`, SHA-256 `E50DA7EFB5E31EA246C6F3EA8766B109A53DA76DDFD9BF44EC0C947B37E3CD64`. It remains uninstalled because the phone disappeared from ADB.
- Next: restore the physical USB ADB transport, install the APK, enable the `.debug` Screen Assist component specifically, verify it is bound after the runner exits, then verify one approved read on a safe external screen and rerun the ordered experimental flows. Keep all messaging unsent and keep the emulator off.
## 2026-11-07 — Bounded scroll-until-visible owner workflow (feature branch)

- Continued from the already-merged Maestro pinned-selector source, without cloning a second Accessibility service or creating a parallel executor.
- Added `NexaBoundedScrollSearch`: literal, owner-written "scroll down until Search" requests; four individually approved gestures maximum, 90-second lifetime, same package/window/Pause-generation binding, one consumed journal token per step, 4.5-second evidence deadline, no replay after ambiguity, context change, process loss, failed action or absence of OS evidence.
- Extended the existing `NexaTaskScreenObservation` with a short-lived, read-only, sensitive-label-filtered unique control check (never reads editable/password values). Extended existing `NexaScreenScroll.prepare` with an opt-in unique-scroll-container requirement so the bounded guide cannot select the largest of multiple independently scrollable containers. Home-screen swipe fallback remains available only for manually reviewed ordinary scrolling.
- Floating `NexaToolOverlayService` recognizes the owner request, revokes prior unconfirmed overlay/text/message approvals, prepares an existing `NexaScreenScroll` operation, and waits for a separate confirmation. It then reads the exact encrypted `NexaUiActionJournal` status after Android's scroll observation; the next scroll needs a fresh owner confirmation. A uniquely observed target is reported, never auto-tapped.
- RED: new Node source tests failed before implementation. GREEN: the first two focused Node checks passed after wiring the guide. A further unique-container and stale-approval regression was added. GitHub source invariants checked 8/8 after wiring; this is not Android runtime certification.
- Isolated Windows worktree: `work/nexa-bounded-scrollsearch-20261107`, source via GitHub only. `npm ci --ignore-scripts --prefer-offline` completed (517 packages), but submodule init/Android compilation was interrupted by loss of BridgeHead/Tailscale connectivity. ADB showed no connected devices during this work. Android instrumentation, full Node suite, same-SHA APK/real overlay acceptance **remain unverified**.
- No production service/container, user secret, `main` functionality, external message/call or Android permission was modified by this feature work.
- Independent SECURITY incident also discovered and repaired before running the source: maliciously obfuscated executable Metro/init startup code introduced at `9bf2c02` and absent .env ignores; isolated clean hotfix merged as PR #115 to Development (`37d9df3`) and PR #116 to main (`71138b8`). This does not prove whether old executed code accessed/transmitted any secret; credentials previously available during affected startup must be treated as exposed and rotated at their providers.

## 2026-11-07 — Physical-device verification resumed for bounded Maestro scroll search

- BridgeHead Windows and the previously authorized Infinix X6831 (Android 13) were reconnected. The feature worktree was fast-forwarded to the remote PR #117 head and original upstream Maestro submodule checked out at `aaa248d7fd4ca4924444dc027af9c7a62a9be348`.
- Verified from feature worktree: `npm test` **420/420 passed**; root/mobile/agent `npm run typecheck` passed; `npm run web:export` passed; pinned `:maestro-portable:testDebugUnitTest` **9/9 passed**.
- The initial unrestricted Android build failed due to Windows Ninja/clang process access denied for `armeabi-v7a`. An arm64-only build then failed because the isolated debug keystore was not located. Once the existing ignored debug keystore was confirmed, `:app:assembleDebug :app:assembleAndroidTest -PreactNativeArchitectures=arm64-v8a -PpaaInstallDebugAlongsideRelease=true` **passed**. The app package was `dev.rilandev.personalaiassistant.debug` (60.15 MB), with a separate test APK (532,949 bytes).
- Installed both APKs on the authorized device using `adb install -r -t --no-streaming`, with no uninstall or application-data clear. Android accepted the in-place debug upgrade. Existing Screen Assist and floating-overlay permissions remained enabled.
- Real device instrumentation: `NexaBoundedScrollSearchAndroidTest` **6/6 passed** (bounded owner approvals, observation, expiry, context, pause and unique target), plus `NexaMaestroTargetSelectionAndroidTest` 4/4 and `NexaMaestroPortableBridgeAndroidTest` 1/1: **11/11 physical-device synthetic tests passed**.
- Started Metro in the isolated clean worktree and established ADB reverse to `tcp:8081`. Host-side Maestro CLI launched the debug app but the read-only Chat/battery flow failed the `Chat` visibility assertion. Local hierarchy included `com.android.systemui` and the lock-screen service; Android reported `deviceLocked=1`, sleep state and NotificationShade foreground. This is a lock-screen/environment blocker, **not evidence of a Chat feature failure or a successful real-app task**. No device lock bypass or permission modification was attempted.
- This milestone does **not** certify a live third-party-app scroll, floating Chat action, full multi-step voice planner, or any automatic outbound message. PR #117 should stay unmerged until unlocked owner-side live acceptance passes. Keep raw screenshots, UI hierarchies and private phone logs local.

## 2026-11-08 — Maestro relative-selector integration and guarded floating tap (PR #117)

- Inspected `mobile-dev-inc/Maestro@aaa248d7` source (`Filters.kt`, `ViewHierarchy.kt`, `UiElement.kt`, `ElementSelector.kt`, `Orchestra.kt`, Android input service, MCP inspection/testing) against the Nexa coordinator, native label tap, scroll controller and owner-review overlay. New changes directly reuse pinned `TreeNode` / `Bounds` in the existing portable module; relative-selection behavior is an **adaptation**, not a copy of Maestro Filters.
- Added `MaestroQuery` support for unique visible `aboveText`, `leftOfText`, `rightOfText`, `childOfText` ancestor matching, and explicit 1–100% viewport visibility; default 100%. New JUnit cases reject ambiguous/missing/sensitive anchors and contradictory constraints. Live native relative tapping supports the four geometry relations, **not** arbitrary nested parent-of controls.
- Extended the original read-only native `NexaMaestroTargetSelection` adapter with non-action anchors. Added `NexaMaestroRelativeTap` literal owner command parsing/strict action roundtrip. Floating Chat presents the same one-shot owner review before calling `NexaScreenLabelTap.approvedRelative`; the preexisting Keystore journal and native Pause generation continue to guard all clicks. Duplicate anchors, editable controls, stale window/coordinates, sensitive words and malformed commands reject rather than guessing.
- Tightened a newly discovered fail-open parsing path: a malformed relative request can no longer fall through to the broad label tap parser. During final native dispatch the previously observed unique anchor must still be present at the reviewed bounds on a fresh current-window root. No generalized model/ADB/UI gesture authority added.
- Added source-first regressions: 5 new portable JUnit cases, 2 native adapter instrumentation cases, 4 owner grammar cases and 6 Node integration checks. GitHub source invariant inspection passed **13/13** before the final malformed-command hardening.
- A separate ephemeral local `kotlinc` smoke harness mirroring the selector and strict owner grammar compiled and passed six geometry/hierarchy checks, a grammar roundtrip, four relations and ten negative examples. **It was a manually recreated source harness, not exact revision Gradle or Android proof.**
- BridgeHead Windows `nextjs-ssh-runner` at `100.66.34.109:22` repeatedly returned `PEER_UNREACHABLE` (Tailscale/SOCKS5). New source cannot yet be installed/tested on the physical Infinix. Previous 420 Node, 9 library JUnit and 11 physical synthetic Android passes belong to **earlier code**. No latest-source build or real floating relative-tap pass is claimed.
- No production deployment, OS permission change, outbound message, automatic Send or `main` merge. Latest branch is retained as draft PR #117. A comprehensive source provenance matrix was added at `docs/architecture/MAESTRO_SOURCE_REUSE_EXECUTION_20261108.md`.

## 2026-11-13 — Real Windows + physical phone verification and chat-row identity fix

- Windows BridgeHead `nextjs-ssh-runner` restored; unchanged main checkout contains a user-owned uncommitted `NexaSetupScreen.tsx`. All source changes were made through GitHub, and tests/builds used isolated `work/nexa-bounded-scrollsearch-20261107`. Pinned Maestro submodule remained at `aaa248d7`.
- Removed a **reintroduced obfuscated startup/Metro payload** and three executable `init` loaders from PR #117 at `95e87c9`, restoring scripts, `.gitignore` and Metro config to clean Development values. Treat credentials exposed to previous execution as potentially compromised.
- Fixed stale native overlay `origin` arguments at `ecd3a22`, making `:app:assembleDebug :app:assembleAndroidTest` compile again.
- Merged newer Development's independent messaging and timeline changes into PR #117 at `2206e51` without overwriting unrelated files; continued Development concurrency needs a final recheck.
- Verified on the isolated worktree: 434 then 436, finally **438/438** Node tests passed, root/mobile/runtime typechecks passed, Expo web export passed, Maestro portable JUnit passed, and arm64 app plus instrumentation APKs built successfully. Previous native synthetic safety instrumentation **17/17** passed on the physical Infinix.
- In-place debug APK and instrumentation APK installs used `adb install -r -t --no-streaming` on owner's Android 13 Infinix X6831 with serial ending 3788; no `pm clear`, uninstall, real message send or OS permission edits.
- The real Maestro CLI no-clear-state Chat → Check battery read-only flow **passed in full** on the physical phone (one transient gRPC device-server failure followed by successful retry).
- Broader Android fixture instrumentation initially failed due `UiAutomationService already registered` / disconnected; added test-only non-suppressing UI Automation accessor `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` (commit `95a4cb9`). The host emulator attempt was aborted because it exhausted Windows memory; only spawned emulator test processes were stopped.
- A separate physical fake-WhatsApp fixture intermittently counted two candidate rows from repeated Accessibility representations of one clickable row. Added source/regression and conservative `NexaWhatsAppChatActions.sameSourceOrAncestor` identity or bounded direct-ancestry dedup (commit `3f681bb`); two distinct sibling contacts remain ambiguous. Compiled Android APK successfully and installed in place; **three separately run physical synthetic scenarios passed**: single row → opens synthetic chat and fills text without Send; duplicate rows → no target; one-letter speech variation vs wrong name. These are not real WhatsApp acceptance.
- Android currently reports **no enabled Nexa Accessibility service** and no active overlay grant on the test packages. Full external floating-chat scroll/tap cannot be called accepted without normal owner-side permission grants. No Android security settings were changed.
 
## 2026-11-13 — Physical experimental suite and Screen Assist bind recovery
- On the wired Infinix X6831 only, the ordered experimental Maestro suite passed 4/4 against debug APK `0.3.2026111305` (Flow 10–13). The flows never sent a message. Node tests passed 420/420 and TypeScript passed.
- Built and installed debug APK `0.3.2026111306` (SHA-256 `3213176E566396396B9AADA4F374DB9D9705B51DA3713CDC40E2F0B406CC8E3`) with debug-only service lifecycle diagnostics. Flow 10 passed on this build.
- Root cause of the apparent post-flow unbind: Maestro's `dev.mobile.maestro` instrumentation remained active after the flow process returned. Android still reported a global `Ui Automation` connection, suppressing normal Accessibility service binding. Stopping only the stale Maestro runner removed Ui Automation; Android then reported both Screen Assist service instances (`.debug` and `.physicalqa`) bound with no crashed services.
- Direct one-shot external-app screen reading remains unverified. Current app is at Chat; the next action is an owner-confirmed tap to open Tools, followed by a separately confirmed tap to arm a single read. If approved, open the stock Calculator, return to Nexa, and verify capture succeeded without exposing any screen text. Do not send messages or use the emulator.

## 2026-11-13 — Emulator continuation after Infinix disconnect
- The Infinix was unplugged by the owner; all subsequent device testing used only `emulator-5554`.
- Installed debug APK `0.3.2026111306` on the API 37 AVD; package metadata confirms version code `2026111306`. APK SHA-256 remains `3213176E566396396B9AADA4F374DB9D9705B51DA3713CDC40E2F0B406CC8E3`.
- The full experimental Maestro batch remains green on the physical run (4/4; flows 10–13). On the emulator, Flow 10 failed at its initial UI assertion before settings changes because Android System UI/launcher repeatedly ANR'd. Rebooting the AVD preserved data and APK but did not clear the SystemUI ANR. No message was sent.
- Screen Assist's one-shot Arm action succeeded on the latest debug app on the Infinix and Calculator opened, but capture review did not provide a verifiable success result. No captured text was emitted. Keep direct cross-app reading marked unverified.
- Current blocker is the emulator's SystemUI ANR dialog. The owner previously requested confirmation for every tap/scroll; a one-time confirmation request to tap the dialog's `Wait` control is pending. Do not interact with the dialog until confirmed.

## 2026-11-14 — Physical Infinix experimental suite and Screen Assist read
- Ran the ordered experimental Maestro workspace on Infinix X6831 (`099344034S003788`), Android 13, using the installed isolated QA2 build `dev.rilandev.personalaiassistant.physicalqa2` version `0.3.2026111406`: **4/4 flows passed** (Flows 10–13). Evidence is in `.qa-results/maestro-physicalqa2-ordered-final/2026-11-14_005223`. Flow 10's standalone JUnit result reports one testcase, zero failures. Flows 11–13 only staged/reviewed text; no message was sent.
- Verified Screen Assist enabled and bound in Android, with no crashed service. On the phone, armed a one-time capture, opened the OEM Calculator (`com.transsion.calculator`), returned to Nexa, and reviewed capture metadata. Nexa reported local capture from that package. The screen text was not sent to AI and is omitted from this log. This demonstrates one external-app read, not general arbitrary-app control.
- Current installed QA2 APK hash verified by pulling the installed package: `759CE602105C3458978B27FF140F677B59D7F38720733A5C2342C8233DF5FA8E` (matches `docs/qa/NEXA_SARVAM_READINESS_RESPONSE_20261114.md`). It was already built/installed in place before this continuation; no release APK was produced here. `.debug` build output remains an older artifact (`0.3.2026111306`) and is not being represented as the installed QA2 build.
- `npm ci` passed (509 packages added; audit reported 0 vulnerabilities), full `npm test` passed **489/489**, `npm run typecheck` passed for root/mobile/runtime, and `npm run web:export` passed. No source edits were made in this continuation.
- Owner approved one individual Calculator scroll to extend external-app interaction evidence; that scroll is the next device action. Request separate confirmation before any tap. The Sarvam key was not used; no model or provider calls were needed. No message was sent.

## 2026-11-14 — Follow-up scroll result and Android permission state
- Completed the separately approved single upward swipe in the OEM Calculator. Before/after device screenshots show the Calculator screen did not visibly change; Calculator has no scrollable content on that screen, so the swipe is **not** evidence of a successful dynamic scroll and no scroll pass is claimed.
- During the attempt, Android Accessibility's global flag went to `0` and the enabled-service list became empty. Android Settings showed Nexa Screen Assist off afterward. The direct one-shot Calculator read from earlier in this session remains valid evidence for that earlier state; the current state no longer permits a new Screen Assist observation.
- Latest installed QA2 app metadata remains version `0.3.2026111406` (`2026111406`); no APK was rebuilt during this continuation. The previously pulled APK hash is recorded above and in the Sarvam readiness note. The desktop has no Maestro CLI available, so Flow 10 was not rerun after Accessibility turned off. Do not change protected Accessibility settings through `settings put` or claim current bound state.
- The four ordered Maestro flows and repository checks remain passed as recorded above. Pending: restore QA2 Screen Assist through the approved Android Settings/Maestro path when the runner is available, then choose a genuinely scrollable safe test app and verify a single approved scroll via fresh one-shot metadata. Get separate approval before any tap. No messages were sent and Sarvam was not used.

## 2026-11-14 — Current-source QA2 rebuild and ordered physical suite
- Installed Maestro CLI 2.12.1 from the official Windows release archive. Java 25 is available. The temporary install is under `%LOCALAPPDATA%\Programs\Maestro`.
- Maestro initially found the installed QA2 app package absent on the phone, so Flow 10 could not start. Rebuilt the current local source with `PAA_BUILD_VERSION_CODE=2026111407`, `-PpaaIsolatedPhysicalQaV2=true`, and arm64-only ABI; build completed successfully.
- Verified APK identity `dev.rilandev.personalaiassistant.physicalqa2`, version `0.3.2026111407`, Android Debug signer SHA-256 `a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`, APK SHA-256 `BE5C100F560A3FAC0E80DECF0F0953D807C74E42FC439125F95FD7A359245501`. Installed with `adb install -r -t`; package/version confirmed on physical Infinix serial `099344034S003788`.
- Flow 10 passed alone (1/1); Android then reported Accessibility enabled and QA2 Screen Assist bound with no crashed service. The ordered adapted Flows 10–13 passed 4/4, zero failures, in `.qa-results/maestro-physicalqa2-v1407-ordered/report.xml`. No message was sent.
- The suite exit left Accessibility off; running Flow 10 alone once more at the end passed 1/1 and restored the QA2 service, which is currently enabled/bound with no crash. No emulator was targeted.
- Current overlay special permission is not granted. One approved Calculator swipe did not visibly change that app's screen and is not a scroll pass. Remaining live acceptance: enable floating overlay through Android's owner-facing settings (only after individually approved tap), then run a user-confirmed scroll on a scrollable external screen and verify the visible result. No Sarvam call or key use.

## 2026-11-15 — Live ACA 24-hour unread summary on Android emulator
- On `emulator-5554`, Nexa Settings showed the existing ACA Keystore session as Connected, revalidated the authorized targets, and showed a selected organization/agent. No password or token was read, entered, or reused.
- Submitted the exact Chat request `Summarize unread emails from the last 24 hours`. Nexa returned a successful read-only summary from `local_mail_database`: matched **0**, included **0 of 0**, rolling UTC window `2026-11-13T22:14:49.892Z`–`2026-11-14T22:14:49.892Z`.
- Oldest mailbox sync was `2026-11-14T22:14:37.712Z`, about 12.18 seconds before the report cutoff. The report warns that local sync freshness does not prove the upstream mailbox has no newer unsynchronized messages.
- No email subjects or bodies were present in the result; no email was drafted or sent. Sanitized result screenshot: `.qa-results/experimental-emulator-ordered-v1501-final/aca-summary-result.png`.

- Follow-up read-only request `Summarize emails in the last 24 hours` also returned successfully from the same verified session. Result: matched **0**, included **0 of 0**; UTC window `2026-11-13T22:18:38.173Z`–`2026-11-14T22:18:38.173Z`; source `local_mail_database`; oldest mailbox sync `2026-11-14T22:18:37.727Z` (about 0.45 seconds before the cutoff). No email content was copied to the log, and no write action occurred. Screenshot: `.qa-results/experimental-emulator-ordered-v1501-final/aca-all24-result.png`.

## 2026-11-15 — ACA mail-routing parity and emulator validation
- Source audit on ACA Development confirmed the web Live Chat fast path and Nexa mobile mail-summary endpoint share the same agent-scoped getMailReportEvidence query and report formatters. Live Chat saves the result in ACA conversation history; Nexa currently keeps its transcript local. The web path can include up to 500 report rows; the mobile endpoint caps at 50 and reports partial coverage.
- Nexa defects fixed: ordinary “recent emails” was not recognized; unsupported read scopes could fall through to the general planner; explicit mail writes could reach that planner; and the direct ACA read was incorrectly gated on a live BYOK model even though the API does not need one. Requests now route through the selected ACA device session without a provider, “recent” maps to a rolling 24-hour scope, unsupported mail reads are refused before querying, and draft/send/reply/forward/delete/archive/move requests get a read-only refusal.
- Test-first proof: the new regression tests failed before the implementation and pass afterward. Focused ACA tests passed 15/15; full npm test passed 547/547; npm run typecheck passed; npm run web:export passed.
- Built and installed local QA2 package dev.rilandev.personalaiassistant.physicalqa2, version code 2026111502, on emulator-5554. “Check my recent emails” returned a local 24-hour report (0 matched, 0 included); the exact unread 24-hour prompt also returned 0/0 with sync freshness. A sender-filter query was stopped without a mailbox query; an email-send request was refused with no action. No subjects or bodies were recorded; no message was drafted or sent. Evidence screenshot: .qa-results/aca-recent-mail-nexa-20261115.png.
- The normal local debug APK compiled, but could not replace the installed release-signed package because its signing certificate differs. It was not uninstalled or wiped. The QA2 package was used to preserve the existing installation. The physical Infinix was not targeted.
- ACA browser Live Chat comparison is still pending: the persistent ACA Read-only QA tab group remains at the login page. Do not reuse the password previously posted in chat; owner sign-in must be entered privately. Once signed in, run “Summarize unread emails from the last 24 hours” in ACA Live Chat and compare count, UTC range, sync freshness, and partial status without copying message bodies into logs.
- A fresh release-signed APK for this exact source is not available yet. Local gh auth status reports no GitHub CLI login, and no workflow-dispatch tool is connected; do not represent the QA2 debug artifact as a public/latest release APK.

## 2026-11-15 — ACA all-history unread follow-up
- After the owner said “proceed,” the persistent ACA browser session remained at the login page; no authenticated web Live Chat session was available.
- On the emulator QA2 build, Nexa’s read-only “Summarize all my unread emails” request returned 50 matched / 50 included, with full coverage for currently synchronized mailboxes and no partial-result marker. Oldest mailbox sync was 2026-11-15T03:08:39.466Z, about 10 seconds before the report cutoff. Message rows appeared only in the app; no subjects or bodies were copied into logs. No message write action occurred.
- Web Chat comparison remains pending owner sign-in in the ACA Read-only QA tab group. The exact 24-hour and all-history Nexa checks now both return ACA reports on the local QA2 build.

## 2026-11-15 — Bounded Nexa ACA mail reply
- Nexa was appending ACA's entire report directly into chat. For all-history unread requests, that can expose dozens of message rows and body excerpts in one reply and is unsuitable for read-aloud.
- Nexa now formats a deterministic brief: total and scope, at most two subject/sender highlights, a partial-coverage warning when needed, and the synchronized-mail caveat. It excludes body excerpts, mailbox addresses, links, and UTC timestamps from the reply; no email content is sent to a BYOK model.
- Added regressions for concise output, zero results, capped coverage, no body/address/timestamp leakage, singular counts, and App wiring. Focused ACA tests passed 20/20; full npm test passed 554/554; typecheck and git diff --check passed.
- QA2 APK rebuilt with version code 2026111503 (`0.3.2026111503`) and installed on `emulator-5554`; SHA-256 `461E9695E85C88BAA1FD108D541821BBF80309850629DDD38AE6D6B22E3BB523`. The physical Infinix and release-signed app were not changed. No mailbox was opened during this install.
- Scope limitation: Android's separate continuous realtime voice service still has no ACA mailbox route. The concise reply applies to the ACA Chat request path and read-aloud of that reply; do not claim continuous live-voice email access. ACA Live Chat browser comparison also remains pending private owner sign-in.

## 2026-11-15 — Human-style ACA email summaries
- Replaced the subject-only response for configured-provider use with a short AI synthesis of up to 20 sanitized ACA excerpts (maximum 450 characters per excerpt). The prompt requests 2–4 spoken-style sentences, treats email text as untrusted, and prohibits message-by-message recitation or invented facts/actions. Mailbox addresses, source links, received-at timestamps, and the full ACA report are excluded from the provider request.
- Exact matched/included coverage and the sync-freshness caveat stay outside model output. With no saved provider or on provider failure, Nexa retains the deterministic subject-only brief and clearly says why it could not summarize message content. Settings now disclose the excerpt transfer and provider billing/privacy implications.
- Verification: focused email-summary/presentation tests 11/11; full `npm test` 563/563; `npm run typecheck` and `git diff --check` passed. QA2 build succeeded and was installed on `emulator-5554`: `dev.rilandev.personalaiassistant.physicalqa2`, version `0.3.2026111505` / code `2026111505`, APK SHA-256 `1351830B87ED454CE5285F308EAE58A7F423444031EB84DBF3E8EE79825CE637`. The app reached the Chat screen; final UI tree showed the expected controls and no app crash was found in the captured startup log.
- Live model/mail synthesis was not run: the QA2 app header still says “Private local assistant,” so no Nexa BYOK provider is configured. No mailbox query or email-content transmission occurred in this verification. The update supports ACA Chat and the “Read last reply aloud” path; the separate continuous realtime voice service still lacks an ACA mail route. Physical Infinix and release-signed app were not touched.
- Against QA2 `0.3.2026111505`, the ordered experimental Maestro flows 10–13 passed **4/4** in `.qa-results/maestro-email-summary-qa2-v1505/report.xml`. Flows 11–13 left their message/text drafts unsent. After the batch, isolated Flow 10 passed **1/1** in `.qa-results/maestro-email-summary-qa2-v1505-flow10-final/report.xml`; Android then reported Accessibility enabled, Screen Assist in the enabled-service list, and a bound service with no crash.

## 2026-11-15 — concise human-style ACA summary refinement
- Tightened the configured-provider synthesis from a 70-word, 2–4 sentence prompt to a 30-word, 1–2 sentence gist. The composed Chat/read-aloud reply is capped at 60 words, states returned/reviewed coverage and ACA freshness briefly, and never appends ACA's full report.
- Added a regression test that first failed at 78 words for a verbose provider output; focused email and overlay tests passed 20/20 after the output cap. The full Node suite passed 565/565, typecheck passed, web export passed, and `git diff --check` passed.
- Built and installed QA2 `dev.rilandev.personalaiassistant.physicalqa2` version `0.3.2026111506` / code `2026111506` on `emulator-5554`. APK SHA-256: `76C962C5B041CE5252C946B9288A64AF60BFE537478AE2CF94665608DF310F20`. Chat launched and remained foregrounded with no crash in the captured crash buffer. Evidence is in `.qa-results/email-summary-qa2-v1506/`.
- A read-only Chat attempt for “Summarize all my unread emails” returned “ACA session expired. Reconnect in Settings.” No email report came back and no excerpt was sent to a model. The header shows “Private local assistant”; a saved BYOK provider is not configured. The human-style model output therefore remains unverified against live mail.
- Experimental Maestro flows 10–13 passed 4/4 on the prior QA2 `0.3.2026111505`; they were not rerun on `0.3.2026111506` because this refinement changed only the JS summary formatter/prompt. The physical Infinix and release-signed app were not targeted. `0.3.2026111506` is a debug QA artifact, not a public/release-signed APK.

## 2026-11-15 — ACA reconnect and exact-build emulator acceptance (QA2 v1513)
- Reconnected ACA on `emulator-5554` and submitted the read-only Chat request `Count my unread emails` from a fresh conversation on QA2 v0.3.2026111513. The response contained the normal mailbox-sync caveat and the deterministic no-provider fallback; the UI did not show an expired-session error. No email body or subject was copied into the work log, and no email write occurred.
- The saved Sarvam test credential was removed from device storage after the post-fix provider request returned a generic provider failure. The current live acceptance therefore confirms ACA retrieval and the short subject-only fallback, not configured-provider LLM synthesis. The sentence-count regression fix is covered by the full tests.
- The latest default APK output had been overwritten by another ARM64 build during the emulator run. Preserved the exact tested x86_64 install as `.qa-results/maestro-qa2-v1513/app-debug-v1513-x86_64.apk`; package `dev.rilandev.personalaiassistant.physicalqa2`, version `0.3.2026111513`, SHA-256 `497A104D6938A09D37D8FC5C8ABCDFF103D3323FFDFC720FD011123DD8A4506B`. Installed without clearing QA2 data and verified Nexa stayed foreground with no React Native crash.
- A preceding v1511 artifact lacked the matching React Native native library on the x86_64 emulator and crashed at launch. Rebuilt cleanly for x86_64 as v1513; the four ordered experimental Maestro flows then passed **4/4** (`.qa-results/maestro-qa2-v1513/report.xml`). Screen Assist control reachability passed 1/1 and standalone Flow 10 passed 1/1 after the batch. Post-run Android state: Accessibility enabled; QA2 Screen Assist listed as enabled and bound; Nexa foreground.
- Full serial `npm test` passed **576/576**; TypeScript checks and Expo web export passed. An earlier parallel validation run reported 575/576; the standalone rerun passed all 576. `git diff --check` is recorded after these status notes are appended.
- No message was sent. This is an emulator QA debug build, not a release-signed/public APK. The v1513 report and related verification are under `.qa-results/`.
- QA artifact retention: the failed pre-query and over-escaped-result-selector runs left Maestro screenshot/hierarchy files under `.qa-results/aca-v1513-live-reconnect/`, `.qa-results/aca-v1513-live-reset/`, and `.qa-results/aca-v1513-live-reset2/`. The latter can include the visible short subject-only fallback preview; no email bodies or credentials were copied to logs or notes. A separate temporary pulled APK copy remains at `.qa-results/qa2-installed-base.apk`: automatic approval review rejected its deletion with the only reason `blocked by policy`.

## 2026-11-15 — Sarvam overlay launcher swipe passed on physical Infinix (QA2 v1515)
- Fixed a bounded launcher-tree traversal defect: hidden/off-page Accessibility nodes could exhaust the 300-node budget before visible Home controls were reached. Traversal now prunes invisible branches and still fails closed when the visible hierarchy is incomplete; no coordinate guessing or approval bypass was added.
- Regression test was observed failing before the fix and passes afterward. Verification: targeted launcher-swipe tests 2/2; full Node suite 578/578; TypeScript checks; `git diff --check`.
- Built and installed QA2 debug package `dev.rilandev.personalaiassistant.physicalqa2` on physical Infinix serial `099344034S003788`, version `0.3.2026111515` / code `2026111515`. APK SHA-256: `341E40049D65B6264A3CCFDB9E6C5E7CA0B2790A892EDCB4130AC93075A70570`.
- Live acceptance: Sarvam planned “Scroll the current home screen to the left”; Nexa showed `Owner review required: screen.scroll direction=left`; owner approved that request once. Android dispatched one launcher scroll in `com.transsion.XOSLauncher`, then Nexa reported an observed screen change and stopped. The Home page indicator advanced from page 1 to page 2; screenshot evidence: `C:\Users\suajt\AppData\Local\Temp\paa-infinix-home-swipe-passed-v1515.png`.
- Final device check: Home launcher remains foreground; QA2 Screen Assist and Launcher Navigation are both enabled and bound; no crashed Accessibility services. No other app was opened and no messages, calls, contacts, or alarms were touched. Emulator was not used for this acceptance. This is a debuggable QA APK, not a public or release-signed build.

## 2026-11-15 — Physical QA2 live Metro mode enabled
- Added an explicit `paaLivePhysicalQaV2` Gradle switch. It requires the existing isolated `.physicalqa2` app identity, enables React Native DevSupport and skips embedding the JS bundle only for that opted-in debug build; normal/offline QA2 and release builds remain self-contained. Added a fixed-host Expo start command and `scripts/qa/start-physical-live-debug.ps1`, which verifies the selected physical serial/package, installs the USB reverse mapping, then starts Metro.
- Replaced the existing QA2 app in place with `adb install -r` (no data clear), version `0.3.2026111516` / code `2026111516`, package `dev.rilandev.personalaiassistant.physicalqa2`. APK SHA-256: `BB0C54F6CE0E8DCDB262A4299E6CC094440625C6E6C225E3D053D54F1FE36C48`.
- Live proof on Infinix `099344034S003788`: Metro served `apps/mobile/index.js` (777 modules). A source-only probe update appeared in device `ReactNativeJS` logs as `NEXA_LIVE_FAST_REFRESH_HOT_UPDATE_1516` while the app stayed installed; the probe was removed and the final source refresh was sent. No APK rebuild/install was used for the probe or its removal.
- Verification: targeted build-mode/startup tests 7/7; full Node suite 579/579; TypeScript checks; `git diff --check HEAD`. The Metro process launched through the new PowerShell runner remains active for continued QA. The emulator was shut down; ADB now targets the physical Infinix only.
- Iteration boundary: JS/TS changes now update through Fast Refresh. Kotlin, manifest, native resources and dependency changes still need a native build and one in-place install. No user data was cleared; no other app or external data was changed.

## 2026-11-15 — Live-mode Accessibility follow-up
- The live Metro/Fast Refresh acceptance remains passed. During recovery of the isolated QA2 Accessibility services, the existing Maestro setup flow enabled both QA2 components but ended with a selector failure on the Launcher Navigation detail switch. Android's final dump lists Screen Assist QA2 and Launcher Navigation QA2 as enabled, with no crashed services, but `Bound services` is empty; do not claim live Screen Assist control is currently ready.
- The phone's foreground changed to BOTIM `BRTCActivity` during that setup flow. No further phone input was sent after observing it; no call/message state was changed intentionally. The emulator was stopped again; ADB lists only the physical Infinix. Recheck binding and continue device actions only after the foreground state is safe.

## 2026-11-15 — v1513 emulator external-screen read acceptance
- Resumed emulator-only QA on `Medium_Phone_API_37.0` (`emulator-5554`), leaving the physical Infinix untouched. QA2 `dev.rilandev.personalaiassistant.physicalqa2` v0.3.2026111513 launched and Android reported Screen Assist enabled and bound with no crashed service.
- Navigated the live Tools screen and scrolled to Screen assistant, armed one local 20-second read, and launched Android Settings. Metadata-only log evidence records `package=com.android.settings nodes=120 captured=true`; the captured text was never displayed or sent to AI. Returned to Nexa and used Discard; the UI confirmed “Screen text discarded from memory.” Evidence: `.qa-results/qa-v1513-screenassist-read.log` and `.qa-results/qa-v1513-discarded.png`.
- ACA reconnect acceptance remains passed on v1513: the read-only unread-email request returned without an expired-session error, using the deterministic short fallback and sync caveat. No email write or message send occurred. Previous ordered Maestro Flows 10–13 passed 4/4 on this exact artifact; this continuation used ADB-driven visual navigation because Maestro is unavailable on the current shell PATH and repeated Android UIAutomator dumps had destabilized the AVD.
- Emulator remains running with Nexa Tools foreground. This verifies a direct, read-only external-app observation for Android Settings only; general arbitrary-app tap/scroll actions remain unverified. Configured-provider email synthesis, ACA web Live Chat comparison, realtime-voice ACA routing/shared history, and a release-signed/public APK remain pending.

## 2026-11-15 — conversation JSON and Android crash/ANR diagnostic review
- No exported Nexa conversation JSON was present in the project/QA artifacts or QA2 `cache/conversation-exports`. The phone has the private `custom.db`; source defines schema-v3 export containing conversation entries, tasks, agent tasks, and Android process-exit summaries. No conversation bodies were extracted or copied to logs.
- Saved Android crash reports and the latest Maestro device log contained zero `FATAL EXCEPTION`, ANR, or native fatal-signal markers. QA2 Android exit history returned only 16 `USER REQUESTED / FORCE STOP` records; zero crash and zero ANR reasons. These are controlled process stops, not evidence of app crashes.
- The latest Settings setup JSON trace has 36 commands and fails at step 36 because `android:id/switch_widget` is absent from the captured hierarchy. Android last reported both QA2 Accessibility components enabled, no crashed services, but no bound services. This proves a setup-flow/binding gap; it does not identify an app crash. The foreground had changed away from Settings, and the physical ADB serial later disappeared, so no further device UI action or binding verification was attempted.
- Next: obtain the actual local JSON export when the phone is safely available; correlate entry/session timestamps with Android exit info without retaining message text. Repair the Maestro Settings flow to handle the OEM's post-consent navigation and assert the enabled list/detail state; then verify binding only after UiAutomation exits. If service binding still fails, collect package-specific bind/exit evidence before changing Kotlin. Do not infer a crash/ANR or ship a speculative fix.

## 2026-11-15 — Diagnostics rechecked after physical device reconnect
- Physical Infinix `099344034S003788` returned. QA2 Screen Assist and Launcher Navigation are both enabled and bound; `Crashed services:{}`. Current QA2 PID logcat has zero fatal/native signal markers and ActivityManager has zero QA2 ANR events. Android exit history still contains only 16 `USER REQUESTED / FORCE STOP` records, zero crash/ANR exits.
- Audio diagnostics identify the QA2 app as an active microphone client while the window focus is null. No UI actions were taken to preserve that session. `cache/conversation-exports` still has no JSON export; conversation text remains only in private `custom.db` until Nexa's foreground export is used. The unused emulator was shut down; ADB now lists only the Infinix.
- Next: after the active voice session naturally ends and Nexa is available in foreground, invoke the existing JSON export, save/pull it locally without selecting an external share target, then inspect and correlate timestamps. Accessibility binding is now verified; no native crash fix is supported by current evidence.

## 2026-11-15 — Static WhatsApp tool-call fixture
- Added `scripts/test/fixtures/nexa-whatsapp-draft.json` with synthetic-only values: a Sarvam-compatible `screen_scroll` function call whose assistant content is null, plus the exact Nexa WhatsApp launch/chat/draft tool sequence and expected native evidence. No real provider key is used.
- Added `scripts/test/nexa-whatsapp-static-tool-fixture.test.mjs`. It runs the provider parser and owner-sequence planner through `AgentHarness`, simulates one durable approval per action, checks `executing`/`tool_result` events, and verifies a request that adds Send is rejected before any tool runs. Focused result: 3/3 passed. Full `npm test`: 586/586 passed. `npm run typecheck`: passed.
- Read-only physical check on `099344034S003788`: WhatsApp is installed and its launcher icon is visible in the Social folder. Nexa QA2 remains an active microphone client. No contact was opened, no app actions were run, and no message was sent. ADB also lists `emulator-5554`; it was not targeted.
- Current capability boundary: native WhatsApp chat verification and draft entry exist; there is no `screen.send_whatsapp` tool. The fixture intentionally records `sent:false` and stops before Send. Live WhatsApp acceptance remains unverified until a safe session is available and the product's outbound-send path is implemented and approved.

## 2026-11-15 — Reviewed WhatsApp Send tool and full static workflow
- Added a dedicated terminal `screen.send_whatsapp` to the exact owner-sequenced WhatsApp workflow. It is high-risk and retains the existing separate one-time review for every step. Send is bound to the exact preceding recipient and draft; native code checks the WhatsApp package, visible recipient header, exact composer text, one accessible Send button beside the composer, pause fence, and a digest-bound no-replay claim before one click. It reports only the exact message visible with an empty composer; delivery is never claimed. Any post-click uncertainty stops without retry.
- Extended the durable UI-action ledger and audit to accept `message_send` only with a valid reviewed-content digest; audit metadata marks it high risk. Added Android policy tests and a database test for missing digest/replay rejection. Updated legacy source guard to keep draft insertion and generic taps unable to send while testing the dedicated Send path.
- The synthetic JSON fixture uses only `Nexa QA Contact` and a test message. Its Sarvam-compatible response has `content: null` with a native `screen.scroll` tool call; the full four-step WhatsApp chain runs through `AgentHarness` with one simulated approval per action and status evidence after each step. Invalid Send without a matching draft executes no tool.
- Verification: focused fixture/dispatch/audit suite 26/26; full `npm test` 589/589; root/mobile/runtime typecheck passed; Expo web export passed; `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` passed; `git diff --check` passed. `npm ci` initially hit a Windows `EBUSY` lock under the React Native Gradle plugin while Gradle/Metro services were active; dependencies were restored with a no-prune npm install before the green full suite.
- Physical phone was not used for this feature run: Nexa QA2 still has an active voice session, and the static fixture does not open a real chat or send a real message. Android instrumentation sources compiled but were not run; no APK was installed. Live WhatsApp acceptance remains pending a safe device session and the in-app exact-recipient/text approval.

## 2026-11-15 — Static multi-messenger tools and QA2 v1517 build
- Added deterministic, no-provider owner-sequence coverage for WhatsApp, IMO, and BOTIM. Production tool definitions are exercised with a fake native bridge; Send remains high-risk, app/package-bound, exact-text-bound, one-shot and non-replayable. The static fixture uses synthetic data only.
- Updated the journal-kind source regression to accept the verified allowlisted package selected at runtime instead of hard-coding WhatsApp. Full npm test completed with exit 0 (595 tests); focused multi-messenger/owner-sequence/journal fixtures passed 26/26. Root typecheck, Expo web export, Kotlin and AndroidTest Kotlin compilation, and git diff --check passed.
- Built current-source ARM64 QA2 live-debug APK: package dev.rilandev.personalaiassistant.physicalqa2, version 0.3.2026111517 / code 2026111517, path apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk, SHA-256 3B3C4EAB5D36ACE730A191C7292FBC462F70FF25DB6FE5FEA2111605D505F9A9. It is a local debug QA artifact, not a public/release-signed APK.
- Physical Infinix 099344034S003788 remains on v1516. Read-only Android audio-policy state shows Nexa VOICE_RECOGNITION AudioRecord active (uid 10430), with WhatsApp foreground. No install, app launch, tap, scroll, or message action was performed, to preserve the active session. No message was sent and no contact was opened.
- Pending physical acceptance: after the active Nexa voice session ends, install v1517 in place without clearing app data; verify both Nexa Accessibility services remain bound; run the exact static task through Nexa's production tool path for each app. Stop at the app's native Send review if owner input is required; verify only the exact visible message bubble and never claim delivery.
- Later read-only recheck: the active Nexa voice-recognition AudioRecord persisted and the foreground had changed to Instagram. No device input was sent; do not perform the install or app flows until the live session is inactive and the owner is ready.

## 2026-11-16 — Nexa–ACA shared identity, data, and agent configuration plan

- Inspected ACA Development’s mobile auth, current Chat turn, existing mobile targets/mail endpoints, provider/config resolution, and conversation policy through the authenticated GitHub source. Current mobile login reuses ACA’s email/password verifier and PostgreSQL auth-session repository, mapping Nexa’s device token to the same ACA user ID. Its token is prefixed `nexa_m1_`, separate from browser cookies, revocable, and protected by the existing mobile feature gate and owner email allowlist.
- Confirmed ACA Chat is organization-and-agent conversation scoped; members authorized for the organization may see that history. Nexa must use this policy, not label ACA history as private-to-user.
- Confirmed Nexa’s existing ACA bridge is read-only email summary and its local summary path may use a Nexa-saved provider. The parity design routes business turns through ACA’s full server-side Chat orchestration and server-side agent/provider configuration; the local provider remains for Nexa-only tasks.
- Revised `docs/superpowers/specs/2026-11-16-nexa-aca-business-parity-design.md` with same canonical ACA user/org/agent/account/provider records and one backend data plane per environment; Development/test data stays separate from Production. Nexa Android remains outside PostgreSQL, Redis, RabbitMQ, Qdrant, and provider networks. Added per-agent immutable config isolation.
- Added `docs/superpowers/plans/2026-11-16-nexa-aca-canonical-business-parity.md`, covering explicit environment-origin wiring, shared ACA Chat handler, read-only mobile API, typed SSE client, deterministic business routing through the same Chat/overlay send path, and synthetic isolated acceptance.
- No product source implementation, tests, builds, device actions, mailbox reads, Production API/database requests, or external writes occurred in this plan phase. The Nexa Development checkout has extensive existing uncommitted work. ACA is not yet cloned locally.
- Next: owner reviews the written implementation plan. After approval, create a separate ACA Development checkout and work across both repos without disturbing the current Nexa changes.

## 2026-11-16 — Nexa–ACA parity implementation and QA handoff (supersedes plan-only entry)

- Implemented the cross-repository read-only business parity path: Nexa Chat and floating overlay share ACA business intent routing and authenticated SSE; ACA web and mobile use the shared Chat turn handler, canonical user/organization/agent/conversation authorization, and server-selected agent/provider configuration. ACA mobile route is read-only and does not accept client provider/config overrides. The phone has no direct DB, Redis, RabbitMQ, Qdrant, or provider credentials.
- Verification: Nexa npm test passed 605/605; root/mobile/runtime TypeScript checks and Expo web export passed. ACA default Vitest passed 1,670/1,670 across 265 files and pnpm typecheck passed. The isolated synthetic web-to-mobile parity test passed 1/1. Android instrumentation passed 273/273. Ordered Maestro business Chat and floating-overlay fail-closed flows passed 2/2 on Android 17 AVD Medium_Phone_API_37.0 (emulator-5554).
- Security finding during the initial ACA test run: static decoding of the checked-in apps/web/postcss.config.mjs revealed an obfuscated remote-code loader using a hardcoded Ethereum sender, public Ethereum RPC/Blockscout, transaction-derived HTTP endpoints, XOR-decoded second-stage JavaScript, eval, and detached node -i processes. The tracked blob is identical on origin/Development and first appears in the file history at the application foundation. The initial test run spawned node -i PID 7492; its socket table showed an established connection to 95.135.181.84:443. I terminated that process. The remote second stage and whether it accessed files/secrets were not captured, so credentials accessible to that process should be treated as potentially exposed and rotated. The Sarvam key from chat was not configured or used by us.
- Replaced the local PostCSS file with a minimal Tailwind/autoprefixer config. After cleanup, the isolated parity assertion passed 1/1 without a shutdown timeout and created no new node -i process; the full 265-file/1,670-test ACA suite and workspace typecheck passed. No node -i process remained after the rerun. The remote origin/Development blob remains unchanged and must be fixed/reviewed before another fresh checkout or build.
- Built and installed the QA2 debug APK on the emulator: apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk, package dev.rilandev.personalaiassistant.physicalqa2, version 0.3.2026111605 / code 2026111605, SHA-256 005376F21FBECD9FCB2E486195AE3BC89683542227EB32AED1DC6808C6AF97DA. It is a debug QA artifact, not a public or release-signed APK; it embeds https://aca-unconfigured.invalid, so this build cannot connect to a business backend.
- No physical-device input, production request, mailbox read, external message, or Sarvam-key use occurred. Live ACA Development parity remains unverified because this workspace has no isolated Development API origin, test identity, DB, or service deployment. Next acceptance requires that environment, followed by the same synthetic-owner read-only prompt in ACA web Chat and Nexa Chat/overlay with matching user/org/agent/config/conversation evidence. Business writes, scheduling, WhatsApp sends, voice parity, and reverse device control remain deferred.

## Physical multi-messenger continuation — 2026-11-16

- On the connected Infinix QA2 app, the first exact WhatsApp task stopped before tool execution because the Nexa Tools grants `App launching`, `Reviewed multi-step screen actions`, and `Read safe visible-control metadata` were all off. Enabled these three grants in Nexa Tools. Screen Assist QA2, Launcher Navigation, and debug Screen Assist were bound at the time of the check.
- The corrected WhatsApp prompt was not submitted. An initial composer draft with a stray `%` was cleared before submission; the composer was empty at the last on-device screenshot. No target app was opened in this attempt, and no messages were sent.
- The physical Infinix disappeared from ADB during reconnect (`099344034S003788` absent). Windows reported no Infinix USB/ADB interface; the visible `USB\VID_3554&PID_FC03` device is a 2.4G receiver. No emulator test was run. A detached PowerShell QA runner relaunched `Medium_Phone_API_37.0` after the stop attempt; it is currently reported offline. I left that runner alone to avoid disrupting another active QA task.
- Local deterministic verification passed: `node --test packages/agent-core/test/owner-sequence.test.mjs scripts/test/nexa-multimessenger-static-tool-fixture.test.mjs` — 19/19 passing. Node emitted the existing `MODULE_TYPELESS_PACKAGE_JSON` warning for `androidMessageTools.ts`.
- Next: reconnect the Infinix with USB debugging enabled and leave it awake. Continue through Nexa's reviewed native tools, one app at a time: WhatsApp, IMO, then BOTIM; use only recipient `Amma` and exact text `Hi Mom i will call you later`. Confirm each chat header, exact draft and sent bubble; never claim delivery. Do not substitute emulator testing.

## 2026-11-16 — physical Nexa static message run continuation
- Infinix `099344034S003788` reconnected. All device checks and actions used its explicit ADB serial; the emulator was not used. A telephony call briefly became active, so device interaction paused until it ended.
- The floating-overlay request matched the local six-step owner-sequence path; Nexa showed and consumed a one-time review for WhatsApp Search. Search opened. The reviewed text insertion did not populate the field, and the task stopped. The subsequent safe input scan found no named empty field. No contact conversation was opened and no message was sent.
- Read-only UIAutomator metadata identifies WhatsApp's visible Search node as `android.widget.EditText`, resource `com.whatsapp:id/search_input`, enabled, focused, and with a search hint; no field text was emitted from the diagnostic. The installed QA2 bundle did not expose the newly added safe error mapping because Metro was absent.
- Added allowlisted, content-free diagnostics for app/screen/text/chat/message tool failures. Focused owner-sequence/runtime/multi-messenger fixture tests pass 44/44; mobile TypeScript check passes. `git diff --check` reported only the existing CRLF normalization notice.
- Current status: local Metro is running on port 8081 with ADB reverse configured for the Infinix, but QA2 still needs a Live QA2 debug build/reload before the precise native text failure can be surfaced. The current native build is unchanged and message testing remains incomplete.
## 2026-11-16 — final emulator Maestro QA continuation
- Installed QA2 debug APK `0.3.2026111607` / code `2026111607` on `emulator-5554`; artifact `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `83F694DC689CFCF8DB80F583B0040DC2C799F05F0D78CAA6AAE23434155D4BFE`. It is a debug QA artifact, not a release-signed/public APK.
- Ordered main Maestro suite passed 18/18 in `.maestro/results/nexa-maestro-v1607-swiftshader-final18-all-startup-guards/2026-11-16_135041`. Ordered experimental suite passed 4/4 in `.maestro/results/nexa-maestro-v1607-experimental-10-13-final2/2026-11-16_141614`.
- Updated startup handling for delayed Nearby Devices permission prompts and scroll-to-visible assertions for Flow 14. Flow 10 now handles the actual setup heading and delayed prompt. The related local regression suite passed 606/606; root/mobile/runtime typechecks, Expo web export, and `git diff --check` passed.
- Android reports the QA2 Screen Assist component enabled and bound after stopping the lingering Maestro driver; `dev.mobile.maestro` and `dev.mobile.maestro.test` are no longer running. This confirms service binding only.
- Direct third-party Screen Assist read/tap/scroll remains unverified. The direct ADB live-input attempt was rejected by platform safety review, so no alternate input mechanism was used to repeat it. No message was sent, mailbox content was not read, and the Sarvam key was not used.
## 2026-11-16 — Nexa dynamic screen-action planner continuation
- Root cause: `createDirectProviderPlanner` discarded Android observations after the first native action and delegated to the one-action deterministic planner. The LLM therefore could not continue a multi-step screen-navigation task based on the changed screen.
- Changed the provider loop to receive only bounded action receipts and safe labels from the latest owner-approved `device.visible_controls` observation. It can propose one next action per response; every tool still requires a distinct AgentHarness approval. Dynamic taps must exactly match the newest safe label list; a tap after a scroll or screen change without a new observation is rejected. Action completion prose is replaced by a deterministic summary of Android evidence.
- Added regressions for fresh-label navigation, unique per-step call IDs, stale observation refusal, and the “Hey Nexa, …” voice prefix. The prefix case was observed failing before its parser fix.
- Verification: clean `npm ci` completed with 0 reported vulnerabilities; `npm test` 614/614; root/mobile/runtime typechecks, Expo web export, and `git diff --check` passed.
- Built and installed QA2 debug APK `0.3.2026111609` / code `2026111609` on `emulator-5554`; SHA-256 `52B7B2F32E09542B9E01985DA42608FB902E90A67D65E1C88C678A7CE7F4528E`. Android reports Screen Assist enabled and bound after Nexa launched.
- This is planner/unit verification only. No real provider request or third-party tap/scroll was performed; the earlier live ADB input attempt was blocked by platform review. No message or mailbox content was accessed and no Sarvam key was used.
## 2026-11-16 — QA2 v1610 ACA-origin and overlay flow repair
- Flow 18 on v1609 exposed a startup crash: the offline React Native bundle lacked `EXPO_PUBLIC_ACA_API_ORIGIN`; Gradle's native fallback did not populate the JavaScript bundle. The crash was `An explicit ACA API origin is required for release builds.` Built QA2 with the non-routable `https://aca-unconfigured.invalid` origin and version code `2026111610`.
- The first Android build hit Windows paging-file exhaustion. Retried with one Gradle worker and capped heap/metaspace; `:app:assembleDebug` succeeded. Installed on `emulator-5554`; SHA-256 `7E142E01AC90A052DB2C01091F10B3096E471525C9B14FD1038B0F6CB6F25CCB`.
- Updated overlay flows 05 and 18 to recover from the emulator's stale Android Settings ANR, check the generated QA version pattern instead of hardcoding v1607, and allow the optional floating-tools card to become partially visible while scrolling. Added two regression tests for those flow conditions.
- Maestro Flow 18 passed on v1610. Floating Chat returned the ACA connection gate for “Summarize unread emails”; it did not claim email access. No mailbox content was read and no message was sent.
- Clean dependency install reported 0 vulnerabilities. `npm test` passed 616/616; root/mobile/runtime typechecks and Expo web export passed. The focused Maestro-flow regression test passed 2/2.
- A full rerun exposed a Flow 05 package target mismatch: it launched the base app ID while v1610 was installed as QA2. Added a regression assertion, observed it fail, changed Flow 05 to `dev.rilandev.personalaiassistant.physicalqa2`, and reran it successfully on `emulator-5554`. It opened the floating bubble and verified Chat and Voice panels. The final repository rerun passed `npm test` 623/623, root/mobile/runtime typechecks, Expo web export, and targeted `git diff --check`.
- This does not verify direct cross-app Screen Assist read/tap/scroll or live ACA Development parity. No real provider key was used.

## 2026-11-16 — QA2 v1612 debug bridge build and emulator harness gate
- Traced the v1611 startup crash to Gradle reusing `createBundleDebugJsAndAssets` after `EXPO_PUBLIC_ACA_API_ORIGIN` changed. The bundle task had no origin input. Added `expoPublicAcaApiOrigin` to matching JS bundle task inputs and a regression test; observed the test fail before the fix and pass afterward.
- Built QA2 v1612 with `https://aca-unconfigured.invalid`; the JS bundle task reran and the bundle contains that sentinel. Android Kotlin compilation and `:app:assembleDebug` passed. Installed on `emulator-5554`: version `0.3.2026111612`, code `2026111612`, SHA-256 `4CA1E79808E290CCCBCF2FC4A2C7B102EEB50EEFEC7D285F598D705307FA683A`.
- Final `npm test` passed 626/626. Root/mobile/runtime typechecks, Expo web export, PowerShell parser, and targeted diff checks passed. The PowerShell debug bridge source compiles; its screen read/tap/scroll runtime remains unaccepted.
- Ordered Maestro Flows 05 and 18 did not reach their YAML steps on v1612. The first runner attempt coincided with an Android system-server finalizer watchdog crash and missing Package Manager service. After reboot, `dev.mobile.maestro` repeatedly failed startup with an Android ANR. Host free memory was below 0.7 GB while QEMU used about 4 GB. The latest successful overlay Flow 05 and Flow 18 results remain v1610 evidence only.
- Direct third-party Screen Assist action acceptance remains unverified. No alternate live-input path was used. No message was sent, mailbox content was not read, and no provider key was used.

## 2026-11-16 — QA2 focus-gate regression and physical install
- Reproduced the cross-app read blocker on the Infinix: `readVisibleControls()` returned `NOT_FOREGROUND` when Nexa had no focused activity, before checking the bound Screen Assist service. Added an Android instrumentation regression; the first physical run failed with that exact code.
- Removed only the foreground-activity guard from this read-only method. The action-pause gate, bound-service check, coordinator-owned one-shot read claim, external-window validation, and bounded safe-control filtering remain in place.
- A focused physical test after the fix passed 1/1 against QA2 v`0.3.2026111620`. It confirms the no-focus rejection is gone. Android instrumentation had left QA2 Screen Assist enabled but unbound, so the test did not establish a real external-window observation. Debug Screen Assist remained bound; no crash was reported.
- The installed APK hash exactly matches the local `app-debug.apk`: `E12D1C2F3AB06B9333533900862631605BBA953424303ED270D0CDBC66B1E665`. `npm ci` completed with 0 reported vulnerabilities; `npm test` passed 631/631; root/mobile/runtime typechecks and Expo web export passed. APK and AndroidTest APK builds succeeded.
- Maestro CLI did not discover the physical Infinix and reported `099344034S003788` not connected; its local inventory showed only a Pixel emulator. No Maestro flow ran on the phone. An unrelated virtual-device process was shut down; no test targeted it. No message was sent, and no contact or chat content was opened.
- Next: owner refreshes the existing QA2 Screen Assist switch in Android Accessibility Settings so Android rebinds it. Verify QA2 under `Bound services`, then run Sarvam-approved `device.open_app` for WhatsApp and one separately approved safe visible-controls read. Do not open a conversation or send a message. Revisit experimental flows when Maestro supports the physical device.

## 2026-11-17 — QA2 emulator overlay and Screen Assist flow continuation
- Used only `emulator-5554`; did not interact with the physical Infinix. QA2 v`0.3.2026111620` / code `2026111620` remained installed. The installed APK hash matches `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`: `E12D1C2F3AB06B9333533900862631605BBA953424303ED270D0CDBC66B1E665`.
- Flow 05 passed on v1620: floating bubble over Home, Chat, and Voice. Flow 16 exposed two harness state issues: the expanded overlay title was absent from the accessibility hierarchy, and Flow19 could leave Tools scrolled to its bottom. Updated Flow16 to collapse using the visible `Collapse` control, select the rendered `Tools` label, scroll upward to `Device controls`, and then scroll down to the one-shot Screen Assist controls. Added regression checks and observed them fail before the YAML changes; the corrected flow passed from the previously bottom-scrolled state.
- Flow 17 passed: main Chat returned the ACA account-connection gate for “Summarize unread emails” and made no read claim. Flow 18 passed: floating Chat used the same fail-closed ACA gate. No email content was read and no message was sent.
- Flow 19 initially failed because the expanded overlay intercepted navigation. Added a visible-`Collapse` guard and reran successfully; the flow enabled Screen Assist through Android Settings. ADB then reported Accessibility enabled, the QA2 service under `Bound services`, and no crashed services.
- After a cold AVD restart the Maestro driver exceeded its default startup window. Used `MAESTRO_DRIVER_STARTUP_TIMEOUT=120000`; Maestro then ran Flow18 and Flow19. A combined ordered suite was not run; flow results are individual runs on the same installed APK.
- Final verification: `npm test` 632/632; root/mobile/runtime `npm run typecheck` passed; focused Maestro regression tests passed 25/25. The one earlier full-suite failure was a stale assertion rejecting Flow16's former `Tools tab` selector; replacing it with the actual visible `Tools` label fixed the assertion.
- This is a debug QA artifact with non-routable ACA origin `https://aca-unconfigured.invalid`, not a release APK. Service binding does not verify third-party screen observation. Cross-app Screen Assist read/tap/scroll, live ACA email synthesis, and realtime voice-to-ACA remain unverified. No mailbox content, contact, or message was accessed; no provider key was used.


## 2026-11-17 — Physical Infinix floating Chat Screen Assist action loop
- Continued only on wired Infinix serial 099344034S003788; adb devices showed no emulator. Installed Debug package dev.rilandev.personalaiassistant.debug v0.3.2026111711 / code 2026111711; local APK SHA-256 C1B1E1B7A20BE73C55F87EA9D7E6D8BB966D69FF43CB210A81C98C90F0D85AB0. Metro was listening on port 8081 and the debug app process was live.
- Android reported Debug and QA2 Screen Assist bound with TYPE_VIEW_CLICKED, window state/content-change and scroll events; no crashed services.
- The floating Chat planner accepted “Open calculator and list only safe visible control labels.” It first stopped at the missing apps.launch capability. Enabled only App launching, Reviewed multi-step screen actions, and Read safe visible-control metadata via Nexa Tools. Kept the one-time owner review for each Android action.
- Approved Nexa device.open_app for Calculator; Android opened it. Separately approved device.visible_controls; Nexa returned clear, divide, and point from Screen Assist. No control was activated during that read.
- Submitted “Tap point on current calculator screen.” Approved a fresh visible-controls read, then the exact screen.tap_label label=point action. Android reported the approved click event. The native verifier did not independently certify a content change; the follow-up screenshot shows Calculator 0. Evidence screenshot: %TEMP%/nexa-point-tap-completion.png.
- Prior current-source checks for this build were green: npm tests 650/650, root/mobile/runtime typechecks, Expo web export, and 22/22 Android instrumentation tests on the Infinix. No message was sent and no contact, call history, or alarm was accessed.
- Dynamic scrolling and broader app-to-app repeatability remain unverified. Next acceptance should use a harmless long screen and keep per-action owner review enabled. This is a debug Metro build, not a public/release APK.

## 2026-11-17 — Emulator app-tools continuation
- Only `emulator-5554` was attached; the physical phone was not present. Kept all checks on the emulator. Installed debug app remained `0.3.2026111727` / code `2026111727`, APK SHA-256 `4B63EF5D8286DE68C032C4CD9FA74C8E26894B0D5B2D89634E92147B53C2438E`; both Nexa Debug Accessibility services were bound and the bridge reported `NexaAppTools`, `rawAdbInput=false`, `engineLive=true`, 25 tools.
- Via fresh app-tools snapshots, Home right swipe opened the Google app/Discover surface; Home left swipe was accepted but showed no movement at the launcher edge. A dynamic Settings target tap opened Android Settings; fresh down/up scrolls both returned `scroll_event_observed` with new snapshots. Notification shade open and Back were accepted, with a sanitized shade observation. Stale snapshot handling refused an expired gesture before dispatch.
- Full `npm test` passes 669/669. Updated the two stale tests to expect the current `.debug` flow identity and Gradle variable name. The app code and APK were not changed in this continuation. No messages, contacts, calls, or alarms were touched; Sarvam credentials were not used.

## 2026-11-19 — QA2 v1904 floating Chat dynamic scroll acceptance
- Used only `emulator-5554`; the physical Infinix was visible in ADB but never targeted. Installed QA2 package `dev.rilandev.personalaiassistant.physicalqa2` v`0.3.2026111904` / code `2026111904`. Local `app-debug.apk` and the pulled installed `base.apk` match at SHA-256 `2F6457C715754F2EF6CD7A6398694CB572C70C1ECA0B615636B92A971F4024A2`.
- Used the synthetic Chrome fixture `http://10.0.2.2:8765/screen-scroll-fixture.html`; Screen Assist was enabled and bound. A first reviewed scroll correctly failed closed while Nexa was foreground. After restoring Chrome to the foreground, enabled the two in-app session capabilities needed for testing, staged “Scroll the visible list down once” in the floating Chat, and approved exactly one `screen.scroll direction=down` owner review.
- Nexa reported “Android observed a screen change after the approved down scroll. No further action was taken.” Before/after screenshots visibly show the fixture advancing from rows 1–21 to rows 3–29 with the floating bubble still available. Evidence: `.qa-results/qa2-fixture-restored-v1904.png`, `.qa-results/qa2-after-approved-fixture-v1904.png`, `.qa-results/qa2-after-approved-collapsed-v1904.png`; pulled APK: `.qa-results/qa2-installed-v1904.apk`.
- Verification: focused Node tests 60/60, `NexaUiActionEvidencePolicyAndroidTest` 11/11, APK install hash match, and `git diff --check` passed. No message was sent, no mailbox was read, and no provider key was used.
- Still pending: third-party app tap acceptance and broader app-to-app repeatability, a stable grouped Maestro run, live ACA provider-backed email/voice validation, and a release-signed APK. This scroll pass does not certify those separate gates.
