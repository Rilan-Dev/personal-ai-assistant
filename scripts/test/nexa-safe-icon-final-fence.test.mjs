import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const root="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const source=readFileSync(root+"NexaScreenActions.kt","utf8");
const tests=readFileSync("apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaInputAndIconFixtureAndroidTest.kt","utf8");

test("safe navigation resolves exactly one complete, non-sensitive native source",()=>{
  assert.match(source,/internal fun uniqueCurrentTarget\(root: AccessibilityNodeInfo, icon: ObservedIcon\)/);
  assert.match(source,/val eligible = !node\.isPassword && !node\.isEditable &&/);
  assert.match(source,/node\.isVisibleToUser && node\.isEnabled/);
  assert.match(source,/if \(count != 1\) return null/);
  assert.match(source,/return if \(nodes\.isEmpty\(\) && count == 1\) matched else null/);
  assert.match(source,/sameResource && sameClass &&/);
  assert.match(source,/kind\?\.first == icon\.semantic && kind\.second == icon\.label/);
});

test("a changed or recycled target cannot be clicked using the old Accessibility node",()=>{
  const confirmed=source.slice(source.indexOf("fun confirm("));
  assert.match(confirmed,/val target = uniqueCurrentTarget\(root, icon\)/);
  assert.match(confirmed,/val clicked = NexaNativeActionSafety\.dispatchWithFence\(/);
  const fenced=confirmed.slice(confirmed.indexOf("val clicked = NexaNativeActionSafety.dispatchWithFence"));
  assert.match(fenced,/service\.externalWindowRoot\(\)/);
  assert.match(fenced,/fresh\.packageName\?\.toString\(\) != snapshot\.packageName/);
  assert.match(fenced,/fresh\.windowId != snapshot\.windowId/);
  assert.match(fenced,/val current = uniqueCurrentTarget\(fresh,icon\)/);
  assert.match(fenced,/current == null \|\| current != target/);
  assert.match(fenced,/current\.performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/);
  assert.doesNotMatch(fenced,/\btarget\.performAction\(/);
});

test("owner Pause, disabled Accessibility and locked screen refuse final icon dispatch",()=>{
  assert.match(source,/approvalFenceCurrent\(service,snapshot\.pauseGeneration\)/);
  assert.match(source,/ScreenAssistState\.connected \|\| ScreenAssistService\.active !== service/);
  assert.match(source,/keyguard\?\.isKeyguardLocked == true/);
  assert.match(source,/NexaUiActionJournal\.claim\(service, snapshot\.token, "tap"/);
  assert.match(source,/val clicked = NexaNativeActionSafety\.dispatchWithFence\(/);
});

test("post-dispatch exceptions are journaled as unknown rather than silently retried",()=>{
  assert.match(source,/var claimed = false/);
  assert.match(source,/claimed = true/);
  assert.match(source,/var enteredNativeAction = false/);
  assert.match(source,/enteredNativeAction = true\s*current\.performAction/);
  assert.match(source,/if \(enteredNativeAction\) outcomeUnknown = true else stale = true/);
  assert.match(source,/if \(outcomeUnknown\) "unknown" else "android_rejected"/);
  assert.match(source,/if \(claimed\) try \{\s*NexaUiActionJournal\.finish\(service,snapshot\.token,"unknown"\)/);
  assert.match(source,/no automatic retry/);
  assert.doesNotMatch(source,/\bThread\.sleep/);
});

test("synthetic Android UI verifies unchanged and mismatching source identity",()=>{
  assert.match(tests,/safeIconResolverRejectsStaleClassResourceOrBounds/);
  assert.match(tests,/uniqueCurrentTarget\(root,observed\)/);
  assert.match(tests,/className="android\.widget\.TextView"/);
  assert.match(tests,/resourceId="dev\.rilandev\.personalaiassistant:id\/not-the-search-icon"/);
  assert.match(tests,/Send remains excluded from all safe navigation actions/);
});
