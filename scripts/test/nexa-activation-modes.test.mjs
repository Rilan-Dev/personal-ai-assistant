import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=p=>readFileSync(p,'utf8');
const service=read(root+'NexaWakeWordService.kt');
const module=read(root+'NexaWakeWordModule.kt');
const assistant=read(root+'NexaAssistantService.kt');
const setup=read('apps/mobile/src/components/NexaSetupScreen.tsx');

test('gesture is default and previous wake preference cannot restart microphone on upgrade',()=>{
  assert.match(service,/MODE_GESTURE = "gesture"/);
  assert.match(service,/MODE_CONTINUOUS = "continuous"/);
  assert.match(service,/MODE_KEY = "activation_mode_v2"/);
  assert.match(service,/getString\(MODE_KEY, MODE_GESTURE\) == MODE_CONTINUOUS/);
  assert.match(service,/getBoolean\("wake_enabled", false\)/);
  assert.match(service,/fun enabled\(ctx: Context\): Boolean = mode\(ctx\) == MODE_CONTINUOUS/);
  assert.match(service,/putString\(MODE_KEY, if \(enabled\) MODE_CONTINUOUS else MODE_GESTURE\)/);
  assert.match(read(root+'NexaWakeBootReceiver.kt'),/NexaWakeWordService\.enabled\(context\)/);
  assert.match(assistant,/NexaWakeWordService\.enabled\(this\)/);
});
test('assistant gesture starts only opted-in live provider; local commands require Talk tap',()=>{
  assert.match(assistant,/autoDismissAfterCommand = false/);
  assert.match(assistant,/Android assistant gesture/);
  assert.match(assistant,/NexaWakeWordService\.assistantShown\(\)/);
  assert.match(assistant,/args\?\.getBoolean\("nexa_overlay_tap"\) == true/);
  assert.match(assistant,/if \(automaticLiveVoice\) startOwnerVoice\(\)/);
  assert.match(assistant,/else feedback\("Enable live AI after assistant invocation/);
  assert.match(assistant,/SpeechRecognizer\.createOnDeviceSpeechRecognizer\(app\)/);
  assert.match(assistant,/silenceWindowMs = 18_000L/);
  assert.match(assistant,/TextToSpeech/);
  assert.match(assistant,/NexaWakeWordService\.assistantClosed\(\)/);
  assert.doesNotMatch(assistant,/MANAGE_HOTWORD_DETECTION|CAPTURE_AUDIO_HOTWORD/);
});
test('continuous mode remains opt-in with honest Android microphone disclosure',()=>{
  assert.match(module,/persistEnabled\(context, true\)/);
  assert.match(module,/data\.putString\("mode", NexaWakeWordService\.mode\(context\)\)/);
  assert.match(module,/protectedHotwordAvailable", false/);
  assert.match(service,/hey nexa/);
  assert.match(service,/Turn off wake listening/);
  assert.match(service,/assistantSessionVisible/);
  assert.match(service,/handoffStartedAt/);
  assert.match(service,/>= 12000L/);
  assert.match(setup,/Disable Hey Nexa · switch to privacy-first mode/);
  assert.match(setup,/green microphone dot/);
  assert.match(setup,/Android's privacy indicator/);
  assert.doesNotMatch(service,/setMicrophoneMute|AudioManager\.setMicrophoneMute|AppOpsManager/);
});
