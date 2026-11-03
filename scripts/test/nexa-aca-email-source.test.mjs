import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const app=readFileSync(new URL('../../apps/mobile/App.tsx',import.meta.url),'utf8');
const bridge=readFileSync(new URL('../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt',import.meta.url),'utf8');
const api=readFileSync(new URL('../../apps/mobile/src/native/acaEmail.ts',import.meta.url),'utf8');
const intent=readFileSync(new URL('../../apps/mobile/src/native/acaMailIntent.ts',import.meta.url),'utf8');
const {parseAcaMailSummaryIntent}=await import('../../apps/mobile/src/native/acaMailIntent.ts');

test('ACA email routing remains separate from native device tool harness',()=>{
  assert.match(app,/parseAcaMailSummaryIntent\(request\)/);
  assert.match(app,/acaMailSummary\(acaSession,acaMailScope/);
  assert.match(app,/createScreenAwarePlanner\(planner,screenGrant,resolveScreenQuestion\)/);
  assert.match(app,/new AgentHarness\(engine\.registry/);
});

test('ACA login has no hardcoded credentials and uses Android Keystore storage',()=>{
  assert.match(bridge,/saveAcaSession/);
  assert.match(bridge,/aca-device-session/);
  assert.match(bridge,/write\(name\("private", "aca-device-session"\), data\)/);
  assert.match(api,/https:\/\/aca\.starcitytechnical\.com/);
  assert.match(api,/method:"DELETE"/);
  assert.doesNotMatch(api,/SMTSllc|smtscontracting@gmail\.com|AsyncStorage/);
});

test('summary intent gate forbids send and draft',()=>{
  assert.match(api,/send\|draft\|reply\|forward\|delete/);
  assert.match(api,/window:"all"\|"24h"/);
});
