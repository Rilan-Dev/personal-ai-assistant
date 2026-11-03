import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const code=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt","utf8");
const actions=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt","utf8");
const method=code.slice(code.indexOf("    private fun confirmOwnerDeviceAction() {"),
  code.indexOf("    private fun sendLocal(raw:"));
const detach=code.slice(code.indexOf("    private fun detachOverlayForNotificationShade() {"),
  code.indexOf("    private fun confirmOwnerDeviceAction() {"));

test("notification shade fully detaches Nexa overlay before invoking Android",()=>{
  assert.match(method,/val isNotificationShade=action=="open_notification_shade"/);
  assert.match(method,/if\(isNotificationShade\) detachOverlayForNotificationShade\(\)/);
  assert.ok(method.indexOf("if(isNotificationShade) detachOverlayForNotificationShade()") <
    method.indexOf("NexaBrowserTaskActions.notificationsApproved(this)"));
  assert.match(detach,/wm\.removeViewImmediate\(existing\)/);
  assert.match(detach,/view=null\s*visible=false/);
  assert.match(detach,/hideSoftInputFromWindow/);
});
test("successful notification global action does not draw Nexa back over SystemUI",()=>{
  const result=method.slice(method.indexOf("if(isNotificationShade) {"));
  assert.match(result,/if\(outcome.startsWith\("Android accepted"\)\)/);
  assert.ok(result.indexOf("return@post") < result.indexOf("render()"));
});
test("refused global action restores error feedback rather than losing owner overlay",()=>{
  const result=method.slice(method.indexOf("if(isNotificationShade) {"));
  assert.match(result,/if\(!visible && view==null\) \{/);
  assert.match(result,/expanded=true\s*section="tools"\s*render\(\)/);
  assert.match(method,/note=outcome.take\(360\)/);
});
test("Back navigation keeps unobtrusive bubble and does not silently activate other tools",()=>{
  assert.match(method,/else if\(isSystemNavigation\) collapse\(\)/);
  assert.match(method,/postDelayed\(\{dispatch\(\)\},240L\)/);
  assert.match(actions,/GLOBAL_ACTION_BACK/);
});
test("notification action is denied when screen assist is disabled or device is locked",()=>{
  assert.match(actions,/ScreenAssistService.active/);
  assert.match(actions,/isKeyguardLocked/);
  assert.match(actions,/GLOBAL_ACTION_NOTIFICATIONS/);
  assert.match(actions,/No notifications were read/);
});
