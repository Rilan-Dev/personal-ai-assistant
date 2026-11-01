#!/usr/bin/env bash
# Monotonic UTC minute bucket with ample headroom past 2038, shared by all CI.
# Greater than all formerly released 2026 Unix-second codes.
set -euo pipefail
now="$(date -u +%s)"
base=1790000000
if [[ "$now" -lt "$base" ]]; then
  echo "Refusing release: build clock predates the stable signing epoch" >&2
  exit 1
fi
code="$((1800000000 + (now - base) / 60))"
if (( code <= 1799999999 || code >= 2100000000 )); then
  echo "Refusing release: Android versionCode outside permitted range" >&2
  exit 1
fi
printf '%s\n' "$code"
