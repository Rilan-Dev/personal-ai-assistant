# Nexa AI — permanent Android APK signing

Status: **Permanent key generated; all three GitHub Actions encrypted secrets are provisioned; CI has successfully validated the signer; native signed-APK release verification underway.**

## Permanent identity

- Alias: `nexa-release`
- Key: RSA-4096, PKCS#12, X.509 self-signed certificate
- Validity: 36,500 days (about 100 years), not literally forever
- Certificate SHA-256:
  `6a00d6d2abaebc80709b0891d755030ce289efad22f0f9488e537d8f0f6722bc`
- Server keystore: `/home/techadmin/.local/share/nexa-signing/nexa-release.p12`
- Server credential file: `/home/techadmin/.local/share/nexa-signing/signing.env`
- Protected permissions: directory 0700, credentials and keystore 0600
- The private key and passwords are NEVER committed, returned to ChatGPT, placed
  in workflow artifacts, or printed in CI logs.

The same certificate SHA is pinned in
`scripts/signing/nexa-release-cert.sha256`. The bootstrap script is idempotent,
never overwrites an initialized identity, and should never be rerun to rotate
this signing certificate. The app's Android applicationId remains
`dev.rilandev.personalaiassistant`.

## CI policy

Both `android-main-promotion.yml` and `android-release-test.yml` use the
exact same encrypted GitHub repository secrets:

1. `NEXA_RELEASE_KEYSTORE_B64`: base64 encoding of the complete PKCS#12 file
2. `NEXA_RELEASE_STORE_PASSWORD`: password for the keystore
3. `NEXA_RELEASE_KEY_PASSWORD`: password for the signing key

The alias is fixed in source to `nexa-release`; no separate variable is
needed. Do not enter those secrets in chat or commit them as files.

`scripts/signing/load-ci-release-key.sh` validates the keystore and pins the
public certificate SHA before allowing the build to proceed. If a secret is
missing or a wrong certificate is presented, release FAILS rather than
generating a disposable key. The APK signature is independently rechecked by
`scripts/signing/verify-release-apk.sh` before an artifact can be uploaded.

The emulator's temporary **debug** key is a separate identity used only by
Android instrumentation. It does not sign released APKs and can be renewed.

A single long-lived Android versionCode calculation is shared by both CI
workflows. It starts above the previously used 2026 Unix-second codes and
increases with UTC time in one-minute increments. Android still requires the
same applicationId, an equal or higher versionCode, and a compatible signing
certificate for in-place upgrades.

## One-time GitHub Actions provisioning

The connected GitHub connector does not expose encrypted-secret writes. The
initial provisioning was successfully completed on 2026-11-01 by securely
streaming the VPS keystore and credentials over an authenticated SSH connection
to the owner-connected home server, whose authenticated GitHub CLI uploaded
all three encrypted repository secrets. No private key was posted to ChatGPT
or the GitHub source repository.

As the GitHub repository administrator, install and authenticate `gh` **on the
authorized server**, then run:

```bash
gh auth login -h github.com
cd /home/techadmin/workspaces/nexa-assistant
bash scripts/signing/upload-secrets-to-github.sh
```

The upload script reads the protected server files, validates the fingerprint,
and writes all three encrypted GitHub Secrets directly via authenticated HTTPS.
It does not echo passwords or keystore contents. The signer will then be ready
for all subsequent GitHub Actions APK releases. After provisioning, trigger
the `Android installable release-test APK` workflow or push approved code to
`main` and verify the uploaded APK certificate against the fingerprint above.

An AES-256 encrypted recovery archive has been created and verified locally
on the VPS. Transfer to separate owner-controlled off-host storage remains
necessary for disaster recovery. The key **must also be backed up off-host**, together with recovery passwords,
in an owner-controlled encrypted vault/storage. GitHub Actions secrets cannot
normally be viewed again and should not be treated as a recoverable backup.
Do not share the key or recovery password in a support ticket or conversation.

## Why old APKs may require one final reinstall

An APK made with a **different temporary signing key** cannot be updated
in place with this new permanent key, no matter how high its versionCode is.
Android authenticates the signer; it is not an app-side setting. The preceding
test APK was generated with a temporary release signer and that private key
was not preserved by the workflow. This signing key will protect subsequent
installs and upgrades, but cannot retroactively sign previous builds.

Before any one-time reinstall, preserve existing provider credentials, saved
settings, and private data. Uninstalling deletes app-private storage; any
Android Keystore-protected values may become unrecoverable. Once the stable-key
APK is installed, **future releases signed with this exact same key** can
update it without uninstalling, assuming versionCode and package identity are
correct.

## Verification checklist

- `bash scripts/signing/verify-local-signer.sh` succeeds without printing a key.
- `npm test`, `npm run typecheck` pass.
- New GitHub Actions workflow can read all three encrypted secrets.
- The workflow has no fallback release key generation.
- Emulator/native build and APK checksum checks pass.
- `apksigner verify --print-certs <apk>` matches the pinned SHA.
- After installation, Nexa Setup reports the expected source revision and
  Android version code.
- Install a second APK signed with the same key and higher versionCode over
  the first; verify user settings survive.

Encrypted-secret provisioning and the CI keystore identity check have passed.
Release readiness still requires successful native APK signature verification
and actual phone upgrade acceptance.
