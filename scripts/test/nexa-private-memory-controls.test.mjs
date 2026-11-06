import assert from "node:assert/strict";
import {test} from "node:test";
import {readFileSync} from "node:fs";
const root=new URL("../../",import.meta.url);
const source=path=>readFileSync(new URL(path,root),"utf8");

test("private recipient memory bulk erase cannot be a model tool",()=>{
  const native=source("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt");
  const bridge=source("apps/mobile/src/native/nexaMessaging.ts");
  const tools=source("apps/mobile/src/native/androidTools.ts");
  assert.match(native,/@ReactMethod fun forgetAllMessageAppPreferences\(p:Promise\)/);
  assert.match(native,/activity\?\.hasWindowFocus\(\)!=true/);
  assert.match(native,/isKeyguardLocked==true/);
  assert.match(native,/messageAppRecord\(raw\)/);
  assert.match(native,/edit\.remove\(it\)/);
  assert.match(native,/check\(edit\.commit\(\)\)/);
  assert.match(bridge,/forgetAllRememberedMessageApps/);
  assert.doesNotMatch(tools,/\bid:\s*["'](?:device|screen)\.forgetAll/);
});

test("clearing remembered messaging apps requires two owner-visible UI presses",()=>{
  const ui=source("apps/mobile/App.tsx");
  assert.match(ui,/clearAllMessageMemoryReview/);
  assert.match(ui,/onPress=\{\(\)=>setClearAllMessageMemoryReview\(false\)\}/);
  assert.match(ui,/onPress=\{\(\)=>void clearAllMessageChannelMemory\(\)\}/);
  assert.match(ui,/if\(clearAllMessageMemoryBusy\|\|!clearAllMessageMemoryReview\|\|!engine\.live\)return/);
  assert.match(ui,/setMessagePreference\(null\)/);
});

test("any exact alias can be inspected and forgotten from the owner Tools screen",()=>{
  const ui=source("apps/mobile/App.tsx");
  assert.match(ui,/const inspectRememberedMessageAlias=async/);
  assert.match(ui,/const forgetSearchedMessageAlias=async/);
  assert.match(ui,/recipientMemoryKey\(memoryAliasQuery\)/);
  assert.match(ui,/await messageAppPreferenceDetails\(key\)/);
  assert.match(ui,/await forgetMessageApp\(key\)/);
  assert.match(ui,/setMemoryAliasResult\(null\)/);
  assert.match(ui,/Recipient alias to manage private channel preference/);
  assert.match(ui,/Forget this alias’s saved app/);
});
