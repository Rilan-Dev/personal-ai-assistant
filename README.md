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
- **Quick Settings tile**: an owner-added Android tile opens the chat
  after unlock if locked. No automatic microphone or tool execution.
- **Tap to talk** in the Android app uses strictly on-device speech recognition
  (Android API 31+ if the service is installed), after a one-time runtime
  microphone permission. Transcript fills the editor for review and does not
  execute any tools until **Send** is pressed.
- Opt-in **Read last reply aloud** uses installed offline TextToSpeech voices
  only, with explicit playback Stop. No hotword/background listening.
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
voice on a real device, accessibility, contacts, and WhatsApp sending are **not yet verified**.

See [full roadmap](docs/project-control/ROADMAP.md),
[next actions](docs/project-control/NEXT_ACTION.md), and
[upstream harness research](docs/architecture/HARNESS_UPSTREAM_RESEARCH.md).
