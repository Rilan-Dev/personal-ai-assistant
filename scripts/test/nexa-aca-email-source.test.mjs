import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../../apps/mobile/App.tsx', import.meta.url), 'utf8');
const bridge = readFileSync(new URL('../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt', import.meta.url), 'utf8');
const gradle = readFileSync(new URL('../../apps/mobile/android/app/build.gradle', import.meta.url), 'utf8');
const api = readFileSync(new URL('../../apps/mobile/src/native/acaEmail.ts', import.meta.url), 'utf8');
const apiConfig = readFileSync(new URL('../../apps/mobile/src/native/acaConfig.ts', import.meta.url), 'utf8');

test('ACA business Chat shares ACA orchestration and stays outside Nexa device harness', () => {
  assert.match(app, /classifyAcaBusinessIntent\(request\)/);
  assert.match(app, /streamAcaChatTurn\(/);
  assert.match(app, /createScreenAwarePlanner\(planner,screenGrant,resolveScreenQuestion\)/);
  assert.match(app, /new AgentHarness\(engine\.registry/);
  assert.match(app, /sharedOverlaySendRef\.current\(request,undefined,id\)/);
  assert.doesNotMatch(app, /acaMailSummary\(|summarizeAcaMailWithProvider|formatAcaMailAiReply/);
  const route = app.indexOf('const acaBusinessIntent=!restore?classifyAcaBusinessIntent(request):null;');
  const planner = app.indexOf('const planner=activeProvider && engine.live', route);
  assert.ok(route >= 0 && route < planner, 'business requests must stop or use ACA before the local planner');
  assert.doesNotMatch(app.slice(route, planner), /appendConversation\(/,
    'business transcripts stay in ACA canonical history instead of a second Nexa history');
  assert.ok(app.includes('Nexa-local model was used'));
});

test('ACA login has no hardcoded credentials and uses Android Keystore storage', () => {
  assert.match(bridge, /saveAcaSession/);
  assert.match(bridge, /aca-device-session/);
  assert.match(bridge, /write\(name\("private", "aca-device-session"\), data\)/);
  assert.match(api, /getAcaApiOrigin\(\)/);
  assert.doesNotMatch(api, /aca\.starcitytechnical\.com/);
  assert.match(apiConfig, /aca-unconfigured\.invalid/);
  assert.match(gradle, /NEXA_ACA_API_ORIGIN/);
  assert.match(gradle, /validateAcaReleaseApiOrigin/);
  assert.match(bridge, /BuildConfig\.NEXA_ACA_API_ORIGIN/);
  assert.doesNotMatch(bridge, /aca\.starcitytechnical\.com/);
  assert.match(api, /method:"DELETE"/);
  assert.doesNotMatch(api, /AsyncStorage|hardcoded_password|demo_password/);
});

test('Android Keystore persists only a same-scope ACA chat conversation checkpoint', () => {
  assert.match(bridge, /validateAcaDeviceSessionPayload/);
  assert.match(bridge, /require\(validateAcaDeviceSessionPayload\(\s*data, BuildConfig\.NEXA_ACA_API_ORIGIN, System\.currentTimeMillis\(\)\)\)/);
});

test('ACA settings do not promise email excerpts will be sent to Nexa BYOK', () => {
  assert.doesNotMatch(app, /Connect an AI provider for email-content summaries/);
  assert.doesNotMatch(app, /selected saved provider.*email/i);
  assert.match(app, /ACA business source selected/);
});

test('ACA sign-in reverses its provisional device session when agent discovery fails', () => {
  assert.match(app, /let provisional:AcaSession\|null=null/);
  assert.match(app, /provisional=await connectAca\(acaEmailInput,acaPasswordInput\)/);
  assert.match(app, /await acaTargets\(provisional\)/);
  assert.match(app, /await disconnectAca\(provisional\)/);
  assert.match(app, /secure cleanup could not finish/);
});
