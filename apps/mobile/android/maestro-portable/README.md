# Maestro portable source bridge for Nexa

This library is **not a copy or fork of the Maestro test engine**. It compiles exactly five original upstream Kotlin files from the gitlinked repository at `third_party/maestro-upstream`, with an explicit Gradle file allowlist:

- `maestro-client/src/main/java/maestro/Point.kt`
- `maestro-client/src/main/java/maestro/Bounds.kt`
- `maestro-client/src/main/java/maestro/TreeNode.kt`
- `maestro-client/src/main/java/maestro/SwipeDirection.kt`
- `maestro-client/src/main/java/maestro/ScrollDirection.kt`

Upstream: https://github.com/mobile-dev-inc/Maestro at commit `aaa248d7fd4ca4924444dc027af9c7a62a9be348`. License: Apache-2.0, see `third_party/maestro-upstream/LICENSE`. Upstream files remain unchanged and are copied **only into ignored Gradle build output**.

The local `MaestroSafeSelector` is original Nexa integration code and consumes those upstream model classes. It returns a bounded, unambiguous **candidate identity only**. It never grants permissions, clicks, types, reads editable values, or drives a device. Nexa's `ScreenAssistService`, task coordinator and one-time action journal retain the security boundary.

**Not linked into APK:** `maestro-client` entire JVM artifact; `AndroidDriver`, `MaestroDriverService`, Orchestra/Jackson/YAML runtime, GraalJS, ADB, gRPC, Selenium, or any iOS drivers. The desktop Maestro CLI continues to run acceptance tests externally.

For a fresh checkout, run `git submodule update --init` and verify the SHA with `scripts/maestro-source-check.ps1` on Windows. Build `apps/mobile/android/gradlew :maestro-portable:testDebugUnitTest`. The Android app includes the portable module through `implementation project(':maestro-portable')`.

**Release gate:** Source/test compilation is not a device-action acceptance result. Follow-up engineering must connect selectors to existing native actions with fresh window checks, approval, journal claims, bounded retries and observed-outcome verification before enabling live cross-app workflows.
