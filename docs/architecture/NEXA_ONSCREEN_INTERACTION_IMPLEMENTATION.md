# Nexa on-screen icon understanding — engineering handoff (November 3, 2026)

**Development branch:** `feature/nexa-screen-icon-recognition-20261103`
**Draft PR:** https://github.com/Rilan-Dev/personal-ai-assistant/pull/9
**Target:** `Development`
**Release:** Not merged or distributed. User performs emulator/physical-device acceptance.

## Implemented in source
1. Search launchable app labels locally from Tools; no broad package inventory, launcher page unknown.
2. One-time Accessibility icon metadata inspection: arm from Tools, open target application during 20-second window, return to Tools and review; maximum 180 nodes, 64 icons; resulting metadata expires.
3. Floating Tools panel: inspect the app beneath the overlay, choose permitted benign navigation icon, **confirm once**. Native target must be the same package, window and exact accessibility label/bounds at action time. Action is consumed before dispatch; no automatic retries. Unsafe semantics (call, video_call, send, delete, attach, camera, share) are read-only.
4. Floating Tools panel: explicitly prepare and approve one scroll up/down on the largest eligible visible scrollable container. No arbitrary touch coordinates or gesture injection.
5. A separate owner-armed Android 11+ accessibility screenshot capture: Kotlin extracts only up to six small unlabeled ImageButton crops; bitmap/hardware buffer disposed. The complete screenshot is not exposed to RN and no capture is sent to AI automatically.
6. Tools displays icon crops locally with expiry. Selecting one icon shows a separate explicit provider upload button. Only THAT icon crop can go to the owner's configured OpenAI, Gemini or OpenRouter chat-completions vision model; unsupported models/provider failures are reported. Vision output is an **unverified hint, never an actionable target**.
7. OpenAI realtime voice function `request_visible_icons` and explicit Sarvam API voice phrases request owner consent before inspecting fixed-vocabulary icon labels. This sends no screenshot, arbitrary text field or coordinates to a model; no voice-initiated clicks.
8. New Android tests cover navigation-only action policy, voice intent and approval type. Node tests cover model output parsing, image restrictions and transmission boundaries. THESE TESTS HAVE NOT BEEN RUN AGAINST THIS BRANCH.

## Permission/user flows
- Enable Screen Assist using Android Accessibility Settings, then return to Nexa.
- For accessibility labels: Tools -> Arm icon scan -> switch to other app -> Review icons.
- For unknown visual icon: Tools -> Arm visual icons -> switch app -> Review images -> select crop -> read upload disclosure -> Send selected icon to AI.
- For a supported navigation tap: manually show Nexa floating overlay on the target screen -> Tools -> Inspect visible icons -> select navigation icon -> Confirm tap.
- For a bounded scroll: floating overlay Tools -> Scroll up/down -> Confirm scroll.
- For voice read: with Nexa assistant voice plate active, ask for visible screen icons -> approve the one-time screen observation.

## Important safety properties
- No `QUERY_ALL_PACKAGES`, auto-launcher crawls, background screenshot monitoring or unrestricted tapping.
- Only Android framework Accessibility APIs; read and action capabilities depend on separately enabled service.
- No model-generated coordinates, direct provider-to-Kotlin action dispatch, text input automation, send/call/delete/purchase.
- User-selected navigation clicks and scrolls are one-shot, window-bound and expiry-bound. `performAction()` returning true means Android accepted an action request, not that its end-state was verified.
- Screenshot vision requests send a selected crop to a third-party model with a separate visible owner gesture; any model description is non-authoritative.
- If service is unavailable, screen is locked, target changes, action is ambiguous or permission is revoked, fail closed.
- No acceptance claims without emulator and real-device tests; managed Sarvam voice functionality differs from Sarvam Model API voice.

## Device acceptance checklist (user-owned)
- Verify Android 11+, 13+ and supported new releases: Accessibility settings, screenshot grants and service lifecycle.
- Confirm existing wake word, live OpenAI/Sarvam voice, WhatsApp reviewed draft, app finding and overlay theme remain functional.
- Scan launcher and 2–3 third-party apps: screen icon labels, unknown button crops, expiry and discard.
- Verify screenshot crops cannot expose more than the icon rectangle; protected window screenshot capture denied.
- Confirm voice approval screen describes 'read-only icon labels' and denying it returns no data.
- Revisit app under overlay after moving it, changing package/window or waiting past 12 seconds. Confirm action always blocked.
- Confirm safe icon taps and scroll requests require confirmation; call/send/delete icons never offer action.
- Trigger process death, rotation, permission revocation, keyguard lock and concurrent app switching.
- Run `npm ci && npm test && npm run typecheck` and Android Gradle compilation + emulator tests; record test logs and exceptions.
- Confirm no GitHub Action auto-trigger and no release signing disruption.

