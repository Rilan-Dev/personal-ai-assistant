import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read = file => readFileSync(file, 'utf8');
const path='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const overlay=read(path+'NexaToolOverlayService.kt');
const commands=read(path+'NexaOverlayCommands.kt');

test('the expanded overlay has its own Chat, Tools and Voice panels without automatic full app launch',()=>{
  assert.match(overlay,/private var section = "chat"/);
  assert.match(overlay,/button\("Chat"\) \{ showSection\("chat"\) \}/);
  assert.match(overlay,/button\("Tools"\) \{ showSection\("tools"\) \}/);
  assert.match(overlay,/button\("Voice"\) \{ showSection\("voice"\) \}/);
  assert.match(overlay,/EditText\(/);
  // Floating Chat now routes through the same React AI tool registry and
  // approval journal as the main Chat, rather than the legacy local grammar.
  assert.match(overlay,/sendSharedChat\(input\.text\.toString\(\)\)/);
  assert.match(overlay,/NexaOverlayChatRelay\.offer\(query\)/);
  assert.match(read(path+'NexaOverlayChatRelay.kt'),/NexaOverlayChatRequest/);
  assert.match(overlay,/button\("Open full Chat"\)/);
  assert.match(overlay,/button\("Full Tools"\)/);
  assert.match(overlay,/button\("Full Voice"\)/);
  assert.doesNotMatch(overlay,/button\("Chat"\) \{ openTab/);
  assert.doesNotMatch(overlay,/button\("Voice"\) \{ openTab/);
  assert.doesNotMatch(overlay,/button\("Tools"\) \{ openTab/);
});

test('local commands use a closed allowlist and do not grant the AI arbitrary device authority',()=>{
  assert.match(commands,/internal object NexaOverlayCommands/);
  assert.match(commands,/when \(text\)/);
  assert.match(commands,/PackageManager\.PERMISSION_GRANTED/);
  assert.match(commands,/NexaTaskCoordinator\.executeOwnerDeviceRead/);
  assert.match(commands,/NexaTaskCoordinator\.executeOwnerReadOnlyPlan/);
  assert.match(read(path+'NexaTaskNativeRead.kt'),/NexaPhoneVoiceTools\.deviceFacts/);
  assert.match(read(path+'NexaPhoneVoiceTools.kt'),/BatteryManager\.BATTERY_PROPERTY_CAPACITY/);
  assert.match(commands,/com\.github\.android/);
  assert.match(commands,/com\.whatsapp/);
  assert.match(commands,/LauncherNavigationState\.begin/);
  assert.match(commands,/Open full Nexa Tools/);
  assert.match(commands,/Not sent/);
  for (const forbidden of [/Runtime\.getRuntime/,/ProcessBuilder/,/exec\(/,
       /sendTextMessage/,/SmsManager/,/ClipboardManager/,/dispatchGesture\(/,
       /performGlobalAction\(/,/startRecording\(/,/MediaRecorder\(/]) {
    assert.doesNotMatch(commands,forbidden);
  }
  assert.doesNotMatch(overlay,/HttpURLConnection|WebSocket|OkHttpClient/);
});

test('overlay gives user controlled wake arm/stop and explicit Android background-mic failure feedback',()=>{
  assert.match(overlay,/NexaWakeWordModule\.modelReady\(this\)/);
  assert.match(overlay,/Manifest\.permission\.RECORD_AUDIO/);
  assert.match(overlay,/Manifest\.permission\.POST_NOTIFICATIONS/);
  assert.match(overlay,/NexaWakeWordService\.START/);
  assert.match(overlay,/NexaWakeWordService\.persistEnabled\(this, false\)/);
  assert.match(overlay,/stopService\(Intent\(this, NexaWakeWordService::class\.java\)\)/);
  assert.match(overlay,/RealtimeVoiceService\.ACTION_STOP/);
  assert.doesNotMatch(overlay,/RealtimeVoiceService\.ACTION_START/);
  assert.match(overlay,/Android blocked background microphone startup/);
  assert.match(overlay,/Stop live voice/);
});

test('overlay remains non-modal, draggable, collapsible, and bounded in size and history',()=>{
  assert.match(overlay,/FLAG_NOT_TOUCH_MODAL/);
  assert.match(overlay,/FLAG_NOT_FOCUSABLE/);
  assert.match(overlay,/TYPE_APPLICATION_OVERLAY/);
  assert.match(overlay,/ACTION_MOVE/);
  assert.match(overlay,/if \(expanded\) minOf\(dp\(360\)/);
  assert.match(overlay,/if \(expanded\) minOf\(dp\(540\)/);
  assert.match(overlay,/ScrollView\(this\)/);
  assert.match(overlay,/while \(history\.size > 10\) history\.removeFirst\(\)/);
  assert.match(overlay,/raw\.trim\(\)\.take\(150\)/);
  assert.match(overlay,/button\("Collapse"\)/);
  assert.match(overlay,/button\("Hide Nexa"\)/);
  assert.doesNotMatch(overlay,/AccessibilityService|READ_SMS|QUERY_ALL_PACKAGES|BOOT_COMPLETED/);
});

test('privacy and background settings are system-owned, not self-granted',()=>{
  assert.match(commands,/PowerManager/);
  assert.match(commands,/isIgnoringBatteryOptimizations/);
  assert.ok(commands.includes('Settings.ACTION_APPLICATION_DETAILS_SETTINGS'));
  assert.ok(commands.includes('Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS'));
  assert.match(commands,/Only you can change system permissions/);
  assert.ok(overlay.includes('button("App permissions")'));
  assert.ok(overlay.includes('button("Battery settings")'));
  assert.doesNotMatch(overlay,/REQUEST_IGNORE_BATTERY_OPTIMIZATIONS/);
});

test('owner tapped voice opens an existing system assistant session, not arbitrary speech capture',()=>{
  const assistant=read(path+'NexaAssistantService.kt');
  assert.ok(assistant.includes('fun showFromOwnerTap(): Boolean'));
  assert.ok(assistant.includes('requestSession("nexa_overlay_tap")'));
  assert.ok(assistant.includes('args?.getBoolean("nexa_overlay_tap") == true'));
  assert.ok(assistant.includes('NexaOverlayCommands.handle(app, command, "assistant-role")'));
  assert.ok(assistant.includes('listenOneDeviceCommand()'));
  assert.ok(overlay.includes('Talk to Nexa · one device command'));
  assert.ok(overlay.includes('NexaAssistantService.showFromOwnerTap()'));
  assert.ok(overlay.includes('NexaWakeWordService.pauseForAssistant()'));
  assert.ok(overlay.includes('NexaWakeWordService.listening'));
  assert.ok(overlay.includes('RealtimeVoiceService.status == "listening"'));
  assert.ok(overlay.includes('Manifest.permission.RECORD_AUDIO'));
  assert.ok(overlay.includes('Select Nexa in Android Default assistant settings'));
  assert.ok(!overlay.includes('AudioRecord('));
  assert.doesNotMatch(assistant,/sendTextMessage|SmsManager|Runtime\.getRuntime\(\)/);
});

test('polite Brave requests and compact Nexa icon remain restricted to explicit app launch',()=>{
  const manifest=read('apps/mobile/android/app/src/main/AndroidManifest.xml');
  assert.match(manifest,/com\.brave\.browser/);
  assert.match(commands,/can you please /);
  assert.match(commands,/open my brave app/);
  assert.match(commands,/com\.brave\.browser/);
  assert.match(overlay,/R\.drawable\.ic_nexa_assistant/);
  assert.match(overlay,/ImageView\(this\)/);
  assert.doesNotMatch(commands,/QUERY_ALL_PACKAGES|Runtime\.getRuntime/);
});
