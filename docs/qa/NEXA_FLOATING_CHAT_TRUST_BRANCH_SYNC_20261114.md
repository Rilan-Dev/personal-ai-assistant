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

A follow-up submodule initialization attempt was interrupted by the BridgeHead Tailscale `PEER_UNREACHABLE` error. Therefore no successful APK compilation, instrumentation APK, emulator UI acceptance or real Infinix device acceptance is claimed from this run. No production or primary Nexa app was replaced.

## Branch-history audit

The remote inventory contained 102 actual branches. After PR #128, its feature head was verified to be an ancestor of `Development`.

The 14 other branch histories not yet ancestors included `main`, 9 older feature/fix branches represented by already-merged PRs #119–#127, and historical security / QA / Maestro branches. GitHub confirmed PRs #117–#127 merged to `Development`. PR #116 was an isolated `main` security hotfix. Unmerged commit lineage does **not** alone imply missing current functionality when source was integrated by a different merge or squash.

Do not automatically force-reset or merge historical branches: `fix/nexa-autonomous-tool-qa-20261105` and `feature/maestro-source-reuse-20261107` include old `init` paths (`init`, `apps/mobile/init`, `services/agent-runtime/init`) absent from the cleaned `Development` tree. Merging them blindly could reintroduce obsolete startup code. The current `Development` source was checked and those paths were not present.

## Acceptance still required

- Android native assemble and instrumentation on the exact latest source after pinned Maestro checkout is restored.
- Real phone, owner-enabled Accessibility and overlay grants, unlocked-device tests, Pause/revocation checks.
- Real WhatsApp and IMO recipient, composer and outcome verification without unintended send.
- Real authenticated Sarvam voice, interruption, wake-to-overlay and resumed Chat task continuity.
- Real five-app home-screen LLM navigation under bounded permissions, with step-by-step observations.
- Release signer migration and rollback evidence, and iOS/macOS device matrix. The installed development-signed primary APK must not be overwritten with an incompatible release certificate.

**Release position: source checks passed; consumer release remains NO-GO without the above independent device and provider evidence.**
