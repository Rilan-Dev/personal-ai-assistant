#!/usr/bin/env bash
# Manual-only GitHub Actions dispatcher for the Personal AI Assistant.
# Never used from a push/PR hook. Requires gh auth login on the invoking
# machine, with GitHub Actions write access. Does not read or print tokens.
set -euo pipefail

readonly REPO="Rilan-Dev/personal-ai-assistant"
usage() {
  echo "Usage: scripts/ci/manual-actions.sh <android-debug.yml|android-emulator.yml|android-release-test.yml|android-main-promotion.yml|core-tests.yml|agent-provider-acceptance.yml> [--dry-run] [--retry-failed]" >&2
  exit 2
}

[[ $# -ge 1 && $# -le 3 ]] || usage
workflow="$1"; shift
case "$workflow" in
  android-debug.yml|android-emulator.yml|android-release-test.yml|android-main-promotion.yml|core-tests.yml|agent-provider-acceptance.yml) ;;
  *) usage ;;
esac
dry_run=false
retry_failed=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=true ;;
    --retry-failed) retry_failed=true ;;
    *) usage ;;
  esac
done

if ! command -v gh >/dev/null 2>&1; then
  echo "BLOCKED: GitHub CLI ('gh') is missing. No workflow dispatched." >&2
  exit 2
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "BLOCKED: GitHub CLI needs one-time owner-authorized authentication with Actions write permission. No workflow dispatched." >&2
  exit 2
fi

sha="$(gh api "repos/$REPO/commits/main" --jq '.sha')"
if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "BLOCKED: Unable to verify the exact current main commit. No workflow dispatched." >&2
  exit 2
fi

# A manual workflow completed successfully or already active for this same
# source commit must not be repeated: saves the owner's GitHub Actions quota.
count_runs() {
  local target="$1" type="$2"
  local expression
  case "$type" in
    passed)
      expression='[.[] | select((.event == "workflow_dispatch" or .event == "push") and .status == "completed" and .conclusion == "success")] | length'
      ;;
    active)
      expression='[.[] | select((.event == "workflow_dispatch" or .event == "push") and .status != "completed")] | length'
      ;;
    failed)
      expression='[.[] | select((.event == "workflow_dispatch" or .event == "push") and .status == "completed" and .conclusion != "success")] | length'
      ;;
    *) return 2 ;;
  esac
  gh run list --repo "$REPO" --workflow "$target" --commit "$sha" \
    --limit 100 --json event,status,conclusion --jq "$expression"
}

if [[ "$(count_runs "$workflow" passed)" -gt 0 ]]; then
  echo "SKIPPED: $workflow has already passed for $sha; no extra Actions minutes used."
  exit 0
fi
if [[ "$(count_runs "$workflow" active)" -gt 0 ]]; then
  echo "SKIPPED: $workflow is already running for $sha; no duplicate run."
  exit 0
fi
if [[ "$(count_runs "$workflow" failed)" -gt 0 && "$retry_failed" != true ]]; then
  echo "BLOCKED: $workflow already failed for $sha. Fix it first, or explicitly use --retry-failed." >&2
  exit 3
fi

# Do not spend release build minutes on native source that has not yet passed
# both the Kotlin/instrumentation compile and emulator acceptance gates.
if [[ "$workflow" == android-emulator.yml || "$workflow" == android-release-test.yml ]]; then
  if [[ "$(count_runs android-debug.yml passed)" -eq 0 ]]; then
    echo "BLOCKED: Android debug compilation has not passed for $sha. No workflow dispatched." >&2
    exit 3
  fi
fi
if [[ "$workflow" == android-release-test.yml ]]; then
  if [[ "$(count_runs android-emulator.yml passed)" -eq 0 ]]; then
    echo "BLOCKED: Android emulator acceptance has not passed for $sha. No release build dispatched." >&2
    exit 3
  fi
fi

if [[ "$dry_run" == true ]]; then
  echo "DRY RUN: would manually dispatch $workflow for $REPO main commit $sha"
  exit 0
fi

echo "Manually dispatching $workflow for $REPO main commit $sha"
gh workflow run "$workflow" --repo "$REPO" --ref main
echo "Dispatch submitted. GitHub Actions will report the actual result; no build success is implied."
