## Latest status — 2026-11-21 Full Access recheck

- Physical Infinix `099344034S003788` only; no emulator was used. Debug build `0.3.2026112106` remains the installed app. Earlier Flow 18 (Chat → Clock) and Flow 20 (floating Chat → Clock) passed on this build without a Nexa approval card.
- A fresh Flow 19 retry did **not** pass: its assertion found the Chat mode was back at “Actions require your permission,” and `nexa.ps1 status` returned `fullAccessAppLaunchReady=false`. The flow did not run a tool. The captured screenshot is `.qa-results/full-access-physical-20261121/v2106-no-pending-final/2026-11-21_181727/Full Access Chat has no routine approval after an app launch/screenshots/step-010-assertCondition-Full_Access_is_on._Nexa.png`.
- The Android test suite had a data-isolation defect: `NexaBoundedAutonomyAndroidTest` and `NexaVoiceTaskPlanAndroidTest` called `clearGrants()` against the installed app preferences and left them cleared. Those tests now snapshot and restore the exact saved permission preferences; a regression test covers restoring Full Access plus an existing grant. Android test Kotlin compilation succeeds.
- Added `.maestro/physical-qa/21-select-and-persist-full-access.yaml` to select Full Access through Nexa Settings and verify it survives an app relaunch. YAML parsing succeeds. The relevant Node tests pass 6/6; the prior full Node suite passed 718/718 and root/mobile/runtime typechecks passed.
- The current Screen Assist observation reports `com.transsion.applock` with no exposed clickables, so I have not tried to bypass the phone's app lock. After the owner unlocks it, run Flow 21, then rerun Flows 19, 18 and 20 on this same serial. Verify bridge readiness and Screen Assist binding afterward. A full matrix of all registered tools and LLM-planned cross-app gestures is still unverified; do not describe the routine-tool result as “anything without approval.” Android grants/unlock and protected sends, calls and payments remain separate safeguards.

## Active status — 2026-11-21 physical Infinix Debug v2106

- The latest embedded-bundle Debug `0.3.2026112106` / code `2026112106` is installed on physical Infinix `099344034S003788`. Emulator use is off-limits for this task.
- Nexa Screen Assist is enabled and Android reports it bound with no crash. The bridge is live, with 25 tools and `rawAdbInput=false`.
- The two app-level capabilities needed for a floating screen action (`device.interact` and `device.observe`) were off on the captured Tools screen. Their narrow setup flow is ready at `.qa-results/screen-assist-setup-20261121/enable-agent-screen-controls.yaml`, but Maestro's device server died before any command ran. Foreground checks showed `com.sh.smart.caller/.InCallActivity`, then Infinix AppLock's `ConfirmLockActivity`; leave the phone untouched until the owner unlocks it and Nexa or Home is foreground.
- Focused agent-core tests pass **53/53**, the full `npm test` suite passes **718/718**, and root/mobile/agent-runtime typechecks pass. The Clock/Stopwatch exact-target test is source-level evidence only; live Sarvam planning and a physical tap have not passed. The subsequent Full Access recheck, test-isolation fix and rerun status are recorded above.
- Setup-flow evidence: `.qa-results/screen-assist-setup-20261121/resume-2106-screen-assist-reinstall.xml` (1/1 passed); driver failures: `.qa-results/screen-assist-setup-20261121/enable-agent-screen-controls.xml` and `enable-agent-screen-controls-retry.xml` (both failed before the flow body).

## Next

1. Once the owner has cleared AppLock and Nexa or Home is foreground, run the narrow capability flow with normal Maestro driver startup and verify both switches are on; avoid broad runtime permission grants.
2. Restore the floating bubble with `.maestro/nexa/27-qa2-floating-chat-setup.yaml`, then verify Screen Assist is still bound after Maestro exits.
3. Submit `Open Clock, then tap the Stopwatch tab. Do not start the stopwatch.` through floating Chat. Approve only the fresh, exact, harmless read/tap review steps; verify Android observed the Stopwatch tap and the timer remains stopped.
4. Record the physical result before claiming the LLM screen-control task passed. No external message is part of this test.

---

## Historical status — emulator Debug v2103/v2104 QA (superseded by physical v2106)

