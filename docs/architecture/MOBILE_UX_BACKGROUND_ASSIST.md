# Mobile UX and owner-controlled background assistant — 2026-11-01

Status: implemented in source; native build and physical device validation pending.

## Navigation and UI

Replaced the stacked prototype with independent Chat, Voice, Tools and Settings tabs. Chat keeps messages, shortcuts, inline one-time approvals, saved task recovery and a compact composer. Voice has push-to-talk, offline text-to-speech and a live-session control. Tools contains app/flashlight grants, screen assistant and Quick Settings entry points. Settings contains encrypted provider key configuration and the optional LangGraph backend.

Added react-native-safe-area-context to handle notches, status bars and gesture navigation. Explicit Android sans-serif replaces reliance on OEM font rendering. Automated navigation, permission, typechecking and web bundling tests pass, but narrow-screen, keyboard-open and TalkBack physical acceptance is still required.

## Experimental owner-started realtime speech-to-speech

Official references:
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
https://developer.android.com/about/versions/14/changes/fgs-types-required
https://developers.openai.com/api/docs/guides/realtime-conversations
https://developers.openai.com/api/reference/resources/realtime/client-events
https://developers.openai.com/api/docs/guides/realtime-vad

RealtimeVoiceService is non-exported and Android foregroundServiceType=microphone, with a persistent Stop notification. The owner must save an OpenAI API key in the existing Android Keystore vault, explicitly tap Start live voice while the app is visible, and grant microphone/notification permissions. There is no boot receiver, wake word, background restart or model-triggered activation. If the OS permits, an active owner-started session may continue while the owner switches apps.

The native service connects to OpenAI Realtime over WSS, with gpt-realtime-2.1-mini as a dedicated audio model independent of the text chat model. AudioRecord captures mono PCM16 at 24 kHz, bounded base64 frames are sent through input_audio_buffer.append, and server VAD handles turns. Incoming response.output_audio.delta is decoded and played using AudioTrack through a bounded queue. The service stops on owner request, notification Stop, network error, handshake failure, or at the 15-minute limit. No audio recordings, prompts or keys are logged or saved by the service. Voice does not have access to device tools.

The prototype uses the owner's personal API key via the native Keystore vault. For a public production version, move to server-minted ephemeral session tokens and a native WebRTC transport (recommended by OpenAI for mobile) with audited echo cancellation, headset routing, interruption handling, cost controls and provider authentication. Real provider/phone integration is NOT VERIFIED from source tests.

## One-time outside-app screen reading

Official references:
https://developer.android.com/guide/topics/ui/accessibility/service
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
https://support.google.com/googleplay/android-developer/answer/10964491

ScreenAssistService requires the owner to enable Accessibility in Android Settings. It has canRetrieveWindowContent=true but canPerformGestures=false, and it cannot click or type in other apps. The owner separately arms a one-time 20-second capture. Only if a different visible app emits an accessibility event is up to 2,000 characters from at most 120 visible nodes captured. Password and editable nodes are skipped, but other visible text can still be sensitive. Nothing is sent to a model automatically.

Captured text stays in process memory, expires, and can be reviewed, discarded, or placed into the chat composer as explicitly untrusted data. Sending to the provider still requires an explicit Send tap. This is not a background screen recorder, persistent monitor, unrestricted accessibility automation or screenshot analysis. Android Accessibility and Google Play rules limit autonomous AI-agent uses; a public release needs explicit standalone disclosure and policy review.

## Existing device capabilities and future work

Available in source: Android app launching, battery status, Camera2 flashlight with explicit grants and one-time approval, push-to-talk, offline text-to-speech, live microphone session prototype, one-time screen reading, encrypted credentials and task journal.

Not implemented: default assistant role, custom wake word, unattended recording, automatic screen recording, arbitrary gestures, background location tracking, contact scraping, calls, WhatsApp sending, native iOS, screen image interpretation. Those require separate OS permissions, purpose-bound interfaces and tests.

## Acceptance gates

1. Local npm ci, npm test, npm run typecheck and npm run web:export.
2. Manually run Android debug compile and emulator tests in GitHub Actions on main, following the repository's manual-only policy.
3. Correct native Kotlin/Gradle errors if found and rerun only as necessary.
4. Manually build and verify a release-test APK. Install on an owner-controlled phone.
5. Test 360/390/412px safe areas, keyboard, TalkBack and tab navigation.
6. Test owner-started foreground microphone, notification Stop, Android background restrictions and live audio connection with an authorized OpenAI API key.
7. Enable screen accessibility via Android Settings; arm, switch apps, return and review; validate no unintended sharing or gestures.

No microphone samples, screen text or provider keys were accessed during source implementation. The new services are not accepted as working on a real phone without a physical test.
