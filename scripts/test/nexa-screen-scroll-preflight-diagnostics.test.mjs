import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const base="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const device=readFileSync(base+"DeviceControlModule.kt","utf8").replace(/\r\n/g,"\n");
const scroll=readFileSync(base+"NexaScreenScroll.kt","utf8").replace(/\r\n/g,"\n");
const harness=readFileSync("packages/agent-core/src/runtime.ts","utf8").replace(/\r\n/g,"\n");

test("reviewed scroll preflight distinguishes missing windows from missing targets",()=>{
  assert.ok(/preflight\.startsWith\("No permitted app window to scroll"\)[\s\S]*?SCROLL_WINDOW_UNAVAILABLE/.test(device));
  assert.ok(/preflight\.startsWith\("No visible scrollable container supports"\)[\s\S]*?SCROLL_CONTAINER_UNAVAILABLE/.test(device));
  assert.ok(/preflight\.startsWith\("Bounded scroll search requires one unique"\)[\s\S]*?SCROLL_TARGET_AMBIGUOUS/.test(device));
  assert.ok(/No permitted app window to scroll\./.test(scroll));
  assert.ok(/No visible scrollable container supports that direction\./.test(scroll));
});

test("preflight diagnostics stay allowlisted and never contain visible screen text",()=>{
  for(const code of ["SCROLL_WINDOW_UNAVAILABLE","SCROLL_CONTAINER_UNAVAILABLE","SCROLL_TARGET_AMBIGUOUS"])
    assert.ok(harness.includes('"'+code+'"'),`native code ${code} is allowlisted`);
  assert.doesNotMatch(device,/contentDescription\?\.toString\(\).*promise\.reject/s);
});
