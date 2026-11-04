# Nexa Maestro and native tool acceptance — 2026-11-04

## Release under test

- Signed APK source revision: `915bfaf9ebdf98ff1b82264626b16204c82d7177`.
- GitHub Actions native instrumentation, release APK assembly and permanent signer: [PASS](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41788135631).
- Isolated source suite: 337/337 tests passed after `npm ci`; TypeScript checks passed.
- Existing full Maestro Cloud attempt: [#41790615424](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41790615424) **NOT ACCEPTED**. All nine cloud flows were STOPPED, not passed. The generated HTML's aggregate `PASSED` heading is misleading when the individual results say `STOPPED` and the CI job failed.
- Experimental notification flow: [#41790632409](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41790632409) **FAIL**. `Nexa Screen Assist connected.*native screen actions ready` never became visible, before an actual notification action could be observed.
- One-at-a-time full Maestro rerun: [#41791083081](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41791083081) started after both prior runs completed; do not mark it passed until all individual steps show success.
- The Maestro workflow now queues owner-requested cloud runs instead of allowing overlapping project uploads. It remains manual-only.

## Automated coverage and what it proves

| Behavior | Tests | Acceptance limitation |
|---|---|---|
| Fresh setup, five-tab navigation, background/resume | Maestro 01, 08 | Must finish with successful individual cloud results |
| Offline wake setup, model download, start and stop | Maestro 02, 06 | Does not prove always-on wake phrase recognition on an actual OEM phone |
| Overlay permission, floating bubble | Maestro 03, 05 | OS permission and bubble must actually appear |
| Voice without a configured provider | Maestro 04 | Safe negative case, not a live Sarvam/OpenAI STS session |
| Screen Assist Settings entry | Maestro 07 | Opening Settings does not prove AccessibilityService binding |
| Owner-approved Chrome browser search | Maestro 09 | Must observe the Chrome results page, not just accepted intent |
| Battery percent and native device status | New Maestro 10 | Requires actual BatteryManager response and bounded percentage visible |
| Deny and one-time approval consumption | New Maestro 11 | Harmless training tool; must not act on other apps or send data |
| Notification shade after user grant | Experimental Maestro 10 | Strictly **NO-GO** until connected service and actual SystemUI panel visible |
| Source intent policy, approval replay, device-action journal | 337 source tests + native Android tests | Passing mocks/policies does not prove actual hardware or external-app outcome |

## Full functionality still requiring independent evidence

| Tool family | Correct evidence |
|---|---|
| Chat native battery, status, storage | Android-provided measured values through both UI and provider tool routes |
| Camera, flashlight | Supported physical hardware, OS grant, approval, observed on/off or camera foreground |
| Chrome and installed apps | Exact intended app/page actually opens; no false assertion from dispatched intent |
| WhatsApp draft | Intended contact reviewed and draft displayed; Nexa never claims an unverified send |
| Screen text, icons, safe taps and four-direction scrolling | User-enabled bound Accessibility service, individual user approval, matching external app/window, observed result and zero crashes |
| Notification shade and Back | Actual Android SystemUI/navigation outcome verified on device |
| Sarvam and OpenAI voice | Real authorized credentials and microphone/audio sessions, interruption, stop/restart, tool-call output, failure recovery |
| Offline wake and background voice | Physical-phone tests across screen lock, incoming call, audio focus, battery saver and process restart |
| Privacy and security | Restricted settings remain user-driven; no ADB permission injection, no autonomous messaging or unapproved external actions |

## Exit criteria

**DO NOT mark complete simply because the signed APK built or the Maestro report said PASSED at aggregate level.** All individual Maestro flows must pass; negative and restricted-setting cases must remain truthful; native provider calls and physical-device controls need separately recorded evidence. Android instrumentation can suppress independent Accessibility services via default UiAutomation flags, so a Maestro test failing to bind Screen Assist is not proof of real-phone functionality, nor can it be counted as success. Record an actual on-device `dumpsys accessibility` and logcat trace if the failure persists.

For physical-device diagnostic steps, use [Nexa Screen Assist runtime acceptance](NEXA_SCREEN_ASSIST_RUNTIME_ACCEPTANCE_20261104.md) and the read-only PowerShell `scripts/acceptance/nexa-screen-assist-device.ps1`. Share logs only after redacting private app text and account information.
