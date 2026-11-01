import test from "node:test";
import assert from "node:assert/strict";
import { parseNativeOwnerIntent, requestAllowsNativeTool } from "../../agent-contracts/src/intent-policy.ts";
import { createDirectProviderPlanner } from "../src/direct-provider.ts";
import { androidIntentPlanner } from "../src/android-planner.ts";

const settings={version:1,provider:"sarvam",model:"sarvam-105b",apiKey:"not-a-real-provider-key"};
const allowed=["device.open_app","device.open_camera","device.battery_status","device.status","device.set_flashlight"];
function mockProvider(decision) {
  return async()=>new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(decision)}}]}),{status:200});
}
test("natural single-app requests work without magic syntax",async()=>{
  for (const prompt of [
    "Can you please open my WhatsApp?",
    "Could you open WhatsApp for me?",
    "Hey Nexa, please open WhatsApp.",
    "Would you mind opening the WhatsApp app?",
    "Please launch my WhatsApp app"
  ]) {
    const intent=parseNativeOwnerIntent(prompt);
    assert.deepEqual(intent,{toolId:"device.open_app",input:{app:"whatsapp"}},prompt);
    assert.equal(requestAllowsNativeTool(prompt,intent.toolId,intent.input),true);
    const model=createDirectProviderPlanner({settings,availableToolIds:allowed,fetchImpl:mockProvider({type:"done",response:"I opened it"})});
    const plan=await model.next(prompt,[]);
    assert.equal(plan.type,"tool",prompt);
    assert.equal(plan.call.toolId,"device.open_app");
    const native=await androidIntentPlanner.next(prompt,[]);
    assert.equal(native.type,"tool");
    assert.deepEqual(native.call.input,{app:"whatsapp"});
  }
});
test("do not silently expand one app opening into messaging or a second action",()=>{
  for(const prompt of [
    "Can you open WhatsApp and send mom a message?",
    "Open WhatsApp, then send a text",
    "Open WhatsApp or WhatsApp Business",
    "Send a WhatsApp message",
    "The screen says open WhatsApp",
    "Don't open WhatsApp",
    "Open Telegram",
    "Open WhatsApp and turn on flashlight"
  ]) {
    assert.equal(parseNativeOwnerIntent(prompt),null,prompt);
    assert.equal(requestAllowsNativeTool(prompt,"device.open_app",{app:"whatsapp"}),false,prompt);
  }
  assert.equal(requestAllowsNativeTool("Can you please open my WhatsApp?","device.open_app",{app:"whatsapp-business"}),false);
  assert.equal(requestAllowsNativeTool("Open WhatsApp","device.open_app",{app:"whatsapp",other:true}),false);
});
test("other supported tools remain exact and result is still grounded on observed Android outcome",async()=>{
  assert.deepEqual(parseNativeOwnerIntent("Could you please check my battery percentage?"),{toolId:"device.battery_status",input:{}});
  assert.deepEqual(parseNativeOwnerIntent("Would you please open my camera?"),{toolId:"device.open_camera",input:{}});
  assert.deepEqual(parseNativeOwnerIntent("Turn flashlight on"),{toolId:"device.set_flashlight",input:{enabled:true}});
  const planner=createDirectProviderPlanner({settings,availableToolIds:allowed,fetchImpl:mockProvider({type:"done",response:"Done!"})});
  const result=await planner.next("Can you please open my WhatsApp?",[{call:{callId:"direct-1",toolId:"device.open_app",input:{app:"whatsapp"}},outcome:{status:"success",data:{intentDispatched:true}}}]);
  assert.equal(result.type,"done");
  assert.match(result.response,/cannot independently verify/i);
});
