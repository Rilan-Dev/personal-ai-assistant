import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const code = readFileSync(
 'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaAssistantService.kt',
 'utf8'
);

test('system assistant loops on-device speech turns and does not force cloud fallback',()=>{
  assert.ok(code.includes('button("🎙 Talk")'));
  assert.match(code,/beginLocalConversation\(\)/);
  assert.match(code,/replyAndListen\(result\)/);
  assert.match(code,/TextToSpeech/);
  assert.match(code,/silenceWindowMs = 18_000L/);
  assert.match(code,/window\.setGravity\(Gravity\.BOTTOM\)/);
  assert.match(code,/SpeechRecognizer\.isOnDeviceRecognitionAvailable\(app\)/);
  assert.match(code,/SpeechRecognizer\.createOnDeviceSpeechRecognizer\(app\)/);
  assert.match(code,/EXTRA_PREFER_OFFLINE, true/);
  assert.match(code,/RECORD_AUDIO/);
  assert.match(code,/RealtimeVoiceService\.status == "listening"/);
  assert.match(code,/RealtimeVoiceService\.status == "connecting"/);
  assert.match(code,/20000L/);
  assert.match(code,/cancelDeviceCommand\(\)/);
  assert.doesNotMatch(code,/createSpeechRecognizer\(|recordFile|uploadTranscript|SpeechRecognizer\.createSpeechRecognizer/);
});
test('system spoken commands use the same deterministic gate as typed overlay tools',()=>{
  const commands = readFileSync('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaOverlayCommands.kt','utf8');
  assert.ok(code.includes('private fun applyDeviceCommand(raw: String)'));
  assert.ok(code.includes('NexaOverlayCommands.handle(app, command, "assistant-role")'));
  assert.ok(commands.includes('"open github", "open github app"'));
  assert.ok(commands.includes('"open whatsapp", "open my whatsapp"'));
  assert.ok(commands.includes('"find github", "find github app"'));
  assert.ok(commands.includes('LauncherNavigationState.begin(pkg)'));
  assert.ok(commands.includes('Not sent.'));
  assert.doesNotMatch(code,/sendMessage|SmsManager|ACTION_SET_TEXT|ACTION_CLICK|dispatchGesture/);
  assert.doesNotMatch(commands,/sendTextMessage|SmsManager|ACTION_SET_TEXT|ACTION_CLICK|dispatchGesture/);
});
