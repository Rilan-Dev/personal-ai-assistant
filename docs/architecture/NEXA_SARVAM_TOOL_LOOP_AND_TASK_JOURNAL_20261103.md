# Nexa voice-agent tools, persistent task journal and crash diagnostics

Source milestone: November 3, 2026
Repository: Rilan-Dev/personal-ai-assistant

## Purpose

The prior signed APK did not retain task progress after voice interruptions and Sarvam Chat only knew about battery/storage/status tools. This milestone extends the existing feature branch with permission-bound function-call proposals, encrypted task checkpoints, on-phone OS process-exit reports, and a reviewed screen-search focus.

### Live agent execution

- User microphone and selected Sarvam or OpenAI provider remain under existing owner consent.
- Sarvam Conversation model receives a nested JSON tool registry made from existing native tool descriptions. Tools can **request** app discovery/open, screen reading, screen icon lookup, bounded scroll and safe uniquely grounded taps. No send, answer-call, dial, payment, delete or grant-permission tool is registered.
- Before a model-proposed action, NexaSarvamActionTools.ownerRequested validates that the actual transcribed owner's utterance requested the same operation and target. Negative utterances never execute.
- Android native NexaLiveToolPolicy validates the named tool and JSON argument shape, Accessibility connection, safe icon/target class, and known-app matching.
- The service presents an owner review in the overlay, unless the operation belongs to an expressly saved low-risk navigation category. Screen contents, app inventories and arbitrary text-label taps always require review. Fuzzy-matched app names always require explicit approval, even in Trusted navigation.
- Only after the owner review/native call completes does Sarvam receive a function_call_output. The remote model cannot bypass permissions or assert an unverified outcome.
- A denied, expired, uncertain or unverified action terminates the action sequence for that model turn. Only independently verified on-screen observations can permit a bounded follow-up call; model rounds are limited to four.
- Barge-in invalidates pending model proposals, preventing stale approvals from an interrupted question.

The native compound-utterance cursor still handles exact two-to-four-step commands such as "Open WhatsApp and read my screen." The new model loop supplements it for non-exact requests such as "Pick the Ask Meta AI or Search input box." Full model-planned, checkpoint-resumable arbitrary tool graphs are not yet complete.

### Encrypted durable voice-task checkpoints

Android-private custom.db now has DB schema version **2**. Upgrade from v1 executes CREATE TABLE for voice_task_checkpoints; it does **not** drop conversation_entries or recreate the Android Keystore key. Task contents (goal, ordered substeps and bounded evidence) are AES-GCM encrypted and indexed by a random task ID. A secure checkpoint is committed before starting a dependent native action; failure stops the sequence.

Task records are saved as planned / executing / verified / completed / uncertain / failed / cancelled, with an updated step index. A process restart leaves incomplete tasks visible for owner review; there is **no automatic replay or unattended resume**. The Voice tab presents recent tasks alongside recent transcripts. Explicit JSON export contains both, and no PostgreSQL cloud connection is configured.

### Crash evidence

Android 11+ ApplicationExitInfo reports recent reasons that this app's processes exited (Java crash, native crash, ANR, memory, user stop). Nexa Voice shows the recent reason list and supports an **explicit** Android share/export of bounded OS traces. The detailed trace may contain sensitive activity text; it is never automatically uploaded. Actual tap-crash root cause requires a user device trace; a successful emulator compile is not a substitute for that evidence.

### Screen/search control

Owner-reviewed search field focusing can now locate an exact visible accessible Search field. Password and unrelated editable content remain excluded. Nexa does not extract input values, type text or infer arbitrary X/Y coordinates from the model. Ambiguous or changed targets are rejected.

### Verification

- npm source tests: re-run on final commit.
- TypeScript typechecking: re-run on final commit.
- Isolated Java 17 / Android SDK 36 native compilation and instrumentation-test compilation: re-run on final commit.
- Physical Android 13 XOS/WhatsApp/SIM acceptance: not executed, owner approval and recordings required.
- Native streaming against a configured real Sarvam account: not executed, never assume provider acceptance from source tests.

## On-device validation

1. Enable Nexa Accessibility service in Settings, grant mic and overlay, start Hey Nexa live Sarvam.
2. Say "Open WhatsApp and read my screen" in one utterance. Approve requested actions, confirm WhatsApp foreground and its actual accessible labels in one task session.
3. Say "Pick the Ask Meta AI or Search input box". Verify the proposal is presented, approve once, confirm it only focuses the search UI, and check no crash.
4. Say "Swipe left" on the XOS home launcher. Observe actual page movement; test another app which does not allow horizontal scroll to verify safe refusal.
5. In Voice settings, select each permission mode; verify that only safe navigation prompts are reduced, and screen reads, app lists and arbitrary text-label taps still require approval.
6. Trigger a harmless foreground-app crash scenario in debug/test only if you have a safe development test method; use View recent history and Export Android crash/ANR diagnostics. Do not induce a real crash on a production phone.
7. Export custom.db history; check current schemaVersion=3 and tasks, androidProcessExits; ensure older conversation records are still present.
8. For a denied/expired tool proposal, ensure no later model-generated action executes without a new request.

## Remaining pending work

- General goal-driven model graph with robust cross-turn context, tool selection and mobile-native checkpoint/resume; current voice loop is bounded and owner-intent constrained, not general autonomy.
- Consent-aware contact binding and message channel preference memory, WhatsApp/SMS composer integration, human-approved send and verified delivery.
- Dedicated Android Telecom / InCallService and owner-granted call-routing workflows; no generic tap guessing on dialer or calls.
- Stable Android phone/device crash reproduction, emulator instrumentation, real provider acceptance and signed-release acceptance.
- Authenticated optional cloud sync with Prisma/Postgres only after owner supplies credentials and explicitly enables data synchronization.

Do not describe native accepted-gesture, launch Intent, or composer open as verified task completion.
