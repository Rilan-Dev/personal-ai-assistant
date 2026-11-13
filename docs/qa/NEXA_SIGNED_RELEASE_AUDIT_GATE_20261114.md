# Nexa signed Android release dependency-risk gate — November 14, 2026

The Nexus/Android signing workflows `.github/workflows/android-main-promotion.yml` and `.github/workflows/android-release-test.yml` were previously owner-manual, pinned to a permanent signing certificate and protected from ephemeral key rotation, but **did not fail before loading the key when npm audit reported high/critical findings**.

Both manually triggered workflows now perform `npm audit --audit-level=high` immediately after `npm ci`, before the signing secrets are exposed to any build step. A nonzero result stops the signed release; the workflow cannot suppress or auto-fix advisories. The QA assertions additionally forbid automatic workflow triggers, `continue-on-error` for this gate, audit bypasses and accidental secret printing.

The isolated Windows source tree passed a fresh `npm audit --json` on November 14, 2026: **0 info / low / moderate / high / critical vulnerabilities** (log: `%TEMP%\\nexa-final-icon-audit-b2c36e8.json`). This is current lockfile evidence, not a promise of future dependency safety. The old October 31 issue count of 13 advisories is stale but remains historically accurate for that older dependency state.

This change is a security gate only. No packages, Android runtime, signing secrets, Android permissions, `main` or production installations were changed. **Signing lineage on the actual consumer Nexa package and key-disaster-recovery checks are not yet certified.** Keep GitHub issue #5 open for those acceptance steps.

Tests: `scripts/test/nexa-signed-release-audit-gate.test.mjs` exercises both release workflows and fails if a release stops being manual or the advisory check is removed, moved after keystore loading or bypassed.
