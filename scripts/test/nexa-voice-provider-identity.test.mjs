import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const app=readFileSync('apps/mobile/App.tsx','utf8');
const bridge=readFileSync('apps/mobile/src/native/liveVoice.ts','utf8');
const journal=readFileSync(root+'AgentJournalModule.kt','utf8');
const service=readFileSync(root+'RealtimeVoiceService.kt','utf8');
const tools=readFileSync(root+'NexaSarvamReadTools.kt','utf8');

test('Voice page uses selected provider for explanatory copy, never old Samvaad credential flags',()=>{
  const voicePanel=app.slice(app.indexOf('<Text style={styles.cardHeading}>Live speech-to-speech</Text>'));
  const voiceOnly=voicePanel.slice(0,voicePanel.indexOf('<Text style={styles.cardHeading}>Live session limitations</Text>'));
  assert.match(voiceOnly,/activeProvider\?\.provider==='sarvam'/);
  assert.match(voiceOnly,/activeProvider\?\.provider==='openai'/);
  assert.match(voiceOnly,/voiceProviderDisplay/);
  assert.match(voiceOnly,/Sarvam Model API live voice/);
  assert.match(voiceOnly,/No live model selected/);
  assert.doesNotMatch(voiceOnly,/sarvamVoiceKeySaved/);
  assert.doesNotMatch(voiceOnly,/Sarvam Voice Agent · 16 kHz PCM/);
  assert.match(app,/voiceSessionRunning\s*\? \(runningVoiceProvider/);
  assert.match(app,/activeProvider\.provider==='sarvam'\?'sarvam':'openai'/);
});

test('a successful Sarvam preflight never selects or starts OpenAI',()=>{
  assert.match(app,/const result=await checkSarvamVoiceAuthorization\(\)/);
  assert.match(app,/setVoiceNotice\(result\.message\)/);
  const fn=app.slice(app.indexOf('const testSarvamVoice=async()=>{'),app.indexOf('const startLiveVoice=async()=>{'));
  assert.doesNotMatch(fn,/setActiveProvider|startOwnerLiveVoice|startVoiceSession|setProviderChoice/);
  assert.match(journal,/if\(provider!="sarvam" && provider!="openai"\) return@synchronized null/);
  assert.match(journal,/VoiceCredentials\("sarvam-api",key\)/);
  assert.match(journal,/VoiceCredentials\("openai",key\)/);
  assert.match(service,/selectedProvider=credentials\.provider/);
  assert.match(service,/if\(selectedProvider=="sarvam-api"\)/);
});

test('running service status tells the UI its real provider without returning any credentials',()=>{
  assert.match(service,/currentProviderId=selectedProvider/);
  assert.match(service,/data\.putString\("provider",RealtimeVoiceService\.currentProviderId/);
  assert.match(bridge,/provider\?:'sarvam-api'\|'openai'\|null/);
  assert.match(app,/liveVoiceStatus\.provider==='sarvam-api'/);
  assert.match(app,/liveVoiceStatus\.provider==='openai'/);
  assert.doesNotMatch(service,/data\.putString\("apiKey"/);
});

test('wake permission is local, whereas live microphone disclosure follows the owner-selected API',()=>{
  assert.match(app,/prepareOwnerLiveVoicePermissions\('device'\)/);
  assert.match(bridge,/provider:'sarvam'\|'openai'\|'device'='device'/);
  assert.match(bridge,/Sarvam Model API \(Saaras realtime recognition\)/);
  assert.match(bridge,/configured OpenAI realtime account/);
  assert.doesNotMatch(bridge,/your Sarvam managed Voice Agent/);
  assert.match(app,/Sarvam is selected for both chat and realtime voice/);
});

test('Android read-only voice tool regex has real word boundaries',()=>{
  assert.match(tools,/Regex\("""\\b\(battery\|charge\|charging\)\\b"""\)/);
  assert.match(tools,/Regex\("""\\b\(storage\|disk\|space\|memory\)\\b"""\)/);
  assert.doesNotMatch(tools,/Regex\("""\\\\b/);
});
