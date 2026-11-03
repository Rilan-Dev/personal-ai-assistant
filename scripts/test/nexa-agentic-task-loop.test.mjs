import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const n='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=(file)=>readFileSync(n+file,'utf8');

test('native compound tasks are recognized before model fallback and require verification',()=>{
  const voice=read('RealtimeVoiceService.kt');
  const planner=read('NexaVoiceTaskPlan.kt');
  assert.match(voice,/NexaVoiceTaskPlan\.parse\(text\)!=null/);
  assert.match(voice,/val plan=if\(fromPlan\)null else NexaVoiceTaskPlan\.parse/);
  assert.match(voice,/advanceCompoundAfterVerifiedAction/);
  assert.match(voice,/operationSession != voiceSessionId/);
  assert.match(planner,/fun advance\(verified:Boolean\)/);
  assert.match(planner,/if\(!verified\)return null/);
  assert.match(planner,/steps\.size !in 2\.\.4/);
});
test('stt does not submit incomplete app names or split two-step commands',()=>{
  const sarvam=read('SarvamApiVoiceTransport.kt');
  const fragment=read('NexaVoiceTaskPlan.kt');
  assert.match(fragment,/hasPendingCompound/);
  assert.match(sarvam,/NexaVoiceFragmentGate\.isIncomplete/);
  assert.match(sarvam,/pendingOpenFragmentAt/);
  assert.match(sarvam,/pendingCompoundFragment/);
  assert.match(sarvam,/val restored=when/);
  assert.match(sarvam,/callbacks\.ownerRequest\(restored\)/);
  assert.match(read('NexaPhoneVoiceTools.kt'),/editDistanceAtMost/);
});
test('approved Android actions leave the voice main looper and are session guarded',()=>{
  const voice=read('RealtimeVoiceService.kt');
  assert.match(voice,/Thread\(\{/);
  assert.match(voice,/nexa-reviewed-action/);
  assert.match(voice,/main\.post \{/);
  assert.match(voice,/activeToolOperation/);
  assert.match(voice,/Voice session \$state:/);
});
test('permissions are persistent, risk-scoped and revocable in the voice UI',()=>{
  const native=read('NexaVoicePermissionPolicy.kt');
  const app=readFileSync('apps/mobile/App.tsx','utf8');
  assert.match(native,/ASK_EVERY_TIME="ask_every_time"/);
  assert.match(native,/TRUST_SAFE="trusted_safe"/);
  assert.match(native,/TRUST_NAVIGATION="trusted_navigation"/);
  assert.match(native,/fun alwaysAllow/);
  assert.match(native,/fun denyCategory/);
  assert.match(native,/if\(!isSafeCategory\(key\)\)return false/);
  assert.match(app,/Allow once/);
  assert.match(app,/Always allow safe/);
  assert.match(app,/Revoke all saved voice approvals/);
});
test('horizontal launcher swipe is bounded and owner-gated',()=>{
  const swipe=read('NexaLauncherSwipe.kt');
  const scroll=read('NexaScreenScroll.kt');
  const xml=readFileSync('apps/mobile/android/app/src/main/res/xml/screen_assist_config.xml','utf8');
  assert.match(xml,/canPerformGestures="true"/);
  assert.match(swipe,/fun isHome/);
  assert.match(swipe,/CATEGORY_HOME/);
  assert.match(swipe,/direction !in setOf\("left","right"\)/);
  assert.match(swipe,/NexaUiActionJournal\.claim/);
  assert.match(swipe,/dispatchGesture/);
  assert.match(scroll,/__home_launcher_swipe__/);
  assert.doesNotMatch(swipe,/request_open_app|send_message|placeCall/);
});
