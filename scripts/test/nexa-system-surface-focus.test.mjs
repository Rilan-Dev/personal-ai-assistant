import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const code=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt","utf8");
const actions=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt","utf8");
const method=code.slice(code.indexOf("    private fun confirmOwnerDeviceAction() {"),
  code.indexOf("    private fun sendLocal(raw:"));
const detach=code.slice(code.indexOf("    private fun detachOverlayForNotificationShade():Boolean {"),
  code.indexOf("    private fun confirmOwnerDeviceAction() {"));
const proposal=code.slice(code.indexOf("    private fun proposeOwnerDeviceAction("),
  code.indexOf("    private fun cancelOwnerDeviceAction() {"));
const render=code.slice(code.indexOf("    private fun render() {"),
  code.indexOf("        val layout: View = if (!expanded)"));

test("system shade detaches all overlay windows before dispatching Android action",()=>{
  assert.match(method,/val isNotificationShade=action=="open_notification_shade"/);
  assert.match(method,/if\(!detachOverlayForNotificationShade\(\)\)/);
  assert.ok(method.indexOf("detachOverlayForNotificationShade()") <
    method.indexOf("NexaBrowserTaskActions.notificationsApproved(this,origin=\"overlay\")"));
  assert.match(detach,/wm\.removeViewImmediate\(existing\)/);
  assert.match(detach,/view=null\s*visible=false/);
  assert.match(detach,/hideSoftInputFromWindow/);
});
test("failed overlay detach stops system request and exposes an actionable error",()=>{
  assert.match(detach,/if\(!detached\)return false/);
  assert.match(method,/notification shade was not requested/);
  assert.match(method,/render\(\)\s*return/);
});
test("accepted global action never reconstructs the expanded overlay over SystemUI",()=>{
  const result=method.slice(method.indexOf("note=outcome.take(360)"));
  assert.match(result,/if\(isNotificationShade\) \{/);
  assert.match(result,/if\(outcome.startsWith\("Android accepted"\)\)/);
  assert.ok(result.indexOf("return@post")<result.indexOf("render()"));
});
test("rejected global actions restore visible error feedback",()=>{
  const result=method.slice(method.indexOf("note=outcome.take(360)"));
  assert.match(result,/if\(!visible && view==null\) \{/);
  assert.match(result,/expanded=true\s*section="tools"\s*render\(\)/);
  assert.match(method,/note=outcome.take\(360\)/);
});
test("Back navigation keeps compact bubble and still allows refusal feedback",()=>{
  assert.match(method,/else if\(isSystemNavigation\) collapse\(\)/);
  assert.match(method,/postDelayed\(\{dispatch\(\)\},240L\)/);
  assert.match(actions,/GLOBAL_ACTION_BACK/);
});
test("owner proposals are single-use, time-limited, and reject missing Screen Assist",()=>{
  assert.match(proposal,/ScreenAssistService.active==null/);
  assert.match(proposal,/No system action was attempted/);
  assert.match(proposal,/proposedDeviceActionExpiresAt=android\.os\.SystemClock\.elapsedRealtime\(\)\+60_000L/);
  assert.match(method,/proposedDeviceAction=null\s*proposedDeviceActionExpiresAt=0L/);
  assert.match(method,/Approval expired/);
});
test("rendering must not attach second window if first detach fails",()=>{
  assert.match(render,/wm\.removeViewImmediate\(old\)/);
  assert.match(render,/if\(!removed\) \{/);
  assert.match(render,/stopSelf\(\)\s*return/);
});
test("native notification action checks service and lock and never reads content",()=>{
  assert.match(actions,/ScreenAssistService.active/);
  assert.match(actions,/isKeyguardLocked/);
  assert.match(actions,/GLOBAL_ACTION_NOTIFICATIONS/);
  assert.match(actions,/No notifications were read/);
  assert.doesNotMatch(actions,/NotificationListenerService/);
});