- Built embedded-bundle Debug `0.3.2026112103` with legacy native-library packaging and installed it on `emulator-5554`. APK SHA-256 is `764D4F66BF21C668516D0AE58CC5EF173DA927964FC17518354A16F79962BD1A`; pulling the installed `base.apk` produced the same hash. This is a debug QA build, not a release-signed APK. The physical Infinix was not targeted.
- The live one-shot Screen Assist path now passed through Nexa's confirmed debug bridge: armed the native 20-second capture, opened Android Settings through `device.open_app`, returned with named Back, and reviewed metadata only. Review reported `captured=true`, source `com.android.settings`; a second review reported `captured=false`, confirming the one-shot result was consumed. No screen text was emitted by `reviewread` or written to evidence. Metadata evidence: `.qa-results/screen-assist-live/screen-read-metadata-20261121.json`.
- Nexa's bridge observed Settings and completed fresh-snapshot down/up scrolls; both returned `structural_change_observed` and the Settings package remained foreground. A named Back returned to Nexa. Evidence: `.qa-results/screen-assist-live/cross-app-scroll-metadata-20261121.json`. The final bridge status reports `rawAdbInput=false` and `accessibilityEnabled=true`.
- Maestro Flows 16, 19, 23 and 27 passed individually and as one ordered suite (**4/4 in 3m42s**). Maestro left its `dev.mobile.maestro` UiAutomation process connected after the ordered run; stopping that completed QA driver released Android's accessibility connection. Both Debug and QA2 Screen Assist are now bound with no crashed services. Ordered-run artifacts are under `.qa-results/screen-assist-live/ordered-20261121/`.
- Verification: `npm test` **716/716 passed** (`.qa-results/screen-assist-live/npm-test-20261121-final.log`); root/mobile/runtime typecheck passed; Expo web export passed; Gradle `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed.
- No messages were sent and no email was read.
- Follow-up Debug `0.3.2026112104` was assembled with the non-routable ACA origin and installed only on `emulator-5554` for ACA session-continuity validation. The local APK and installed `base.apk` match at SHA-256 `61B8A0A3DAD5ED53A83A7C0D2340514CB37D410D23748016BDB5453DFB8FBA54` (`x86_64`). The focused Keystore validation instrumentation passed 4/4; fresh `npm test` passed 717/717 and root `npm run typecheck` passed. The v2103 Maestro flow results above were not rerun on v2104; this remains a debug QA artifact.

## Remaining gates at that emulator checkpoint

1. Maestro Flow 16 verifies that the Arm/Review controls are reachable, while live capture was exercised through the passing `scripts/qa/test-screen-assist-live.ps1` acceptance path using the same native one-shot APIs. A direct Maestro tap-through of Arm and Review remains unverified because Maestro's UiAutomation disconnects Screen Assist while driving; its incompatible AndroidJUnit test is explicitly ignored for the same reason.
2. Provider-backed LLM planning remains blocked by the previously observed Sarvam HTTP 401/403. A valid rotated test credential is needed before claiming live LLM planning acceptance.
3. Local Nexa Chat/overlay source integration now targets ACA's canonical session, organization, agent, configuration, conversation and read-only tool path. Fresh offline tests and typechecks pass, but live Development parity, provider-backed email synthesis, ACA mail in continuous voice, broader reviewed app taps, and a release-signed APK remain pending. Do not use Production while the remote ACA initializer incident is unresolved; v2104 is a debug QA artifact.

---

## Historical status — 2026-11-20 emulator Debug v2011 QA

- Built embedded-bundle Debug `0.3.2026112011` and installed it on `emulator-5554`. Local APK and the installed `base.apk` match at SHA-256 `5A16791FE9E618C798730D7AFC11A57B14A3E8492F106D53D9F17821502C0BFD`; evidence: `.qa-results/installed-base-v2011-20261120.apk`. This is a debug QA build, not a release-signed APK. The connected Infinix was not targeted.
- On this build, Maestro Flows 16, 23 and 27 passed individually. Flow 19 also passed on the emulator, reopening Android Accessibility Settings, refreshing the Nexa service grant, and returning to the Tools screen; run log: `.qa-results/screen-assist-restore-v2011-20261120/maestro.log`.
- Post-Maestro Android state lists Debug and QA2 Screen Assist as enabled and bound, with no crashed or pending bindings. Evidence: `.qa-results/screen-assist-bound-v2011-post-maestro-20261120.txt`. Two synthetic in-memory Android tests passed: `ownerArmedVisibleTextSnapshotIsOneTimeAndOnlyInMemory` and `screenSnapshotIsBoundedAndOwnerCanDiscardWithoutReading`.
- Nexa's debug bridge then verified live cross-app observation and dynamic actions: it observed the launcher, opened Chrome from a fresh indexed snapshot, observed Chrome, opened Android Settings from another fresh snapshot, and completed fresh-snapshot vertical down/up scrolls with structural UI changes. Full page text was filtered; only package/control metadata and target labels were surfaced. Evidence: `.qa-results/nexa-bridge-cross-app-v2011-20261120.txt`. This verifies the reviewed native screen tools, not LLM planning.
- A follow-up tap on the Android Settings Apps row was not verified: Android Settings ANR'd on its subpage, and Nexa correctly returned `not_observed`/no snapshot while the ANR dialog was present. Closing the hung Settings task restored the main Settings snapshot; no setting changed. This tap is recorded as failed, not passed.
- Fresh checks: `npm test` **708/708 passed** (`.qa-results/npm-test-final-20261120.log`); root/mobile/runtime typecheck passed (`.qa-results/typecheck-final-20261120.log`); Expo web export passed (`.qa-results/web-export-final-20261120.log`).
- The separate Arm one screen read / Review captured text UI remains unverified. Maestro's UiAutomation session disconnects Screen Assist while it drives, and post-run rebind alone is not proof of that capture path. The debug bridge snapshots were temporary; full page text was not printed or written to evidence. No message was sent and no email was read.

## Historical remaining gates (superseded by the 2026-11-21 results)

1. Verify the separate Arm one screen read / Review captured text UI through an interaction route that does not keep Android UiAutomation active while arming. Keep capture local, confirm only source package/status, and discard it; do not use direct ADB input as a workaround.
2. Provider-backed UI planning remains blocked by the saved Sarvam test credential returning HTTP 401/403. Once a valid test credential is saved in Nexa, rerun the owner-reviewed synthetic Chrome task and separate observation from tap approval.
3. ACA live email/voice parity and a release-signed APK remain separate workstreams. The v2011 APK is a debug QA artifact only.

---

## Current status — 2026-11-20 emulator Debug v2010 QA

- Built and installed embedded-bundle Debug `0.3.2026112010` on `emulator-5554`. Local APK and installed `base.apk` SHA-256 match: `935011CCB28EED549EDD086742A7E476D871A8A1AB077BF04C3A016901BC13B1`. This is a debug QA APK, not a release-signed build. The connected Infinix was not targeted.
- Fixed Flow 16's restored-scroll-position assumption. After selecting Tools, it now scrolls UP until `Device controls` is visible before asserting it. The exact flow passed on the emulator with JUnit `1 test / 0 failures`; artifact: `.qa-results/debug-screen-assist-reachability-v2010-20261120/report.xml`.
- Post-Maestro ADB verification reports Accessibility enabled, Debug and QA2 Screen Assist enabled and bound, and no crashed or pending binding services. Flow 16 verified the reviewed-action and safe visible-control switches plus the Arm/Review control availability; it did not arm a capture or interact with another app.
- On v2010, Flow 27 (floating-bubble setup) passed after its dynamic scroll fix, and Flow 23 (provider settings navigation) passed after handling the collapsed optional-features card. Flow 30 staged a synthetic Chrome screen-read review, but the Chat request timed out and the action journal showed no owner-confirmed UI action. Artifacts are `.qa-results/debug-floating-chat-setup-v2010-20261120-r2/report.xml`, `.qa-results/debug-provider-settings-state-v2010-r3/report.xml`, `.qa-results/debug-overlay-screen-read-stage-v2010-20261120/report.xml`, and `.qa-results/debug-action-journal-after-screen-read-timeout-v2010/report.xml`.
- Fresh full `npm test`: **706/706 passed** (`.qa-results/npm-test-resume-20261120.log`); root/mobile/runtime typecheck and Expo web export passed. Android `:app:assembleDebugAndroidTest` compiled successfully, and two synthetic in-memory Screen Assist tests passed on `emulator-5554`; evidence: `.qa-results/native-screen-assist-synthetic-v2010-20261120.log`.
- The live one-shot capture is still unverified on v2010. The synthetic Chrome Maestro attempt reached the Arm button, but Maestro's Accessibility driver made the service report `connected=false`; Nexa correctly rejected arming and showed Screen Assist Off. After Maestro exited the service rebound. Failure artifacts are under `.qa-results/debug-screen-assist-capture-v2010-20261120/`. No capture text was obtained. A desktop-controller attempt could not activate the emulator window; the earlier direct ADB input tap was rejected by automatic review and was not retried.

## Remaining live QA gates

- Physical Infinix `099344034S003788` was also tested on Debug v2009 without targeting the emulator: fresh observe/indexed tap, vertical down/up and launcher left/right passed with new post-action snapshots; horizontal scroll on Nexa's vertical-only Tools page correctly refused without dispatch. A real Sarvam Chat request generated and, after owner review/resume, opened Clock; Screen Assist snapshots verified `com.transsion.deskclock` and safe controls. The later `device.visible_controls` review was not completed. An unrelated ChatGPT draft appeared during final phone observation, so no further device actions were taken. Continue only after the phone is back on a safe Nexa/Home screen, preferably by starting the next test in the floating overlay. No messages or alarms were changed.
- QA2's saved Sarvam credential is configured as `sarvam-105b`, but Test connection and the owner-approved floating Chat planning request returned HTTP 401/403. The synthetic Chrome task stopped before any target tap. A valid rotated provider credential is required for provider-backed tap acceptance; no credential should be printed in QA artifacts.
- After provider connection succeeds, clear/retry the timed-out synthetic floating-Chat task, approve the fresh visible-control observation, verify the model proposes the exact observed `Open QA details` button, separately approve the tap, and verify the UI change. Then continue the ordered directional/cross-app acceptance. For live Screen Assist capture, use an interaction route that does not hold Android UiAutomation while arming; do not count a bound-service check or the synthetic state-unit tests as a live capture pass.
- ACA live email/voice parity and a release-signed APK remain separate pending gates. No message was sent and no mailbox was read.

---

## Previous status — 2026-11-20 emulator QA2 provider gate

- QA2 `0.3.2026112006` is installed on `emulator-5554`. The Infinix was not targeted. Android retained the Nexa Screen Assist enabled-service setting after a cold boot but left it unbound; toggling emulator Accessibility off/on restored the bound QA2 service.
- Floating chat and one-shot visible-control review were verified over Android Clock. The “Tap the stopwatch tab” task reached the saved-provider planner after owner-approved observation, then stopped with the provider auth error. No target tap occurred.
- A QA2 Settings flow used the saved Keystore credential without entering or exposing it. The app displayed the Sarvam `sarvam-105b` provider as configured, but Test connection returned HTTP 401/403. The saved credential/account must be made valid before live LLM task execution can pass.
- Verification: Maestro QA2 provider flow completed and captured `.qa-results/qa2-provider-connection-20261120-r3/`; focused agent/screen/provider/flow tests 82/82; full `npm test` 702/702; root/mobile/runtime typecheck, Expo web export, and `git diff --check` passed. The one-off QA2 flow lives in `.qa-results/maestro/`, outside the tracked debug-only Maestro suite.
- Current limitations: a provider-planned screen tap is still unverified; ACA live mail synthesis/voice routing and public release signing remain separate pending gates. No message was sent and no mailbox content was read.

## Next

1. Save a working Sarvam Model API credential in Nexa’s Android Keystore, then rerun the connection check and the approved safe Clock tap flow. Keep observation and the tap as separate one-time approvals.
2. After the live tap passes, continue ordered cross-app and directional-scroll acceptance on the same current app build.
3. Continue ACA mail/voice parity and release APK checks as separate gates; do not treat mocked tests as live service acceptance.

---

## Previous status — 2026-11-20 physical Infinix Debug v2009

- Built `dev.rilandev.personalaiassistant.debug` from current local source as version `0.3.2026112009` / code `2026112009`; `:app:assembleDebug` succeeded. APK: `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `24759890BDAEF4CA117B2181895039C2B1450CC0E4932F41CEA4C83E4D8FAD22`. Installed and verified on Infinix `099344034S003788`. Removed `.physicalqa2` and the `.physicalqa.test`/`.physicalqa2.test` instrumentation packages; did not use `emulator-5554`.
- Restored live JS delivery without stopping the existing but non-responsive Metro process on 8081: started a second Expo Metro on 8082 and mapped device port 8081 to host 8082. `/status` returned `packager-status:running`; `nexa.ps1 status` returned `bridgeAvailable=true`, `engineLive=true`, 25 tools, and `rawAdbInput=false`.
- Focused tests for the sequential tool loop, Sarvam tool-call contract, ACA routing/email boundary and static multi-messenger fixture passed 63/63. Fresh full `npm test` passed 702/702 after correcting Flow 33, which still referenced `.physicalqa2`; root/mobile/runtime typecheck and web export passed. These message-fixture tests use mocked tools; no real message was sent.
- Physical screen setup is pending. `.maestro/physical-qa/17-enable-screen-assist-only.yaml` failed at its `Nexa · Tools` assertion; `nexa.ps1 observe` returned `ACCESSIBILITY_DISABLED`. Android currently reports no enabled or bound accessibility services, and `topResumedActivity` is BOTIM's call activity. Stopped device interaction to avoid disturbing the active call. Maestro artifacts: `%USERPROFILE%\.maestro\tests\2026-11-20_140616`.
- Do not bypass Android accessibility consent or Nexa per-action review. After the call ends, rerun Flow 17 on the Infinix, verify Screen Assist is enabled and bound, then run fresh-observation tap and each directional scroll separately. Only after those pass should the safe bounded multi-step tool loop be exercised on-device. Do not send a message as part of the navigation test.
## Current QA status — 2026-11-19 emulator QA2 v1904 dynamic scroll acceptance

Only `emulator-5554` was targeted in this continuation; the connected physical phone was not used. QA2 package `dev.rilandev.personalaiassistant.physicalqa2` v`0.3.2026111904` / code `2026111904` is installed. Local `app-debug.apk` and the pulled installed `base.apk` have matching SHA-256 `2F6457C715754F2EF6CD7A6398694CB572C70C1ECA0B615636B92A971F4024A2`. This is a debug QA build, not a release APK.

**Dynamic cross-app scroll acceptance:** The test used only the synthetic Chrome fixture at `http://10.0.2.2:8765/screen-scroll-fixture.html`. Nexa Tools had `Reviewed multi-step screen actions` and `Read safe visible-control metadata` enabled for the app session, and Android reported QA2 Screen Assist enabled and bound. The first reviewed attempt correctly stopped because Nexa was foreground. After restoring Chrome as foreground, floating Chat proposed `screen.scroll direction=down`; one owner review was approved. Nexa then reported “Android observed a screen change after the approved down scroll. No further action was taken.” The unobstructed post-action screenshot shows rows 3–29; the reset fixture showed rows 1–21.

**Evidence:** `.qa-results/qa2-fixture-restored-v1904.png`, `.qa-results/qa2-after-approved-fixture-v1904.png`, and `.qa-results/qa2-after-approved-collapsed-v1904.png`. Installed APK copy: `.qa-results/qa2-installed-v1904.apk`.

**Checks:** Focused Node regression run passed 60/60 across `nexa-screen-scroll-actions`, `android-planner`, and `direct-provider`; `NexaUiActionEvidencePolicyAndroidTest` passed 11/11 on the emulator; `git diff --check` passed (line-ending notices only).

**Still pending:** Repeatable dynamically grounded tap validation on a third-party app, the stable grouped Maestro report (the prior v1818 grouped run was a driver failure although affected flows passed individually), live provider-backed ACA mail/email and realtime voice acceptance, and release signing. No message was sent, no mailbox was read, and no Sarvam key was used.

**Next:** Continue with a safe third-party app tap/scroll scenario using fresh Screen Assist observations and one owner review per action. Keep the full ordered Maestro suite, ACA live validation, and release build as separate acceptance gates.

---
## Archived QA status — 2026-11-18 emulator v1818 continuation

