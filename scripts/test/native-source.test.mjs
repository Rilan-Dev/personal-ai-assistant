import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
const read = path => readFileSync(new URL("../../" + path, import.meta.url), "utf8");
const build = read("apps/mobile/android/app/build.gradle");
const app = read("apps/mobile/app.json");
const manifest = read("apps/mobile/android/app/src/main/AndroidManifest.xml");
const module = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt");

test("Android package IDs align with Expo config", () => {
  assert.match(build, /applicationId "dev\.rilandev\.personalaiassistant"/);
  assert.equal(JSON.parse(app).expo.android.package, "dev.rilandev.personalaiassistant");
});
test("explicit Kotlin ReactPackage is registered", () => {
  assert.match(read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/MainApplication.kt"),
    /add\(DeviceControlPackage\(\)\)/);
});
test("AndroidManifest requests minimal package visibility, not dangerous permissions", () => {
  assert.match(manifest, /<package android:name="com\.whatsapp"\s*\/>/);
  assert.match(manifest, /<package android:name="com\.whatsapp\.w4b"\s*\/>/);
  assert.doesNotMatch(manifest, /READ_SMS|SEND_SMS|READ_CALL_LOG|QUERY_ALL_PACKAGES|SYSTEM_ALERT_WINDOW/);
});
test("native operations are restricted to camera and allowlisted apps", () => {
  assert.match(module, /"whatsapp" -> "com\.whatsapp"/);
  assert.match(module, /"whatsapp-business" -> "com\.whatsapp\.w4b"/);
  assert.match(module, /INTENT_ACTION_STILL_IMAGE_CAMERA/);
  assert.match(module, /UNSUPPORTED_APP/);
  assert.doesNotMatch(module, /sendMessage|ACTION_CALL|READ_CONTACTS|sendTextMessage/);
});
test("iOS bundle ID aligns with Expo config", () => {
  const ios = read("apps/mobile/ios/mobile.xcodeproj/project.pbxproj");
  assert.match(ios, /PRODUCT_BUNDLE_IDENTIFIER = dev\.rilandev\.personalaiassistant;/);
  assert.equal(JSON.parse(app).expo.ios.bundleIdentifier, "dev.rilandev.personalaiassistant");
});
test("Android secure journal uses Keystore AES-GCM and synchronous claims", () => {
  const journal = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt");
  const nativePackage = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlPackage.kt");
  assert.match(nativePackage,/AgentJournalModule\(reactContext\)/);
  assert.match(journal,/AndroidKeyStore/);
  assert.match(journal,/AES\/GCM\/NoPadding/);
  assert.match(journal,/setKeySize\(256\)/);
  assert.match(journal,/putString\(k, encrypt\(v\)\)\.commit\(\)/);
  assert.match(journal,/write\(k, record\("claimed", fp\)\); p\.resolve\(true\)/);
  assert.doesNotMatch(journal,/MODE_WORLD_READABLE|allowBackup="true"/);
});
test("Android UI cannot run live tools without the native secure journal", () => {
  const ui = read("apps/mobile/App.tsx");
  assert.match(ui,/nativeDeviceAvailable\(\) && androidJournalAvailable\(\)/);
  assert.match(ui,/journal: engine\.live \? androidActionJournal : undefined/);
  assert.match(ui,/resume: Boolean\(restore\)/);
});
test("Android push-to-talk is foreground-only, requires runtime microphone grant and on-device STT", () => {
  const manifest = read("apps/mobile/android/app/src/main/AndroidManifest.xml");
  const voice = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/VoiceControlModule.kt");
  const adapters = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlPackage.kt");
  assert.match(manifest, /android\.permission\.RECORD_AUDIO/);
  assert.match(manifest, /android\.speech\.RecognitionService/);
  assert.match(manifest, /android\.intent\.action\.TTS_SERVICE/);
  assert.doesNotMatch(manifest, /FOREGROUND_SERVICE_MICROPHONE|BIND_VOICE_INTERACTION|CAPTURE_AUDIO_OUTPUT/);
  assert.match(adapters, /VoiceControlModule\(reactContext\)/);
  assert.match(voice, /isOnDeviceRecognitionAvailable\(context\)/);
  assert.match(voice, /createOnDeviceSpeechRecognizer\(context\)/);
  assert.doesNotMatch(voice, /createSpeechRecognizer\(context\)/);
  assert.match(voice, /checkSelfPermission\(Manifest\.permission\.RECORD_AUDIO\)/);
  assert.match(voice, /currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(voice, /private fun releaseRecognizer\(\)/);
  assert.match(voice, /recognizer\?\.cancel\(\); recognizer\?\.destroy\(\)/);
  assert.match(voice, /onHostPause\(\)/);
  assert.match(voice, /onHostDestroy\(\)/);
});
test("TTS is explicitly offline and shutdown on lifecycle stop", () => {
  const voice = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/VoiceControlModule.kt");
  assert.match(voice, /!it\.isNetworkConnectionRequired/);
  assert.match(voice, /KEY_FEATURE_NOT_INSTALLED/);
  assert.match(voice, /OFFLINE_TTS_UNAVAILABLE/);
  assert.match(voice, /override fun onHostDestroy/);
  assert.match(voice, /tts\?\.shutdown\(\)/);
  assert.match(voice, /@ReactMethod fun stopSpeaking/);
});
test("speech controls never automatically send a transcribed command", () => {
  const ui = read("apps/mobile/App.tsx");
  const bridge = read("apps/mobile/src/native/androidVoice.ts");
  assert.match(ui, /const transcript = await transcribeOnDevice\(voiceLanguage\)/);
  assert.match(ui, /setInput\(transcript\)/);
  assert.doesNotMatch(ui, /send\(transcript/);
  assert.match(ui, /Review or edit it, then press Send/);
  assert.match(ui, /onPress=\{listening \? cancelListening : listenForCommand\}/);
  assert.match(ui, /onPress=\{speaking \? cancelSpeaking : readLastReply\}/);
  assert.match(bridge, /PermissionsAndroid\.request\(permission/);
  assert.match(bridge, /return answer === PermissionsAndroid\.RESULTS\.GRANTED/);
  assert.match(bridge, /Platform\.OS === "android"/);
});
test("Quick Settings tile can only open foreground chat after unlock", () => {
  const source = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AssistantTileService.kt");
  const manifest = read("apps/mobile/android/app/src/main/AndroidManifest.xml");
  const device = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt");
  const ui = read("apps/mobile/App.tsx");
  assert.match(manifest, /android:permission="android\.permission\.BIND_QUICK_SETTINGS_TILE"/);
  assert.match(manifest, /android\.service\.quicksettings\.action\.QS_TILE/);
  assert.match(manifest, /android:name="\.AssistantTileService"/);
  assert.match(source, /isLocked\) unlockAndRun\(openChat\)/);
  assert.match(source, /PendingIntent\.FLAG_IMMUTABLE/);
  assert.match(source, /Build\.VERSION_CODES\.UPSIDE_DOWN_CAKE/);
  assert.match(source, /MainActivity::class\.java/);
  assert.doesNotMatch(source, /SpeechRecognizer|listenOnce|RECORD_AUDIO|\.openSupportedApp\(/);
  assert.match(device, /requestAddTileService\(/);
  assert.match(ui, /requestAssistantQuickTile\(\)/);
  assert.match(ui, /never starts the microphone or performs actions automatically/);
});
test("Android journal synchronizes claims across bridge instances and atomically checkpoints", () => {
  const journal = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt");
  assert.match(journal, /private companion object \{\s*val lock = Any\(\)/);
  assert.match(journal, /val sessionKey = name\("session", s\)/);
  assert.match(journal, /val latestKey = name\("latest", "device"\)/);
  assert.match(journal, /\.putString\(sessionKey, encrypt\(data\)\)\s*\.putString\(latestKey, encrypt\(s\)\)\s*\.commit\(\)/);
  assert.match(journal, /synchronized\(lock\) \{/);
});
test("battery status reads from Android BatteryManager without new permissions", () => {
  const device=read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt");
  const manifest=read("apps/mobile/android/app/src/main/AndroidManifest.xml");
  const adapter=read("apps/mobile/src/native/androidTools.ts");
  assert.match(device,/getSystemService\(Context\.BATTERY_SERVICE\)/);
  assert.match(device,/BATTERY_PROPERTY_CAPACITY/);
  assert.match(device,/service\.isCharging/);
  assert.match(device,/percent !in 0\.\.100/);
  assert.match(adapter,/id: "device\.battery_status"/);
  assert.doesNotMatch(manifest,/BATTERY_STATS|DUMP|READ_PHONE_STATE/);
});
