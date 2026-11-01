import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(p,'utf8');
const base='apps/mobile/android/app/src/main/';
const manifest=read(base+'AndroidManifest.xml');
const service=read(base+'java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt');
const module=read(base+'java/dev/rilandev/personalaiassistant/NexaToolOverlayModule.kt');
const role=read(base+'java/dev/rilandev/personalaiassistant/NexaAssistantService.kt');
const setup=read('apps/mobile/src/components/NexaSetupScreen.tsx');
const app=read('apps/mobile/App.tsx');

test('assistant role always provides Android Settings fallback when the dialog is unavailable',()=>{
  assert.match(role,/Settings\.ACTION_VOICE_INPUT_SETTINGS/);
  assert.match(role,/Settings\.ACTION_MANAGE_DEFAULT_APPS_SETTINGS/);
  assert.match(role,/Settings\.ACTION_SETTINGS/);
  assert.match(role,/openDefaultAssistantSettings\(p\)/);
  assert.match(role,/createRequestRoleIntent\(RoleManager\.ROLE_ASSISTANT\)/);
  assert.match(role,/context\.lifecycleState == LifecycleState\.RESUMED/);
  assert.match(setup,/Open Android Default apps \/ Digital assistant settings/);
  assert.doesNotMatch(setup,/disabled=\{[^}]*!s\.assistantRoleAvailable/);
});
test('owner-enabled overlay stays small, movable, dismissible, and nonmodal',()=>{
  assert.match(manifest,/android\.permission\.SYSTEM_ALERT_WINDOW/);
  assert.match(manifest,/android:name="\.NexaToolOverlayService"/);
  assert.match(manifest,/android:exported="false"/);
  assert.match(service,/WindowManager\.LayoutParams\.TYPE_APPLICATION_OVERLAY/);
  assert.match(service,/FLAG_NOT_FOCUSABLE/);
  assert.match(service,/FLAG_NOT_TOUCH_MODAL/);
  assert.match(service,/else dp\(52\)/);
  assert.match(service,/ACTION_MOVE/);
  for(const button of ['Chat','Voice','Tools','Stop live voice','Collapse','Hide Nexa'])
    assert.match(service,new RegExp(button));
  assert.match(service,/Settings\.canDrawOverlays\(this\)/);
  assert.match(module,/Settings\.ACTION_MANAGE_OVERLAY_PERMISSION/);
  assert.match(module,/context\.lifecycleState != com\.facebook\.react\.common\.LifecycleState\.RESUMED/);
});
test('overlay cannot execute arbitrary AI tool calls, capture screens, or secretly listen',()=>{
  for(const forbidden of [/AccessibilityService/,/MediaRecorder/,/dispatchGesture/,/ACTION_CLICK/,/ACTION_SET_TEXT/,/READ_SMS/,/onBoot/,/sendMessage\(/]) {
    assert.doesNotMatch(service,forbidden);
  }
  assert.match(service,/RealtimeVoiceService\.ACTION_STOP/);
  assert.doesNotMatch(service,/RealtimeVoiceService\.ACTION_START/);
  assert.ok(!service.includes('AudioRecord('));
  assert.match(module,/consumePendingTab/);
  assert.match(app,/consumeToolOverlayNavigation/);
});
test('overlay permission appears independently from default-assistant gesture',()=>{
  assert.match(setup,/Floating quick tools/);
  assert.match(setup,/Allow display over apps/);
  assert.match(setup,/Show floating bubble/);
  assert.match(setup,/Hide bubble/);
  assert.match(app,/requestToolOverlayPermission/);
  assert.match(app,/openNexaAssistantSettings/);
  assert.match(read('apps/mobile/src/native/nexaSetup.ts'),/overlayGranted/);
  assert.match(read(base+'java/dev/rilandev/personalaiassistant/NexaSetupModule.kt'),/Settings\.canDrawOverlays/);
});
test('custom wake-word is explicitly not claimed as enabled',()=>{
  assert.match(role,/putBoolean\("wakeWordReady", NexaWakeWordModule\.modelReady\(context\)\)/);
  // Overlay may check RECORD_AUDIO permission before an explicit owner tap,
  // but must not itself contain a microphone recorder or speech recognizer.
  assert.ok(service.includes('Manifest.permission.RECORD_AUDIO'));
  assert.doesNotMatch(service,/HotwordDetector|SpeechRecognizer|MediaRecorder/);
});
