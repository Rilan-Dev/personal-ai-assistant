# Nexa PowerShell Tool Bridge

## Intent
Let the owner trigger Nexa's existing Android tools from PowerShell on an attached device, without issuing Android shell/input commands. The app remains the only screen executor.

## Goals
- Provide `nexa.ps1` commands for status, tool discovery/invocation, screen observation, indexed tap, directional scroll, and named system navigation.
- Carry requests over a debug-only Android local abstract socket, exposed to the host with `adb forward` only.
- Execute each tool through the existing Nexa AgentHarness and encrypted one-shot approval journal.
- Require `-Confirm` for screen reads and every tool with side effects; bind taps to a recent, single-use snapshot/index.
- Return bounded visible text and clickable labels/bounds to the local PowerShell caller only. Keep snapshot contents in memory and omit editable/password fields.
- Permit an explicit safe tool allowlist; never expose send/call/payment or arbitrary shell/raw coordinates.

## Constraints
- No `adb shell`, `adb input`, `am`, `uiautomator`, or host-side UI automation in `nexa.ps1`.
- No second autonomous planner or LLM call; each CLI action is one deterministic, validated Nexa tool call.
- Debug builds only; local abstract socket; current-window checks, owner pause checks, exact tool fingerprint, durable one-shot claims and stale-snapshot rejection remain active.
- User manually opens Nexa once so the debug bridge can start. The CLI can then operate while Nexa is backgrounded as long as Android keeps its process alive.
- Message-send and other irreversible tools remain unavailable from the PowerShell bridge.

## Approaches
1. ADB shell input: rejected because it bypasses Nexa's tool policy and violates the no-shell requirement.
2. Exported Android intents/broadcasts: rejected because the request would bypass the current tool registry and require shell/intent dispatch.
3. Debug-only local abstract socket plus React Native event dispatch: selected because it uses `adb forward` only for transport and routes actions through the existing AgentHarness/native tools.

## Runtime contract
- `GET /v1/status`, `GET /v1/tools`.
- `POST /v1/observe`, `/v1/tap`, `/v1/scroll`, `/v1/key`, and `/v1/invoke`.
- Handshake fields: protocol `nexa-qa-v1`, executor `NexaAppTools`, `debugBuild=true`, and `rawAdbInput=false`.
- Screen snapshots include an opaque ID, visible external package, bounded visible non-editable text, and safe clickable labels with dynamic bounds. The tap CLI accepts only snapshot ID plus index; native code resolves the same live target by label and rejects expired/changed snapshots.
- Named keys are limited to Back, Home, and Notifications; no arbitrary keycode injection.

