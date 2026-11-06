import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const native='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const src=name=>readFileSync(native+name,'utf8');
const app=readFileSync('apps/mobile/App.tsx','utf8');
const tools=readFileSync('apps/mobile/src/native/androidTools.ts','utf8');

test('owner-only native emergency stop is persistent, reversible and never a model tool',()=>{
  const policy=src('NexaNativeActionSafety.kt');
  const bridge=src('DeviceControlModule.kt');
  assert.match(policy,/owner_paused_device_actions/);
  assert.match(policy,/getBoolean\(KEY,false\)/);
  assert.match(policy,/fun permitsNewAction/);
  assert.match(policy,/owner_pause_generation/);
  assert.match(policy,/fun pauseGeneration/);
  assert.match(bridge,/fun setNativeDeviceActionsPaused\(paused:Boolean,promise:Promise\)/);
  assert.match(bridge,/currentActivity\?\.hasWindowFocus\(\)!=true/);
  assert.match(bridge,/keyguard\?\.isKeyguardLocked==true/);
  assert.match(app,/Native device actions · owner stop/);
  assert.match(app,/updateNativeActionsPaused\(next\)/);
  assert.match(tools,/nativeActionSafetyAvailable/);
  const registry=tools.slice(tools.indexOf('export function createAndroidTools'));
  assert.doesNotMatch(registry,/setNativeDeviceActionsPaused|updateNativeActionsPaused/);
});

test('pausing blocks permanent UI claims and legacy Voice owner approvals',()=>{
  assert.match(src('NexaUiActionJournal.kt'),
    /!NexaNativeActionSafety\.permitsNewAction\(ctx\)/);
  assert.match(src('NexaLegacyVoiceStepApproval.kt'),
    /!NexaNativeActionSafety\.permitsNewAction\(context\)/);
  assert.match(src('RealtimeVoiceService.kt'),
    /pending\.safetyGeneration==NexaNativeActionSafety\.pauseGeneration\(this\)/);
  assert.match(src('NexaBrowserTaskActions.kt'),
    /NexaNativeActionSafety\.permitsNewAction\(context\)/);
  assert.match(src('NexaOverlayCommands.kt'),
    /NexaNativeActionSafety\.permitsNewAction\(context\)/);
  assert.match(src('DeviceControlModule.kt'),
    /NexaMessageInputActions\.cancel\(\)/);
  assert.match(src('DeviceControlModule.kt'),
    /NexaTextInputActions\.cancel\(\)/);
  assert.match(src('FlashlightControlModule.kt'),
    /enabled && !NexaNativeActionSafety\.permitsNewAction\(context\)/);
  assert.match(src('NexaToolOverlayService.kt'),/Pause device actions/);
  assert.match(src('NexaToolOverlayModule.kt'),/setOf\("chat","voice","tools","settings"\)/);
});
