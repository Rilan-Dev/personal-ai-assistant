# Android release-test APK through GitHub Actions

The GitHub workflow in .github/workflows/android-release-test.yml builds the
actual optimized, non-debuggable Android RELEASE variant with Hermes, Gradle
release optimizations, native device modules and bundled JavaScript.
It does not need Expo Go or a running Metro development server.

It is deliberately signed with a TESTING certificate, not a Play Store
or production signing identity. This is an APK for owner-controlled UAT.

## Download and install

1. Visit the repository's GitHub Actions page.
2. Open the workflow named Android installable release-test APK.
3. Select the newest successful main-branch run (green check).
4. Under Artifacts download personal-ai-assistant-release-test-apk.
5. Extract its ZIP, then install app-release.apk on an Android phone.
6. The ZIP also includes release-test-sha256.txt and
   release-test-verification.txt. Verify the checksum and signer fingerprint.

The workflow first runs npm ci, npm test and npm run typecheck. It then builds
with Gradle :app:assembleRelease, verifies the signed APK using apksigner and
uploads the APK and checksum as a 14-day GitHub Actions artifact.

## Signing continuity for physical device testing

By default, GitHub Actions generates a random temporary TESTING signing key
for each build. The APK can be installed cleanly, but it may be impossible to
upgrade an older build with a different signature. Android uninstall removes
your encrypted settings and secure action journal.

To enable repeatable in-place upgrades, generate your own private TESTING
keystore and configure:

- Secret PAA_TEST_RELEASE_KEYSTORE_B64 (base64 file bytes)
- Secret PAA_TEST_RELEASE_STORE_PASSWORD
- Secret PAA_TEST_RELEASE_KEY_PASSWORD
- Repository variable PAA_TEST_RELEASE_KEY_ALIAS

Create a private testing key using keytool with the alias and passwords you
will configure, for example the keytool -genkeypair command with RSA 3072
and a long random store/key password.

On Windows PowerShell, copy encoded bytes:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("paa-testing-release.keystore")) | Set-Clipboard

In GitHub repository Settings > Secrets and variables > Actions, save the
three secrets and key alias variable. Do not commit the keystore or paste
key material into chats. An invalid supplied key must fail the workflow.

Gradle never uses the Android DEBUG signing identity for RELEASE builds.
This testing key is not suitable for signing a publicly distributed
production version without a dedicated release-security review.

## Acceptance limits

The APK is installable and built in release mode, but testing on a physical
phone is still necessary for speech recognition, TTS, flashlight, battery,
Quick Settings and encrypted restore. Actual LLM provider responses require
the owner's configured API key. Existing dependency advisories must be
reviewed before public production distribution.

## Source provenance included in newer artifacts

Current manual Android release workflow writes
release-test-source-sha.txt containing GITHUB_SHA, alongside the APK,
sha256sum and signing verification. Confirm the SHA is the desired main
commit before installing; older signed release-test APKs (for example,
the 7b8787a build) do not contain the new mobile UX and live voice code.
The release-test APK is NOT production signed.


## Assistant-operated manual launch (GitHub Actions minutes conservation)
The owner explicitly permits the assistant to start manual workflows when a
development milestone is ready. Automatic push/PR triggers stay disabled.
When the execution environment has an authenticated GitHub CLI session with
Actions write permission, the assistant uses the quota-safe wrapper:

    scripts/ci/manual-actions.sh android-debug.yml
    scripts/ci/manual-actions.sh android-emulator.yml
    scripts/ci/manual-actions.sh android-release-test.yml

The wrapper checks the exact main source SHA, skips already successful or
running same-commit workflows, stops duplicate failures unless explicitly
retried, and refuses expensive release builds until native and emulator
checks have passed for that SHA. This tool is invoked by the assistant when
needed; it is NOT installed as any automatic Git/CI hook.

An authenticated session is a one-time host/account setup step performed
through GitHub's own login flow, not by pasting private tokens into chat.
If no Actions dispatch permission is available, the assistant must explain
that blocker rather than misrepresent a prior APK as the latest.

## Consolidated main branch release pipeline (2026-11-01)

Source development occurs exclusively on Development. Once a complete
feature is tested locally, fast-forward its tested commit(s) to main in
ONE push. That push triggers exactly one GitHub Actions workflow,
android-main-promotion.yml, if Android, agent, test, or workflow source
has changed. Documentation-only main commits do not trigger it.

The workflow performs local source checks then Android API 35 emulator
instrumentation before building an installable, signed release-test APK
on the SAME GitHub Actions runner. If any earlier gate fails, the
release build/ARTIFACT WILL NOT run, saving paid Actions minutes.
SHA/provenance, signer verification and checksum accompany successful
APK artifacts. The five independent workflows remain manually runnable
and do not auto-trigger from main or Development.

Manual workflow calls remain available via GitHub Actions or an
authenticated scripts/ci/manual-actions.sh; no GitHub secrets are
requested in chat and no workflow_dispatch is emulated by rerunning
outdated jobs.
