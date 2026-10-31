# 2026-10-31 npm dependency advisory triage

The npm audit result from the pinned project lockfile currently includes:
- Critical: 0
- High: 2
- Moderate: 9
- Low: 1
- Total: 12 findings, primarily in the Expo SDK build/toolchain dependency tree.

High-severity packages:
1. zod 3.25.76, a transitive dependency used by installed Expo CLI 58.1.17.
   The affected range is reported as below 4.6.6; server-side agent-runtime
   separately installs Zod 4.6.7. Advisory:
   https://github.com/advisories/GHSA-mrhx-c7xc-m3w5
2. @expo/cli, high due to transitive zod and other build-tool packages.
   npm audit offers a forced Expo downgrade, which is incompatible with the
   React Native 0.88 / Expo SDK 58 native project and is NOT applied.

Other advisories include transitive glob 13.0.6, minimatch 10.2.6 and
deepmerge 4.x. See the per-run npm audit artifact in GitHub Actions for
current precise versions, CVEs and fix suggestions.

Risk notes:
- Many of these packages participate in project configuration, bundling and
  Expo CLI operation; their presence in the npm dependency graph does NOT
  automatically prove that vulnerable code is part of the distributed APK.
  Conversely, npm audit --omit=dev still reported these dependencies because
  Expo lists them in production dependencies. Evaluate true bundle inclusion,
  reachable attack surface and untrusted input at release review.
- Avoid running the Expo bundler/admin CLI with untrusted project files.
  Remote agent endpoint accepts bounded JSON and uses its own patched Zod 4.
- A forced transitive Zod 3 -> 4 override may break Expo's unsupported internal
  API expectations and would not be safe merely because npm audit goes green.
- Do not downgrade the whole Expo SDK to 51/53 just to silence npm audit.
- Continue monitoring upstream compatible Expo SDK 58 patch releases and
  security fixes; verify with npm ci, core tests, Web export, Android Gradle
  native compile and emulator acceptance before adopting.

The new Core tests workflow captures npm audit JSON and a readable summary
as short-lived GitHub Actions artifacts, informational rather than a silent
waiver. This report is not production security sign-off. Do not distribute a
production signed APK with high findings unreviewed.