Only `emulator-5554` was used in this continuation; the physical phone and emulator were not used together. The installed QA2 package is `dev.rilandev.personalaiassistant.physicalqa2` v`0.3.2026111818` / code `2026111818`. The local debug APK SHA-256 is `87A6C7957119116E98254CDE027C48911C682408BDBE862AC408CF08C0684D9D`. This is a debug QA artifact, not a public or release-signed APK.

**Maestro coverage:** All 19 flows in `.maestro/nexa` (01–12 and 14–20; no flow 13) passed individually on v1818. Experimental flows 10–13 passed individually. The cloud permission recovery, physical QA preflight/setup flows 00–02 and 16, and the QA2 floating-bubble setup also passed individually. JUnit evidence for the latest standalone runs is under `.maestro/results/qa2-v1818-x86_64/`; the final accessibility setup passed 1/1 in `final-screen-assist-setup-20261118/report.xml`.

**Grouped-run limitation:** The five-flow `core-suite/report.xml` run is not green: flow 05 passed, then the Maestro Android device server died during `viewHierarchy`/`deviceInfo`; the other four cases are harness `DeviceServerDiedException` errors, not app assertions. Each affected flow subsequently passed in its own standalone run. Therefore the scripts have individual pass evidence, but the grouped run must not be described as passing.

**Settings ANR recovery:** A later Flow 19 run exposed a real Android Settings ANR while enabling Screen Assist. The flow now waits through `Settings isn't responding` and retries the toggle once only if Android still reports it off. The updated flow passed 1/1 in `final-screen-assist-retry-20261118/report.xml`. The read-only battery smoke flow also passed 1/1 in `physical-debug-battery-20261118/report.xml`. After stopping Maestro's driver packages, Android again reported Screen Assist enabled and bound with no crash.

**External screen-read evidence:** Screen Assist is enabled and bound with no crashed service. A user-armed one-shot read captured only the Android Settings screen (`com.android.settings`, 120 nodes, `captured=true`, length 419). Nexa then showed “Screen text discarded from memory.” No screen text was included in the evidence or sent to AI. Metadata-only evidence is `.maestro/results/qa2-v1818-x86_64/settings-read-metadata.txt`.

**Still unverified:** a floating-Chat LLM request that initiates an owner-approved tap or scroll on an unrelated app; live provider-backed ACA/email parity and realtime voice routing; a stable ordered grouped Maestro run; and release signing. The exposed Sarvam key was not used. No message was sent and no email was read in this continuation.

**Next:** stabilize or split the Maestro runner lifecycle so grouped reports remain connected, then run one owner-approved floating-Chat action against a safe Settings page with a configured test provider. Keep ACA provider-backed acceptance and release signing as separate gates.

---
## Archived QA status — 2026-11-17 emulator continuation

Only `emulator-5554` is currently attached, so validation is continuing on the emulator until the physical phone appears in ADB. The installed Nexa debug app is `dev.rilandev.personalaiassistant.debug` v`0.3.2026111727` / code `2026111727`; APK SHA-256 is `4B63EF5D8286DE68C032C4CD9FA74C8E26894B0D5B2D89634E92147B53C2438E`. The app-tools bridge reports `NexaAppTools`, `rawAdbInput=false`, `engineLive=true`, and 25 tools. Both Nexa Debug Accessibility services are bound.

**Live action evidence:** A fresh Home observation and confirmed right swipe opened the Google Discover surface; the new observation showed the Google app and its offline “Couldn't connect to Discover” state. The left swipe from the Home edge was accepted but Android reported no visible change because there is no adjacent page on that side. A fresh Home target was used to tap Settings, which opened Android Settings. Fresh observed down and up scrolls there both returned `scroll_event_observed` and new snapshots. Notification shade open and Back were also accepted; the shade snapshot exposed no notification text or tappable targets. A stale snapshot was rejected before any gesture was dispatched.

**Automated checks:** Full `npm test` passes 669/669; focused screen-control regressions pass 59/59; the four tests covering the two corrected package-ID expectations pass 4/4. The two stale assertions now match the current single `.debug` app identity and Gradle variable refactor. The APK was already built from the current native fixes; this continuation changed tests and QA notes only.

**Still unverified:** the floating-chat LLM loop issuing a screen action, repeatability across unrelated third-party apps, and physical-device confirmation. No message was sent and no contacts, call history, or alarms were accessed. The previously shared Sarvam key was not used.

**Next:** keep using `emulator-5554` until the physical phone appears in `adb devices -l`; then confirm the serial before switching the same safe, owner-reviewed app-tools checks to that phone.

---
## Archived QA status — 2026-11-17 physical Infinix scroll evidence

Only the wired Infinix, serial 099344034S003788, was targeted; no emulator was used. The installed live-debug package is dev.rilandev.personalaiassistant.debug v0.3.2026111711 (code 2026111711). Metro is serving on port 8081 with the device reverse active. The debug Nexa bridge reports executor=NexaAppTools, rawAdbInput=false, engineLive=true, and 23 registered tools. Android reports both Debug and QA2 Screen Assist bound without crashed services.

**Root cause/fix:** Native scrolls can finish as scroll_event_observed even when Android has not independently established the final position. Planner completion logic accepted older generic statuses and rejected these native statuses, producing a false “did not verify” failure after an accepted scroll. The planner now reports the approved directional event with an explicit position-verification limit; structural/legacy change evidence is reported separately. Tap-event wording remains equally conservative. Owner-sequence evidence accepts the native scroll and structural-change statuses.

**Physical action evidence:** Nexa's device.open_app opened Android Settings. A separately confirmed downward screen.scroll used a fresh Accessibility snapshot. Before/after Settings snapshots changed from 15 to 12 normalized text lines, with 23 line differences after volatile status-bar values were excluded. Android returned actionAccepted=true, verification=scroll_event_observed, businessOutcomeVerified=false. This verifies visible movement, not an app-level task result. A separate down event at the list boundary did not change normalized screen text; the conservative event-only status did not claim that the page moved. From the Home launcher, one confirmed left scroll returned structural_change_observed and changed normalized text from 53 to 52 lines (97 differing lines). The right-scroll attempt first rejected a stale snapshot without a gesture; after a fresh observation, the foreground changed to another app and no scroll was issued there.

**Automated checks:** The three focused regressions pass for up/down/left/right; npm test passes 653/653; root, mobile, and agent-runtime typechecks pass; git diff --check exits 0 (Git reports existing line-ending conversion warnings). The app is using the live Metro bundle, so no APK rebuild was needed for these TypeScript changes. Prior same-day web export and 22/22 Android instrumentation results are historical and were not rerun in this session.

**Still unverified:** a fresh live floating-Chat/Sarvam request against the updated planner; a physical right scroll; repeated control across unrelated apps; and release-signed/public APK production. The physical vertical and left scrolls were invoked through Nexa's debug app-tools bridge, not through the floating Chat LLM loop. No message was sent.

**Next:** run one new owner-reviewed floating-Chat request on Android Settings and verify that the overlay reports the directional event without claiming a verified final position. Keep message-send checks separate from this safe scroll test.

---
## Archived QA status — 2026-11-17 emulator QA2 (latest dual-architecture rerun)

QA2 `dev.rilandev.personalaiassistant.physicalqa2` v`0.3.2026111708` (code `2026111708`) is installed on `emulator-5554`. Local and installed `base.apk` SHA-256 match: `DDF05F44C0813AD58FABF460D4782C53D2BC43940620A320D9094DA071D8DCD9`. The embedded JS bundle SHA-256 is `0F5968FB88AEC4219332885B935DF6B021FD070D79129D8C363CF516A4C4367A`; the APK contains the embedded bundle plus `arm64-v8a` and `x86_64` React Native libraries. It is a local debug QA APK, not release-signed. The physical Infinix was not targeted.

**Ordered Maestro result:** Flows 05, 17, 18, 19, then 16 passed 5/5 in 4m 53s using Maestro 2.12.1 and the exact installed APK. JUnit: `.maestro/results/qa2-v1708-dualarch-batch-final-20261117/report.xml`. Flow 17/18 confirm ACA fail-closed routing; Flow 19 enables Screen Assist; Flow 16 confirms its controls remain reachable. After Maestro exited, its two driver packages were stopped; Android then listed QA2 Screen Assist under `Bound services`, with `Crashed services: {}`. Nexa remained foreground. Post-suite screenshot: `.maestro/results/qa2-v1708-dualarch-post-suite-screenassist-enabled.png`.

**Screen Assist read:** On this exact APK, one local read of Android Settings succeeded (`com.android.settings`, 31 nodes, `captured=true`, length 16). The text was not opened, displayed, or sent to AI; Nexa showed its discard confirmation. Metadata-only log: `.maestro/results/qa2-v1708-dualarch-settings-read-metadata.txt`; discard screenshot: `.maestro/results/qa2-v1708-dualarch-current-screen.png`. This verifies one read, not AI-directed external tap or scroll.

**Source checks:** `npm test` 645/645; `npm run typecheck` passed for root, mobile, and agent runtime; `npm run web:export` passed. `git diff --check` passed after this status update (line-ending conversion warnings only).

**Still unverified:** an AI-initiated, owner-confirmed semantic tap and dynamic scroll on an external app; live provider-backed ACA email synthesis and realtime voice routing; release-signed/public APK production. ACA-origin flows remain at the local account gate because this QA build uses `https://aca-unconfigured.invalid`, so no email was read. No message was sent and no Sarvam key was used.

**Next:** verify the action loop against a safe external screen using the required per-action owner confirmation. For live ACA parity, use a valid isolated Development origin and test account; confirm realtime voice routing separately. Release signing remains a distinct workflow.

---

## 2026-11-16 — Nexa–ACA canonical business identity and parity

**Direction:** Owner approved ACA as the canonical business backend and now requires the same identity, database, accounts, agent/provider configuration, and backend service plane. Within each environment both clients use the same ACA records; Development and Production remain isolated. Nexa calls authenticated ACA APIs and never connects directly to PostgreSQL, Redis, RabbitMQ, Qdrant, or provider APIs.

**Verified from ACA Development source:** Mobile login verifies the same ACA email/password record and user ID through the existing PostgreSQL-backed auth path, then creates a separate revocable `nexa_m1_` device session. The current mobile feature flag and email allowlist fail closed. ACA Chat resolves the selected agent’s server-side runtime/configuration and uses its canonical business services.

