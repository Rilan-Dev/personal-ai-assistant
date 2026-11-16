import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const source=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaScreenLabelTap.kt",
  "utf8"
).replace(/\r\n/g,"\n");

test("reviewed search taps can fall back to one gesture on a revalidated live field",()=>{
  assert.match(source,/private fun uniqueLiveSearchInput\(/,
    "a fresh visible search field must be resolved after approval");
  assert.match(source,/val liveSearch=uniqueLiveSearchInput\(liveRoot,pkg,label,reviewedBounds\)/,
    "the final dispatch must reacquire the reviewed target from the current external window");
  assert.match(source,/gestureTap=\{dispatchSearchFieldTap\(service,liveBounds\)\}/,
    "only the reviewed live search-field bounds may supply the tap location");
  assert.match(source,/private fun dispatchSearchFieldTap\(service:ScreenAssistService,bounds:Rect\)/,
    "the search-field fallback must be a bounded native Accessibility gesture");
  assert.match(source,/fun dispatch\(click:\(\)->Boolean,focus:\(\)->Boolean,[\s\S]*?click\(\) \|\| focus\(\) \|\| gestureTap\(\)/,
    "gesture dispatch must only run after both semantic actions are rejected");
  assert.match(source,/bounds\.centerX\(\)[\s\S]*?bounds\.centerY\(\)[\s\S]*?GestureDescription\.Builder\(\)[\s\S]*?service\.dispatchGesture/,
    "gesture coordinates must come from the current unique field bounds");
  assert.match(source,/bounds==reviewedBounds\)matches\.add\(node\)[\s\S]*?matches\.size!=1\)return null/,
    "the fresh target must be unique and retain its owner-reviewed geometry");
});

test("ordinary named controls keep semantic ACTION_CLICK without a coordinate fallback",()=>{
  assert.match(source,/else if\(searchInputTarget\)[\s\S]*?gestureTap=\{dispatchSearchFieldTap\(service,liveBounds\)\}[\s\S]*?else found\.performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/,
    "the geometric fallback is restricted to an explicitly named safe Search input");
});
