import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const service=readFileSync(root+'RealtimeVoiceService.kt','utf8');
const spoken=readFileSync(root+'NexaPhoneVoiceTools.kt','utf8');
const overview=readFileSync(root+'NexaScreenVoiceOverview.kt','utf8');
const screen=readFileSync(root+'ScreenAssistService.kt','utf8');
const transport=readFileSync(root+'SarvamApiVoiceTransport.kt','utf8');
const scroll=readFileSync(root+'NexaScreenScroll.kt','utf8');
const ui=readFileSync(root+'NexaToolOverlayService.kt','utf8');

test('Sarvam voice routes explicit screen request to native visible single-use owner approval',()=>{
  assert.match(service,/NexaPhoneVoiceTools\.wantsVisibleText\(text\)/);
  assert.match(service,/NexaPhoneVoiceTools\.scrollDirection\(text\)/);
  assert.match(service,/NexaPhoneVoiceTools\.clickSafeIcon\(text\)/);
  assert.match(service,/read_visible_screen_text/);
  assert.match(service,/screenOperation && \(!ScreenAssistState\.connected \|\| ScreenAssistService\.active==null\)/);
  assert.match(service,/Screen Assist is disabled/);
  assert.match(service,/scroll_screen:/);
  assert.match(service,/tap_safe_icon:/);
  assert.match(service,/pendingApprovalSummary="Read visible app labels and share the result with your voice provider/);
  assert.match(service,/NexaScreenVoiceOverview\.readVisibleTextApproved\(\)/);
  assert.match(service,/NexaScreenVoiceOverview\.scrollApproved/);
  assert.match(service,/NexaScreenVoiceOverview\.tapSafeApproved/);
  assert.match(service,/ACTION_APPROVE_TOOL/);
  assert.match(service,/ACTION_DENY_TOOL/);
  assert.match(service,/age !in 0\.\.60_000L/);
});

test('screen reading is bounded and excludes password/input fields and app keyguard',()=>{
  assert.match(overview,/fun readVisibleTextApproved\(\)/);
  assert.match(overview,/node\.isPassword \|\| node\.isEditable/);
  assert.match(overview,/inspected<130/);
  assert.match(overview,/labels\.size<16/);
  assert.match(overview,/take\(800\)/);
  assert.match(screen,/isKeyguardLocked/);
  assert.match(screen,/externalWindowRoot\(\)/);
  assert.doesNotMatch(overview,/dispatchGesture|MediaProjection|ACTION_SET_TEXT/);
});

test('click and scroll require a current external Accessibility window and one approved action',()=>{
  assert.match(overview,/NexaScreenActions\.inspect\(service\)/);
  assert.match(overview,/NexaScreenActions\.select\(snap\.token,matches\.single\(\)\.id\)/);
  assert.match(overview,/NexaScreenActions\.confirm\(service,snap\.token\)/);
  assert.match(overview,/NexaScreenScroll\.prepare\(service,direction\)/);
  assert.match(overview,/NexaScreenScroll\.confirm\(service\)/);
  assert.match(scroll,/if\(direction !in setOf\("up","down","left","right"\)\)/);
  assert.match(scroll,/ACTION_SCROLL_LEFT/);
  assert.match(scroll,/ACTION_SCROLL_RIGHT/);
  assert.match(ui,/button\("Scroll left"\)/);
  assert.match(ui,/button\("Scroll right"\)/);
  assert.doesNotMatch(overview,/ACTION_CALL|ACTION_SET_TEXT/);
});

test('device-fact and tool-callback crashes are trapped before escaping provider network thread',()=>{
  assert.match(spoken,/return try \{ deviceFactsSafe\(context,utterance\) \}/);
  assert.match(spoken,/catch \(_: Exception\) \{ "Android device information is temporarily unavailable\." \}/);
  assert.match(transport,/val locallyHandled=try \{ callbacks\.ownerRequest\(text\) \}/);
  assert.match(transport,/catch \(_:Exception\)/);
});

test('only bounded screen navigation intent grammar may generate a local request',()=>{
  assert.match(spoken,/fun wantsVisibleText\(utterance:String\)/);
  assert.match(spoken,/fun scrollDirection\(utterance:String\)/);
  assert.match(spoken,/fun clickSafeIcon\(utterance:String\)/);
  assert.match(spoken,/\(search\|menu\|settings\|back\|close\)/);
  assert.doesNotMatch(spoken,/fun dispatchArbitraryGesture|performGlobalTap|executeAnyAction/);
});
