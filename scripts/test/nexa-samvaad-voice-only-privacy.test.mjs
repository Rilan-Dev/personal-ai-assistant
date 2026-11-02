import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validateProviderSettings} from '../../packages/agent-core/src/direct-provider.ts';


const dir='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const vault=readFileSync(dir+'AgentJournalModule.kt','utf8');
const voice=readFileSync(dir+'RealtimeVoiceService.kt','utf8');
const preflight=readFileSync(dir+'SarvamVoiceReadiness.kt','utf8');
const ui=readFileSync('apps/mobile/App.tsx','utf8');
const keyBridge=readFileSync('apps/mobile/src/native/sarvamVoiceConfig.ts','utf8');

test('voice workspace credentials are not accepted as Chat Model API credentials',()=>{
  const fake='sk_samvaad_'+ 'abcXYZ1234567890';
  assert.match(keyBridge,/sk_samvaad_\[A-Za-z0-9_-\]\{16,500\}/);
  assert.match(keyBridge,/saveSamvaadWorkspaceKey/);
  assert.throws(()=>validateProviderSettings({
    version:1,provider:'sarvam',model:'sarvam-105b',apiKey:fake
  }),/SAMVAAD_VOICE_KEY_ONLY/);
  assert.match(vault,/require\(!key\.startsWith\("sk_samvaad_"\)\)/);
});

test('workspace key remains Android Keystore encrypted, never returned to RN or Chat',()=>{
  assert.match(vault,/fun saveSarvamVoiceWorkspaceKey\(key: String, p: Promise\)/);
  assert.match(vault,/write\(name\("private","sarvam-voice-key"\),key\)/);
  assert.match(vault,/fun sarvamVoiceWorkspaceKeyPresent\(p: Promise\)/);
  assert.match(vault,/fun clearSarvamVoiceWorkspaceKey\(p: Promise\)/);
  assert.match(vault,/fun sarvamStreamingConsent\(ctx: Context\)/);
  assert.match(vault,/if\(readEncrypted\(ctx,"sarvam-voice-key"\)!=null\)/);
  assert.match(vault,/if\(!sarvamStreamingConsent\(ctx\)\)return@synchronized null/);
  assert.match(vault,/if\(sarvam!=null && sarvamStreamingConsent\(ctx\)\)/);
  assert.match(vault,/fun openSarvamVoicePreflightCredentials/);
  assert.match(preflight,/openSarvamVoicePreflightCredentials\(context\)/);
  assert.doesNotMatch(keyBridge,/loadSarvamVoiceWorkspaceKey|readSarvamVoiceKey/);
  assert.doesNotMatch(vault,/fun loadSarvamVoiceWorkspaceKey\(/);
});

test('consent defaults off; saving a replacement key revokes consent and active calls',()=>{
  assert.match(vault,/readEncrypted\(ctx,"sarvam-voice-streaming-consent"\)=="accepted-v1"/);
  assert.match(vault,/write\(name\("private","sarvam-voice-streaming-consent"\),"declined"\)/);
  assert.match(vault,/setSarvamVoiceStreamingConsent\(accepted: Boolean, p: Promise\)/);
  assert.match(vault,/if\(!accepted\) reactApplicationContext\.stopService/);
  assert.match(ui,/setSarvamStreamingConsent\(false\)/);
  assert.match(ui,/Allow Sarvam voice streaming/);
  assert.match(ui,/Zero-day Voice Agents retention is not currently supported/);
  assert.match(ui,/Save voice workspace key/);
});

test('private voice-only transport receives mic audio but no phone data or transcript-triggered device tools',()=>{
  assert.match(voice,/!AgentJournalModule\.hasSarvamVoiceKey\(applicationContext\)/);
  assert.match(voice,/sarvamTransport\?\.sendAudio\(bytes\)/);
  assert.match(ui,/Nexa does not send files, contacts, screen content or NexaHub records/);
  const transport=readFileSync(dir+'SarvamAudioTransport.kt','utf8');
  assert.doesNotMatch(transport,/NexaHub|nexahub|READ_CONTACTS|\.getContacts|MediaProjection|uploadFile|device\.open_app/);
  assert.match(transport,/client\.media\.audio_chunk/);
  assert.match(transport,/user_identifier",UUID\.randomUUID\(\)\.toString\(\)/);
  assert.doesNotMatch(transport,/user_identifier",cfg\.userId/);
});
