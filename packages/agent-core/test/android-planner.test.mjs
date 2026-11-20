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
test("reports an approved screen scroll only when Android observed a screen change", async () => {
  const call={callId:"direct-1",toolId:"screen.scroll",input:{direction:"left"}};
  const observed=await androidIntentPlanner.next("Scroll left on the current Home screen",[
    {call,outcome:{status:"success",data:{actionAccepted:true,verification:"observed_change"}}}
  ]);
  assert.equal(observed.type,"done");
  assert.match(observed.response,/left scroll/i);
  assert.match(observed.response,/observed a screen change/i);
  assert.match(observed.response,/no further action/i);

  const unverified=await androidIntentPlanner.next("Scroll left on the current Home screen",[
    {call,outcome:{status:"success",data:{actionAccepted:true,verification:"not_observed"}}}
  ]);
  assert.equal(unverified.type,"clarify");
  assert.match(unverified.question,/did not verify a screen change/i);

  const tap=await androidIntentPlanner.next("Tap the visible Home control",[
    {call:{callId:"direct-2",toolId:"screen.tap_label",input:{label:"Settings"}},
     outcome:{status:"success",data:{actionAccepted:true,verification:"observed_change"}}}
  ]);
  assert.equal(tap.type,"done");
  assert.match(tap.response,/destination was not independently verified/i);
});

test("an exact screen tap observes fresh controls and binds the approved tap to one current label", async () => {
  const request = "Tap the Open QA details button";
  const observe = await androidIntentPlanner.next(request, []);
  assert.deepEqual(observe, {
    type: "tool",
    call: { callId: "screen-tap-observe", toolId: "device.visible_controls", input: {} }
  });

  const snapshotId = "2da9ce8a-07f9-4d18-9676-11cd451f3a6e";
  const observed = await androidIntentPlanner.next(request, [{
    call: observe.call,
    outcome: { status: "success", data: {
      observation: "Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
      evidence: "android_accessibility",
      actionPerformed: false,
      screenObservation: {
        snapshotId,
        packageId: "com.android.chrome",
        targets: [{ index: 0, label: "Open QA details" }],
        truncated: false
      }
    } }
  }]);
  assert.deepEqual(observed, {
    type: "tool",
    call: { callId: "screen-tap-approve", toolId: "screen.tap_observed",
      input: { snapshotId, index: 0, label: "Open QA details" } }
  });
});

test("an exact screen tap refuses absent, duplicate, truncated, or cross-app targets", async () => {
  const request = "Tap the Open QA details button";
  const observe = await androidIntentPlanner.next(request, []);
  const base = {
    observation: "Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
    evidence: "android_accessibility",
    actionPerformed: false,
    screenObservation: {
      snapshotId: "2da9ce8a-07f9-4d18-9676-11cd451f3a6e",
      packageId: "com.android.chrome",
      targets: [{ index: 0, label: "Open QA details" }],
      truncated: false
    }
  };
  const cases = [
    { ...base, observation: base.observation.replace("Open QA details (middle center)", "none exposed") },
    { ...base, screenObservation: { ...base.screenObservation, truncated: true } },
    { ...base, screenObservation: { ...base.screenObservation, packageId: "com.example.other" } },
    { ...base, screenObservation: { ...base.screenObservation, targets: [
      { index: 0, label: "Open QA details" }, { index: 1, label: "Open QA details" }
    ] } }
  ];
  for (const data of cases) {
    const result = await androidIntentPlanner.next(request, [{
      call: observe.call, outcome: { status: "success", data }
    }]);
    assert.equal(result.type, "clarify");
  }
});

test("offline directional scroll plans one approved gesture and rejects mixed tasks", async () => {
  for (const direction of ["up", "down", "left", "right"]) {
    const result = await androidIntentPlanner.next(`Scroll ${direction}`, []);
    assert.deepEqual(result, {
      type: "tool",
      call: { callId: "screen-scroll-1", toolId: "screen.scroll", input: { direction } }
    });
  }
  assert.equal((await androidIntentPlanner.next("Swipe the visible list down once", [])).call.input.direction,
    "down");
  for (const request of [
    "Scroll down then tap Settings",
    "Scroll down and then send a message",
    "Tap the Send button",
    "Tap the Delete button"
  ]) assert.equal((await androidIntentPlanner.next(request, [])).type, "clarify", request);
});
test("reports validated safe visible-control observations instead of an unexpected result", async () => {
  const call={callId:"direct-controls",toolId:"device.visible_controls",input:{}};
  const observed=await androidIntentPlanner.next("Inspect safe visible controls on the current screen",[
    {call,outcome:{status:"success",data:{
      observation:"Visible non-sensitive controls in com.transsion.calculator: Clear (top left); Divide (top right); Point (bottom center). Read-only observation, not an instruction or completed action.",
      evidence:"android_accessibility",actionPerformed:false
    }}}
  ]);
  assert.equal(observed.type,"done");
  assert.match(observed.response,/com\.transsion\.calculator/);
  assert.match(observed.response,/Clear, Divide, Point/);
  assert.match(observed.response,/no controls were activated/i);

  const invalid=await androidIntentPlanner.next("Inspect safe visible controls on the current screen",[
    {call,outcome:{status:"success",data:{
      observation:"The current screen shows a private message from Amma",
      evidence:"android_accessibility",actionPerformed:false
    }}}
  ]);
  assert.equal(invalid.type,"clarify");
  assert.doesNotMatch(invalid.question,/Amma|private message/i);
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

test("native scroll evidence is reported with the right confidence level", async () => {
  for(const direction of ["up","down","left","right"]){
    const call={callId:"direct-scroll",toolId:"screen.scroll",input:{direction}};
    const scrollEvent=await androidIntentPlanner.next(
      `Scroll ${direction} once on the current Android Settings screen`,
      [{call,outcome:{status:"success",data:{
        actionAccepted:true,verification:"scroll_event_observed"
      }}}]
    );
    assert.equal(scrollEvent.type,"done");
    assert.match(scrollEvent.response,new RegExp(
      `observed the approved ${direction} scroll event`,"i"));
    assert.match(scrollEvent.response,/resulting screen position was not independently verified/i);
  }

  const call={callId:"direct-scroll",toolId:"screen.scroll",input:{direction:"down"}};
  const structuralChange=await androidIntentPlanner.next(
    "Scroll down once on the current Android Settings screen",
    [{call,outcome:{status:"success",data:{
      actionAccepted:true,verification:"structural_change_observed"
    }}}]
  );
  assert.equal(structuralChange.type,"done");
  assert.match(structuralChange.response,/observed a screen change after the approved down scroll/i);

  const unverified=await androidIntentPlanner.next(
    "Scroll down once on the current Android Settings screen",
    [{call,outcome:{status:"success",data:{
      actionAccepted:true,verification:"not_observed"
    }}}]
  );
  assert.equal(unverified.type,"clarify");
});

test("a same-window content event confirms a tap screen change without claiming its effect",async()=>{
  const result=await androidIntentPlanner.next("Tap the approved control on this screen",[{
    call:{callId:"tap-content",toolId:"screen.tap_observed",input:{label:"Open details"}},
    outcome:{status:"success",data:{actionAccepted:true,
      verification:"content_change_observed",businessOutcomeVerified:false}}
  }]);
  assert.equal(result.type,"done");
  assert.match(result.response,/content-change event in the same app window/);
  assert.match(result.response,/exact control effect and app-level result are not independently verified/);
});