**Important visibility behavior:** Existing ACA Chat conversations are organization-and-agent scoped, so authorized organization members may see them; they are not private to the creator. Nexa must preserve that policy and must not market its ACA conversation history as personal/private.

**Design and plan:** `docs/superpowers/specs/2026-11-16-nexa-aca-business-parity-design.md` and `docs/superpowers/plans/2026-11-16-nexa-aca-canonical-business-parity.md`. The plan adds an explicit Development API origin, reuses ACA’s full turn handler for read-only business access in both Nexa Chat and overlay, pins per-agent configuration, and keeps Android-only tasks in the existing native harness. Business mutations, voice, and reverse device control remain later gates.

**Implementation:** Nexa Chat and floating overlay route ACA business-read prompts through one versioned, read-only ACA Chat API. ACA reuses its existing identity, organization/agent authorization, conversation repository, business records, and server-selected agent/provider configuration. Nexa’s device token is separate and revocable; the phone receives no DB, Redis, RabbitMQ, Qdrant, or provider credentials. Local device tasks remain on Nexa’s existing native harness.

**Verification on 2026-11-16:** Nexa `npm test` 605/605; root/mobile/runtime typechecks and Expo web export passed. ACA Vitest 1,670/1,670 across 265 files and `pnpm typecheck` passed. Android instrumentation passed 273/273. Ordered Maestro business Chat and floating-overlay flows passed 2/2 on `Medium_Phone_API_37.0` (`emulator-5554`). No physical-device actions, mailbox reads, message sends, or provider-key use occurred.

**QA APK:** `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`, QA2 package `dev.rilandev.personalaiassistant.physicalqa2`, version `0.3.2026111605`, SHA-256 `005376F21FBECD9FCB2E486195AE3BC89683542227EB32AED1DC6808C6AF97DA`. This is a debug QA artifact, not a public/release-signed build. It uses `https://aca-unconfigured.invalid` so it cannot contact a business backend.

**Security gate:** The checked-in ACA Development PostCSS config contained an obfuscated loader that resolves a hardcoded Ethereum sender through public RPC/Blockscout, derives HTTP endpoints from transaction data, fetches XOR-encoded JavaScript, then evaluates and spawns it. This blob is identical on origin/Development. I replaced it in this local checkout with the plain Tailwind/autoprefixer config; the cleaned targeted test and full ACA suite pass. The earlier test run spawned detached node -i PID 7492 and had an established connection to 95.135.181.84:443; I terminated that process. Whether its remote second stage accessed local files or credentials is unknown. Treat secrets available to that test process as potentially exposed and rotate them. The remote branch still needs the fix and review before another fresh checkout/build.

**Live-parity gate:** There is no configured isolated ACA Development API origin, test identity, database, or local service deployment in this workspace. Source and synthetic tests prove the same-handler/auth/config boundaries and fail-closed behavior, but do not prove a live ACA request or mailbox summary.

**Next:** remediate the compromised origin/Development config and rotate any credentials reachable by the earlier process; then configure an isolated ACA Development deployment and synthetic test account, inject its Development origin into a fresh QA APK, and compare the same read-only prompt in ACA web Chat and Nexa Chat/overlay against canonical user/org/agent/config/conversation evidence. Keep writes, scheduling, WhatsApp sends, voice parity, and reverse device control deferred.

---

## 2026-11-15 — Multi-messenger static fixture and physical QA continuation

**Ready:** the no-provider static owner planner and production message-tool definitions cover WhatsApp, IMO, and BOTIM with exact app/recipient/text binding and a high-risk one-shot Send claim. npm test passes (595 tests, exit 0); focused fixtures pass 26/26; root TypeScript checks, Expo web export, Android app/test Kotlin compilation, and git diff --check pass. QA2 live-debug ARM64 APK v0.3.2026111517 (code 2026111517) was built at apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk; SHA-256 3B3C4EAB5D36ACE730A191C7292FBC462F70FF25DB6FE5FEA2111605D505F9A9.

**Physical test is pending:** Infinix serial 099344034S003788 remains on v1516. Read-only Android audio-policy output reports Nexa's VOICE_RECOGNITION AudioRecord active; the foreground changed from WhatsApp to Instagram during the source/build checks. Preserve that session; do not install/restart Nexa or control the phone until recording is inactive and the owner is ready.

**Next:** when safe, install v1517 in place without clearing data; verify Nexa Screen Assist and Launcher Navigation are bound; run the exact no-provider workflow through Nexa tools for Amma and Hi Mom i will call you later in each app. Keep the app's Send review intact; if owner approval is presented, wait for the user. Verify the exact visible message bubble only; never infer delivery. No live message was sent during this source/build phase.

This QA APK is not a signed release/public app. Do not bypass Android permissions or the app's Send review.

---

## 2026-11-08 — Current Development safety/build verification and remaining release gates

**Verified source commit:** `a2c4ec378e9385f68edd2d77fbea83148830ba29` (before these documentation-only notes). Development retains security fix PR #115 removing obfuscated bootstrap execution. No consumer `main` promotion or production service/database mutation.

Implemented: exact per-step WhatsApp conversation-header verification and owner-reviewed no-Send draft; owner-stop session and Pause-generation fence; owner-visible encrypted recipient-app preference lookup, correction/forget and clear-all controls. Extended the same one-shot native journal and atomic final dispatch fence to approved Chrome searches, Camera, SMS/WhatsApp/manual-messenger draft intents, Back, notification shade, launcher open and launcher swipes. Chat app-launch continuation now waits for **the exact visible Android package under the original Pause generation**; a missing Accessibility observation or paused/replaced session remains `not_observed`, never a JS-upgraded success. Native activity dispatch never proves a message was sent, browser research completed or a business task succeeded.

The pinned Maestro submodule `aaa248d7fd4ca4924444dc027af9c7a62a9be348` was initialized in the isolated Linux verification checkout; Gradle now fails clearly before Kotlin compilation if the submodule is missing. The existing live label-tap flow already uses the compiled `NexaMaestroTargetSelection` wrapper and original read-only `MaestroSafeSelector`, with Nexa retaining its sole native gesture executor.

**Exact-source results:** Node **421/421**, TypeScript, web export, `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:maestro-portable:testDebugUnitTest` all passed. Isolated **debug-only** APK SHA-256: `28c9f1aa31e3534d097c042c024e00b3b7cb33a6d63cc6ff11604392a0764add` (about 179 MiB); AndroidTest APK SHA-256: `5f4859d21bc0c724b3a0dee5710f8110c0313165210cf00add25dfd74ce23470`. The debug artifact is NOT a signed consumer update. No new real-device instrumentation or actual WhatsApp typing/recipient acceptance was performed for this SHA.

**Blocking external checks:** Windows BridgeHead/Infinix device unreachable via tailnet. Dedicated AlmaLinux API35 emulator exited with code **139** before boot; no Android instrumentation executed. Real Sarvam provider STS/voice, wake/overlay long-session, accessibility gestures in third-party apps, and signed physical-device release E2E remain **unverified**. `npm audit` observed 10 dependency advisories: **2 high, 7 moderate, 1 low**, including current Expo CLI/Zod 3; the automatic suggested major Expo SDK downgrade was *not* applied without compatibility evidence.

**Still unfinished source work (do not label done):** replace separate legacy Voice/Chat task executors with a single versioned Kotlin parent task/step authority for all eligible actions; durable cross-origin mandates, exact recipient/field binding and dependency tracking, observed multi-screen continuation, bounded scroll-until-visible with fresh claims per gesture, robust interruption/recovery; complete Sarvam managed-provider tool-result and audio acceptance, release kill switch/rollback/signer lineage, dependency advisory remediation and production upgrade/rollback evidence. Keep `NEXA_TASK_V2_ENABLED` off for production, no automatic Send or permission bypass. **Production remains NO-GO.**

---


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

## 2026-11-07 — Owner Pause/Resume action-generation race closed (Development)

- Development now binds the native owner Pause generation to prepared icon, scroll, generic text-field and recipient-message-field reviews. A Pause invalidates these approvals permanently even after Resume. A final synchronized generation check wraps the actual click, scroll, app-launch Intent or text mutation, with no late dispatch if the owner paused.
- The emergency-stop fence also guards semantic label taps, uniquely grounded WhatsApp chat-row taps, and launcher swipes. Existing native one-shot SQLite journal claims, exact reviewed text/recipient bindings, non-replay and no automatic Send remain unchanged. No model tool can call the owner Pause/Resume setter.
- Source checks for the combined Development milestone: 399 Node tests passed, TypeScript passed, Expo web export passed. Initial native Kotlin app and test-source compilation passed at `70eab05`. The subsequent exact-head debug/test APK assembly and on-device instrumentation must be recorded separately from those results.
- Remain open: unified general-purpose task/step execution across every Chat/Voice/overlay screen tool, evidence-based multi-screen continuation, strong recipient/conversation identity and delivery verification, cross-tool interruption and Voice recovery, complete owner memory correction controls, physical phone approval and microphone/provider tests, signed-current-revision release/rollback acceptance. Do not promote main, enable automatic sending, or claim full autonomous phone-control completion from this bounded safety fix.

## 2026-11-07 — Final debug-build evidence and device gate
- Source `d8a458c84da2` (with all owner-Pause generation-fence changes): 399/399 Node tests, TypeScript and Expo web export **passed**; local debug APK and Android instrumentation APK assembly **passed** using a disposable non-release signing identity, not CI signing.
- The local Android API 35 emulator exited with native code 139 on both SwiftShader and graphics-disabled startups before boot. On-device test execution **BLOCKED** by the emulator. Do not infer that the new native Pause/Resume instrumentation test passed just because test sources compiled.
- Current-source signed release APK, real Infinix approvals and Sarvam STS/voice acceptance, coordinated step executor, full multi-screen observation, identity-bound messaging and release rollback remain NO-GO. Earlier signed-APK emulator and Infinix acceptance on `fbda034` belongs only to that older source.
- Next concrete acceptance: run `NexaNativeActionSafetyAndroidTest`, no-replay UI audit and reviewed-text/message tests on a healthy Android device for the latest exact source; record device SDK/build fingerprint, APK hash and execution results before any `main` promotion. Do not use the disposable local debug APK as a consumer update.

## 2026-11-07 — Owner-grounded multi-tool Chat harness integrated (source milestone)

