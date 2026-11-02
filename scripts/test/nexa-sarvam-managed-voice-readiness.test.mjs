import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const native='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const readiness=readFileSync(native+'SarvamVoiceReadiness.kt','utf8');
const transport=readFileSync(native+'SarvamAudioTransport.kt','utf8');
const realtime=readFileSync(native+'RealtimeVoiceService.kt','utf8');
const idleGate=readFileSync(native+'NexaVoiceIdleGate.kt','utf8');
const nativeUi=readFileSync('apps/mobile/src/native/liveVoice.ts','utf8');
const ui=readFileSync('apps/mobile/App.tsx','utf8');

test('owner tests Sarvam signed Voice Agent authorization locally, without streaming or exposing secrets',()=>{
  assert.match(readiness,/AgentJournalModule\.openSarvamVoicePreflightCredentials\(context\)/);
  assert.match(readiness,/https:\/\/apps\.sarvam\.ai\/api\/app-runtime\//);
  assert.match(readiness,/header\("X-API-Key", cfg\.apiKey\)/);
  assert.match(readiness,/appendQueryParameter\("interaction_type", "call"\)/);
  assert.match(readiness,/reference_id/);
  assert.match(readiness,/uri\.scheme == "wss"/);
  assert.match(readiness,/host\.endsWith\("\.sarvam\.ai"\)/);
  assert.match(readiness,/response\.use/);
  assert.match(readiness,/401, 403/);
  assert.match(readiness,/404/);
  assert.doesNotMatch(readiness,/Log\.|println\(|toString\(\).*apiKey|WebSocketListener|startRecording/);
  assert.match(realtime,/fun checkSarvamVoiceAuthorization\(p: Promise\)/);
  assert.match(realtime,/context\.currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(nativeUi,/checkSarvamVoiceAuthorization/);
  assert.match(ui,/Verify Sarvam Voice Agent connection/);
});

test('continuous managed conversation does not terminate for an ordinary 45-second pause or a long spoken answer',()=>{
  assert.match(idleGate,/limitMs: Long = 120_000L/);
  assert.match(realtime,/main\.postDelayed\(maxSession,60\*60\*1000L\)/);
  assert.match(realtime,/override fun audio\(bytes:ByteArray\) \{[\s\S]*idleGate\.reset\(SystemClock\.elapsedRealtime\(\)\)/);
  assert.match(realtime,/ArrayBlockingQueue<PlaybackChunk>\(96\)/);
  assert.match(realtime,/audioQueue\.poll\(\)/);
  assert.doesNotMatch(realtime,/Voice playback buffer overloaded/);
  assert.match(realtime,/sarvamTransport\?\.sendAudio\(bytes\)/);
  assert.match(transport,/server\.event\.user_interrupt/);
  assert.match(transport,/server\.action\.interaction_connected/);
  assert.match(transport,/server\.action\.interaction_end/);
});

test('managed Sarvam audio-to-audio does not reroute voice into one-shot dictation or synthesizer',()=>{
  assert.match(transport,/client\.media\.audio_chunk/);
  assert.match(transport,/server\.media\.audio_chunk/);
  assert.match(transport,/audio\/wav/);
  assert.match(realtime,/activeRate=if\(selectedProvider=="sarvam"\)16000 else RATE/);
  assert.doesNotMatch(transport,/speech-to-text-realtime\/ws|text-to-speech\/ws/);
  assert.doesNotMatch(readiness,/api-subscription-key|\.apiKey\.toString\(/);
});
