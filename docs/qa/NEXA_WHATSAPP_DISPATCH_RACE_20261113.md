# Nexa WhatsApp target and message-composer race hardening (2026-11-13)

## Scope

Branch `fix/nexa-whatsapp-row-and-composer-race-20261113`, based on `Development@2a61d72f28a85ad7a5385babe19fd631f9cc78d1`.

This milestone changes only owner-reviewed Android UI navigation and synthetic tests. It does **not** introduce an autonomous Send action, request Android OS grants, touch personal messaging accounts, or promote the consumer `main` branch.

## Confirmed defects in prior source

1. `NexaWhatsAppChatActions.inspect` previously merged a clickable candidate when **either** it belonged to the same native source/short ancestor chain **or** the clickable rectangles overlapped. Distinct animated/overlapping contact rows could thus be misrepresented as one uniquely grounded contact.
2. `NexaWhatsAppChatActions.openApproved` selected a row before the final dispatch and called `performAction(ACTION_CLICK)` on that older node. The app/window/row could change between review and dispatch.
3. `NexaMessageInputActions.confirm` likewise performed `ACTION_SET_TEXT` on a node selected before the final dispatch and could report an observation from that same stale node.

## Corrections

- The matching row deduplication requires **same Accessibility source identity or short direct ancestry**, never rectangle overlap alone. Incomplete traversal of the bounded tree returns no actionable target. A verified open conversation additionally requires exactly one eligible composer.
- During the existing owner Pause-fenced click, obtain a **fresh root** and require the same WhatsApp package, window id, unique target, source identity, class, resource id, label-match confidence, and bounds. No new gesture executor, second click or automatic retry is added.
- During the existing one-time reviewed draft fill, obtain a **fresh root inside the same final dispatch fence**; require the original recipient header, unique empty composer, same source/class/id/coordinates; use only the newly inspected node for `ACTION_SET_TEXT`.
- Post-fill verification reads the current app/window and uniquely selected composer from another fresh Accessibility root. No visible typed text = no verified drafting claim, even if Android accepted the request.
- If Android throws **after** entering a native effect, the journal records `unknown`, not `android_rejected`, and no retry occurs.
- Added an Android-test-only `NexaChatListQaActivity` fixture with two different native clickable rows overlapping by >50% height, plus a test requiring both to remain ambiguous. No WhatsApp installation, real contact, user content or Send action is involved in this fixture.
- Updated the older row-source regression to assert geometry-only dedup is forbidden. Normalized CRLF for the Maestro owner-accessibility tests on Windows, retaining all original permission and no-clear-state requirements.

## Source and execution evidence

| Gate | Evidence | Result |
|---|---|---|
| Focused Node safety tests | Three source test files, exact `c2fb162090f0ad474f78445b8e412765c80501d2` | **21/21 pass, 0 failures** |
| Initial 470-test Node sweep before Windows CRLF / obsolete-source regressions were repaired | Windows log `%TEMP%\\nexa-whatsapp-final-node-25eb7f6.log` | **466/470; 4 failures** (three CRLF-sensitive Maestro flow assertions and one expectation that geometry-only dedup was still allowed) |
| Final full Node suite after corrections | Not verified at documentation creation | PENDING |
| TypeScript | `npm run typecheck`, source SHA `25eb7f6` | PASS |
| Earlier native compilation | `:maestro-portable:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest --offline`, source SHA `944aeea` | PASS; **not** evidence for the later Android overlapping-row fixture |
| Final Android arm64 `.physicalqa` app and androidTest APK | Windows BridgeHead runner temporarily returned `PEER_UNREACHABLE`; pending a new completed build | PENDING |
| On-device Android instrumentation | Infinix X6831 Android 13 was connected but no exact-source new QA APK was installed during this increment | NOT RUN |
| Owner's actual WhatsApp app | No real recipient, chat, message draft or Send operation was performed | NOT VERIFIED |
| Native release signing + rollback | No current-source release key or recovery acceptance | NO-GO |

The windows runner is `nextjs-ssh-runner`, isolated checkout `D:\\RiluRaafu\\personal-ai-assistant\\work\\nexa-bounded-scrollsearch-20261107`. The ordinary Windows root checkout has unrelated user-owned dirty files and must not be reset. The phone's primary `dev.rilandev.personalaiassistant` package is not a disposable QA target.

## Exit gates

