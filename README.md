# Personal AI Assistant

An Android-first personal agentic AI assistant built with Expo SDK 58, React Native,
TypeScript and Kotlin. The same repository contains an iOS native starter and
a Web demo. Linux, macOS and Windows desktop clients remain future milestones.

## What actually works in source

- React Native chat UI and opt-in native device launch grant.
- Android Kotlin bridge for allowlisted WhatsApp/WhatsApp Business and camera
  **Intent launch requests**, not clicks, screenshots, message delivery or calls.
- The `@paa/agent-core` bounded tool-execution engine with local capability gates.
- An Android Keystore-backed encrypted checkpoint and at-most-once action ledger:
  the app durably reserves each action before execution and cannot silently
  replay an uncertain action after a crash.
- **Flashlight**: Android Camera2 on/off requests gated by explicit OS
  CAMERA consent, a separate in-app hardware grant and one-time approval.
  A TorchCallback must observe the changed state before success; no camera
  images or video are read by this tool. Physical phone UAT pending.
- **Battery status**: read-only Android BatteryManager tool. "Check battery
  level" reports current percentage and charging state, with no phone identifiers
  or privileged permissions.
- **Mobile UI**: four bottom tabs (Chat, Voice, Tools, Settings), modern
  midnight-dark layout, safe-area support for display cutouts/status bar,
  readable system type and conversational tool approvals.
- **Experimental live voice**: explicitly owner-started Android microphone
  foreground service with Stop notification and OpenAI Realtime audio
  streaming. Can continue while other apps are visible when Android permits;
  not an unattended wake-word listener. Real-device testing pending.
- **One-time screen text assist**: optional owner-enabled Android Accessibility
  service; arm for 20 seconds, inspect a single bounded text snapshot from
  another app, review/discard or explicitly send to AI. Never auto-acts.
- **In-app AI Configuration**: choose OpenAI, Gemini, Groq or OpenRouter,
  select model, enter a masked API key, test the connection and save encrypted
  settings on Android using Android Keystore. Direct BYOK AI chat works
  without a separate LangGraph backend. API provider costs may apply.
- **Installable release-test APK**: GitHub Actions builds the optimized
  Android release variant signed with a dedicated testing certificate,
  verifies its signature and publishes an APK artifact plus SHA-256.
  This is for private testing, not a public production release.
- **Nexa panel offline device command (source milestone):** tap Speak a device
  command after invoking the Android assistant gesture; exact offline phrases
  Open GitHub, Open WhatsApp and Find my GitHub app use fixed native allowlists.
  It is one-shot, owner-triggered, and cannot send WhatsApp messages or act as
  a background wake-word detector. Physical-phone tests remain mandatory.
- **Nexa Android assistant opt-in (source milestone):** Android RoleManager
  selection and system-owned compact VoiceInteractionSession UI. The owner can
  start/stop existing live audio from the panel after granting microphone and
  notification access in the app. This is not a hotword detector, not a hidden
  recording service, and is pending APK/emulator and real-phone acceptance.
- **Owner-enabled GitHub launcher navigation (source milestone):** separate
  Accessibility grant; owner-initiated scan of default HOME for GitHub icon,
  bounded horizontal swipes, no icon click and no arbitrary app access.
- **Explicit Chat-to-WhatsApp review:** a quoted message request locally
  prefills Tools and asks the owner to select the correct contact; no alias
  inference, automatic dispatch or hidden WhatsApp Send action.
- **WhatsApp reviewed compose (source milestone):** user selects one contact,
  enters a short message and opens a draft for manual Send in WhatsApp. The
  assistant never asserts that a message was sent or delivered.
- **GitHub package lookup (source milestone):** an owner-triggered, allowlisted
  package check for com.github.android and an optional GitHub launch Intent.
  The Android launcher icon page cannot be inferred and is reported unknown.
- **Quick Settings tile**: an owner-added Android tile opens the chat
  after unlock if locked. No automatic microphone or tool execution.
- **Tap to talk** in the Android app uses strictly on-device speech recognition
  (Android API 31+ if the service is installed), after a one-time runtime
  microphone permission. Transcript fills the editor for review and does not
  execute any tools until **Send** is pressed.
- Opt-in **Read last reply aloud** uses installed offline TextToSpeech voices
  only, with explicit playback Stop. The separate live-voice mode is
  user-started with an Android microphone foreground service and notification;
  there is no wake-word or unattended recording.
- Owner-driven **Approve once**, **Decline** and **Resume saved task** controls.
  Use the **Test approval** quick prompt on a native Android build to test
  this workflow safely; it does not perform any real device operation.
- **Real LangGraph.js StateGraph** in the Node-only `services/agent-runtime` service
  for one-step model planning and validation, using the LangChain OpenAI-compatible
  model adapter. Model calls run on the server; mobile executes authorized tools.
- A planning-only, authenticated HTTP API and an opt-in Android UI connection.
- Unit/integration test suite for policy bypasses, invalid tool calls, HTTP
  authentication, prompt-injection-safe observation redaction, and loop behavior.

## Testing and developing

