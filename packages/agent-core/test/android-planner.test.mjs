import assert from "node:assert/strict";
import { test } from "node:test";
import { androidIntentPlanner } from "../src/android-planner.ts";

const run = (text) => androidIntentPlanner.next(text, []);
test("plans camera launch without screen automation", async () => {
  assert.deepEqual(await run("Open my camera"), {
    type: "tool",
    call: { callId: "native-1", toolId: "device.open_camera", input: {} }
  });
});
test("plans a named installed-app lookup", async () => {
  assert.equal((await run("Hey Nexa, open WhatsApp")).call.input.app, "whatsapp");
  assert.equal((await run("Open WhatsApp Business")).call.input.app, "whatsapp-business");
  assert.equal((await run("Open Calendar")).call.input.app, "calendar");
});
test("checks bridge diagnostics", async () => {
  assert.equal((await run("Check device status")).call.toolId, "device.status");
});
test("refuses multi-action instructions rather than partially executing", async () => {
  for (const text of [
    "Open WhatsApp and send ROM a birthday message",
    "Open camera and take a picture",
    "Open my banking application and transfer money",
    "Call ROM",
    "Where am I"
  ]) assert.equal((await run(text)).type, "clarify", text);
});
test("does not invent screen verification after an intent", async () => {
  const r = await androidIntentPlanner.next("Open camera", [
    {call:{callId:"native-1",toolId:"device.open_camera",input:{}},
     outcome:{status:"success",data:{intentDispatched:true,verification:"not_observed"}}}
  ]);
  assert.equal(r.type, "done");
  assert.match(r.response, /cannot independently verify/);
});
test("training approval command plans a safe isolated mock tool", async () => {
  const call = await androidIntentPlanner.next("Test approval", []);
  assert.equal(call.type, "tool");
  assert.deepEqual(call.call, {callId:"native-1",toolId:"demo.approval_test",input:{}});
  const done = await androidIntentPlanner.next("Test approval", [
    {call:call.call,outcome:{status:"success",data:{training:true,noDeviceAction:true}}}
  ]);
  assert.equal(done.type, "done");
  assert.match(done.response,/no device operation/);
});
test("deterministic Android battery status reflects observed charge only", async () => {
  const planned = await androidIntentPlanner.next("Check battery level",[]);
  assert.deepEqual(planned,{type:"tool",call:{callId:"native-1",toolId:"device.battery_status",input:{}}});
  const finished=await androidIntentPlanner.next("Check battery level",[
    {call:planned.call,outcome:{status:"success",data:{levelPercent:37,charging:false}}}
  ]);
  assert.equal(finished.type,"done");
  assert.match(finished.response,/37%/);
  assert.match(finished.response,/not currently charging\.$/);
});
test("deterministic flashlight planner only accepts one exact on/off command",async()=>{
  const on=await androidIntentPlanner.next("Turn flashlight on",[]);
  assert.deepEqual(on,{type:"tool",call:{callId:"native-1",toolId:"device.set_flashlight",input:{enabled:true}}});
  const alternate=await androidIntentPlanner.next("Turn on the flashlight",[]);
  assert.deepEqual(alternate.call.input,{enabled:true});
  const off=await androidIntentPlanner.next("Switch off my torch",[]);
  assert.deepEqual(off.call.input,{enabled:false});
  const unsupported=await androidIntentPlanner.next("Turn flashlight on and send a text",[]);
  assert.equal(unsupported.type,"clarify");
  const result=await androidIntentPlanner.next("Turn flashlight on",[
    {call:on.call,outcome:{status:"success",data:{enabled:true,verification:"observed"}}}
  ]);
  assert.equal(result.type,"done");
  assert.match(result.response,/confirmed flashlight ON/);
});
