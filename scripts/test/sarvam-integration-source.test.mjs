import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=(path)=>readFileSync(path,"utf8");
const native="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const chat=read("packages/agent-core/src/direct-provider.ts");
const agent=read(native+"AgentJournalModule.kt");
const audio=read(native+"SarvamAudioTransport.kt");
const speech=read(native+"SarvamSpeechModule.kt");
const voice=read(native+"RealtimeVoiceService.kt");
const ui=read("apps/mobile/App.tsx");

test("Sarvam chat uses supported sarvam-105b with subscription-key authentication",()=>{
  assert.match(chat,/sarvam: \{ label: "Sarvam AI"/);
  assert.match(chat,/https:\/\/api\.sarvam\.ai\/v1\/chat\/completions/);
  assert.match(chat,/defaultModel: "sarvam-105b"/);
  assert.match(chat,/"api-subscription-key":cfg\.apiKey/);
});

test("Sarvam voice agent credentials and stable pseudonymous user ID are encrypted",()=>{
  assert.match(agent,/saveSarvamVoiceAgent\(/);
  assert.match(agent,/loadSarvamVoiceAgent\(/);
  assert.match(agent,/clearSarvamVoiceAgent\(/);
  assert.match(agent,/write\(name\("private","sarvam-voice"\),data\)/);
  assert.match(agent,/UUID\.randomUUID\(\)\.toString\(\)/);
  assert.match(agent,/fun openVoiceCredentials/);
  assert.match(agent,/provider != "openai" && provider != "sarvam"/);
  assert.match(agent,/if \(provider=="openai"\)/);
  assert.match(agent,/VoiceCredentials\(provider,key,org,work,app,user\)/);
  assert.match(agent,/openSarvamApiKey/);
  assert.doesNotMatch(agent,/Log\.d\(|Log\.i\(/);
});

test("Sarvam STS uses exact managed Voice Agent audio WebSocket protocol",()=>{
  assert.match(audio,/https:\/\/apps\.sarvam\.ai\/api\/app-runtime\//);
  assert.match(audio,/header\("X-API-Key",cfg\.apiKey\)/);
  assert.match(audio,/followRedirects\(false\)/);
  assert.match(audio,/followSslRedirects\(false\)/);
  assert.match(audio,/reference_id/);
  assert.match(audio,/interaction_type","call"/);
  assert.match(audio,/endpoint\.scheme!="wss"/);
  assert.match(audio,/host\.endsWith\("\.sarvam\.ai"\)/);
  assert.match(audio,/appendQueryParameter\("user_identifier_type","custom"\)/);
  assert.match(audio,/input_sample_rate","16000"/);
  assert.match(audio,/output_sample_rate","16000"/);
  assert.match(audio,/client\.action\.interaction_start/);
  assert.match(audio,/client\.media\.audio_chunk/);
  assert.match(audio,/server\.media\.audio_chunk/);
  assert.match(audio,/server\.event\.user_interrupt/);
  assert.match(audio,/client\.system\.pong/);
  assert.match(audio,/server\.action\.interaction_end/);
  assert.match(audio,/audio_base64/);
  assert.match(audio,/sample_rate",16000/);
});

test("Live Sarvam route is native FGS audio-to-audio with NO transcript handlers",()=>{
  assert.match(voice,/selectedProvider=="sarvam"/);
  assert.match(voice,/SarvamAudioTransport\(/);
  assert.match(voice,/activeRate=if\(selectedProvider=="sarvam"\)16000 else RATE/);
  assert.match(voice,/sarvamTransport\?\.sendAudio\(bytes\)/);
  assert.match(voice,/sarvamTransport\?\.close\(\)/);
  assert.match(voice,/startForeground\(NOTIFICATION_ID/);
  assert.match(voice,/15\*60\*1000L/);
  assert.doesNotMatch(audio,/transcriptCallback|response\.transcript|append\(.*transcript|textCallback|messages\.add/);
  assert.doesNotMatch(audio,/device\.set_flashlight|device\.open_app|dispatchGesture|performGlobalAction/);
  assert.doesNotMatch(audio,/Log\.|println\(|SharedPreferences|writeFile|FileOutputStream/);
  assert.doesNotMatch(voice,/transcriptCallback|writeFile|openFileOutput/);
});

test("One-turn STT uses memory-only 16k WAV and explicitly recognizes text for review",()=>{
  assert.match(speech,/saaras:v4/);
  assert.match(speech,/https:\/\/api\.sarvam\.ai\/speech-to-text/);
  assert.match(speech,/MultipartBody\.Builder\(\)/);
  assert.match(speech,/audio\/wav/);
  assert.match(speech,/api-subscription-key/);
  assert.match(speech,/checkSelfPermission\(Manifest\.permission\.RECORD_AUDIO\)/);
  assert.match(speech,/usableForeground\(\)/);
  assert.match(speech,/9500/);
  assert.match(speech,/transcript/);
  assert.doesNotMatch(speech,/FileOutputStream|openFileOutput|MediaRecorder\.setOutputFile/);
});

test("One-turn TTS uses Bulbul v4 WAV and Android AudioTrack, never a transcript for live mode",()=>{
  assert.match(speech,/bulbul:v4-flash/);
  assert.match(speech,/https:\/\/api\.sarvam\.ai\/text-to-speech/);
  assert.match(speech,/speech_sample_rate",16000/);
  assert.match(speech,/output_audio_codec","wav"/);
  assert.match(speech,/AudioTrack\(/);
  assert.match(speech,/fun stopSpeaking/);
  assert.match(speech,/RealtimeVoiceService\.status=="listening"/);
});

test("Chat/Voice/Settings support Sarvam user configuration without automatic sending",()=>{
  assert.match(ui,/sarvamVoiceConfigAvailable/);
  assert.match(ui,/saveSarvamVoiceConfig/);
  assert.match(ui,/deleteSarvamVoiceConfig/);
  assert.match(ui,/Sarvam Voice Agent \(live STS\)/);
  assert.match(ui,/dictateSarvamOnce\(\)/);
  assert.match(ui,/speakSarvamOnce\(/);
  assert.match(ui,/startOwnerLiveVoice\(/);
  assert.match(ui,/No transcript is displayed, sent to Chat, or saved locally/);
  assert.match(ui,/server-side Sarvam may retain call records|Sarvam performs recognition and reasoning on its servers/i);
  assert.doesNotMatch(ui,/send\(dictated\)/);
});

test("Native Sarvam service declares no new background location/contacts or hotword rights",()=>{
  const manifest=read("apps/mobile/android/app/src/main/AndroidManifest.xml");
  assert.match(manifest,/FOREGROUND_SERVICE_MICROPHONE/);
  assert.match(manifest,/\.RealtimeVoiceService/);
  assert.match(manifest,/android:exported="false"/);
  assert.doesNotMatch(manifest,/READ_CONTACTS|ACCESS_BACKGROUND_LOCATION|RECEIVE_BOOT_COMPLETED/);
  assert.match(read(native+"DeviceControlPackage.kt"),/SarvamSpeechModule\(reactContext\)/);
});


test("Android microphone consent identifies the actual selected voice provider",()=>{
  const controls=read("apps/mobile/src/native/liveVoice.ts");
  assert.match(controls,/startOwnerLiveVoice\(provider:'sarvam'\|'openai'/);
  assert.match(controls,/provider==='sarvam'/);
  assert.match(controls,/your Sarvam managed Voice Agent/);
  assert.match(controls,/your OpenAI realtime provider/);
  assert.match(ui,/startOwnerLiveVoice\(activeProvider\.provider==='sarvam'\?'sarvam':'openai'\)/);
});


test("Live streaming and one-turn dictation/playback are mutually exclusive",()=>{
  assert.match(ui,/liveVoiceStatus\.state==='connecting' \|\| liveVoiceStatus\.state==='listening'/);
  assert.match(ui,/liveVoiceBusy\|\|running\|\|listening\|\|speaking/);
  assert.match(ui,/disabled=\{running\|\|liveVoiceBusy\|\|!engine\.live\|\|listening\|\|speaking\}/);
});
