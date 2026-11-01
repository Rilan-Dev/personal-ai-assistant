import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const code = readFileSync(
 'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaAssistantService.kt',
 'utf8'
);

test('system assistant accepts one explicit on-device spoken command with no cloud fallback',()=>{
  assert.match(code,/button\("Speak a device command"\)/);
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
test('system spoken commands are deterministic and do not send messages or tap arbitrary apps',()=>{
  assert.match(code,/private fun applyDeviceCommand\(raw: String\)/);
  assert.match(code,/"open github", "open github app", "open my github app"/);
  assert.match(code,/"open whatsapp", "open my whatsapp"/);
  assert.match(code,/"find github", "find my github app", "find github app"/);
  assert.match(code,/LauncherNavigationState\.begin\(pkg\)/);
  assert.match(code,/Messages require review in Nexa Tools/);
  assert.doesNotMatch(code,/sendMessage|SmsManager|ACTION_SET_TEXT|ACTION_CLICK|dispatchGesture/);
});
