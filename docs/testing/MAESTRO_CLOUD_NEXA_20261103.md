# Nexa Maestro Cloud acceptance suite (November 3, 2026)

Source application: `Rilan-Dev/personal-ai-assistant`. Application ID: `dev.rilandev.personalaiassistant`.
Maestro project: `proj_01m6fyb4qre3kvtx4jft00gvnh`.
Device baseline: Pixel 6 Android 13 (`android-33`), with Android 14–16 available in the manual workflow.

## Status

**NOT YET EXECUTED ON MAESTRO CLOUD**. The Android release-test APK with ARM64 native libraries
was downloaded from successful GitHub run `41562771187`, with source commit
`c5387224a36876fd6cee106de5d31ee8c5a9d7c6`.
Maestro CLI v2.12.1 was installed and eight isolated flows were written. No
Maestro Cloud execution was started: the runner did not have an authenticated
Maestro Cloud credential, and a direct invocation carrying the credential was
blocked by the remote command security boundary. Do not mark a flow as passed
until it has a corresponding Maestro Cloud run result.

## Manual GitHub Actions execution (credentials never committed)

1. Rotate the Maestro API key if it was pasted into a chat or ticket.
2. In the Nexa GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret**.
3. Add **MAESTRO_CLOUD_API_KEY** with the newly rotated key. Do not store it in YAML, PR comments, logs, or APKs.
4. After `.github/workflows/maestro-cloud-manual.yml` has been merged into the default branch,
   visit **Actions → Nexa Maestro Cloud UI acceptance (manual only) → Run workflow**.
5. Select `suite: all` for the full suite, or a specific suite when debugging;
   select `android-33` to match the Android 13 baseline, then check the Maestro Console
   for per-flow status, screenshots, command history, and video.

The workflow is **manual-only** (`workflow_dispatch`); it creates a fresh debug APK
from the exact selected Git commit and uploads it to Maestro Cloud using
`MAESTRO_CLOUD_API_KEY` as a masked environment secret. It does not use personal
Sarvam/OpenAI keys or contact information. A source code failure is not to be
misrepresented as a completed cloud test.

## Flows

| Flow | Purpose | Acceptance type |
| --- | --- | --- |
| `01-fresh-setup-and-navigation.yaml` | Fresh-install startup and five RN tabs | UI smoke |
| `02-hey-nexa-wake-setup.yaml` | Hey Nexa disclosures, disabled model, status refresh | UI / privacy |
| `03-overlay-permission-entry.yaml` | Navigate into Android display-over-other-apps setting | OS permission |
| `04-voice-without-provider.yaml` | Verify voice screen handles missing provider safely | UI / configuration |
| `05-overlay-bubble-end-to-end.yaml` | Grant overlay, show bubble over Home, expand panel | Experimental OS E2E |
| `06-hey-nexa-model-runtime.yaml` | Download offline model, start/stop opt-in foreground listener | Experimental wake runtime |
| `07-screen-assist-settings-entry.yaml` | Open Accessibility settings from permission center | OS permission |
| `08-navigation-background-resume.yaml` | Repeated tab taps and foreground/background restoration | Crash smoke |

The experimental flows deliberately fail if permissions, overlay rendering,
model download or service startup cannot be completed. Screenshots and native
hierarchy data from Maestro should be used to correct brittle selectors.
These flows make no contacts, no outgoing calls, and no WhatsApp sends.

## Critical boundaries

- An emulator UI test **cannot certify** the physical Infinix/XOS launcher's
  horizontal page gestures, OEM overlay lifetime, no-gesture hotword detection,
  incoming/outgoing SIM-call behavior, or battery-policy effects.
- A screenshot of `Listening for Hey Nexa` proves only that the app reports the
  state; it does **not** prove that a spoken wake phrase actually activated it.
- To test actual wake detection, feed known audio into a supported local device
  or use an instrumented offline detector input with consent, then verify a
  full service handoff; finally repeat on the owner's physical Android phone.
- System overlay permissions and Accessibility privileges are not equivalent
  to ordinary Android `RECORD_AUDIO` runtime grants.
- Cloud test devices are ephemeral. Avoid putting real communications, personal
  contacts, API keys, chat history or provider accounts in Maestro screenshots.
- Inspect crash/ANR evidence after each failure: a successful UI tap is not
  evidence that Accessibility clicked the intended control or that the service
  survived the action.

## Triage acceptance checklist

- One successful cloud run with recorded results and screenshots on Android 13;
  repeat on Android 14 and 16 when possible.
- Fresh install renders the expected modern Nexa UX, not a previous design.
- Overlay grant → floating bubble → home screen → expanded overlay all verified.
- User opt-in wake listener starts/stops without process death or stale mic state.
- No cloud-flow test reports an unverified action as a completed user task.
- Physical-device microphone wake-word and XOS overlay UAT remain separate gates.