- New `createOwnerSequentialPlanner` reuses the existing `AgentHarness` for up to six literal owner-approved command steps; prevents partial execution when any clause is unsafe/unknown and advances only after bounded evidence. Sequence examples: `Check battery then check storage`, `Open Calendar then tap Search` (requires observed installed-app destination and fresh Screen Assist), and `Tap Search then type "notes" into Notes` (requires one approved empty field). It never performs Send/Submit.
- Chat now exposes opt-in `device.interact` and `device.observe` capabilities. Kotlin reuses existing Accessibility controllers and one-time action journal. The exact parent Chat session/call/arguments, saved owner approval and Pause generation are independently verified by `NexaAgentActionAudit.reserveReviewedUiStep` before each UI effect; one-time event prevents replay. Existing approval/Resume UI remains required for each sensitive step.
- Tests: complete Node **405/405 PASS**, TypeScript PASS and Android Kotlin plus instrumentation-source compilation **BUILD SUCCESSFUL** on implementation/security SHA `1617875e5ffc`. Native instrumentation has NOT executed on a phone; previous API 35 emulator segfault and no currently connected physical phone remain device acceptance blockers.
- This does **not** claim a general AI model-directed multi-screen app executor, Voice and overlay shared runtime, end-to-end WhatsApp recipient/message assurance, unattended approvals, or current-source release certification. Native Chat requires foreground owner review, and inaccessible third-party app windows fail closed. User requested development only, so production services and main remain unchanged.

## 2026-11-07 — Current APK device acceptance
- Current Development `d8a458c84da2c232377c0a559d766c81ea2c1096` has a successful signed release-test workflow `42278234419`; version `0.3.1800066544` is installed on the Infinix. Live Chat notification-shade and exact installed-app Clock launch were observed after owner approval, with Screen Assist bound.
- Exact-build full Maestro suites remain pending: Infinix's Maestro driver dies before step 1 under heavy memory pressure; the API 37 emulator went offline during APK installation when Windows free memory fell below 1 GB. Restore stable test-runner resources, then install this exact APK on the emulator and rerun main 14 plus experimental 4 flows. Do not count the earlier `fbda034` 18/18 result as a pass for `d8a458c`.
- Continue implementing and testing the broader dynamic screen agent separately. These bounded Chat actions do not establish arbitrary multi-step app control, silent observation, actual third-party text entry, automatic messaging, or voice-session continuity. No Sarvam live test was performed with the exposed key.
## 2026-11-07 — Physical suite green on signed d8a458c APK
- Infinix X6831 passed main Maestro 14/14 in one batch and experimental Maestro 4/4 in one batch on signed `d8a458c84da2c232377c0a559d766c81ea2c1096` APK. Screen Assist was restored through Android Settings after the suites and verified enabled and bound after stopping Maestro's driver; overlay remained allowed and Nexa was foreground.
- Phone Maestro driver workaround: manually install the embedded `maestro-app.apk` and `maestro-server.apk` from local `maestro-client.jar`, then run with `--no-reinstall-driver`. The ordinary reinstall path killed the driver at `deviceInfo` before any step.
- New Development source has landed after `d8a458c`; do not represent the tested APK as certifying those commits. Next exact-build gate: integrate current Development, run source/native checks, build a signed APK at one stable milestone, and repeat physical acceptance. The user requested physical-device-only testing; keep emulator stopped.
## 2026-11-07 - Maestro development continuation (isolated feature)
Pinned Maestro source is now compiled directly into the Nexa Android build through `:maestro-portable`; tested source models and a standalone **read-only** safe selector are present. The Git submodule source pin is `aaa248d7fd4ca4924444dc027af9c7a62a9be348`, and native Gradle compilation with the dependent app and instrumentation-test source succeeded. CI workflows that compile Android or run Maestro obtain this submodule on checkout.

**Next implementation milestone:** integrate the selector into Nexa's existing `NexaScreenLabelTap` target resolution under one still-live accessibility window and a consumed one-shot action journal, rather than adding a new gesture executor. Then implement a bounded `scrollUntilVisible` control loop on top of existing `NexaScreenScroll`, verify the observed result after each iteration and refuse to replay uncertain operations. Extend the existing `NexaTaskCoordinator` for multi-step Chat/Voice/overlay tasks with explicit owner grants and no external send without review.

Before merging: verify the exact-commit Android APK+instrumentation build, `npm test`, `npm run typecheck`, workflow YAML, emulator Maestro and owner-device behavior. Do not count a compiled test APK as a passed device test. Keep `main` unchanged.

## 2026-11-07 — Next Maestro integration development

The pinned Maestro models are now wired to the **existing** Nexa label-tap path through a bounded, read-only adapter; no new accessibility authority was added. Source tests (Node 406/406, TS, web export, native app+androidTest compilation) passed, and focused selector instrumentation passed 4/4 on an Infinix X6831. The prior installed debug APK passed one Chat/battery Maestro flow after Metro was enabled.

The full natural-language Android agent remains incomplete. Continue in this order: (1) avoid conflicting physical-device installations and restore owner-managed Screen Assist + overlay permissions; (2) verify a single real externally approved tap from the **same exact source APK**; (3) add bounded inspect-scroll-verify with one per-gesture journal claim; (4) connect approved multi-step tool envelopes to the existing NexaTaskCoordinator without a second executor; (5) test Chat and floating overlay with safe app/field actions, preserving unsent messages and clear failure recovery.

Physical test caveat: the .debug package disappeared after the focused instrumentation run while .qa and base packages were present, so a following Maestro Chat test failed at package launch. No full device-control acceptance until package state and permissions are stabilized. Do not merge to main or claim all Maestro features migrated. A host-side Maestro CLI remains separate from normal Android APK permissions.

## Current continuation (2026-11-13)
- Recovered phone ADB connection and built/installed a side-by-side debug APK on Infinix X6831.
- Fixed the syntax error in the unfinished Nexa setup screen edit. TypeScript passes.
- Remaining: enable/rebind Screen Assist for the installed debug package via Android Accessibility Settings, then verify read-only screen observation plus approval-gated navigation across an app. Current `dumpsys accessibility` shows no bound service, so do not claim cross-app control is working yet.
- Debug app was force-stopped after check; emulator remains off.

## 2026-11-13 continuation result
- Infinix X6831 only; no emulator started.
- Fixed setup screen bottom inset hiding optional setup controls under the sticky Continue footer. Typecheck passes.
- Experimental Maestro suite passed 4/4 (3m58s) against physical device using corrected Flow 10 and permission-compatible flow setup. Owner review/message flows did not send messages.
- Existing app-launch scenario passed on the updated debug client: owner approved `device.open_app` for Clock; “World Clock” was observed.
- Screen Assist is enabled and bound after Maestro exits. No service crash reported.
- Still pending: explicit external-app screen read/scroll/tap verification. Do not claim general arbitrary-app control until that evidence exists.

## 2026-11-13 — Screen-read continuation
- Physical Infinix X6831 only; no emulator used. The phone initially appeared as ADB serial `099344034S003788`, then disappeared after Settings navigation. ADB server restart and device re-enumeration found no phone; Windows only reported a USB HID/composite peripheral, not the Android ADB interface.
- Accessibility Settings was reached. The first duplicate “Nexa Screen Assist” entry enabled `dev.rilandev.personalaiassistant.physicalqa/dev.rilandev.personalaiassistant.ScreenAssistService`; Android reported it bound with no crash. The `.debug` package service was not enabled, so this does not validate the current debug APK's screen-read path.
- Source review confirms visible-text reading requires owner approval and returns bounded, visible, noneditable labels; password/editable nodes are excluded. No screen text was read or retained, and no message was sent.
- Local checks: `npm test` 415/415 passed; `npm run typecheck` passed; `:app:assembleDebug :app:testDebugUnitTest` and `:app:assembleDebugAndroidTest` succeeded. Android test sources compiled, but device instrumentation and Maestro could not run after USB loss. `testDebugUnitTest` is `NO-SOURCE`.
- Fresh local debug APK: `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk`, package `dev.rilandev.personalaiassistant.debug`, version `0.3.2026111301`, SHA-256 `E50DA7EFB5E31EA246C6F3EA8766B109A53DA76DDFD9BF44EC0C947B37E3CD64`. It is built but not installed on the phone.
- Resume when the Infinix ADB transport returns: install this APK; enable the matching `.debug` Screen Assist component in Android Settings; stop Maestro before checking `dumpsys accessibility`; open a safe external app and perform one approved, read-only screen-label observation through Nexa; then rerun the ordered four experimental Maestro flows on the same physical device. Do not send messages or use an emulator.
## 2026-11-07 — Bounded scroll search follow-up gates

Feature `feature/nexa-bounded-scrollsearch-20261107` adds a finite local owner scroll-search guide, a unique-container requirement, and an existing-journal-only confirmation/evidence loop in floating Tools/Chat. There is no unattended tap, voice automation, messaging, or new Android permission authority.

**Before merging this feature to Development:** finish the unique-container/stale-approval Node checks, run complete `npm test`, `npm run typecheck`, `npm run web:export`, pinned Maestro portable JUnit, Android app plus test APK compilation/instrumentation, and exact APK/signer verification. On a physically connected Infinix with user-granted Accessibility/overlay permissions, test a real unique target, absent target, duplicate label, wrong window/container, Pause during acceptance, cancel, max four scrolls, and no accidental Send. Preserve owner data; never clear app storage or grant OS permissions behind the owner's back.

BridgeHead Windows Tailscale connection was interrupted during submodule checkout; ADB had no attached device. No physical-device acceptance can be claimed from source tests or a compiled APK alone. Existing unrelated general dynamic task executor, cross-interface Voice/overlay planner and fully verified WhatsApp messaging remain separate release gates; no automatic outbound send.

## 2026-11-07 — Updated release gate after on-device tests

Completed since the previous note: npm **420/420**, TypeScript checks, Expo web export, Maestro portable JUnit **9/9**, arm64 debug APK plus instrumentation APK assembly, same-app in-place debug upgrade, and **11/11 synthetic tests running on the physical Infinix**. The approved Android Accessibility and overlay grants persisted. See WORKLOG for exact evidence.

