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
  assert.doesNotMatch(api,/AsyncStorage|hardcoded_password|demo_password/);
});

test('read-only mail request parsing preserves user scope and blocks writes',()=>{
  assert.match(intent,/send\\|draft\\|reply\\|forward\\|delete/);
  assert.deepEqual(parseAcaMailSummaryIntent('Summarize unread emails from the last 24 hours'),{unreadOnly:true,inboxOnly:false,window:'24h'});
  assert.deepEqual(parseAcaMailSummaryIntent('How many unread inbox emails do I have?'),{unreadOnly:true,inboxOnly:true,window:'all'});
  assert.deepEqual(parseAcaMailSummaryIntent('Summarize emails in the last 24 hours'),{unreadOnly:false,inboxOnly:false,window:'24h'});
  assert.equal(parseAcaMailSummaryIntent('Send an email to Mom'),null);
  assert.equal(parseAcaMailSummaryIntent('Summarize emails from John in the last 24 hours'),null);
  assert.equal(parseAcaMailSummaryIntent('Summarize emails today'),null);
  assert.equal(parseAcaMailSummaryIntent('Open Chrome and search what is AI'),null);
});
