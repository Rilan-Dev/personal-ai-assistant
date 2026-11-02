import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validateProviderSettings} from '../../packages/agent-core/src/direct-provider.ts';
const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const vault=readFileSync(root+'AgentJournalModule.kt','utf8');
const runtime=readFileSync(root+'RealtimeVoiceService.kt','utf8');
const transport=readFileSync(root+'SarvamApiVoiceTransport.kt','utf8');
const ui=readFileSync('apps/mobile/App.tsx','utf8');

test('legacy Samvaad keys remain forbidden as Model API keys',()=>{
  const fake='sk_samvaad_abcdefghijklmnop';
  assert.throws(()=>validateProviderSettings({version:1,provider:'sarvam',model:'sarvam-105b',apiKey:fake}),/SAMVAAD_VOICE_KEY_ONLY/);
  assert.match(vault,/require\(!key\.startsWith\("sk_samvaad_"\)\)/);
  assert.match(vault,/VoiceCredentials\("sarvam-api",key\)/);
});
test('user-selected Sarvam Model API key stays Android Keystore encrypted and old agent IDs are not used',()=>{
  assert.match(vault,/readEncrypted\(ctx,"provider-config"\)/);
  assert.match(vault,/fun openSarvamApiKey\(ctx: Context\)/);
  assert.match(runtime,/SarvamApiVoiceTransport\(applicationContext,credentials\.apiKey/);
  assert.doesNotMatch(transport,/sarvam-voice|sarvam-voice-key|cfg\.orgId|cfg\.workspaceId|cfg\.appId/);
  assert.doesNotMatch(transport,/writeFile|FileOutputStream|Log\.|println\(/);
});
test('microphone streaming consent defaults off, changing API key revokes consent',()=>{
  assert.match(vault,/readEncrypted\(ctx,"sarvam-voice-streaming-consent"\)=="accepted-model-api-v1"/);
  assert.match(vault,/if\(!sarvamStreamingConsent\(ctx\)\) return@synchronized null/);
  assert.match(vault,/old != data/);
  assert.match(vault,/write\(name\("private","sarvam-voice-streaming-consent"\),"declined"\)/);
  assert.match(ui,/Enable realtime voice streaming/);
  assert.match(ui,/setSarvamStreamingConsent\(false\)/);
});
test('direct phone-to-model flow does not transmit contacts, files or NexaHub records',()=>{
  assert.doesNotMatch(transport,/NexaHub|READ_CONTACTS|getContacts|MediaProjection|uploadFile|device\.open_app/);
  assert.match(transport,/client|audio_input/);
  assert.match(transport,/api\.sarvam\.ai/);
  assert.match(ui,/NexaHub files, contacts, screen data and device inventory are not sent/);
});
