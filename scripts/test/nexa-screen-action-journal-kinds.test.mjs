import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const dir="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const source=(name)=>readFileSync(dir+name,"utf8");

test("named visible-label click uses the journal canonical tap kind",()=>{
  const labelTap=source("NexaScreenLabelTap.kt");
  const journal=source("NexaUiActionJournal.kt");
  assert.match(labelTap,/NexaUiActionJournal\.claim\(service,token,"tap",pkg,origin=origin\)/);
  assert.doesNotMatch(labelTap,/NexaUiActionJournal\.claim\(service,token,"tap_label",pkg\)/);
  assert.match(journal,/kind !in setOf\("tap","scroll_up","scroll_down","scroll_left","scroll_right",/);
});
test("launcher horizontal gesture uses the accepted scroll_left/scroll_right kinds",()=>{
  const launcher=source("NexaLauncherSwipe.kt");
  assert.match(launcher,/NexaUiActionJournal\.claim\(service,token,"scroll_\$direction",pkg\)/);
  assert.doesNotMatch(launcher,/"launcher_swipe_\$direction"/);
  assert.match(launcher,/NexaLauncherSwipe|class|object/);
  assert.match(launcher,/isHome\(service,root\)/);
  assert.match(launcher,/GestureDescription/);
});
test("one-time claim cannot be silently reused when Android reports uncertain results",()=>{
  const journal=source("NexaUiActionJournal.kt");
  assert.match(journal,/if \(entries\.getJSONObject\(i\)\.optString\("id"\) == id\) return@synchronized false/);
  assert.match(journal,/status","claimed"/);
});