## Known limitations / future implementation
- Unknown icon crops require a supported configured vision model; there is no bundled local icon-classifier model.
- No unrestricted cross-app AI plans. The current voice model can only request a one-time approved screen overview; control remains manual and policy-bound.
- No durable persisted UI action journal for overlay-only one-shot navigation. The in-memory approval is consumed before dispatch; process death cannot replay it. Integrating per-action encrypted journal evidence is a future milestone.
- No multi-step action chains or verified post-action screen transitions, visual grounding confidence calibration, and no general keyboard typing.
- No APK/emulator/real-phone tests or performance measurements performed in this change set.


## Follow-up implementation: durable at-most-once UI action ledger (November 3, 2026)

- Kotlin `NexaUiActionJournal` stores a bounded (40-event) AES-256-GCM Android Keystore-encrypted record in private SharedPreferences; raw package names, target labels, coordinates, screen text and screenshots are not persisted.
- Native UI tap and scroll controllers now commit an approval-token claim BEFORE `AccessibilityNodeInfo.performAction()`. If the secure claim cannot be committed, the UI action is blocked. Approval tokens are SHA-256 digested, actions cannot be reclaimed, and no retry occurs after an uncertain dispatch.
- Native action results distinguish Android request acceptance/rejection from business-task success. After Android accepts an action, `NexaUiActionVerifier` performs one delayed, read-only check of the accessible screen package/window and normalized icon geometry. The journal may upgrade the result to `observed_change` or `no_observable_change`; neither signifies completion.
- The floating Tools overlay includes **Action journal summary**, which displays only aggregate event states, never details from other apps.
- `NexaUiActionJournalAndroidTest.kt` adds test cases for replay denial, redacted at-rest records, invalid action types, and observed-change transitions. These tests were authored but not run on a real device in this iteration, per owner's acceptance workflow.
- This follow-up does not grant the voice model any screen-click or scroll tools, does not install new permissions, and does not alter release signing or automatic CI trigger policies.


## Next milestone — bounded owner-guided navigation and external app window resolution

The native floating overlay now supports **Menu → Search** and **Menu → Settings**
guides, plus direct Menu, Search and Settings guides. This is a strictly
literal, finite-state guide; it is not an unrestricted multi-step agent.

- Every guide is limited to one or two known non-destructive icon semantics.
- The active guide expires after 90 seconds, is bound to the first inspected
  app package, and is cancelled when leaving Tools or closing the overlay.
- Each step requires a fresh screen inspection, explicit target selection and
  separately confirmed action. The next step is only suggested after Android
  reports accepting the previous action; no automatic tap or retry follows.
- Both `menu` and `more_options` are accepted as menu icon semantics.
- The screen reader now requests Android's interactive-window listing, selects
  a permitted external application window beneath Nexa's application overlay
  and declines protected/system/keyboard/Nexa windows. This addresses the
  previous issue where an overlay-owned active window could hide the target app.
- The native click-target match now also compares bounded view resource IDs
  and widget class names when Android exposes them, in addition to
  package/window/semantic label/bounds.
- The action and scroll executor, delayed verifier and visual-crop callback
  use the external-app window resolver to reduce accidental inspection of
  Nexa's floating interface.
- Automated Android test source is added for action ordering, timeouts,
  package mismatch and unsupported commands. User-owned device testing and
  APK compilation remain explicitly pending.

No model-generated screen plan can invoke Android actions; the guide is
owner-directed. A guide finishing only means its requested clicks were
submitted to Android, not that a business task succeeded.


## Local screen understanding and read-only voice lookup (2026-11-03)

The next milestone adds deterministic, local icon questions without expanding cross-app action privileges:

- The one-time icon scan now carries optional, validated `windowBoundsPx` captured with the same accessibility observation. On multiwindow layouts, relative location hints refer to the *observed app window*, not the entire display.
- `packages/agent-core/src/screen-understanding.ts` resolves a constrained grammar such as "Where is the search icon?", "Find the menu button" and "List visible icons" from a validated short-lived snapshot. It returns named locations (e.g. top-right) only when trustworthy window geometry exists. Repeated matches are ambiguous rather than silently ranked.
- Action requests, long compound commands and unsupported natural-language instructions are rejected; this module never returns a native action token, click coordinate or executable plan. Unknown visual icons are not treated as verified, and call/send/delete controls remain read-only.
- React Native Tools now provides a locally evaluated icon-question input plus counts of known navigation controls and unlabeled icons. Nothing is sent to an AI provider by this feature. The existing optional "Explain metadata in Chat" flow remains a separate explicit owner action.
- OpenAI Realtime adds an explicitly approved `request_locate_visible_icon` tool with an enumerated target; Sarvam Model API voice can recognize exact "Where is the search icon?" phrases. Each request requires a visible owner approval. A fresh external app scan runs only after approval, returns a short spoken relative-location hint, and discards actionable state.
- New Node and Android test cases were committed for strict read-only grammar, stale snapshots, ambiguity, window geometry, action rejection, voice consent and provider-tool arguments.
- **Testing status:** source additions only; no APK, emulator or physical phone acceptance claimed. No production deployment, CI automation change or new permission added.

