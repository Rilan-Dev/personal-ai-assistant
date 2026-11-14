# Nexa floating Chat, trusted navigation and branch integration — 14 November 2026

## Scope

- Target: `Rilan-Dev/personal-ai-assistant`, branch `Development`.
- Integrate floating-Chat requests with the existing React Native Chat agent, tool planner, native policy, and encrypted one-shot action journal.
- Introduce revocable persistent trust only for explicitly requested low-risk app opening and bounded scrolling.
- Retain Android owner-consent boundaries, lock-screen checks, Pause, fresh Accessibility observation, and separate approval for consequential actions.
- Preserve historical branches and protect existing phone data, signing certificates and live services.

## GitHub changes verified

1. PR [#128](https://github.com/Rilan-Dev/personal-ai-assistant/pull/128) merged at `22587ee54045886d42907a128392b8ee562adf83`. GitHub reported a clean merge.
2. A concurrent `Development` update advanced it to `c9cc79c5130cc2c3a0cb9e6b7e2d3085f2ea9bea`, changing accessibility and Maestro QA labels. Initial full source suite had one mismatched test assertion: YAML used `Chat`/`Tools`, test still expected `Chat tab`/`Tools tab`.
3. Commit `206c42a5b1151ec45e78296e7f96eee4467b1e98` updated the two test assertions through GitHub without suppressing the remount/scroll safety check.

## Exact-source independent verification

An isolated Node 24 container on an authorized test host ran against `206c42a5b1151ec45e78296e7f96eee4467b1e98`:

| Gate | Evidence |
| --- | --- |
| Full Node regression | 544 tests: **543 passed, 0 failed, 1 skipped** (Windows-specific check on Linux) |
| TypeScript checks | Passed across root, mobile and agent-runtime workspaces |
| Expo web export | Passed, `Exported: dist` |
| npm audit | **0 vulnerabilities** |
| Git working tree after tests | Clean |

Before the concurrent update, the first combined merge `22587ee` had 540 Node passes, 1 skip, 0 fails, with TypeScript/web/audit passing. Do not apply these earlier results to a later branch head without checking the intervening diffs.

## Android build and actual device verification

The isolated home-server QA checkout was created at `22587ee`, `npm ci` installed 511 packages, and Gradle entered the native task graph. The first offline build reached `:maestro-portable:verifyMaestroPortableInputs` and failed because a fresh checkout had **not initialized** the pinned `third_party/maestro-upstream` submodule at `aaa248d7fd4ca4924444dc027af9c7a62a9be348`.

The pinned Maestro submodule was subsequently restored successfully at `aaa248d7fd4ca4924444dc027af9c7a62a9be348`. Native build `assembleDebug assembleDebugAndroidTest` advanced through `:app:compileDebugKotlin`, Java compilation and native CMake builds, but failed at **`:app:validateSigningDebug`** because the fresh isolated QA checkout has no `apps/mobile/android/app/debug.keystore`. No APK was signed or installed. The Android 35 QA emulator remained offline in ADB, so no device instrumentation was run.

A second exact-source offline Gradle run on `Development` **`868f6c34a6b83b9e29bbdddf57e998a54c9a9346`** with the pinned Maestro checkout ran `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` and completed **BUILD SUCCESSFUL** (168 tasks: 14 executed, 154 up-to-date). This proves app and instrumentation Kotlin compilation; it does **not** prove APK packaging, signer compatibility, or live Android behavior. The debug-signing setup mutation was denied by execution safety checks. No production or primary Nexa app was replaced.

## Cross-platform Android native-build correction

PR [#129](https://github.com/Rilan-Dev/personal-ai-assistant/pull/129) merged at `868f6c34a6b83b9e29bbdddf57e998a54c9a9346`. The unconditional Windows `D:/paa-cxx` CMake build directory had created an unintended literal `apps/mobile/android/app/D:/` intermediate folder on Linux. The fix applies this short path only on Windows and retains normal Android Gradle Plugin native staging elsewhere.

Verification on the exact PR feature source `a1a48b7`: dedicated platform-staging regression **1/1 passed**, full Node suite **544 passed, 1 skipped, 0 failed** (545 total), TypeScript passed, npm audit **0 vulnerabilities**. The subsequent exact merge head `868f6c3` was successfully compiled with Gradle native Kotlin tasks as recorded above.

## Branch-history audit

The remote inventory contained 102 actual branches. After PR #128, its feature head was verified to be an ancestor of `Development`.

The 14 other branch histories not yet ancestors included `main`, 9 older feature/fix branches represented by already-merged PRs #119–#127, and historical security / QA / Maestro branches. GitHub confirmed PRs #117–#127 merged to `Development`. PR #116 was an isolated `main` security hotfix. Unmerged commit lineage does **not** alone imply missing current functionality when source was integrated by a different merge or squash.

Do not automatically force-reset or merge historical branches: `fix/nexa-autonomous-tool-qa-20261105` and `feature/maestro-source-reuse-20261107` include old `init` paths (`init`, `apps/mobile/init`, `services/agent-runtime/init`) absent from the cleaned `Development` tree. Merging them blindly could reintroduce obsolete startup code. The current `Development` source was checked and those paths were not present.

## Acceptance still required

- Complete debug APK signing/assembly with an isolated disposable test certificate; then boot an authorized Android emulator and execute the compiled instrumentation tests. Native Kotlin compilation is already verified on `868f6c3` with pinned Maestro.
- Real phone, owner-enabled Accessibility and overlay grants, unlocked-device tests, Pause/revocation checks.
- Real WhatsApp and IMO recipient, composer and outcome verification without unintended send.
- Real authenticated Sarvam voice, interruption, wake-to-overlay and resumed Chat task continuity.
- Real five-app home-screen LLM navigation under bounded permissions, with step-by-step observations.
- Release signer migration and rollback evidence, and iOS/macOS device matrix. The installed development-signed primary APK must not be overwritten with an incompatible release certificate.

**Release position: Node/TypeScript/web/security audit and native Kotlin/test compilation passed; APK packaging, emulator/physical-device execution and live provider E2E remain NO-GO without independent evidence.**
