import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=p=>readFileSync(root+p,'utf8');
const wake=read('NexaWakeWordService.kt');
const overlay=read('NexaWakeFallbackOverlay.kt');
const voice=read('RealtimeVoiceService.kt');

test('wake always tries compact native voice fallback when OEM assistant service unavailable',()=>{
  assert.match(wake,/showFromWakeWord\(\)/);
  assert.match(wake,/if \(!shown\)/);
  assert.match(wake,/showFallbackConversation\(manager\)/);
  assert.match(wake,/assistant did not open|assistant session did not open/i);
  assert.match(overlay,/TYPE_APPLICATION_OVERLAY/);
  assert.match(overlay,/Settings\.canDrawOverlays\(service\)/);
  assert.match(overlay,/isOnDeviceRecognitionAvailable\(service\)/);
  assert.match(overlay,/SpeechRecognizer\.createOnDeviceSpeechRecognizer\(service\)/);
  assert.match(overlay,/NexaWakeWordService\.assistantShown\(\)/);
  assert.match(wake,/assistantClosed\(\)/);
  assert.doesNotMatch(overlay,/startForegroundService\(|WebSocket|SmsManager|READ_CONTACTS|Runtime\.getRuntime/);
});

test('fallback voice uses a bounded on-device speech loop with truthful action status',()=>{
  assert.match(overlay,/maxTurns = 16/);
  assert.match(overlay,/handler\.postDelayed\(silence, 18_000L\)/);
  assert.match(overlay,/2 \* 60 \* 1000L/);
  assert.match(overlay,/NexaOverlayCommands\.handle\(service, phrase\)/);
  assert.match(overlay,/SpeechRecognizer\.ERROR_NO_MATCH/);
  assert.match(overlay,/TextToSpeech/);
});
test('native live voice catches initialization failures and preserves privacy',()=>{
  assert.match(voice,/Log\.e\("NexaVoice", "Provider initialization failed/);
  assert.match(voice,/stopWithStatus\("error", "Could not initialize live voice provider"\)/);
  assert.match(voice,/FOREGROUND_SERVICE_TYPE_MICROPHONE/);
  assert.match(voice,/Stop voice/);
  assert.doesNotMatch(voice,/Log\.e\([^\n]*apiKey|Log\.e\([^\n]*token/);
});
