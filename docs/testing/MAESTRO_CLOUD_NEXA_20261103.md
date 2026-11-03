# Nexa Maestro Cloud — Android UI acceptance

Date: **November 3, 2026**. Repository: `Rilan-Dev/personal-ai-assistant`.
Application: `dev.rilandev.personalaiassistant`.
Maestro Cloud project: `proj_01m6fyb4qre3kvtx4jft00gvnh`.

## Verified result

**8 of 8 Maestro Cloud flows passed (0 failed) on Pixel 6, Android 13 (`android-33`).**

- GitHub Actions acceptance: [run #41613381123](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41613381123) — completed with success.
- [Maestro Cloud results, screenshots, and per-step evidence](https://app.maestro.dev/project/proj_01m6fyb4qre3kvtx4jft00gvnh/maestro-test/app/app_01m6g5kv3hfyprkeqh4wqmtbdn/upload/mupload_01m6gcpypkf9csx0rf49rx1e70).
- Tested artifact: the **signed release APK** from successful main-promotion [run #41597774425](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41597774425), not a separately rebuilt debug APK.
- The acceptance workflow verified the APK's SHA-256, embedded source commit, and unchanged app-runtime source before uploading it to Maestro Cloud.
- The eight flows include **112 YAML steps**. The cloud result, not YAML parsing or an APK build, is the pass evidence.

## Individual flows

| Flow | What passed |
| --- | --- |
| `01-fresh-setup-and-navigation.yaml` | Fresh-install setup and all five React Native tabs |
| `02-hey-nexa-wake-setup.yaml` | Privacy-first activation disclosure, protected-hotword limitation, model download control, status refresh |
| `03-overlay-permission-entry.yaml` | Launch Android's display-over-apps Settings permission screen |
| `04-voice-without-provider.yaml` | Unconfigured provider state shown without a live provider or silent fallback |
| `05-overlay-bubble-end-to-end.yaml` | Open Android 13 permission list, select Nexa AI, grant overlay permission, show bubble over Home, expand default Chat, switch to Voice and verify Talk control |
| `06-hey-nexa-model-runtime.yaml` | Download offline wake model; opt in to and stop Android's foreground Hey Nexa listener |
| `07-screen-assist-settings-entry.yaml` | Open Android Accessibility settings for Screen Assist |
| `08-navigation-background-resume.yaml` | Repeated tab navigation and background/foreground restoration without the scenario failing |

## Failure-to-pass history

- [Run #41597419467](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41597419467): initial upload blocked by an embedded newline in the encrypted Maestro secret. Fixed by sanitizing CR/LF **in memory**, without logging the secret.
- [Run #41597795124](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41597795124): **5/8 passed**. Three test scripts expected UI strings without scrolling or assumed Android 13 would open the app-specific overlay switch directly.
- [Run #41611651359](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41611651359): **7/8 passed**. The remaining test looked for a Voice-panel button while the floating overlay was still on its default Chat panel.
- [Run #41613381123](https://github.com/Rilan-Dev/personal-ai-assistant/actions/runs/41613381123): **8/8 passed** after verifying the bubble's Chat panel, then tapping Voice before checking its Talk control.

No special-permission bypass, forced grant, or automatic message send was introduced to obtain these results.

## How to run again

The repository workflow is [`.github/workflows/maestro-cloud-manual.yml`](../../.github/workflows/maestro-cloud-manual.yml).
It requires the encrypted GitHub Actions repository secret `MAESTRO_CLOUD_API_KEY`.
Its **explicit/manual** entry points are:

1. GitHub Actions → **Nexa Maestro Cloud UI acceptance (manual only)** → **Run workflow**, choose `main`, `suite: all`, and `android-33`; or
2. The repository owner's **exact** comment `/nexa-maestro run all android-33` on the designated [QA PR #29](https://github.com/Rilan-Dev/personal-ai-assistant/pull/29). This narrowly scoped event gate is not a general comment or push trigger.

The job retrieves the most recent successful signed main-promotion APK, verifies integrity and runtime-source consistency, then uploads it and the selected flows. Avoid placing personal contacts, actual communications, or AI-provider API credentials in Cloud screenshots.

## Important scope limits

**This is Android 13 emulator UI acceptance, not full on-device product acceptance.**

- The opt-in wake-model test verifies reported listener start/stop, **not recognition of the spoken phrase** `Hey Nexa` through the real phone microphone.
- The Cloud Pixel 6 is not the user's Infinix/XOS phone. The OEM launcher, battery restrictions, assistant gesture, and persistent system-overlay lifetime remain physical-device acceptance items.
- The suite does not exercise real Sarvam/OpenAI provider conversation, actual WhatsApp typing/sending, phone calls, or crash reproduction on Infinix. Consent and approval gates stay intact.
- Android 14–16 cross-version acceptance and real microphone audio injection are separate tests; do not mark them passed based only on this Android 13 result.