**Only the live screen-control acceptance remains unresolved for this bounded-scrolling milestone:** the phone was asleep and securely locked (`deviceLocked=1`, NotificationShade foreground), so the external Maestro CLI could not find Nexa's Chat control. Unlocking must occur through the normal owner-controlled device flow; do not bypass the lock or weaken Android security. With a freely accessible phone, verify the actual floating Chat/Tools screen, request `scroll down until [safe label]`, accept each native scroll separately, confirm observed target after real scrolling, and exercise failure/cancel/Pause/no-send cases. Until successful, PR #117 remains open and **live floating/third-party scrolling is NO-GO**, despite compiled code and synthetic instrumentation.

Full general-purpose agentic multi-app execution, richer Maestro command portability and voice/overlay continuity are separate engineering milestones; the entire desktop Maestro CLI cannot execute inside a non-root Android AccessibilityService.

## 2026-11-08 — Precise pending implementation and acceptance gates (PR #117)

New source milestones are committed but **not yet native-compiled**: scoped owner-relative tap grammar and overlay approval, native anchor-based target selection and final current-window revalidation, and pure Maestro `above/left/right/childOf` plus 1–100% viewport selection. The existing owner-native journal/Pause fence and no-send policy remain mandatory.

**Immediate execution once Windows BridgeHead is reachable:**
1. Refresh remote `Development` and PR #117 heads, reconcile concurrently landed changes without overwriting existing work or force-pushing another developer's branch. Preserve existing debug signer, installed app data, untracked tests and private host logs.
2. Fast-forward the isolated Windows worktree; `git submodule update --init`, verify pinned SHA, `npm ci --ignore-scripts` in isolated directory where required, then run all new/old Node tests, `npm run typecheck`, web export and `:maestro-portable:testDebugUnitTest`.
3. Compile exact-source arm64 `:app:assembleDebug :app:assembleAndroidTest`; prove APK package/signing identity; install with `adb install -r -t` only when safe, never `uninstall` or `pm clear`.
4. Run `NexaMaestroRelativeTapAndroidTest`, `NexaMaestroTargetSelectionAndroidTest`, `NexaBoundedScrollSearchAndroidTest` and all other related synthetic instrumentation on the same actual installed APK.
5. With the owner normally unlocking the Infinix, run read-only Maestro CLI inspection plus approved floating Chat actions: `tap Open below Recent` in a controlled external test screen, ambiguity, anchor movement, app/window switch, Pause, and no automatic messaging; verify actual outcome after each action. A lock-screen scenario must explicitly fail closed.
6. Merge to `Development` only after passing the above exact-head gates, with documented build, rollback and reviewed PR diff; keep `main` release unchanged until independent acceptance.

**Remaining higher-level work after this milestone:** ephemeral full Accessibility hierarchy and scoped `childOf` in the live tap path (currently only in pure selector), motion-stable nested scrolling in actual apps, one canonical durable Chat/Voice/overlay multi-step executor, realistic messaging recipient/composer verification without automatic Send, screenshot-assisted confidence under explicit consent, and real-provider/release acceptance.

The Windows runner returned `PEER_UNREACHABLE` through the tailnet SOCKS5 proxy during this feature revision. The previous 420/420 Node and 11/11 on-device passes were from an *older* feature commit and **must not be reported as verification of the new 2026-11-08 changes**. Later `Development` commits also arrived concurrently during a proposed merge; the merge was **aborted before Git mutation** instead of overwriting their updates.

## 2026-11-13 — Updated no-go gates after physical Nexa/Maestro testing

**Verified for the feature revision at `3f681bb`:** 438/438 Node tests; latest arm64 debug and AndroidTest Gradle build pass; synthetic physical Chat/WhatsApp-style navigation and text-entry tests passed individually (three cases) without Send; previous 17/17 physical Maestro selector and bounded-scroll instrumentation passes; real Maestro CLI read-only Chat/battery flow passed on earlier compatible debug build. App/source references and exact logs are in the Windows `work` folder and this Worklog.

**Unverified:** live floating overlay controlling third-party apps, scoped parent/descendant selectors in the actual Accessibility service (only portable-library pure selector implements `childOfText`), stable nested `scrollUntilVisible` over third-party lists, one canonical durable Chat/Voice/overlay multi-step executor, real WhatsApp recipient selection and unsent composer checks, vision/occlusion acceptance, installer signer/release rollback and production certification.

**Blockers:** Android owner has not enabled Screen Assist/overlay for the actual installed debug package and may need to grant through Android Settings; do not use ADB to bypass. UI automation harness with consecutive fixture tests still exhibits device-level connection races; prefer individually verified synthetic tests and disposable emulator once memory is available. Newer Development commits keep arriving; reconcile remote refs without force pushing or replacing user-owned dirty main Windows checkout.

**Promotion:** Feature PR #117 remains a draft and production/main are unchanged. A synthetic physical test is not equivalent to owner-reviewed live third-party app control. Verify exact-source compilation and real OS consent before release.
 
## Latest continuation (2026-11-13)
- The four ordered experimental Maestro flows pass on the Infinix; JS suite 420/420 and TypeScript pass. Current installed debug APK is `0.3.2026111306`.
- Resolved the service-binding blocker: stale `dev.mobile.maestro` instrumentation retained global Ui Automation after its flow ended. Stopping that test runner caused Android to bind both Nexa Screen Assist instances; current Accessibility dump has no crashed services.
- Remaining runtime acceptance: owner-confirmed navigation into Tools and owner-confirmed one-shot read of a safe external screen (Calculator), then return to Nexa and verify successful capture without revealing captured text. Do not send messages, use an emulator, or claim general arbitrary-app control from this single read.

## Latest status — 2026-11-13 emulator continuation
- Ready: physical Infinix Flow 10–13 batch 4/4; Node 420/420; TypeScript; debug APK `0.3.2026111306` installed on `emulator-5554` (API 37).
- The emulator repeatedly shows Android SystemUI ANR before Maestro can interact. No additional emulator flow is green yet. The full physical batch is the successful four-flow evidence.
- Direct one-shot Screen Assist read is still unverified. The Arm button succeeded and stock Calculator was opened on Infinix, but review status did not confirm capture; no text was emitted or sent.
- To continue emulator QA, clear the current system dialog only after the user confirms the pending `Wait` tap. Then retry Flow 10, stop the Maestro runner, verify Screen Assist is bound, and exercise a one-shot read without exposing text. No messages.

## Latest status — 2026-11-14 physical-device continuation
- Completed on physical Infinix X6831 / Android 13, serial `099344034S003788`; no emulator target was used.
- Ordered experimental Maestro suite: **4/4 passed** (Flows 10–13), results `.qa-results/maestro-physicalqa2-ordered-final/2026-11-14_005223`.
- Direct Screen Assist one-shot read: **passed** on OEM Calculator package `com.transsion.calculator`; Android Accessibility reports the QA2 Screen Assist service enabled and bound with no crashed services. Captured contents stay private and are not sent automatically.
- Installed isolated QA2 app is version `0.3.2026111406`, package `dev.rilandev.personalaiassistant.physicalqa2`, SHA-256 `759CE602105C3458978B27FF140F677B59D7F38720733A5C2342C8233DF5FA8E`. This is a debug QA artifact, not a public release APK.
- Repo checks: `npm ci` passed (0 audit findings); `npm test` **489/489**; `npm run typecheck` passed (root, mobile, agent runtime); `npm run web:export` passed.
- The previous 2026-11-13 emulator and unverified-read entries above are historical and superseded by this result. Current remaining live acceptance: one individually approved scroll in Calculator; get a separate approval before every tap. No messages were sent and no Sarvam credential was used.

## Follow-up after approved Calculator scroll — 2026-11-14
- The approved one-swipe Calculator check was executed, but the displayed Calculator screen did not move; do not count this as a dynamic scroll pass.
- Android Accessibility is currently off (`accessibility_enabled=0`) and no enabled service is listed. The installed app still reports QA2 version `0.3.2026111406`; no current binding is claimed.
- Maestro CLI is unavailable on this host, so the existing Flow 10 cannot be rerun from this workspace right now. Next: restore Screen Assist using the established Maestro/Android Settings consent flow, then use a safe scrollable third-party screen for the approved single-scroll observation. Obtain separate confirmation before any tap.

## Next physical-device action (2026-11-14)
Current state: QA2 `0.3.2026111407` is installed on the Infinix; Flow 10 and ordered experimental Flows 10–13 pass; Screen Assist is currently enabled and bound with no crash. The app's `SYSTEM_ALERT_WINDOW` grant is not enabled. Before testing floating Chat scroll: open Android's overlay-access screen for the QA2 package, obtain the owner's individual approval before enabling that setting, then use a scrollable safe external screen and request approval for each scroll. The previous approved Calculator swipe caused no visible scroll. Do not claim dynamic scroll is accepted until a visible external-screen change is verified. No messages were sent and no Sarvam key was used.

## 2026-11-15 — ACA unread-summary live acceptance
- Completed on `emulator-5554` from the already saved, server-revalidated ACA device session. The exact 24-hour unread request returned successfully with **0 matched / 0 included** from `local_mail_database`; the oldest mailbox sync was about 12 seconds before the report cutoff. No password was used, and no email was drafted or sent.
- The requested 24-hour read-only check is complete. Broader ACA acceptance remains separate: compare equivalent counts against ACA web for the same authorized source, then exercise all-history and revoked/disabled/offline cases before claiming full ACA release acceptance. Do not use the previously pasted password; any future login must use a freshly rotated credential entered privately in Nexa.
- Also completed the all-message 24-hour read-only request on that same session: **0 matched / 0 included**, `local_mail_database`, oldest sync about 0.45 seconds before cutoff. Both requested 24-hour reports returned successfully and contained no email rows. No subjects, senders, or bodies were copied; no email was drafted or sent.

## Latest action — ACA mail routing and live comparison
Ready: local Nexa mail-routing fix; focused tests 15/15, full tests 547/547, typecheck, web export, and debug QA2 build/install all pass. The emulator confirmed “Check my recent emails” and the exact last-24-hours unread summary both reach ACA directly and return 0 matched / 0 included. Unsupported custom filter is refused before query; email write request is refused. No email content was copied to logs and nothing was sent.

