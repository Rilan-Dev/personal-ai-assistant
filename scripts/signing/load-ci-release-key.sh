#!/usr/bin/env bash
# One immutable release signing identity shared by all Nexa APK workflows.
# Secrets must be configured in GitHub Actions; never create a fallback key.
set -euo pipefail
umask 077
: "${GITHUB_ENV:?GITHUB_ENV must be supplied by GitHub Actions}"
: "${NEXA_RELEASE_KEYSTORE_B64:?Missing NEXA_RELEASE_KEYSTORE_B64 repository secret}"
: "${NEXA_RELEASE_STORE_PASSWORD:?Missing NEXA_RELEASE_STORE_PASSWORD repository secret}"
: "${NEXA_RELEASE_KEY_PASSWORD:?Missing NEXA_RELEASE_KEY_PASSWORD repository secret}"

root="$(cd "$(dirname "$0")/../.." && pwd)"
keystore="$root/apps/mobile/android/app/testing-release.keystore"
expected="$(tr -d '[:space:]' < "$root/scripts/signing/nexa-release-cert.sha256")"
test "${#expected}" -eq 64
test ! -s "$keystore" || { echo "Refusing to overwrite an existing release keystore" >&2; exit 1; }
printf '%s' "$NEXA_RELEASE_KEYSTORE_B64" | base64 --decode > "$keystore"
chmod 0600 "$keystore"
test -s "$keystore"

keytool -list -keystore "$keystore" -storetype PKCS12 \
  -storepass:env NEXA_RELEASE_STORE_PASSWORD -alias nexa-release >/dev/null

actual="$(keytool -exportcert -rfc -keystore "$keystore" -storetype PKCS12 \
  -storepass:env NEXA_RELEASE_STORE_PASSWORD -alias nexa-release 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 \
  | sed -E 's/.*=//;s/://g' | tr '[:upper:]' '[:lower:]')"
if [[ "$actual" != "$expected" ]]; then
  echo "ERROR: GitHub signing secret does not match permanently pinned Nexa certificate. Refusing release." >&2
  exit 1
fi

# Values are written to an ephemeral runner environment file, never stdout.
{
  printf 'PAA_TEST_APK_STORE_PASSWORD=%s\n' "$NEXA_RELEASE_STORE_PASSWORD"
  printf 'PAA_TEST_APK_KEY_PASSWORD=%s\n' "$NEXA_RELEASE_KEY_PASSWORD"
  printf 'PAA_TEST_APK_KEY_ALIAS=nexa-release\n'
} >> "$GITHUB_ENV"
echo "Permanent Nexa signer loaded and certificate identity confirmed."
echo "Public certificate SHA256: $actual"
