import test from "node:test";
import assert from "node:assert/strict";
import { parseNativeOwnerIntent, requestAllowsNativeTool } from "../../agent-contracts/src/intent-policy.ts";
import { createDirectProviderPlanner } from "../src/direct-provider.ts";
import { androidIntentPlanner } from "../src/android-planner.ts";
import { AgentHarness, ToolRegistry } from "../src/runtime.ts";

const settings={version:1,provider:"sarvam",model:"sarvam-105b",apiKey:"not-a-real-provider-key"};
const allowed=["device.open_app","device.open_camera","device.battery_status","device.status","device.set_flashlight","device.open_notification_shade"];
function mockProvider(decision) {
  return async()=>new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(decision)}}]}),{status:200});
}
test("natural single-app requests work without magic syntax",async()=>{
  for (const prompt of [
    "Can you please open my WhatsApp?",
    "Could you open WhatsApp for me?",
    "Hey Nexa, please open WhatsApp.",
    "Would you mind opening the WhatsApp app?",
    "Please launch my WhatsApp app",
    "Can you please i Open my WhatsApp",
    "I want you to open the whatsapp app",
    "I need you to open WhatsApp",
    "I'd like you to open WhatsApp",
    "I would like you to open WhatsApp"
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
    "Open WhatsApp and turn on flashlight",
    "I open WhatsApp",
    "I want WhatsApp opened and a message sent"
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

test("explicit notification shade requests become one owner-approved native action",async()=>{
  for(const prompt of [
    "Open notification bar",
    "Hey Nexa, please open the notification shade.",
    "Could you show notifications?"
  ]) {
    const expected={toolId:"device.open_notification_shade",input:{}};
    assert.deepEqual(parseNativeOwnerIntent(prompt),expected,prompt);
    assert.equal(requestAllowsNativeTool(prompt,expected.toolId,{}),true,prompt);
    const native=await androidIntentPlanner.next(prompt,[]);
    assert.equal(native.type,"tool",prompt);
    assert.equal(native.call.toolId,expected.toolId,prompt);
    const direct=createDirectProviderPlanner({settings,availableToolIds:allowed,fetchImpl:async()=>{
      throw new Error("The exact owner intent should not require a provider call");
    }});
    const planned=await direct.next(prompt,[]);
    assert.deepEqual(planned,{type:"tool",call:{callId:"direct-1",...expected}},prompt);
  }
});

test("notification shade intent rejects reading, negation, and compound requests",()=>{
  for(const prompt of [
    "Read my notifications",
    "Open notification bar and read the alerts",
    "Do not open the notification shade",
    "The screen says open notification bar"
  ]) {
    assert.equal(parseNativeOwnerIntent(prompt),null,prompt);
    assert.equal(requestAllowsNativeTool(prompt,"device.open_notification_shade",{}),false,prompt);
  }
});

test("notification shade planner cannot execute until the one-time approval is recorded",async()=>{
  let executed=false;
  const registry=new ToolRegistry();
  registry.register({
    id:"device.open_notification_shade",
    description:"Open Android's notification shade after owner approval",
    capabilities:[],risk:"medium",hasExternalSideEffect:true,
    validateInput:input=>input!==null && typeof input==="object" && !Array.isArray(input) && Object.keys(input).length===0,
    execute:async()=>{executed=true;return {status:"success",data:{notificationShadeAccepted:true}};}
  });
  const harness=new AgentHarness(registry,androidIntentPlanner);
  const result=await harness.run({request:"Open notification bar",sessionId:"shade-approval-test",grantedCapabilities:new Set()});
  assert.equal(result.status,"awaiting_approval");
  assert.equal(result.call.toolId,"device.open_notification_shade");
  assert.equal(executed,false,"planning must never open SystemUI before owner confirmation");
});
