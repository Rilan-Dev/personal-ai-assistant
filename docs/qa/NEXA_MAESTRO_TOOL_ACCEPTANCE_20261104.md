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

## Verified follow-up evidence — 2026-11-04 (supersedes earlier provisional run statuses)

| Acceptance surface | Evidence | Result |
|---|---|---|
| Development signed APK and native security/action replay | [Android run #41802422534](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41802422534), source `714b175b` | PASS: 340 source tests, 110 Android API 35 instrumentation tests, signed APK and signature verification |
| Twelve standard Maestro UI flows | [Maestro #41804370133](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41804370133), Development | **PASS 12/12**: setup, navigation/resume, overlay, offline wake setup/runtime, voice unconfigured safety, Chrome observed results, screen settings entry, battery/status observed native values, one-time approval, and disconnected ACA email consent gate |
| WhatsApp owner request **local review only** | [Maestro #41848990853](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41848990853), manually owner-triggered from main workflow, exact Development source checkout and signed APK #41802422534 | **PASS 1/1**: quoted owner request reached local review notice. No contact automatically chosen; no message sent |
| Notification shade actual Android system action | [Maestro #41790632409](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41790632409) | **FAIL/NO-GO**: AccessibilityService readiness was not observable in Maestro Cloud before action. Do not claim the notification panel opened |
| Live Sarvam/OpenAI provider + microphone, real phone voice/wake, WhatsApp draft in real app, safe screen taps/scrolls and crash-free hardware use | No authorized physical device or authenticated live-provider acceptance record | **NOT VERIFIED/NO-GO**; source/emulator tests are not substitutes |

### Tool-specific acceptance boundaries

- **Chat battery/status:** positive UI evidence from Maestro on Android Cloud. **Storage:** native policy-tested; separate real-device measurement not accepted.
- **Chrome search:** the standard Maestro flow observed the destination page after owner review. **Camera, flashlight and arbitrary installed-app launch:** native code/policy checks exist, but real hardware/OS grants/physical result still need acceptance.
- **WhatsApp:** quoted request to the one-time local review is verified. Actual Android contact selection, intended-recipient correctness and WhatsApp draft launch are **not** verified. Nexa never automatically sends.
- **Accessibility, icons, tap, scroll, notification shade, Back:** unit/native policy coverage does not prove a bound service or a visible external-app result. The separate notification test fails readiness in Maestro's suppressed-service environment; physical-device testing is still necessary.
- **Hey Nexa and voice:** offline setup, model download and stop UI flows passed in Cloud; actual wake-phrase recognition and live Sarvam/OpenAI microphone/provider conversations have **not** passed physical acceptance.
- **ACA email:** safe unconnected mailbox state passed. This does not prove a real authorized mailbox sync/summary, nor authorize sending mail.

### Release decision

**Standard Maestro Cloud UI suite: accepted 12/12. WhatsApp review-only Cloud scenario: accepted 1/1. Complete Nexa functionality: NO-GO**, pending live Android device evidence, real provider testing and the still-unverified notification/screen-control actions. Keep Android permission grants and all external side effects owner controlled; never replace unverified results with a success label.

The Development test was fixed in [PR #82](https://github.com/Rilan-Dev/personal-ai-assistant/pull/82). A separate owner-comment workflow for running a signed Development artifact from the default-branch GitHub workflow was merged in [PR #83](https://github.com/Rilan-Dev/personal-ai-assistant/pull/83); it is manual-only, verifies release ancestry, APK hash and unchanged app source, and uses per-flow CLI proof.
