import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const overlay=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt","utf8");
const actions=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt","utf8");
const method=overlay.slice(overlay.indexOf("    private fun confirmOwnerDeviceAction() {"),
  overlay.indexOf("    private fun sendLocal(raw:"));

test("system navigation releases the expanded overlay BEFORE dispatching Android action",()=>{
  assert.match(method,/val isSystemNavigation=action=="open_notification_shade" \|\| action=="go_back"/);
  assert.match(method,/if\(isSystemNavigation\) collapse\(\) else render\(\)/);
  assert.ok(method.indexOf("if(isSystemNavigation) collapse() else render()") <
    method.indexOf("NexaBrowserTaskActions.notificationsApproved(this)"));
  assert.match(method,/postDelayed\(\{dispatch\(\)\},240L\)/);
});
test("accepted system action never reopens an expanded overlay on top of the destination",()=>{
  assert.match(method,/if\(isSystemNavigation && outcome.startsWith\("Android accepted"\)\) \{/);
  const accepted=method.slice(method.indexOf('if(isSystemNavigation && outcome.startsWith("Android accepted"))'));
  assert.ok(accepted.indexOf("return@post")<accepted.indexOf("render()"));
});
test("rejected and uncertain system actions remain visible to owner",()=>{
  assert.match(method,/if\(isSystemNavigation\) \{\s*expanded=true\s*section="tools"/);
  assert.match(method,/note=outcome.take\(360\)/);
});
test("notification action demands connected owner-enabled accessibility and does not read private notifications",()=>{
  assert.match(actions,/ScreenAssistService.active/);
  assert.match(actions,/GLOBAL_ACTION_NOTIFICATIONS/);
  assert.match(actions,/isKeyguardLocked/);
  assert.match(actions,/No notifications were read/);
  assert.doesNotMatch(actions,/NotificationListenerService|READ_SMS|query.*notification/i);
});
