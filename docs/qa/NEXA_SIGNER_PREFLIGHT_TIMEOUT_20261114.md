# Nexa installed-app signer preflight: time-bounded mismatch — November 14, 2026

## Observed production-blocking state

The physical **Infinix X6831 / Android 13** has the primary package `dev.rilandev.personalaiassistant` installed at versionCode `1`, versionName `0.3.0`. Read-only `adb pull` of its installed base APK, followed by `apksigner verify --print-certs`, found installed certificate SHA-256:

`a5e79669a9e741284375d3a8d9bf78c26887d32590699565a07bd942676fc1f2`

The repository's **pinned permanent release signer** in `scripts/signing/nexa-release-cert.sha256` is:

`6a00d6d2abaebc80709b0891d755030ce289efad22f0f9488e537d8f0f6722bc`

These certificates differ. Android does **not** ordinarily permit a differently signed APK to replace the currently installed primary app without a validated signing lineage. **Do not uninstall/reset user data, rotate production signing keys, or claim consumer release-certified.**

## What hung

The existing read-only PowerShell acceptance script `scripts/acceptance/verify-installed-nexa-signer.ps1` already computed and printed the correct public digest comparison, but after determining mismatch, called optional `keytool -list -v` to classify an existing local debug keystore. On this Windows SSH host, the child `keytool.exe` remained active for several minutes. The first attempt timed out at 90 seconds; the durable second attempt timed out after 300 seconds. That optional classification must never delay a mandatory fail-closed release check.

## Source correction

Only the optional existing public-certificate `keytool` diagnostic was changed:

- Launch via `Start-Process -PassThru`, redirecting public listing output/errors to temporary files within the existing audit temp directory.
- `WaitForExit(10000)` caps this subprocess at **10 seconds**; kill it on timeout or dispose it in `finally`.
- A timed-out or missing diagnostic is never accepted as evidence of signer compatibility. The mandatory `SIGNER_MISMATCH` throw and temporary APK cleanup remain in place.
- No `adb install`, `uninstall`, `pm clear`, permission grant, debug-key export, certificate rotation, or production mutation.

## Verified

- Feature source commit `be01ec525ea8e4c9168ee960f7bb9f6e7f73556b`, test commit `ac1479ba48da616a23d2d68cf1e0736a1d30a8b5`.
- Focused Node tests **6/6 PASS**, including actual Windows PowerShell parser validation and fail-closed signer/source checks.
- Full repository Node tests **499/499 PASS** (log `%TEMP%\\nexa-signer-preflight-node-be01ec5.log`).
- Actual Windows BridgeHead invocation of the revised script against Infinix device `099344034S003788` **terminated in ~20 seconds** with exit code **1**, exact reason `SIGNER_MISMATCH: in-place release upgrade NOT certified; do not uninstall or clear user data`. Public installed and expected SHA-256 values in the output matched the independent read-only comparison above.
- After the run, `PREFLIGHT_TEMP_REMAINING=0` and `UNFINISHED_KEYTOOL=0`. No source/build/phone data modified beyond running a read-only query.

## Release blockers still open

Securely export and verify restore of the existing user-owned primary app state in an isolated environment before any signing-lineage migration plan. Confirm actual Android certificate lineage with release tools, safe versionCode progression, provider secrets and full rollback before changing the consumer package. Owner-mediated Android Accessibility permission, real WhatsApp unsent composer acceptance, Sarvam BYOK/live audio/wake and iOS build are unrelated release gates.

This is **Development-only acceptance-tool reliability**, not a signing-key migration or production certification.
