#!/usr/bin/env bash
set -euo pipefail
umask 077
root="$(cd "$(dirname "$0")/../.." && pwd)"
dir="$HOME/.local/share/nexa-signing"
test -s "$dir/nexa-release.p12"
test -s "$dir/signing.env"
test "$(stat -c %a "$dir/nexa-release.p12")" = 600
test "$(stat -c %a "$dir/signing.env")" = 600

# Verify the exact same loader used by GitHub Actions without outputting secrets.
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$root:/work:Z" -v "$dir:/signing:ro,Z" -w /work \
  eclipse-temurin:17-jdk bash -c '
    set -euo pipefail
    source /signing/signing.env
    export NEXA_RELEASE_STORE_PASSWORD NEXA_RELEASE_KEY_PASSWORD
    export NEXA_RELEASE_KEYSTORE_B64="$(base64 -w0 /signing/nexa-release.p12)"
    export GITHUB_ENV="$(mktemp)"
    trap '\''rm -f "$GITHUB_ENV" /work/apps/mobile/android/app/testing-release.keystore'\'' EXIT
    bash scripts/signing/load-ci-release-key.sh
    grep -q "^PAA_TEST_APK_KEY_ALIAS=nexa-release$" "$GITHUB_ENV"
  '
echo "LOCAL_PERSISTENT_SIGNER_VERIFIED"
