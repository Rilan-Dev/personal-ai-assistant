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
