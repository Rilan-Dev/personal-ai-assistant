# Floating Overlay Shared Chat Design

Date: 2026-11-15
Status: implementation authorized by the user's instruction to proceed without repeated confirmation.

## Intent

Floating Chat must use the current React Chat state so a connected ACA session, AI provider, task policy, and action journal are visible to an overlay request even when those settings changed after the listener was mounted.

## Current behavior and defect

`NexaOverlayChatRelay` sends owner-entered overlay text to the existing `App.tsx` Chat `send()` path. The subscription effect currently captures `send` but does not depend on ACA state. A live overlay request reached the shared path and incorrectly reported that ACA was disconnected while the Settings screen showed ACA connected.

## Approaches

1. **Selected — stable callback ref:** Keep one native relay and planner. Store the latest `send` callback in a React ref on every render; the long-lived listener invokes that current callback. This fixes stale Chat state without duplicating routing.
2. **Duplicate ACA routing in Kotlin:** Rejected because it would create a second mail path, bypass the shared Chat policy, and require native access to ACA secrets.
3. **A separate overlay voice/mail agent:** Rejected because it would add another planner and does not match the user's preference for the floating chat surface.

## Goals and constraints

- Overlay requests use current ACA/provider state and the existing `send()` policy.
- The existing one-shot request token, timeout, durable action journal, and explicit screen-action approval remain mandatory.
- ACA remains read-only; do not log email contents or reuse the removed Sarvam test key.
- Use the emulator for acceptance; do not touch the connected physical phone.

## Non-goals

- Add a new realtime-voice ACA route or shared ACA browser conversation history.
- Store, log, or upload screen text or mailbox contents beyond the existing requested ACA summary behavior.
- Change the behavior of message sending or remove per-action screen review.

## Verification

Add a failing regression for the listener's stale callback before implementing the ref. Then verify an overlay ACA summary on the connected emulator, compare the same 24-hour prompt with ACA web Chat using only count/scope/freshness/partial status, and exercise one approved overlay screen scroll on a safe emulator Settings view. Provider-backed synthesis remains live-unverified without a fresh valid provider credential.
