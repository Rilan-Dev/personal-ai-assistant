#!/usr/bin/env bash
# Run once after GitHub CLI authentication with permission to edit repository
# Actions secrets. This script NEVER prints a keystore or password.
set -euo pipefail
umask 077
repo="Rilan-Dev/personal-ai-assistant"
dir="$HOME/.local/share/nexa-signing"
store="$dir/nexa-release.p12"
env_file="$dir/signing.env"
root="$(cd "$(dirname "$0")/../.." && pwd)"
command -v gh >/dev/null || {
  echo "GitHub CLI (gh) is not installed. Install gh and run gh auth login first." >&2
  exit 1
}
gh auth status -h github.com >/dev/null 2>&1 || {
  echo "GitHub admin authorization missing. Run gh auth login, then retry." >&2
  exit 1
}
test -f "$store" && test -f "$env_file"
test "$(stat -c '%a' "$store")" = 600
test "$(stat -c '%a' "$env_file")" = 600
cmp -s "$dir/cert.sha256" "$root/scripts/signing/nexa-release-cert.sha256" || {
  echo "Signer fingerprint differs from permanent pin. No secrets were changed." >&2
  exit 1
}
# Export credentials only within this shell and upload directly through the
# authenticated HTTPS GitHub CLI connection.
source "$env_file"
: "${NEXA_RELEASE_STORE_PASSWORD:?}"
: "${NEXA_RELEASE_KEY_PASSWORD:?}"
: "${NEXA_RELEASE_ALIAS:?}"
test "$NEXA_RELEASE_ALIAS" = nexa-release

base64 -w0 "$store" | gh secret set NEXA_RELEASE_KEYSTORE_B64 --repo "$repo"
printf '%s' "$NEXA_RELEASE_STORE_PASSWORD" |
  gh secret set NEXA_RELEASE_STORE_PASSWORD --repo "$repo"
printf '%s' "$NEXA_RELEASE_KEY_PASSWORD" |
  gh secret set NEXA_RELEASE_KEY_PASSWORD --repo "$repo"

echo "Three encrypted GitHub repository secrets uploaded. Private key was not displayed."
gh secret list --repo "$repo" | grep '^NEXA_RELEASE_' | cut -f1
echo "Review GitHub Actions and verify the release APK certificate matches the pinned fingerprint."
