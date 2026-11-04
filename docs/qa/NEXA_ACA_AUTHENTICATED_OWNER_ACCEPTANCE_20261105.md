# Nexa + ACA — authenticated owner and Android release acceptance

**Date:** 2026-11-05
**Scope:** Nexa Android read-only ACA email summary; do not use this checklist to authorize unattended email or WhatsApp sends.

## Prerequisites (must be owner-confirmed)

1. **Rotate the ACA password that was exposed in the original chat** at [ACA Settings](https://aca.starcitytechnical.com/settings). Use the website's Change Password form. **Never paste the new password into chat, GitHub, logs or tests.** The system revokes other ACA sessions when changing the password.
2. Install a **permanently signed** Nexa APK built from the latest `Development` application source. Use GitHub Actions `android-main-promotion.yml`, not the QA-only debug APK; verify workflow success, source SHA, certificate, and SHA-256 artifact digest. Preserve the existing app installation when the signer is compatible.
3. Test on an actual owner-controlled Android phone. Android Accessibility, microphone, overlay and contact permissions require **visible, owner-driven** grants. Do not auto-enable restricted Android settings through ADB or instrumentation.
4. ACA production's `NEXA_MOBILE_EMAIL_ENABLED` remains **OFF** and `NEXA_MOBILE_ALLOWED_EMAILS` default-denies everyone. Enable an owner-only canary only after credential rotation and approval of the planned read-only test. No automatic production enablement is implied by passing source/emulator tests.

## Owner-account email acceptance on the phone

1. In **Nexa → Settings → AI Comms Agent · Email**, sign in with the **rotated ACA credentials entered privately on the phone**. The phone must show only authorized organizations and agents. If the server flag is OFF, expect an unavailable message; do not bypass this control.
2. Confirm agent selection. If the account has multiple agents, select the intended one explicitly. The current selection must be invalidated after that agent loses access.
3. In Chat, request **“Summarize unread emails from the last 24 hours.”** Confirm the report says it came from local sync, includes 24h UTC window, matched and included counts, and sync freshness. Compare matched counts with the ACA web Chat query on the **same organization and agent**. The web can show up to 500 detailed rows; Nexa caps at 50, and must say when coverage is partial.
4. Request **“Summarize all my unread emails.”** Confirm that all-history unread scope (not 24h) is used, report count aligns to web evidence, no guessed emails appear, and a partial report does not claim full coverage.
5. Request a **write or unsupported scoped question** (“Send this email”, “Summarize emails from John today”) and confirm this read-only ACA bridge does **not** send, auto-draft, silently drop the sender constraint, or expose an incorrect unfiltered report.
6. Check error states: disable network; reconnect; pause/revoke the selected agent or remove membership in ACA; rotate the password again if needed. Nexa must not report itself as verified when a previously stored credential or organization/agent selection is no longer authorized.
7. Tap **Disconnect ACA**. Verify local session removal. If remote revocation could not be confirmed, Nexa must display that limitation rather than claiming server logout. On another login, only a new device-only token may work.
8. Review on ACA server (without dumping tokens/secret fields): API status, denied requests for anonymous/invalid bearer, tenant and agent mismatch, session expiration/revocation, and rollback by setting the flag OFF.

## Android regression acceptance

- Battery and device status must display values supplied by Android, not invented numbers.
- Browser/app intent: verify actual foreground destination; no false success on dispatcher acceptance.
- WhatsApp: prepare exact text and choose the correct contact **visibly**; **do not send** during QA.
- Screen Assist: turn on the service by user action, inspect an outside-app screen, identify requested text/input box, tap or type with per-action approval, then verify the visible result. Capture a sanitized `dumpsys accessibility` and crash trace if it fails.
- Overlay: show and dismiss a bubble, grant/revoke overlay permission normally, verify state without crashes.
- Wake and voice: test an actual “Hey Nexa” wake on the phone; authorized Sarvam/OpenAI microphone sessions must start/stop, recover from interruption, and not crash. A successful unconfigured-provider negative test does **not** prove actual speech streaming.
- Check screen lock, backgrounding, process restart and network loss. Report only observed actions, not intended ones.

## Evidence and completion decision

Record **phone model, Android version, APK run ID/SHA, selected ACA organization and agent names, comparison counts, timestamps, permissions granted, passing/failing actions, and sanitized logs**. Do not record email subjects, mailbox bodies, passwords, API keys or bearer tokens.

**Phase 1 production GO** only after rotated password, owner-account read-only UAT, tenant and session boundaries, device tests, rollback and disk capacity checks pass. The unified SSO/PKCE, business conversations, provider configuration, realtime voice orchestration, and reviewed send workflows remain distinct later phases — their implementation is not certified by this acceptance document.