Pending:
1. Sign in privately in the open Chrome tab group ACA Read-only QA. Then run the exact prompt “Summarize unread emails from the last 24 hours” in ACA Live Chat and compare matched/included counts, UTC range, oldest sync time, and partial-coverage status against Nexa. Keep subjects and bodies out of logs.
2. If an installable update of the main release-signed package is needed, obtain an authenticated GitHub Actions dispatch for the exact Development source. The local debug signer cannot update the installed release-signed app; it remains installed and untouched. The side-by-side QA2 build is debug only, not a public release.
3. Full ACA shared conversation history is not part of the current mail-summary bridge: Nexa uses the same authorized mail evidence/report path but keeps chat history local. A shared ACA conversation requires the separate mobile chat/history API phase.

## Follow-up — ACA all-history unread check
The emulator QA2 build also passed “Summarize all my unread emails”: 50 matched / 50 included, full synchronized-data coverage, oldest sync about 10 seconds before cutoff. No message content was copied into logs. ACA web Chat remains untested because its tab is still at sign-in; complete private sign-in in ACA Read-only QA before comparing the same prompt.

## 2026-11-15 — bounded ACA reply and live-voice gap
Ready: Nexa Chat no longer appends the entire ACA report. It replies with the matched count/scope, at most two subject/sender highlights, partial-result status, and the local-sync caveat. It does not return email bodies, mailbox addresses, links, or UTC timestamps to chat/read-aloud, and it does not send email content to a BYOK model. Verification: focused ACA tests 20/20, full npm test 554/554, typecheck and `git diff --check` pass. QA2 `0.3.2026111503` built and installed on `emulator-5554` (SHA-256 `461E9695E85C88BAA1FD108D541821BBF80309850629DDD38AE6D6B22E3BB523`).

Pending:
1. Continuous Android live voice still has no ACA mail-summary route; the bounded reply currently applies to Nexa Chat and read-aloud of its reply. Add a read-only live-voice bridge only with an explicit, owner-visible rule for whether the short subject/sender preview may be processed by the selected speech provider; do not forward message bodies.
2. ACA web Live Chat comparison remains pending private sign-in in the open `ACA Read-only QA` tab group. Run the same 24-hour prompt after sign-in and compare only counts, scope, freshness, and partial status.
3. QA2 is a debug test package, not a new release-signed APK. The physical Infinix and main release app remain untouched.

## Latest — human-style ACA email summary (2026-11-15)
Ready: configured-provider ACA Chat summaries synthesize at most 20 sanitized excerpts (450 characters each) into a short spoken-style brief. Counts, sample coverage, and ACA sync caveat remain authoritative app text. No-provider/provider-failure cases retain the deterministic subject preview. Settings discloses that bounded excerpts go to the saved provider only for a requested content summary.

Verification: focused ACA summary tests 11/11; full test suite 563/563; typecheck and `git diff --check` pass. QA2 `0.3.2026111505` (code `2026111505`, SHA-256 `1351830B87ED454CE5285F308EAE58A7F423444031EB84DBF3E8EE79825CE637`) is installed on `emulator-5554` and opens to Chat without a captured app crash. Ordered experimental Maestro flows 10–13 pass 4/4 (`.qa-results/maestro-email-summary-qa2-v1505/report.xml`); a final standalone flow 10 passes 1/1 and restores Screen Assist, which Android confirms enabled and bound with no crash.

Pending:
1. The QA2 instance currently reports “Private local assistant,” indicating no Nexa BYOK provider is saved. Save the intended provider in Settings, then run a private end-to-end summary on authorized ACA mail; no email content was queried or sent to a model during this verification.
2. Continuous realtime voice still bypasses the ACA summary route; current scope is Chat and reading the last reply aloud.
3. ACA web Live Chat count/freshness comparison still needs a private signed-in browser session. QA2 remains a debug test package, not a public or release-signed APK; the physical Infinix and main release installation remain untouched.

## Latest — concise ACA summary refinement (2026-11-15)
Ready: configured-provider summaries now request a 1–2 sentence, 30-word gist; final Chat/read-aloud response is capped at 60 words, while preserving exact match/coverage and sync caveats. Deterministic subject-only fallback remains available when a provider is not saved or fails. Verification: full Node suite 565/565; typecheck, web export, and `git diff --check` pass. QA2 `0.3.2026111506` installed and launched on `emulator-5554` (SHA-256 `76C962C5B041CE5252C946B9288A64AF60BFE537478AE2CF94665608DF310F20`).

Pending:
1. The emulator's saved ACA session expired; reconnect privately in Nexa Settings to run a live read-only summary. No mailbox rows were returned in the attempted QA request. No Nexa BYOK provider is saved, so model synthesis also needs the intended provider configured before live summary acceptance.
2. Continuous realtime voice still has no ACA mail route; current behavior is Chat plus “Read last reply aloud.”
3. Maestro flows 10–13 passed on `0.3.2026111505`; they were not rerun on this JS-only `0.3.2026111506` refinement. The release-signed/public APK and physical Infinix remain untouched; the new install is QA2 debug only.
4. ACA web Live Chat comparison still requires the owner to sign in privately in the ACA Read-only QA browser session.

## Latest — ACA reconnect and v1513 emulator verification (2026-11-15)
Ready: the reconnected ACA session returned a read-only unread-email summary on the installed x86_64 QA2 v0.3.2026111513 build. The response used Nexa's deterministic subject-only fallback and retained the sync caveat; no expired-session error appeared. The saved Sarvam test key has been removed, and the latest live model-provider request failed generically, so configured-provider LLM synthesis after the sentence-cap fix remains unverified.

Verification: full serial Node suite 576/576; root/mobile/runtime typecheck; Expo web export; experimental Maestro Flows 10–13 4/4; Flow 16 Screen Assist controls 1/1; final standalone Flow 10 1/1. After Maestro exited, Android reported Accessibility enabled and QA2 Screen Assist enabled/bound. The test artifact is `.qa-results/maestro-qa2-v1513/app-debug-v1513-x86_64.apk` (SHA-256 `497A104D6938A09D37D8FC5C8ABCDFF103D3323FFDFC720FD011123DD8A4506B`). It is debug QA, not a public or release-signed APK.

Pending:
1. Recheck configured-provider email synthesis only after a fresh, valid provider credential is privately stored; do not reuse the removed exposed test key.
2. Realtime voice still lacks an ACA mail-summary route, and Nexa Chat does not yet share the ACA Live Chat conversation history.
3. No release-signed/public APK was produced. The exact tested artifact is the separate x86_64 QA2 APK linked above; the default Gradle output was later rewritten by an ARM64 build and is not the artifact certified by these Maestro results.
4. Review or manually remove the noted QA diagnostic screenshots/hierarchies if the short subject-only preview should not remain in the workspace. Automatic approval review rejected automated deletion of the temporary pulled APK (`.qa-results/qa2-installed-base.apk`, reason `blocked by policy`); no further deletion was attempted.

## Latest — ACA reconnect and Screen Assist emulator continuation (2026-11-15)
Complete: ACA's read-only unread-email request returned on QA2 v0.3.2026111513 without an expired-session error; Nexa used its short no-provider fallback and retained the sync caveat. Emulator Screen Assist was then verified directly: the service was bound, a one-shot read was armed from the visible Tools screen, Android Settings became foreground, and the service log recorded `package=com.android.settings nodes=120 captured=true`. The captured screen text was not displayed or sent to AI; Nexa's Discard control confirmed “Screen text discarded from memory.” Metadata-only evidence: `.qa-results/qa-v1513-screenassist-read.log`; discard UI evidence: `.qa-results/qa-v1513-discarded.png`. Emulator `emulator-5554` remains running with Nexa Tools foreground. Physical Infinix was not targeted.

The exact QA2 x86_64 v1513 artifact remains `.qa-results/maestro-qa2-v1513/app-debug-v1513-x86_64.apk` (SHA-256 `497A104D6938A09D37D8FC5C8ABCDFF103D3323FFDFC720FD011123DD8A4506B`). Earlier on this artifact, Node tests passed 576/576, typechecks and Expo web export passed, and ordered Maestro Flows 10–13 passed 4/4. This is a debug QA build, not a release-signed APK.

Pending: configured-provider email synthesis needs a fresh provider credential privately saved; no ACA web Live Chat comparison was run in this continuation; realtime voice still lacks the ACA mail-summary route and shared ACA chat history; broad arbitrary-app tap/scroll acceptance remains unverified; no public/release-signed APK was produced. Do not reuse the removed Sarvam test key.

## Latest — physical Home-screen swipe acceptance (2026-11-15)
Complete for this milestone: QA2 `0.3.2026111515` is installed on physical Infinix `099344034S003788`; a fresh Sarvam overlay request planned one left swipe, Nexa requested owner review, the owner approved once, and Android's observed Home page changed from page 1 to page 2. Evidence: `C:\Users\suajt\AppData\Local\Temp\paa-infinix-home-swipe-passed-v1515.png`. The launcher traversal fix prunes invisible hierarchy branches while keeping a strict bounded traversal and fail-closed incomplete-tree behavior. Full Node suite 578/578, TypeScript checks, and `git diff --check` passed. APK SHA-256: `341E40049D65B6264A3CCFDB9E6C5E7CA0B2790A892EDCB4130AC93075A70570`.

Still pending outside this acceptance: repeatable dynamically grounded tap/scroll in other apps; the broader ordered Maestro suite on the exact v1515 source; live voice/multi-step cross-app acceptance; release signing and public distribution gates. The user’s per-action review requirement remains: fresh approval before each tap or scroll. No other app or external data was changed in the Home swipe test.

## Latest — physical live-debug loop ready (2026-11-15)
Ready on the Infinix: QA2 `dev.rilandev.personalaiassistant.physicalqa2` v`0.3.2026111516` is installed in live Metro mode, retaining QA2 app data. APK SHA-256: `BB0C54F6CE0E8DCDB262A4299E6CC094440625C6E6C225E3D053D54F1FE36C48`. Metro/Fast Refresh was verified with a temporary source-only probe; its updated marker appeared in Android logs without an APK install, and the probe has been removed. The Metro server is currently running.

To resume after Metro stops, from the project root run `.\scripts\qa\start-physical-live-debug.ps1` in PowerShell. It defaults to physical serial `099344034S003788`, checks that only the requested serial and QA2 package are used, and sets up `adb reverse tcp:8081 tcp:8081`. JS/TS edits hot-refresh; native Kotlin/manifest/resource/dependency edits still require one native rebuild and in-place install. Full Node tests 579/579, TypeScript checks, and `git diff --check HEAD` passed. The emulator is off.

