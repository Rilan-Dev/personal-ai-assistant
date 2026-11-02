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
