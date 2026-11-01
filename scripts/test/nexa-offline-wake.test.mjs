import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(p,'utf8');
const dir='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const service=read(dir+'NexaWakeWordService.kt');
const module=read(dir+'NexaWakeWordModule.kt');
const matcher=read(dir+'NexaWakePhrase.kt');
const manifest=read('apps/mobile/android/app/src/main/AndroidManifest.xml');
const setup=read('apps/mobile/src/components/NexaSetupScreen.tsx');
const app=read('apps/mobile/App.tsx');
test('offline detector uses pinned Apache-licensed model and explicit user download',()=>{
  assert.match(module,/vosk-model-small-en-us-0\.15\.zip/);
  assert.match(module,/30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498/);
  assert.match(module,/MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(module,/instanceFollowRedirects = false/);
  assert.match(module,/MAX|50L \* 1024 \* 1024/);
  assert.match(module,/Archive limit exceeded/);
  assert.match(module,/ZIP path traversal refused/);
  assert.match(module,/180L \* 1024 \* 1024/);
  assert.match(module,/MODEL_HASH/);
  assert.match(module,/installModel\(p: Promise\)/);
  assert.match(module,/NOT_FOREGROUND/);
  assert.match(read('apps/mobile/android/app/build.gradle'),/com\.alphacephei:vosk-android:0\.3\.47/);
});
test('microphone remains user-armed, disclosed, and pauses for assistant handoff',()=>{
  assert.match(manifest,/android:name="\.NexaWakeWordService"/);
  assert.match(manifest,/foregroundServiceType="microphone"/);
  assert.match(manifest,/android:exported="false"/);
  assert.match(manifest,/RECEIVE_BOOT_COMPLETED/);
  assert.match(read(dir+'NexaWakeBootReceiver.kt'),/Tap to resume Hey Nexa safely after restart/);
  assert.doesNotMatch(read(dir+'NexaWakeBootReceiver.kt'),/startForegroundService\(/);
  assert.match(service,/START_NOT_STICKY/);
  assert.match(service,/startForeground\(ID/);
  assert.match(service,/Turn off wake listening/);
  assert.match(service,/Nexa wake phrase recognized/);
  assert.match(service,/NotificationManager\.IMPORTANCE_HIGH/);
  assert.match(service,/Model\(NexaWakeWordModule\.modelDir\(this\)\.absolutePath\)/);
  assert.match(service,/Recognizer\(model, 16000\.0f, grammar\)/);
  assert.match(service,/AudioRecord\(/);
  assert.match(service,/releaseAudio\(\)/);
  assert.match(service,/NexaAssistantService\.showFromWakeWord\(\)/);
  assert.doesNotMatch(service,/WebSocket|Retrofit|sendMessage|dispatchGesture|startActivity\(Intent\(Intent\.ACTION_CALL/);
});
test('wake phrase gate requires stable, nontrivial phrase and does not auto-send',()=>{
  assert.match(matcher,/complete && matches >= 2/);
  assert.match(service,/gate\.observe\(phrase, SystemClock\.elapsedRealtime\(\), utteranceFinished\)/);
  assert.match(matcher,/hey nexa/);
  assert.match(matcher,/2000/);
  assert.match(service,/manager\.notify\(ID \+ 1/);
  assert.doesNotMatch(service,/sendTextMessage|WhatsApp|MessageSend/);
});
test('setup gives explicit download, start, stop and live status controls',()=>{
  assert.match(setup,/Download offline model/);
  assert.match(setup,/Enable Hey Nexa \(saved setting\)/);
  assert.match(setup,/Disable Hey Nexa/);
  assert.match(setup,/40 MB model/);
  assert.match(app,/prepareWakeListening/);
  assert.match(app,/prepareOwnerLiveVoicePermissions/);
  assert.match(app,/stopWakeListening/);
  assert.match(app,/setInterval/);
  assert.match(read('apps/mobile/src/native/androidWakeWord.ts'),/PAANexaWakeWord/);
});
test('system assistant hotword readiness depends on verified model',()=>{
  assert.match(read(dir+'NexaAssistantService.kt'),/putBoolean\("wakeWordReady", NexaWakeWordModule\.modelReady\(context\)\)/);
  assert.match(read(dir+'NexaAssistantService.kt'),/nexa_hotword/);
  assert.match(read(dir+'NexaAssistantService.kt'),/listenOneDeviceCommand\(\)/);
  assert.match(read(dir+'VoiceControlModule.kt'),/NexaWakeWordModule\.modelReady\(context\)/);
});

test('R8 retains Vosk/JNA reflective symbols and only suppresses desktop AWT namespace',()=>{
  const rules=read('apps/mobile/android/app/proguard-rules.pro');
  assert.match(rules,/-keep class org\.vosk\.\*\* \{ \*; \}/);
  assert.match(rules,/-keep class com\.sun\.jna\.\*\* \{ \*; \}/);
  assert.match(rules,/-dontwarn java\.awt\.\*\*/);
  assert.doesNotMatch(rules,/-dontwarn \*\*|-ignorewarnings/);
});

test('one owner opt-in survives updates, rearms after system assistant and never hides mic indicator',()=>{
  const assistant=read(dir+'NexaAssistantService.kt');
  assert.match(service,/START_STICKY/);
  assert.match(service,/NexaWakeWordService/);
  assert.match(service,/while \(running\.get\(\)\)/);
  assert.match(service,/fun assistantClosed\(\)/);
  assert.match(service,/fun persistEnabled/);
  assert.match(service,/handoffPending = true/);
  assert.match(assistant,/NexaWakeWordService\.assistantClosed\(\)/);
  assert.match(module,/resumeIfEnabled\(p: Promise\)/);
  assert.match(module,/persistEnabled\(context, true\)/);
  assert.match(module,/persistEnabled\(context, false\)/);
  assert.match(service,/microphone indicator is expected/);
  assert.match(setup,/The Android green dot indicates real microphone activity/);
  assert.match(app,/resumeWakeIfEnabled/);
});
