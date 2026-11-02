import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const kotlin='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=path=>readFileSync(path,'utf8');
const setup=read(kotlin+'NexaSetupModule.kt');
const assistant=read(kotlin+'NexaAssistantService.kt');
const audio=read(kotlin+'RealtimeVoiceService.kt');
const ui=read('apps/mobile/src/components/NexaSetupScreen.tsx');
const bridge=read('apps/mobile/src/native/nexaSetup.ts');
const app=read('apps/mobile/App.tsx');

test('live voice from assistant gesture defaults off and needs visible explicit opt-in',()=>{
  assert.match(setup,/getBoolean\("auto_voice_invocation_v1", false\)/);
  assert.match(setup,/currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(setup,/openVoiceCredentials\(context\) != null/);
  assert.match(setup,/if \(enabled && !liveProviderReady\)/);
  assert.match(setup,/VOICE_PERMISSION_MISSING/);
  assert.match(setup,/autoStartVoiceOnInvocation/);
  assert.match(bridge,/setAutoStartVoiceOnInvocation/);
  assert.match(ui,/Audio is sent to that provider during the active session/);
  assert.match(ui,/onAutoVoiceToggle/);
  assert.match(app,/onAutoVoiceToggle=.*toggleAssistantConversation/);
});

test('system invocation routes to live provider only when configured and opted in',()=>{
  assert.match(assistant,/getBoolean\("auto_voice_invocation_v1", false\)/);
  assert.match(assistant,/val automaticLiveVoice = hasCredentials && \(cameFromWake &&/);
  assert.match(assistant,/if \(automaticLiveVoice\) startOwnerVoice\(\)/);
  assert.match(assistant,/else feedback\("Save a live AI provider key/);
  assert.match(assistant,/liveVoiceLaunchedFromSession/);
  assert.match(assistant,/action = RealtimeVoiceService.ACTION_STOP/);
  assert.match(assistant,/NexaWakeWordService.assistantClosed\(\)/);
});

test('barge-in invalidates buffered output and rejects old response chunks',()=>{
  assert.match(audio,/private val playbackTurn = AtomicLong\(0L\)/);
  assert.match(audio,/synchronized\(playbackLock\)/);
  assert.match(audio,/playbackTurn.incrementAndGet\(\)/);
  assert.match(audio,/chunk.turn == playbackTurn.get\(\)/);
  assert.match(audio,/json.optString\("response_id"\) == responseId/);
  assert.match(audio,/responseId = json.optJSONObject\("response"\)/);
  assert.match(audio,/\.put\("interrupt_response",true\)/);
  assert.match(audio,/Reply in English, Tamil or Hindi/);
  assert.match(audio,/Never claim an app opened unless the result confirms it/);
  assert.doesNotMatch(audio,/AudioManager\.setMicrophoneMute|AppOpsManager/);
});