1. Restore BridgeHead connectivity and run the full 470+ Node tests, Android QA build and instrumentation from a **single final SHA**.
2. On a test-only signed APK, run the new synthetic overlapping-row test and existing duplicate/unique-chat/unsent composer tests; assert `OK (N tests)`, not merely instrumentation exit.
3. With regular owner-granted Accessibility and overlay settings, observe externally authorized real-window WhatsApp chat opening and draft (but never automatically send); verify recipient and post-action window evidence independently.
4. Separately complete real Sarvam voice/wake task flow, canonical Chat/Voice/overlay executor integration, signing lineage, recovery and rollback before production.

**No consumer release certification or general WhatsApp E2E completion is claimed.**


## Verified emulator re-acceptance — 13 November 2026 (supersedes pending gates above)

The complete source tree at Git commit `747258d8f9ec12958f9853c5332e9af2f429903e` was fetched cleanly in the isolated Windows worktree. The Android APK embeds that same full SHA, independently asserted by the instrumented revision test.

| Gate | Actual evidence | Result |
|---|---|---|
| Full Node regression | `%TEMP%\\nexa-full-node-747258d.log` | **474 passed, 0 failed** |
| TypeScript | `npm run typecheck`, `%TEMP%\\nexa-typecheck-747258d.log` | **PASS** |
| Web static export | `npm run web:export`, `%TEMP%\\nexa-web-export-747258d.log` | **PASS**, 382 bundled modules, exported `dist` |
| Android Gradle | `:maestro-portable:testDebugUnitTest :app:assembleDebug :app:assembleAndroidTest`, offline, x86_64, QA2, `%TEMP%\\nexa-qa2-emulator-747258d.log` | **BUILD SUCCESSFUL**, 258 actionable tasks (28 executed, 230 up to date) |
| APK/AndroidTest SHA256 | `74145BB253F2C7D2664679A49D683149FFBC93E2CEF3CC5E0B2509608EF38F74` / `400EF06C0F79A76A90B878F6F3D9C4AF39F5F5CDDEABC47D69AC62142DD165A7` | Verified exact installed artifacts |
| Emulator revision identity | `NexaBuildIdentityAndroidTest`, parameter `nexaExpectedRevision=747258d8f9ec12958f9853c5332e9af2f429903e` | **OK (1 test)** |
| Synthetic WhatsApp, message input, icon, isolation and intent | `%TEMP%\\nexa-emulator-synthetic-747258d.log` | **OK (18 tests)** |
| Maestro portable bridge / relative hierarchy / scroll | `%TEMP%\\nexa-emulator-maestro-747258d.log` | **OK (23 tests)** |
| Emergency Pause, durable no-replay journal, recovery, timeline | `%TEMP%\\nexa-emulator-safety-747258d.log` | **OK (17 tests)** |
| Total selected emulator Android instrumentation | Four independent runs with explicit `OK (N tests)` | **59 passed, 0 failed** |

The first emulator attempt to run UI automation **did not** pass: the emulator had an orphaned `dev.mobile.maestro.test` active instrumentation session holding Android's `Ui Automation` service. The second instrumentation session raised `UiAutomationService already registered!`. The existing background Maestro test had no active host-side Maestro process or ADB forward. The test-only `dev.mobile.maestro` process was force-stopped **without uninstalling apps, clearing data, granting permissions, altering the host OS or touching any production user app**. This released the test service. A fresh synthetic overlapping-row test then reported `OK (1 test)`, and all 59 selected emulator tests above subsequently passed.

The earlier Kotlin-only isolated fixture had failed with `NoClassDefFoundError: kotlin.Unit` in a different APK process; this is resolved by the Java-only synthetic fixtures. The normal Android app's Kotlin runtime was not changed.

**Remaining explicit acceptance limits:** The physical Infinix X6831 disconnected from ADB before these exact-source tests, so its current on-device acceptance remains unverified. No genuine WhatsApp recipient/chat, real outbound Send, Sarvam realtime provider, overlay permission grant, release signing lineage or rollback has been exercised or certified. The emulated tests prove the bounded synthetic behavior, not general autonomous execution or production readiness. Promotion to `Development` can safely include this guarded source fix without implying consumer release approval.

The remainder of the old pending table above describes the **earlier pre-fix moment**; this section supersedes the corresponding development/test statuses. All tests above were run against the pre-documentation code SHA `747258d`; documentation-only Git commits do not change that compiled source.
