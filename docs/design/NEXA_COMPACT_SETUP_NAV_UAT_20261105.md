# Nexa UI: screenshot-grounded setup, navigation and compact overlay refinement

Date: 2026-11-05. Branch: feature/nexa-compact-permissions-navigation-20261105.

## Verified input

Inspected the actual **1080 × 2400 Android emulator PNG** and its UIAutomator
hierarchy from GitHub Actions run **41916720191**, artifact
**13365709058** (`nexa-feature-emulator-startup`). This run's messaging
Maestro stage ultimately failed; the screenshot is *not* proof of passing
end-to-end messaging or owner permission acceptance. It does verify the
installed application's old first-run layout:

- The large indigo hero panel dominates the first screen.
- The first permission card occupies another tall area and only the start of
  the second permission card is visible above the five-item tab bar.
- The user cannot see the full set of available choices or Continue without
  scrolling. An eight-section permissions form is not appropriate for an
  initial conversational assistant entry.
- Five permanent bottom destinations occupy space that should prioritize
  Chat and Voice, with setup-related routes used far less often.

The **latest successful signed release** observed before this change was
workflow **41917330496** (source `e13991f`), but that run did not upload
full-UI screenshots. Do not confuse an APK success with screen acceptance.

## Changes implemented

1. **Three main destinations.** Chat, Voice and Tools remain bottom tabs.
   Settings is an accessible 44dp header action from every screen. Setup is
   accessible by a clearly labeled shortcut in Tools and Settings, and is
   still displayed on first launch until explicitly acknowledged.
2. **Concise first-run permissions.** Short Nexa intro with source status;
   microphone/notifications and default-assistant gesture appear first.
   Six additional features are in an explicitly expandable section with
   individual action buttons and real Android enabled/off statuses.
   Guidance for each permission is available under a separate “Why is
   this needed?” disclosure. Critical disconnected Accessibility status
   remains visible as needed.
3. **Persistent Continue.** Continue to Nexa and Skip for now remain
   accessible without scrolling through advanced details. Skipping changes
   only local setup acknowledgement; it does not grant a device permission.
4. **Advanced wake options.** Continuous microphone, hotword limitations
   and live-provider disclosures are still present, now under a clearly
   labeled advanced section. Existing consent and Stop controls are
   unchanged.
5. **Compact floating overlay.** Chat/Tools no longer waste vertical space
   on the decorative waveform. Voice retains waveform and actual native
   voice status; all approved action controls and owner-visible safety
   boundaries remain.
6. **Automated tests aligned with navigation.** UIAutomator capture
   navigates the three primary tabs and explicitly exercises Open settings
   and Open permission setup. Maestro first-run, background resume and
   other flow scripts are updated for these entry points and optional
   feature expansion. CI still captures actual signed-bundle emulator
   renders, not screenshots of a Metro-less debug bundle.

## Safety and quality gates

- No additional Android runtime permission, Accessibility service,
  background recording, autonomous Send or unapproved action was added.
- Android settings and confirmation controls are the authority for grants.
- The on-device overlay remains opt-in and cannot be asserted working
  from a React Native source test.
- Respect existing stopped/muted/disconnected/approval states; the UI
  does not claim microphone readiness or message delivery based on an
  icon animation.

### Still needing native/owner evidence

- Inspect the **new** CI screenshot artifact for the compact Setup,
  conversation, Voice, Tools, Settings and optional expanded permissions.
  The old screenshot is a baseline, not a new-build screenshot.
- Validate button clipping, orientation, large fonts, screen-reader
  traversal and Android keyboard/composer behavior on emulator and phone.
- Physically accept the floating overlay on the Infinix Hot 30; the
  authorized home server was not reachable via Tailscale at audit time,
  and there was no verified direct ADB connection to the phone.
- Check backdrop transparency, drag, expansion, small screen bounds,
  action-review controls, Reduce Motion, and actual mic Stop state on
  Android OEM builds. A synthetic emulator grant is not a substitute
  for the owner's real Android permission selection.

## Signed-emulator capture gate incident

Release run 41923156005 stopped before Gradle native compilation with
`/usr/bin/sh: ./gradlew: not found`. GitHub emulator-runner executed each
multiline action command as a separate shell; a prior `cd` was not retained.
This is a **CI invocation failure**, not an app pass or fail. The follow-up
workflow sends a single folded `bash -lc` command, preserving the repository
working directory across native tests, signed APK bundle and exact-APK capture.
The YAML parser confirms it resolves to exactly one shell command. Visual and
physical-device acceptance remain unverified until a fresh successful run.
