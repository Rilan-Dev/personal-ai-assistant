# Physical Nexa debug-app acceptance — Maestro CLI

These are opt-in, non-destructive tests separated from `.maestro/nexa/config.yaml`: the older suite assumes the base package ID and often runs `clearState: true`. Never run it against a configured personal phone without reviewing every flow.

**Prerequisites:** Owner-controlled Android test device connected by USB; signed `dev.rilandev.personalaiassistant.debug` package installed; Metro serving the debug JavaScript bundle (from `apps/mobile`); `adb reverse tcp:8081 tcp:8081`; correct `ANDROID_SERIAL`. Execute the host-side Maestro CLI against `.maestro/physical-debug/01-chat-battery.yaml`. Keep `clearState: false`.

**Evidence, 2026-11-07:** Before installing the new selector-integrated APK, the Chat battery flow passed on a physical Infinix X6831 after Metro was started. On the new selector APK, synthetic on-device instrumentation passed 4/4 for unique/duplicate and protected/stale targets. The subsequent Chat rerun was blocked because the debug package disappeared while other Nexa APK variants were present, suggesting another device workflow or package-state change. **Do not count that rerun as a pass.** Full real third-party tap and floating Chat action acceptance remains unverified.

This flow neither tests unrestricted agent navigation nor re-grants Accessibility/overlay permissions. Individual screen actions require owner approval, a current accessible foreground window, one-time journal claims and post-action verification. Never upload personal Maestro screenshots, hierarchy dumps or logcat to GitHub.

Maestro is linked via Git submodule at `third_party/maestro-upstream`, commit `aaa248d7fd4ca4924444dc027af9c7a62a9be348` (Apache 2.0). Its CLI/ADB driver remains external and cannot be used as unrestricted permissions inside Nexa's Android APK.