### Next design/acceptance constraints

More capable device action workflows must continue to use fresh native window/target confirmation and the encrypted at-most-once journal, not any visual model description or this read-only answer. Rich per-element visual similarity and OEM-specific accessibility testing remain future work.


## Evidence-grounded icon hints, revocable agent access and action verification (2026-11-03)

The next source increment provides four bounded features. These changes are **not** a claim of device acceptance, reliable task completion, or general autonomous Android control.

1. **Visual evidence association:** `groundVisualIcon` compares a manually selected icon crop, the image-model's unverified description, and a separately captured accessibility icon observation. The app, optional Android window ID, capture deadlines, timestamps (max 30 seconds apart), and bidirectional rectangle overlap (at least 85%) must agree. Duplicate candidates, conflicting labels, different windows, expired data and unsupported model labels fail closed. The result is a limited/non-limited *evidence category*, not an invented confidence percentage. It is **always non-executable**.
2. **Owner-released screen agent interface:** The owner must capture a snapshot and explicitly choose **Release icon metadata to agent (20s)** in Tools. In-memory owner grant expires after at most 20 seconds, or immediately when Android backgrounds the app. Only the whitelisted icon snapshot is shared with deterministic `screen.observe` or `screen.suggest_navigation` tool responses, never screenshots, message text or coordinates. The corresponding deterministic planner recognizes a small set of owner screen questions and safe menu-path requests; other requests follow the existing assistant policy. These tools do not authorize clicks and do not grant a persistent device-wide observation permission.
3. **Stronger post-action evidence:** Accepted native taps/scrolls first receive an encrypted at-most-once journal claim. A bounded native observer now fingerprints a visible accessibility tree using structure, view class, resource ID, count and geometry (no content/text) and correlates Android `TYPE_VIEW_SCROLLED` or app-window events by package/window within two seconds. Evidence is classified as `scroll_event_observed`, `window_change_observed`, `structural_change_observed`, `no_observable_change`, or `unverified`. The journal preserves these categories, which are **not** proof that a user task succeeded. No automatic replay or retry occurs.
4. **Expiry correction:** Native `expiresInMs` is the *remaining* lifetime when a scan is consumed. The React Native adapters now attach `expiresAtWallMs`; on-device screen Q&A, agent grants, and visual grounding honor this absolute deadline rather than double-counting scan age.

### Source verification and owner acceptance

Local isolated tests exercised a portable grounding/tool mirror (10 Node tests) and pure Kotlin evidence policy (6 assertions); TypeScript standalone checks on that local mirror completed. These are **not** full-repository CI, Android Gradle compilation, emulator tests or physical-device acceptance. User handles the latter.

Suggested owner acceptance: verify app/window switching, icon crop correlation, long-aged-but-unexpired observations, visual conflicts and duplicates, 20-second agent release expiration, background revocation, scroll event reporting with no visible icon movement, denied/expired tap approvals, Android accessibility revocation and operation across Android/OEM versions. Confirm there are no external sends, call initiation, destructive clicks or payment actions.


## Follow-up: bounded evidence scoring, safe workflow previews, and context-aware result attribution

This increment refines the existing opt-in screen assistant without increasing autonomous permissions:

- `screen-confidence.ts` produces a transparent **heuristic evidence index** from 0–85, NOT a calibrated probability or safety certification. A crop overlapping exactly one Accessibility node is **tentative** when its semantic label is unknown; independently matching labels raise the category to **corroborated**. Missing source/window identity, ambiguous matches, model-unknown labels, stale observations and conflicts lower or block the score. Scores never authorize an Android action.
- Navigation previews describe only supported Menu/Search/Settings steps. If the first live icon is absent the preview asks for a new scan; if two candidates exist, it marks the plan ambiguous. Even a ready preview is not executable: each tap still requires a new inspection, owner selection, approval and the existing encrypted at-most-once native journal.
- The agent screen router now checks the currently released (at most 20-second) grant **again when returning** a tool observation, requires exactly one observation, and binds its tool and arguments to the current exact user request. It refuses stale results, wrong-question answers and background-revoked grants rather than letting a previous screen observation leak into another conversation.
- Android result attribution requires that the post-action accessibility root still belongs to the originally approved package/window. A switch to a different window yields `context_changed_unverified`, which is recorded in the encrypted journal without falsely claiming the action succeeded. A missing window yields `unverified`; all captured roots are recycled. Existing scroll/window events and structural hashes are still only OS evidence, not semantic task completion.
- React Native Tools shows the evidence index and reasons when a user explicitly sends a selected icon crop for AI identification. A separate preview control displays owner-confirmed navigation steps based on a freshly captured icon snapshot.
- Source-level regression tests cover duplicate icons, unknown labels, conflicting windows, expiring consent, cross-request result replay, navigation ambiguity and post-action window switches. Device/Gradle acceptance remains owner-managed.

No auto-send, calls, arbitrary coordinate taps, unrestricted gestures, new Android privileges, release APK, or production deployment were added.
