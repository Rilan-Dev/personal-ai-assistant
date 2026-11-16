#!/usr/bin/env bash
# Called by reactivecircus/android-emulator-runner. The runner executes each
# YAML script line independently, so a separate "cd" would not persist.
set -euo pipefail
if [[ "${NEXA_ACA_ENVIRONMENT:-}" != "development" ]]; then
  echo "Set NEXA_ACA_ENVIRONMENT=development before running ACA-connected acceptance." >&2
  exit 2
fi
: "${EXPO_PUBLIC_ACA_API_ORIGIN:?Set EXPO_PUBLIC_ACA_API_ORIGIN to the Development ACA origin}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root/apps/mobile/android"

# Android Keystore, permissions and action replay must pass before building.
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace

# A debug APK needs Metro and is not reliable release UI evidence.
# Build the exact permanently signed release artifact for screenshots.
./gradlew :app:assembleRelease --no-daemon --stacktrace

cd "$repo_root"
bash scripts/ci/capture-nexa-ui.sh apps/mobile/android/app/build/outputs/apk/release/app-release.apk
