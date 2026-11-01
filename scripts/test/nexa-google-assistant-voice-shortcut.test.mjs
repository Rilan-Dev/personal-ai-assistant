import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read=path=>readFileSync(path,'utf8');
const manifest=read('apps/mobile/android/app/src/main/AndroidManifest.xml');
const shortcut=read('apps/mobile/android/app/src/main/res/xml/nexa_shortcuts.xml');
const strings=read('apps/mobile/android/app/src/main/res/values/strings.xml');
const app=read('apps/mobile/App.tsx');
const setup=read('apps/mobile/src/components/NexaSetupScreen.tsx');
const native=read('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaWakeWordModule.kt');

test('Assistant Voice shortcut uses a real standard Android App Action and an explicit Voice deep link',()=>{
  assert.match(manifest,/android\.app\.shortcuts/);
  assert.match(manifest,/@xml\/nexa_shortcuts/);
  assert.match(manifest,/android:scheme="nexa" android:host="voice"/);
  assert.match(shortcut,/actions\.intent\.OPEN_APP_FEATURE/);
  assert.match(shortcut,/android:shortcutId="nexa_voice"/);
  assert.match(shortcut,/android:data="nexa:\/\/voice"/);
  assert.match(shortcut,/android:targetClass="dev\.rilandev\.personalaiassistant\.MainActivity"/);
  assert.match(strings,/name="nexa_voice_shortcut_short"/);
});

test('Shortcut only navigates to Voice screen; incoming links cannot start idle recording',()=>{
  assert.match(app,/Linking\.getInitialURL\(\)/);
  assert.match(app,/Linking\.addEventListener\('url'/);
  assert.match(app,/url==='nexa:\/\/voice'/);
  assert.match(app,/voiceShortcutRequested\.current=true/);
  assert.match(app,/navigate\('voice'\)/);
  assert.match(app,/!voiceShortcutRequested\.current/);
  assert.doesNotMatch(shortcut,/RECORD_AUDIO|VOICE_START|WakeWordService|android:foregroundServiceType/);
  assert.match(setup,/This is NOT an always-on "Hey Nexa" replacement/);
});

test('An ordinary downloaded APK never claims protected hotword or hides mic indicator',()=>{
  assert.match(native,/protectedHotwordAvailable", false/);
  assert.match(setup,/Starting microphone capture remains a separate, visible action/);
  assert.match(setup,/green microphone dot/);
  assert.doesNotMatch(manifest,/MANAGE_HOTWORD_DETECTION|CAPTURE_AUDIO_HOTWORD/);
  assert.doesNotMatch(shortcut,/HotwordDetectionService|SoundTrigger/);
});
