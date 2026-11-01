# Hey Nexa — adaptive overlay design (2026-11-02)

This implements the approved translucent floating-assistant concept in **native Kotlin** rather than using a full-screen static screenshot. The home screen remains visible outside the small overlay window.

## Shared design language
- `NexaOverlayTheme.kt`: midnight glass in dark mode, pearl glass in light mode, elevated translucent cards, 44dp+ touch targets, a compact waveform illustration and common status typography.
- `auto` uses Android's current light/dark appearance, plus Android 12+ (`system_accent1_300`/`system_accent1_600`) wallpaper-derived color resources when present; a safe Nexa accent fallback is used otherwise. No wallpaper images are read.
- Owners can cycle **Auto → Dark → Light** from the movable overlay's Theme control. This is an appearance choice, **not** permission to start microphone/screen monitoring.
- Content is laid out inside a bounded window rather than an opaque full-screen Activity. Native translucent gradients emulate glass but cannot provide cross-app wallpaper blur on all OEMs.

## Three entry points
1. **Hey Nexa fallback**: a rounded conversation card, short live status, safe local tool shortcuts, explicit app action approval, collapse/expand voice dock and Stop. Opening Tools keeps the live session active when allowed.
2. **Android system assistant gesture**: same header, quick read-only tools, conversational status and live-voice controls. No screen dim; Android owns the assistant window.
3. **Owner-enabled bubble**: movable 52dp launcher with an expanded, scrollable Chat/Tools/Voice panel; same style and theme chooser. Local Chat remains a bounded **offline device tool** interface, not an unapproved cloud agent.

## State and privacy
The dynamic status field communicates connecting, listening, tool result, interruption and tool approval. The decorative waveform is **not** a microphone-active indicator: Android microphone permission and system recording indicators remain authoritative. No screen content is read merely because the UI shows a panel. Open-app and message review rules are unchanged.

## Verification
Local Linux source checks: 226 tests passed; TypeScript and web export passed before final translucent palette adjustment. A dedicated emulator test exercises Auto/Dark/Light palette creation and control rendering. Native emulator/real-device acceptance is necessary to confirm OEM window placement, response layout, touch pass-through, color contrast and hotword-to-live transition.
