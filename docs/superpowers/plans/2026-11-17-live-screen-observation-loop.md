# Live screen observation loop

## Goal

After every approved screen tap, semantic scroll, or named Android key action,
return a short-lived, redacted Accessibility snapshot containing safe labeled
targets and icon metadata. Let the next planner step select only a target from
that newest snapshot. Keep native one-time owner review, target freshness,
Pause fencing, and no-replay journal checks authoritative.

## Plan

1. Add regression tests for post-action observation forwarding, snapshot-bound
   tap selection, stale-snapshot rejection, four scroll directions, and named
   key actions. Run them to confirm the current gap.
2. Extend the existing in-memory screen snapshot with safe target/icon
   metadata; exclude editable/password values and unsafe controls. Keep the
   existing raw-text snapshot debug-only.
3. Attach a fresh safe snapshot to the successful native tap/scroll/key result.
   Expose Back/Home/Notifications through the regular reviewed Chat tool
   registry, preserving the exact three-key allowlist.
4. Pass only validated snapshot metadata to the direct provider and require
   snapshot-bound, explicitly requested targets for dynamic taps. Invalidate
   old controls after an action that did not return a snapshot.
5. Run focused tests, the project acceptance checks, Android build, install on
   the wired Infinix only, and safe physical tests for tap, four scroll
   directions, named keys, and action-to-fresh-observation continuity.

## Acceptance

- Each successful tap/scroll/key result carries a fresh snapshot or an explicit
  unavailable observation; no stale snapshot can authorize the next tap.
- Model-visible data is bounded safe control/icon metadata only; no screenshot,
  editable values, message body, notification text, contact details, arbitrary
  keycodes, or raw coordinates are sent to the planner.
- Taps use a unique label from an unexpired snapshot; native code rechecks the
  exact live app/window and target bounds at dispatch.
- All four scroll directions and Back/Home/Notifications reach the existing
  one-action owner review and native executor.
- Physical acceptance is reported only after testing the wired Infinix; no
  emulator results count for this task.