Node.js 22+ is required. An Android SDK/JDK must be installed to compile an
APK; Xcode on macOS is required for an iOS build.

```bash
npm ci
npm test
npm run typecheck
npm run web:export
npm run android # Requires Android SDK/ADB
```

The generated Web output and simulated demo do not imply the Android native
bridge has run on an actual phone.

## Optional LangGraph planning service

The Android UI uses its **local deterministic planner by default**.
To opt in to a remote LLM planner:

1. Provision a trusted Node server, or run the service on your own machine.
2. Generate a random 32+-character access token and put it only in the server
   environment as `AGENT_API_TOKEN`. Set `AGENT_LLM_API_KEY`,
   `AGENT_LLM_MODEL`, and optionally an HTTPS
   `AGENT_LLM_BASE_URL` for compatible providers.
3. Start `npm run start:agent`. It binds to `127.0.0.1:8741` by default.
4. For phone access, put a secure TLS-terminating proxy or tunnel in front
   of the service. Do not expose a plaintext TCP API to the Internet.
5. In the **native Android app**, expand **Agent backend**, enter your HTTPS
   base URL and session bearer token, then explicitly enable the connection.

A mobile client never receives the model API key. The server supports only
the currently allowlisted planning tool IDs. It does not have access to the
device, does not execute actions, and does not automatically run in the
background. It receives user commands and minimized tool outcomes only when
the user enables it. The mobile access token is held in memory for the
current app session and cleared when disconnected.

**Important:** A backend planner can request a tool, but the native executor
may reject it if the app-launch grant is off or the tool is invalid. Any
action beyond camera/app launching is unavailable and must not be claimed
to have executed.

## Security / release readiness

This GitHub repository is currently **public**. Never commit tokens,
credentials, API keys, contacts, messages, screen captures, or signing keys.
The code is under development; do not use it for emergency communication,
live messaging, or private-device data without reviewing permissions.

After a successful Android debug build, a seven-day GitHub Actions artifact can be downloaded for owner device testing. It is NOT a signed production release.

A debug APK produced in a different CI run uses a fresh temporary signing key;
it may require uninstalling the previous debug build, which clears its local
journal. Process-restart acceptance tests should use the SAME installed APK.
The new journal has not yet passed physical-device process-death testing.

Before personal APK release, resolve or assess upstream dependency advisories
and complete a real Android phone acceptance test. Backend LLM provider calls,
Android physical-device tests, iOS native compilation, wake-word detection,
live speech on a real device, Accessibility service behavior across other apps,
contacts and WhatsApp sending are **not yet verified**. The new Android live
voice and screen reading adapters require a fresh manual CI build and UAT.

See [full roadmap](docs/project-control/ROADMAP.md),
[next actions](docs/project-control/NEXT_ACTION.md), and
[upstream harness research](docs/architecture/HARNESS_UPSTREAM_RESEARCH.md).

## Development branch and release CI quota policy

Contributors build features on the Development branch; commits and pull
requests to that branch NEVER trigger GitHub Actions. Once a complete
objective passes local tests, promote the tested changes to main in ONE
fast-forward push. Android/agent source changes to main start exactly ONE
consolidated GitHub Actions pipeline: source tests, Android instrumented
emulator acceptance, then a signed release-test APK only after earlier gates
succeed. Documentation-only changes do not trigger the pipeline.

The original five workflows remain manual-only. The owner also authorizes
selective manual runs when authenticated GitHub Actions write access is
available. See docs/architecture/ANDROID_RELEASE_TEST_APK.md.

## Sarvam AI chat, dictation, speech synthesis, live audio

The Android application now offers Sarvam AI as a BYOK provider under
Settings > AI Configuration. The default chat model is sarvam-105b,
authenticated with api-subscription-key over the official HTTPS API.

The separate Voice tab uses Sarvam Saaras v4 for explicit one-shot
dictation, Bulbul v4 Flash for optional read-aloud, and a native
Android WebSocket for owner-started full-duplex managed Sarvam Voice
Agents. Configure a **committed** Sarvam agent's org, workspace and
app IDs under Settings > Sarvam Voice Agent. Android Keystore encrypts
the account key and agent settings. Live audio travels directly to
Sarvam and returns directly to the speaker; the app **does not
display, persist or forward live voice transcripts**. Sarvam itself
may perform speech recognition and retain call data on its servers.

For provider setup, protocol, source validation and acceptance
limitations, see docs/architecture/SARVAM_AI_INTEGRATION.md.

### On-demand private Android tools

On Android, **Tools → Private, one-time observations** lets the
signed-in phone owner select a single phone contact through the system
contact picker or read an approximate current location. The app does
not request bulk READ_CONTACTS, ACCESS_FINE_LOCATION, or background
location permission. It uses Android's temporary selected-contact
URI grant and the foreground ACCESS_COARSE_LOCATION permission only.

Results are previewed locally, automatically cleared when leaving
Tools or after 60 seconds, and are never sent to an AI provider until
the owner explicitly inserts them into Chat and sends that message.
The one-time location API is available on Android 11 or later.
Neither a voice agent nor an LLM can call these privacy-sensitive
tools automatically.