## Follow-up — live QA2 Accessibility rebind remains pending (2026-11-15)
The live Metro setup and verified JS Fast Refresh remain ready. After the existing QA2 Settings flow, Android reports both QA2 Accessibility components enabled and no crashes, but neither service is bound. The flow ended on a Launcher Navigation selector assertion; its final screenshot/hierarchy is under `C:\Users\suajt\.maestro\tests\2026-11-15_175617\`. The phone then showed BOTIM `BRTCActivity`, so no more device UI actions were taken. Once the phone is available on a safe Nexa/Home screen, rerun service-binding verification before any cross-app screen-control test. Emulator is stopped; only the Infinix is attached.

## Latest — conversation/Android diagnostics review (2026-11-15)
Findings: no conversation JSON export exists in the workspace or QA2 export cache; the source database `custom.db` is private, and the app's schema-v3 JSON export includes decrypted conversation text. No transcript was extracted. Available Android evidence shows zero crash, ANR, or native-signal markers in saved crash/device logs; QA2 system exit history contains only 16 `USER REQUESTED / FORCE STOP` records (0 crash, 0 ANR).

The latest physical Settings Maestro JSON trace fails on step 36 (`android:id/switch_widget` not found). Android showed both QA2 Accessibility components enabled, none bound, and no crashed service. The physical serial then disappeared from ADB, so binding could not be rechecked. Plan: obtain the JSON export once the phone is available; make the Settings flow tolerate its post-consent navigation; verify Android binding after Maestro releases UiAutomation; gather fresh package-specific diagnostics before any native service fix. No app source fix is justified by the current crash/ANR evidence.

## Physical reconnect update — diagnostics rechecked (2026-11-15)
The Infinix returned on `099344034S003788`. QA2 Screen Assist and Launcher Navigation are bound, with no crashed services. Latest QA2 logcat/ActivityManager checks show 0 fatal/native crash markers and 0 ANRs; Android exit history contains only 16 user-requested force stops. Nexa is currently an active microphone client and has no focused window, so I did not open Nexa or interrupt the session. No conversation JSON export exists yet; the encrypted/private DB is present. The emulator is off and ADB lists only the Infinix.

When Nexa is foreground and the voice session has ended, export its JSON locally and inspect it without sending it to another app. Continue the Settings flow fix separately; the previous step-36 selector miss was not a crash.

## Previous checkpoint — WhatsApp static fixture before Send support (2026-11-15)

At this checkpoint the fixture covered only open-chat and draft entry; Send was absent and the three-step focused test passed. This status is superseded by the current reviewed Send workflow below. The physical phone had WhatsApp installed, but the Nexa voice session was left undisturbed and no real chat/message was accessed.

## Current — reviewed WhatsApp tool-call workflow (2026-11-15)

Ready: the static scenario now exercises the full synthetic WhatsApp chain through Nexa's `AgentHarness`: open WhatsApp, verify the exact chat, fill the exact draft, then invoke the new dedicated high-risk `screen.send_whatsapp`. The provider fixture is tool-call-only (`content: null`), checks per-step execution/status evidence, and does not use a live key or a real contact. Native send revalidates recipient, draft and one unique adjacent Send button, claims a digest-bound no-replay journal under the pause fence, and only reports that the exact message is visible in the conversation; delivery is not claimed.

Verified: focused suite 26/26; full Node suite 589/589; root/mobile/runtime typecheck; Expo web export; Android app and instrumentation Kotlin compilation; `git diff --check`.

Pending: Android instrumentation has been compiled but not run, and the QA APK has not been rebuilt or installed. Nexa QA2 was still an active microphone client, so no app, contact or live message was touched. When the voice session is over, run the Android instrumentation on the physical Infinix, build/install the current native source once, then validate only with explicit in-app review of the exact recipient/text. The static fixture already passes; it intentionally will never send to a real contact.

## Current action — 2026-11-16 physical multi-messenger run

Ready: QA2 Screen Assist and Launcher Navigation were bound on the Infinix, and the Nexa Tools grants for app launch, reviewed screen actions and visible-control metadata are enabled. The exact static owner-sequence path passes its focused local tests 19/19.

Pending: the physical serial `099344034S003788` disappeared during the live run and is no longer in ADB. A separate detached QA runner has restarted the emulator; no emulator test will be used, and I have left that process alone. The corrected task prompt was not submitted; no target app opened and no messages were sent. Reconnect the Infinix with USB debugging on and the screen awake, then resume the exact `Amma` / `Hi Mom i will call you later` flow in WhatsApp, IMO and BOTIM, verifying each recipient, draft and visible sent bubble before reporting.

## Current continuation — 2026-11-16 physical QA2

The Infinix is online and the prior phone call ended. The static overlay request reached the local owner-sequence planner, WhatsApp Search opened, and text entry failed; no chat was opened and no message was sent. Nexa's safe field scan found no named empty input. A read-only UI hierarchy check found the WhatsApp Search `EditText` focused and enabled, with the `com.whatsapp:id/search_input` resource; screen text was not emitted. Focused local checks pass 44/44 and mobile typecheck passes.

Next: build the existing isolated Live QA2 debug variant (`paaIsolatedPhysicalQaV2=true`, `paaLivePhysicalQaV2=true`) from the current local source and install only to serial `099344034S003788` using `adb -s`. Metro is already running locally on 8081 with reverse forwarding. Confirm the live bundle is loaded, rerun the exact static WhatsApp sequence once, and use the newly surfaced safe native error category to fix field matching. Then verify the full recipient/draft/send flow one app at a time for WhatsApp, IMO, and BOTIM; confirm each visible sent bubble and never infer delivery. Keep emulator untouched.

## Current — Full Access approvals mode (2026-11-20)
Ready and verified: Chat, floating overlay, and supported live voice can use persisted Full Access to skip routine Nexa tool-approval waits for registered tools after native authorization. Android instrumentation passed 2/2 on `emulator-5554`, including outbound-action blocking and Pause/mode checks; Node suite 708/708, typecheck, and web export pass. Debug APK: `apps/mobile/android/app/build/outputs/apk/debug/app-debug.apk` (x86_64 only, code `2026112011`, SHA-256 `6F7F9672548D1F66A036227A0184D402D1319E9A553E2B38ADB7113E75CB0E00`). Physical phone was untouched.

Still enforced by policy: exact owner review for message, call, and payment actions; Android runtime permissions, Screen Assist consent, unlocked state, and owner Pause. Remaining outside this feature check: no public/release-signed APK was made, and no live provider-backed cross-app task was exercised.

## 2026-11-20 — Physical Infinix Full Access install and smoke test
- Built and installed the current ARM64 debug APK on physical serial `099344034S003788`: version `0.3.2026112012`, code `2026112012`, SHA-256 `195CD5B9E530C87D7538840ADDC89035AAA914EEAD57A9AB4ADC9BDC30EAA0B6`. Android reports `primaryCpuAbi=arm64-v8a`; no emulator was targeted for these steps.
- Ran `NexaFullAccessPermissionAndroidTest` on the physical phone: `OK (2 tests)`. It verifies Full Access auto-approves routine device actions, keeps send/call/payment fenced, and respects Pause and the selected mode.
- Started the local Expo Metro server and USB reverse on port 8081 because the debug APK does not bundle the JS bundle. Nexa's bridge reported `nexa-qa-v1`, `NexaAppTools`, `bridgeAvailable=true`, `engineLive=true`, 25 tools, and `rawAdbInput=false`.
- `device.battery_status` completed through `nexa.ps1` without the CLI `-Confirm` flag: Android BatteryManager reported 24%, not charging. `device.open_app` dispatched a Calculator intent (`com.transsion.calculator`) but returned `verification=not_observed`; this is not counted as a verified app-open.
- Android bridge status reports `accessibilityEnabled=false`. Therefore live screen observation and a real chat-to-screen-action approval-card check remain unverified. No provider request or message was made. The battery check is universally read-only and alone does not prove Full Access chat behavior.

## Latest — Nexa–ACA shared backend verification (2026-11-21)

Ready locally: the Nexa mobile Chat adapter calls ACA's shared chat-turn handler and existing identity, organization membership, agent configuration, conversation, mail, and tool services. It does not connect to PostgreSQL directly and does not create a second business database. Mobile chat remains server-enforced read-only for this first parity gate; ACA authorization and configuration revision checks remain authoritative.

Verification: ACA focused mobile boundaries/parity passed 40/40 tests, the full web suite passed 1,670/1,670 across 265 files, monorepo typecheck passed, and web lint completed with 0 errors and 16 existing React-hook warnings. The current Nexa emulator Screen Assist build and live one-shot capture/scroll evidence are recorded above.

Live shared-backend acceptance remains pending. Nexa QA is pointed at the fail-closed `https://aca-unconfigured.invalid` origin; ACA Development has no isolated API identity/database configured in this workspace. Docker, PostgreSQL client tools, and ACA dependency ports are unavailable here. The ACA local checkout contains substantial pre-existing uncommitted edits; the mobile chat route exists there locally but is absent from GitHub `Development`. Local and remote `Development` each have one unique commit after a remote force-update, so no merge, rebase, push, or deployment was performed.

Next: provision or connect an isolated ACA Development stack and synthetic test identity, deploy the reviewed ACA mobile adapter only to that environment, build Nexa with the Development origin, then compare the same authorized read-only prompt through ACA web and Nexa for account, organization, agent, configuration revision, canonical conversation, and tool audit. Keep Production out of the test. After read-only parity, add the remaining write/review UI and realtime voice route through ACA's existing approval workflows; do not bypass them from the mobile client.

## Latest — ACA build-safety finding
- ACA local `init` and `apps/web/init` contained packed code after their normal setup call. Static decoding of the web payload found HTTP/HTTPS downloads, detached `node -i`, and downloaded-code evaluation. The suffixes were removed locally and guarded with a two-case regression test.
- ACA full web suite passed 1,672/1,672; monorepo typecheck and root build passed after cleanup. No production API or provider was used.
- The fetched ACA GitHub Development initializer blobs are different and remain unreviewed; the ACA checkout was not merged, rebased, pushed, or deployed. Do not build/deploy from the remote ref until the exact blobs and relevant CI secret exposure are reviewed. Detailed evidence: `D:\RiluRaafu\ai-comms-agent\docs\project-control\sessions\SEC01-ACA-INIT-LOADER-20261121.md`.
