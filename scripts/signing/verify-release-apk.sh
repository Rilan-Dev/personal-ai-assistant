#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
apk="$root/apps/mobile/android/app/build/outputs/apk/release/app-release.apk"
expected="$(tr -d '[:space:]' < "$root/scripts/signing/nexa-release-cert.sha256")"
test -s "$apk"
: "${ANDROID_HOME:?ANDROID_HOME required}"
bin="$ANDROID_HOME/build-tools/36.0.0/apksigner"
test -x "$bin"
"$bin" verify --verbose --print-certs "$apk" | tee "$root/release-test-verification.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$root/release-test-verification.txt"
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$root/release-test-verification.txt" | head -1 | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
if [[ "$actual" != "$expected" ]]; then
  echo "ERROR: Signed APK certificate mismatch. Refusing artifact upload." >&2
  exit 1
fi
echo "Confirmed permanent Nexa release certificate: $actual"
sha256sum "$apk" | tee "$root/release-test-sha256.txt"
printf '%s\n' "$actual" > "$root/release-test-signing-certificate.txt"
echo "Release APK verification succeeded."
