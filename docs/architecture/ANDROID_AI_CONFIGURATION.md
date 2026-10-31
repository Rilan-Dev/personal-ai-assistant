# Android AI Configuration — direct bring-your-own-key mode

## What the phone can do

The Android app has an expandable **AI Configuration & API Key** panel.
Choose OpenAI, Google Gemini, Groq or OpenRouter, enter the exact model name
supported by your provider and your personal API key, then select:

- **Test connection** — sends one small harmless prompt to the selected
  provider's HTTPS chat-completions API. This uses provider API quota/credits.
  It cannot trigger a phone tool, call, SMS, or background microphone.
- **Save & Enable AI** — validates your settings, saves encrypted configuration
  via Android Keystore AES-256-GCM, and activates the provider for chat.
- **Remove stored key** — clears the native encrypted preference and disables
  direct-provider mode. The key is never placed in a JS bundle, GitHub secret
  or repository file.

After saving, ask "Hello", "Explain the difference between AI and automation",
or a single supported device command such as "Check battery level".
The provider only proposes a typed plan. Android then independently checks
tool allowlisting, input validation, capabilities and one-time action approval
before performing any device action. Results such as battery level or torch
confirmation come from the device, not a model's unverified claim.

The current direct-provider mode is **single-turn planning**: the prompt does
not yet include previous chat messages. Android session checkpoints still
record executed tool results locally for crash/replay prevention.

## Security and privacy

- API keys are **entered by the device owner**, never compiled into the APK.
- Keys are **encrypted at rest** inside app-private Android preferences using
  the same Android Keystore AES-GCM machinery validated in emulator. Android
  automatic app backup is disabled. The key is loaded into app process memory
  only when needed to call the selected provider.
- The chat request and a bounded system instruction are sent **directly to
  the chosen third-party provider** over HTTPS with a bearer API key.
  Preset HTTPS endpoints are fixed, not editable in this initial release.
- No contacts, inbox, recorded audio, raw screen data or full native tool
  results are sent by this direct-provider adapter.
- The provider can charge for requests, depending on its billing plan.
  A ChatGPT subscription does not itself grant OpenAI API credits.
- The model can hallucinate or format output incorrectly. Invalid output
  fails closed; it cannot authorize an unrelated native tool. If there has
  been a native action, the phone uses its local observed result instead of
  asking the provider to claim success.
- This is an owner-controlled prototype, **not a multi-user production
  credential service**. A rooted/compromised phone or malicious in-process
  JavaScript can still potentially access keys while they are in memory.
- OpenAI, Gemini, Groq and OpenRouter may change their compatible models
  and API behavior. Enter a provider-supported model identifier and use
  Test connection on the actual device.

## Current supported tools

The direct-provider mode can ask the Android allowlist to: read battery,
read bridge status, launch camera, launch the installed WhatsApp app without
sending, or toggle flashlight with explicit CAMERA permission, app grant,
one-time user approval and verified hardware callback. No message sending,
contact lookup, call placement, Accessibility automation or always-on
listening is available.

The older optional remote LangGraph planning backend remains a separate
choice using its own backend access token. Choosing one planning mode disables
the other in the current running session.

## Device acceptance

1. Install the current **release-test APK** (optimized non-debuggable
   release variant) from GitHub Actions; it does not need Expo Go or a Metro
   development server.
2. Open **AI Configuration & API Key**, choose your provider, enter model/key.
3. Select **Test connection**. Verify success or a sanitized error:
   unauthorized (401/403), quota (429), model not found (404),
   unsupported response or network/timeout.
4. **Save & Enable AI**, then ask "Hello" and verify an actual answer.
5. Relaunch without uninstalling. The encrypted provider configuration should
   be restored. Remove the key and relaunch; it should remain absent.
6. Try "Check battery level" and verify the reported Android percentage.
7. Try a flashlight command and verify the independent OS grant, app grant
   and approval are still required. Deny and verify no hardware action.
8. With networking off, the provider request should fail safely without
   fallback to a different provider.
9. Verify the provider key is never printed in the chat trace or Android logs.

A real key was **not supplied to development or CI**. Mock tests verify the
protocol, tool policy and redaction; a successful real-provider call requires
the owner's configured API key and mobile network connection.
