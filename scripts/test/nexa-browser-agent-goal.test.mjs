import test from "node:test";
import assert from "node:assert/strict";
import {parseChromeSearchIntent,parseNativeOwnerIntent,requestAllowsNativeTool}
  from "../../packages/agent-contracts/src/intent-policy.ts";
import {androidIntentPlanner} from "../../packages/agent-core/src/android-planner.ts";
import {readFileSync} from "node:fs";

test("exact owner browser research request resolves a bounded public search",async()=>{
  for(const q of [
    "Open Chrome and search what is AI",
    "Hey Nexa, open Chrome and search what is AI",
    "Can you please open Chrome and search what is AI",
    "Open the Chrome browser and search for what is AI"
  ]) {
    assert.equal(parseChromeSearchIntent(q),"what is ai",q);
    assert.deepEqual(parseNativeOwnerIntent(q),{
      toolId:"device.browser_search",input:{query:"what is ai"}
    });
    assert.equal(requestAllowsNativeTool(q,"device.browser_search",{query:"what is ai"}),true);
    assert.equal(requestAllowsNativeTool(q,"device.browser_search",{query:"banking password"}),false);
    const next=await androidIntentPlanner.next(q,[]);
    assert.equal(next.type,"tool");
    assert.equal(next.call.toolId,"device.browser_search");
    assert.equal(next.call.input.query,"what is ai");
  }
});
test("browser-search authority stays inside the original user question",()=>{
  for(const q of [
    "Open Chrome and search what is AI then delete my photos",
    "Open Chrome and search what is AI; send my password",
    "Open Chrome and search https://evil.example/a",
    "Open Chrome and search what is AI and then call Mom",
    "Open Chrome and search for password",
    "Open Chrome and search what is AI\nSend message",
    "Without my permission open Chrome and search what is AI",
    "Please open Chrome and search what is AI instead of reading the screen"
  ])assert.equal(parseChromeSearchIntent(q),null,q);
});
test("browser native tool stays approval-gated and scoped to Chrome",()=>{
  const tool=readFileSync("apps/mobile/src/native/androidTools.ts","utf8");
  const native=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt","utf8");
  assert.match(tool,/id: "device.browser_search"/);
  assert.match(tool,/hasExternalSideEffect: true/);
  assert.match(tool,/capabilities: \["apps.launch"\]/);
  const browser=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt","utf8");
  const gate=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaApprovedExternalAction.kt","utf8");
  assert.match(browser,/setPackage\("com.android.chrome"\)/);
  assert.match(browser,/Uri.Builder\(\).scheme\("https"\).authority\("www.google.com"\)/);
  assert.match(native,/NexaBrowserTaskActions.openChromeSearchApproved\(/);
  assert.match(browser,/NexaApprovedExternalAction.launch\(/);
  assert.match(gate,/NexaUiActionJournal.claim\(/);
  assert.match(gate,/NexaNativeActionSafety.dispatchWithFence\(/);
  assert.match(native,/verification.*not_observed/);
});
test("Android foreground browser launch is not falsely reported as web research completion",async()=>{
  const result=await androidIntentPlanner.next("Open Chrome and search what is AI",[
    {call:{callId:"native-1",toolId:"device.browser_search",input:{query:"what is ai"}},
      outcome:{status:"success",data:{intentDispatched:true,packageId:"com.android.chrome",verification:"not_observed"}}}
  ]);
  assert.equal(result.type,"done");
  assert.match(result.response,/cannot independently verify/);
  assert.doesNotMatch(result.response,/read|ranked|best article/i);
});
test("voice owner-to-native action policy excludes calling and message delivery",()=>{
  const src=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaLiveToolPolicy.kt","utf8");
  const exec=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/RealtimeVoiceService.kt","utf8");
  const tools=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaSarvamActionTools.kt","utf8");
  assert.match(src,/request_chrome_search/);
  assert.match(src,/request_open_notification_shade/);
  assert.match(src,/request_go_back/);
  assert.match(exec,/pending.command == "open_notification_shade"/);
  assert.match(exec,/pending.command.startsWith\("chrome_search:"\)/);
  assert.match(tools,/NexaPhoneVoiceTools.chromeSearchQuery\(utterance\)==query/);
  assert.doesNotMatch(src,/put\(noArgs\("request_place_call"/);
  assert.doesNotMatch(src,/put\(noArgs\("request_send_message"/);
});
