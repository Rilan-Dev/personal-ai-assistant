#!/usr/bin/env bash
set -euo pipefail
umask 077
signing_dir="$HOME/.local/share/nexa-signing"
mkdir -p "$signing_dir"
chmod 0700 "$signing_dir"
store="$signing_dir/nexa-release.p12"
env_file="$signing_dir/signing.env"
docker_env="$signing_dir/.container-env"
if [[ -e "$store" || -e "$env_file" ]]; then
  if [[ -s "$store" && -s "$env_file" ]]; then
    echo "SIGNING_IDENTITY_ALREADY_EXISTS; refusing rotation."
    exit 0
  fi
  echo "INCOMPLETE_SIGNING_STATE: refusing rotation. Investigate before retry." >&2
  exit 1
fi
password="$(openssl rand -hex 32)"
printf 'NEXA_STORE_PASSWORD=%s\n' "$password" > "$docker_env"
chmod 0600 "$docker_env"
trap 'rm -f "$docker_env"; unset password' EXIT
docker run --rm --user "$(id -u):$(id -g)" --env-file "$docker_env" \
  -v "$signing_dir:/signing:Z" eclipse-temurin:17-jdk \
  keytool -genkeypair -noprompt -keystore /signing/nexa-release.p12 \
  -storetype PKCS12 -alias nexa-release -keyalg RSA -keysize 4096 \
  -validity 36500 -dname "CN=Nexa AI Android Release,O=Nexa Owner,C=IN" \
  -storepass:env NEXA_STORE_PASSWORD -keypass:env NEXA_STORE_PASSWORD \
  >/dev/null
test -s "$store"
chmod 0600 "$store"
{
  printf 'NEXA_RELEASE_STORE_PASSWORD=%s\n' "$password"
  printf 'NEXA_RELEASE_KEY_PASSWORD=%s\n' "$password"
  printf 'NEXA_RELEASE_ALIAS=nexa-release\n'
} > "$env_file"
chmod 0600 "$env_file"
docker run --rm --user "$(id -u):$(id -g)" --env-file "$docker_env" \
  -v "$signing_dir:/signing:ro,Z" eclipse-temurin:17-jdk \
  keytool -exportcert -rfc -storetype PKCS12 \
  -keystore /signing/nexa-release.p12 -alias nexa-release \
  -storepass:env NEXA_STORE_PASSWORD 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 \
  | sed -E 's/.*=//;s/://g' | tr '[:upper:]' '[:lower:]' \
  > "$signing_dir/cert.sha256"
test "$(wc -c < "$signing_dir/cert.sha256")" -eq 65
chmod 0644 "$signing_dir/cert.sha256"
echo "PERSISTENT_SIGNER_CREATED"
echo "Signing certificate SHA256: $(cat "$signing_dir/cert.sha256")"
echo "No private key or password printed or committed."
