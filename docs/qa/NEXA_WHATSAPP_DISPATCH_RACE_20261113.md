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
