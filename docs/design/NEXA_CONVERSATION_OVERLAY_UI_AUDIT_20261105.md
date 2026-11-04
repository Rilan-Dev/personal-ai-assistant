# Nexa conversation and overlay UI critique — 2026-11-05

## Evidence, provenance, and screenshots

Reviewed the actual current Android **main** source at e7bc56c and supplied
earlier phone screenshot (old Personal AI Assistant all-in-one layout).
Inspected November 4 Maestro Cloud run 41848990853 (passed WhatsApp owner
review). Its downloaded GitHub artifact contains only
`nexa-maestro-detailed.html` and `nexa-maestro-cli.log`, NOT PNG screenshots.
The flow includes `takeScreenshot`, but image playback requires authenticated
Maestro Cloud; its browser is sign-in-gated. No cloud PNGs were invented.

A newer manual messaging QA run 41910540609 (Nov 4) was unsuccessful on a
Maestro assertion about the first-run setup heading. Its main-branch workflow
was updated to attempt synthetic emulator `screencap`, but its artifacts
did not contain a startup screenshot. Consequently it is NOT a visual design
acceptance or proof that the latest Android UI is rendered correctly.

This change adds *real emulator screenshots* to the main-promotion workflow:
`nexa-ui-screenshots` captures the first-run Setup, Chat, Voice, Tools, Setup
and Settings tabs using `adb exec-out screencap` and a UIAutomator dump
after launching the installed debug APK in a clean API-35 emulator.
The native tests and release-signing protections remain separate, and the
workflow fails if screenshot navigation cannot find accessible tabs.
The screenshots are synthetic emulator evidence, not images from the owner's
phone, a real contact/account, or a user-approved floating overlay.

## Self-critique of the pre-change UI

1. **Conversation hierarchy** — The assistant message used a large tinted
   bordered card that competes with content. Chat should primarily be
   readable paragraphs with subdued user bubbles; requests for exact
   side-effect approval remain in explicit separate review surfaces.
2. **Duplicate shortcuts** — The empty welcome showed suggestions while
   the bottom composer had another persistent command rail, leaving little
   room for messages. Remove the duplicate rail; offer suggestions only
   before conversation starts.
3. **Composer priority** — The old 14px input and rectangular color-filled
   send affordance were less legible than a rounded, high-contrast dock.
   Preserve the explicit voice and tool buttons and current stop state.
4. **State feedback** — The voice orb was essentially a static glyph, even
   while connecting, muted, speaking or in error. User must see a textual
   state, not infer microphone or tool success from a decorative animation.
5. **Motion** — Tab switching and overlay opening were abrupt. Add subtle,
   cancellable, native-driver chat entrance and session-state breathing
   animation. Honor Android Reduce motion. Native bubble motion respects
   system animator scale, and only applies when its open/collapsed state
   changes.
6. **Overlay density** — Its previous header consumed vertical space with
   redundant headings and a 37dp decorative waveform. Use a tighter header,
   scrollable body, and a slightly taller bounded expanded panel (with touch
   feedback), preserving its focus and accessibility action gates.
7. **Permission forms** — Setup remains separate from conversation; real
   Android grants must be read back after Settings rather than inferred
   from user tapping a button. Compact text and progressive disclosure
   can be a future design milestone, but do not hide legal disclosures or
   distinct system permissions.

## Implementation principles

- Follow a ChatGPT-inspired **conversation-first pattern** without copying
  another product's logo, brand identity or interface pixel-for-pixel.
- Keep Nexa midnight-dark styling, readable 15px body with 24px leading,
  user bubbles in subtle neutral surfaces, plain assistant responses,
  stable bottom composer, contextual conversation approvals, and a discreet
  five-tab navigation until a separate UAT validates an IA restructure.
- Use React Native Animated and a native Android ViewPropertyAnimator; no
  animation SDK/dependency, network/API calls, image rendering, external
  microphone capture, or new system permissions.
- A pulsing voice state is presentation ONLY. The Android microphone
  indicator, live status, explicit Stop notification and permission gates
  remain the source of truth.
- No hidden action replay, no automatic WhatsApp Send, no forced disclosure
  acceptance and no gesture/click permission expansion.
- Android `ValueAnimator.areAnimatorsEnabled()` and
  `AccessibilityInfo.isReduceMotionEnabled()` skip motion where requested.

## Screenshot acceptance matrix

| Surface | Required evidence | Gates |
|---|---|---|
| Setup | Fresh first-run PNG and hierarchy | No permission granted; clear opt-in |
| Chat empty | PNG | Heading, suggestions, bottom composer visible |
| Chat conversation | User/assistant messages and approvals PNG | Message legibility, no duplicate rail |
| Voice idle | PNG | Explicit mic-off state |
| Voice states | Real-session authorized device | Distinguish connecting/listening/speaking/muted/error; no fake live signal |
| Tools/Settings | PNG/hierarchy | Controls and permission labels not clipped |
| Bubble collapsed/expanded | User-authorized Android permission | Touch pass-through, drag, no external app obscured |
| Overlay light/dark | Real OEM test | Readability against bright/dark app |
| Keyboard open & screen zoom | Android emulator and phone | Composer stable, labels visible |
| Reduce motion | Android accessibility setting | No pulse/entrance motion |
| Landscape/small devices | Additional emulator profiles | Avoid cut-off buttons and inaccessible approve controls |

**Unverified after source changes:** actual latest workflow screenshots,
full mobile conversation/keyboard screenshots, visual snapshot diffs,
hardware voice animation, overlay cross-OEM UAT, battery impact, and
Maestro Cloud screenshot inspection. These should never be labelled
passed purely because source tests compile.

## Follow-up

- Download the new `nexa-ui-screenshots` artifact from the next completed
  main-promotion run, inspect each PNG and UI hierarchy, and record specific
  visual defects. A setup screen screenshot is not evidence of later tabs.
- Obtain authorized Maestro Cloud access to view workflow `takeScreenshot`
  assets or configure explicit permitted export to a GitHub artifact.
- Validate small-screen, large-font, high-contrast, dark/light, RTL, keyboard,
  and physical OEM overlay entry before broad release.


### Screenshot provenance correction

CI's Android debug APK does not bundle React Native JS and normally needs a
running Metro server. It must NOT be used for visual proof. The capture workflow
now compiles the exact source SHA/versioned, permanently signed RELEASE APK
while the emulator remains alive, replaces the synthetic debug install with
that release APK, then captures the app from an empty permission state. The
artifact records the installed release APK SHA-256 for comparison with the
uploaded signed APK. If the screenshot script or on-device UI selectors fail,
the main-promotion gate fails instead of publishing a misleading image.
