# Owner-controlled native action stop — development implementation

**Scope:** Nexa Android `Development`; runtime pause mechanism, not production
certification. The shared general-purpose execution engine remains disabled.

## Owner behavior

- Open **Preferences → Native device actions · owner stop** and choose **Pause device actions**.
- The floating Nexa overlay also has a one-way **Pause device actions** button.
  Its **Action safety settings** shortcut opens Preferences. The overlay cannot
  resume paused actions by itself.
- Only the unlocked foreground Nexa application may **Resume device actions**.
- Pausing does not change Android Accessibility, camera, notification, assistant
  role, microphone or contacts permissions and cannot undo an in-flight command.

## Enforcement

`NexaNativeActionSafety` stores a boolean and a monotonic pause generation in
private local SharedPreferences. New actions deny on preference read errors.
Every Pause advances the generation even when previously paused. Resume never
decrements it. The Voice approval card captures the generation **when offered**,
then rejects its execution after any intervening Pause/Resume cycle.

Enforcement boundaries include:
- `NexaUiActionJournal.claim`, before the durable SQLite no-replay tombstone
  and all UI taps, scrolls, safe text entry and native app launch actions;
- `NexaLegacyVoiceStepApproval.consume` and the live Voice worker, before an
  approved compound step is dispatched;
- the foreground Chat native bridge before launching installed apps, camera,
  Chrome search or notification shade;
- Android global Back/notification actions and the Chrome/overlay launch paths;
- flashlight **ON**, while flashlight **OFF** remains possible for safety.

Pausing discards pending native screen-click, scroll, message-field and generic
text-field selections. Chat UI clears pending in-memory review surfaces.
A consumed action token stays permanently non-replayable after resumption.

## Authority boundaries

A language model, external app, background provider callback, or the read-only
Tool registry cannot call the React Native owner **setter**. The setter requires
foreground Nexa focus and an unlocked device; the floating overlay offers only
Pause, never Resume. This switch is **additional** to explicit per-action
approval, target-window verification, Android permissions, reviewed fingerprints,
Voice step claims, and build-time feature flags.

## Database and rollback

No custom.db schema migration and no Docker deployment required; existing
encrypted task and action-claim ledger stays untouched. Device pause state
is stored only in local app preferences. To disable affected operations at
runtime, Pause; to restore owner-approved manual operations, Resume in Settings.
Do not force-promote `main` or bypass the signed APK, provider-consent and
on-device rollback gates.

## Verification and limitations

Node source regressions, TypeScript checks and Android instrumentation source
cover the pause fence, non-replay, persistent preferences, and source bindings.
Native Kotlin compilation and device execution must be checked for the exact
implementation SHA; source tests alone are insufficient.

This mechanism **does not** yet implement a general one-parent-task Kotlin
scheduler across Chat/Voice/overlay. It also does not guarantee that Android
will retroactively cancel an already-issued OS/third-party action.
