import assert from "node:assert/strict";
import {test} from "node:test";
import {AgentHarness, ToolRegistry} from "../src/runtime.ts";
import {InMemoryActionJournal} from "../src/journal.ts";
import {androidIntentPlanner} from "../src/android-planner.ts";
import {createOwnerSequentialPlanner,parseOwnerMultiToolRequest,
  evidenceForOwnerStep} from "../src/owner-sequence.ts";

test("exact owner sequence parses six bounded steps and preserves quoted text",()=>{
  const plan=parseOwnerMultiToolRequest(
    'Open WhatsApp then tap Search then type "Mom then Dad" into Search field then scroll down');
  assert.equal(plan?.length,4);
  assert.deepEqual(plan?.map(x=>x.toolId),
    ["device.open_app","screen.tap_label","screen.fill_text","screen.scroll"]);
  assert.deepEqual(plan?.[2].input,{targetLabel:"Search",exactText:"Mom then Dad"});
  assert.deepEqual(plan?.map(x=>x.callId),
    ["owner-seq-1","owner-seq-2","owner-seq-3","owner-seq-4"]);
  assert.equal(parseOwnerMultiToolRequest("Check battery then check storage")?.length,2);
  assert.deepEqual(parseOwnerMultiToolRequest("Open WhatsApp and tap Search")?.map(x=>x.toolId),
    ["device.open_app","screen.tap_label"]);
  assert.equal(parseOwnerMultiToolRequest("What's my battery percentage and check storage")?.length,2);
  assert.equal(parseOwnerMultiToolRequest('Open Chrome and search what is ai then check battery')?.length,2);
  assert.equal(parseOwnerMultiToolRequest('Tap Search then type "please then and after" into Notes')?.[1].input.exactText,
    'please then and after');
});

test("leaves semantic control descriptions to the screen-aware planner",()=>{
  assert.equal(parseOwnerMultiToolRequest(
    "Open Calculator then tap its decimal point once"
  ),null);
  assert.equal(parseOwnerMultiToolRequest(
    "Open Calculator then tap the decimal point in Calculator one time"
  ),null);
});

test("entire sequence fails closed if any step could send, grant, transfer or hide approval",()=>{
  for(const request of [
    "Open WhatsApp then tap Send",
    "Open Calendar then send Mom a message",
    "Tap Allow then scroll down",
    "Open bank app then transfer money",
    "Scroll down then click Pay",
    "Tap Search then type 123 into Password",
    "Tap Search then fill Recipient with Dad",
    "Open WhatsApp then call Mom",
    "Open WhatsApp then tap Search then tap Send",
    "Open Calendar then check battery then check storage then scroll down then tap Search then press Settings then tap Menu",
    'Tap Search then type "not closed into Search',
    "Open WhatsApp; then tap Search",
  ]) assert.equal(parseOwnerMultiToolRequest(request),null,request);
});

test("a verified read-only sequence uses one durable AgentHarness session and both tools",async()=>{
  const registry=new ToolRegistry();
  const calls=[];
  for(const [id,data] of [
    ["device.battery_status",{levelPercent:52,charging:false}],
    ["device.storage_status",{totalGb:64,freeGb:11}]
  ]){
    registry.register({id,description:id,capabilities:[],risk:"low",
      hasExternalSideEffect:false,validateInput:x=>!!x&&typeof x==="object"&&Object.keys(x).length===0,
      execute:async()=>{calls.push(id);return {status:"success",data};}});
  }
  const journal=new InMemoryActionJournal();
  const harness=new AgentHarness(registry,createOwnerSequentialPlanner(androidIntentPlanner));
  const result=await harness.run({request:"Check battery then check storage",
    sessionId:"owner-task-1",grantedCapabilities:new Set(),journal,maxSteps:6});
  assert.equal(result.status,"completed");
  assert.equal(calls.length,2);
  assert.equal(journal.state("owner-task-1","owner-seq-1"),"settled");
  assert.equal(journal.state("owner-task-1","owner-seq-2"),"settled");
  const saved=await journal.loadSession("owner-task-1");
  assert.equal(saved?.observations.length,2);
});

test("a sensitive step pauses for approval and resumes only once",async()=>{
  const registry=new ToolRegistry();let toggles=0,reads=0;
  registry.register({id:"device.set_flashlight",description:"Flashlight",
    capabilities:["hardware.flashlight"],risk:"medium",hasExternalSideEffect:true,
    validateInput:x=>!!x&&typeof x==="object"&&
      Object.keys(x).length===1&&x.enabled===true,
    execute:async()=>{toggles++;return {status:"success",data:{enabled:true,verification:"observed"}};}});
  registry.register({id:"device.battery_status",description:"Battery",
    capabilities:[],risk:"low",hasExternalSideEffect:false,
    validateInput:x=>!!x&&typeof x==="object"&&Object.keys(x).length===0,
    execute:async()=>{reads++;return {status:"success",data:{levelPercent:55,charging:true}};}});
  const journal=new InMemoryActionJournal();
  const harness=new AgentHarness(registry,createOwnerSequentialPlanner(androidIntentPlanner));
  const options={request:"Turn flashlight on then check battery",sessionId:"owner-task-2",
    grantedCapabilities:new Set(["hardware.flashlight"]),journal,maxSteps:6};
  const offered=await harness.run(options);
  assert.equal(offered.status,"awaiting_approval");
  assert.equal(toggles,0);
  assert.equal(await journal.approveAction("owner-task-2",offered.call.callId,offered.fingerprint),true);
  const result=await harness.run({...options,resume:true});
  assert.equal(result.status,"completed");
  assert.equal(toggles,1);
  assert.equal(reads,1);
  const replay=await harness.run({...options,resume:true});
  assert.equal(replay.status,"blocked");
  assert.equal(toggles,1);
});

test("native outcome uncertainty stops the next tool; a model result is not evidence",async()=>{
  const plan=parseOwnerMultiToolRequest("Open Calendar then tap Search");
  assert.equal(plan?.length,2);
  assert.equal(evidenceForOwnerStep(plan[0],{
    status:"success",data:{intentDispatched:true,verification:"not_observed"}},false).safe,false);
  assert.equal(evidenceForOwnerStep(plan[0],{
    status:"success",data:{intentDispatched:true,verification:"destination_package_observed"}},false).safe,true);
  assert.equal(evidenceForOwnerStep(plan[1],{
    status:"success",data:{actionAccepted:true,verification:"not_observed"}},true).safe,false);
  const planner=createOwnerSequentialPlanner(androidIntentPlanner);
  const next=await planner.next("Open Calendar then tap Search",[{
    call:plan[0],outcome:{status:"success",
      data:{intentDispatched:true,verification:"not_observed"}}
  }]);
  assert.equal(next.type,"clarify");
  assert.match(next.question,/not observed/);
});

test("changed step argument and fake evidence stop the continuation",async()=>{
  const planner=createOwnerSequentialPlanner(androidIntentPlanner);
  const plan=parseOwnerMultiToolRequest("Check battery then scroll down");
  const changed=await planner.next("Check battery then scroll down",[{
    call:{...plan[0],input:{enabled:true}},outcome:{
      status:"success",data:{levelPercent:91,charging:false}}
  }]);
  assert.equal(changed.type,"clarify");
  const fake=await planner.next("Check battery then scroll down",[{
    call:plan[0],outcome:{status:"success",data:{levelPercent:110,charging:false}}
  }]);
  assert.equal(fake.type,"clarify");
});


test("six-step sequence has an extra completion turn and never claims success early",async()=>{
  const request="Check battery then check storage then check device status "+
    "then check battery then check storage then check device status";
  const steps=parseOwnerMultiToolRequest(request);
  assert.equal(steps?.length,6);
  const data={
    "device.battery_status":{levelPercent:50,charging:false},
    "device.storage_status":{totalGb:64,freeGb:24},
    "device.status":{platform:"android",accessibilityEnabled:false}
  };
  const planner=createOwnerSequentialPlanner(androidIntentPlanner);
  const observations=steps.map(call=>({call,outcome:{
    status:"success",data:data[call.toolId]
  }}));
  const end=await planner.next(request,observations);
  assert.equal(end.type,"done");
  assert.match(end.response,/6 owner-requested steps/);
});


test("recipient-bound WhatsApp send is the terminal one-time-approved step",()=>{
  const request='Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "I am in a meeting and will call later" then send it';
  const steps=parseOwnerMultiToolRequest(request);
  assert.deepEqual(steps?.map(s=>s.toolId),[
    "device.open_app","screen.open_whatsapp_chat","screen.compose_whatsapp",
    "screen.send_whatsapp"
  ]);
  assert.deepEqual(steps?.[1].input,{recipientHint:"Mom"});
  assert.deepEqual(steps?.[2].input,{recipientHint:"Mom",
    exactText:"I am in a meeting and will call later"});
  assert.deepEqual(steps?.[3].input,{recipientHint:"Mom",
    exactText:"I am in a meeting and will call later"});
  assert.equal(steps?.[3].callId,"owner-seq-4");
});

test("search-selected WhatsApp, IMO and BOTIM messages are exact six-step owner plans",()=>{
  const cases=[
    ["WhatsApp","whatsapp","com.whatsapp"],
    ["IMO","imo","com.imo.android.imoim"],
    ["BOTIM","botim","im.thebot.messenger"]
  ];
  for(const [label,appId,packageId] of cases){
    const request=`open ${label} then tap Search then type "Amma" into Search `+
      `then tap Amma then prepare a ${label} message for Amma saying `+
      `"Hi Mom i will call you later" then send it`;
    const steps=parseOwnerMultiToolRequest(request);
    assert.deepEqual(steps?.map(step=>step.toolId),[
      "device.open_app","screen.tap_label","screen.fill_text","screen.tap_label",
      "screen.compose_message","screen.send_message"
    ],label);
    assert.deepEqual(steps?.[0].input,{app:appId});
    assert.deepEqual(steps?.[4].input,{appId,recipientHint:"Amma",
      exactText:"Hi Mom i will call you later"});
    assert.deepEqual(steps?.[5].input,steps?.[4].input);
    assert.equal(evidenceForOwnerStep(steps[0],{status:"success",data:{
      intentDispatched:true,packageId,verification:"destination_package_observed"
    }}).safe,true);
    assert.equal(evidenceForOwnerStep(steps[5],{status:"success",data:{
      appId,verification:"sent_message_visible",recipientMatched:true,
      exactTextMatched:true,sent:true,deliveryReported:false
    }},true).safe,true);
  }
});

test("search-to-send plans reject wrong recipients, wrong apps, and any nonterminal Send",()=>{
  for(const request of [
    'open IMO then tap Search then type "Amma" into Search then tap Amma then prepare a BOTIM message for Amma saying "hello" then send it',
    'open BOTIM then tap Search then type "Amma" into Search then tap Amma then prepare a BOTIM message for Dad saying "hello" then send it',
    'open WhatsApp then tap Search then type "Amma" into Search then tap Amma then prepare a WhatsApp message for Amma saying "hello" then send it then tap Search',
    'open WhatsApp then send it',
    'open WhatsApp then tap Search then type "Amma" into Search then tap Amma then send it'
  ]) assert.equal(parseOwnerMultiToolRequest(request),null,request);
});

test("multi-step WhatsApp drafting rejects switched recipient and unsupported context",()=>{
  for(const cmd of [
    'Open WhatsApp then open chat with Mom then prepare WhatsApp message for Dad saying "hi"',
    'Open Calendar then open chat with Mom then prepare WhatsApp message for Mom saying "hi"',
    'Open WhatsApp then prepare WhatsApp message for Mom saying "hi"',
    'Open WhatsApp then open chat with Mom then tap Search then prepare WhatsApp message for Mom saying "hi"',
    'Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "hi" then send it to Dad',
    'Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "hi" then send it then send it',
    'Open WhatsApp then send it',
    'Open WhatsApp then open chat with Mom then send it',
    'Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "hi" then prepare WhatsApp message for Mom saying "again"',
    'Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "hi and then pay"',
  ]) {
    const result=parseOwnerMultiToolRequest(cmd);
    if(cmd.includes('"hi and then pay"')) {
      assert.equal(result?.[2].input.exactText,'hi and then pay');
    }else assert.equal(result,null,cmd);
  }
});

test("recipient evidence is not a generic window-change or a model assertion",async()=>{
  const request='Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "hello"';
  const steps=parseOwnerMultiToolRequest(request);
  const check=(index,data)=>evidenceForOwnerStep(
    steps[index],{status:"success",data},index===2).safe;
  assert.equal(check(1,{verification:"observed_change",actionAccepted:true}),false);
  assert.equal(check(1,{verification:"verified_chat_header",
    recipientMatched:false,messageComposerVisible:true,submitted:false}),false);
  assert.equal(check(1,{verification:"verified_chat_header",
    recipientMatched:true,messageComposerVisible:true,submitted:false}),true);
  assert.equal(check(2,{verification:"exact_text_visible",
    recipientMatched:true,submitted:false,sent:false}),true);
  assert.equal(check(2,{verification:"exact_text_visible",
    recipientMatched:true,submitted:false,sent:true}),false);
  assert.equal(check(2,{verification:"exact_text_visible",
    recipientMatched:false,submitted:false,sent:false}),false);
  const planner=createOwnerSequentialPlanner(androidIntentPlanner);
  const stopped=await planner.next(request,[{
    call:steps[0],outcome:{status:"success",data:{
      intentDispatched:true,verification:"destination_package_observed"}}
  },{
    call:steps[1],outcome:{status:"success",data:{
      verification:"observed_change",actionAccepted:true}}
  }]);
  assert.equal(stopped.type,"clarify");
});

test("an owner-reviewed tap accepts same-window content evidence without a task claim",()=>{
  const call={callId:"tap-content",toolId:"screen.tap_observed",input:{label:"Open details"}};
  const checked=evidenceForOwnerStep(call,{status:"success",data:{
    verification:"content_change_observed",actionAccepted:true,
    businessOutcomeVerified:false
  }},true);
  assert.equal(checked.safe,true);
  assert.match(checked.reason,/screen change/i);
  assert.equal(evidenceForOwnerStep(call,{status:"success",data:{
    verification:"content_change_observed",actionAccepted:false
  }},true).safe,false);
});

test("Send evidence requires the exact recipient, exact text, and no delivery claim",()=>{
  const call={callId:"send-1",toolId:"screen.send_whatsapp",input:{
    recipientHint:"Mom",exactText:"I will call later"
  }};
  assert.equal(evidenceForOwnerStep(call,{status:"success",data:{
    verification:"sent_message_visible",recipientMatched:true,
    exactTextMatched:true,sent:true,deliveryReported:false
  }},true).safe,true);
  for(const data of [
    {verification:"sent_message_visible",recipientMatched:false,
      exactTextMatched:true,sent:true,deliveryReported:false},
    {verification:"sent_message_visible",recipientMatched:true,
      exactTextMatched:false,sent:true,deliveryReported:false},
    {verification:"sent_message_visible",recipientMatched:true,
      exactTextMatched:true,sent:false,deliveryReported:false},
    {verification:"sent_message_visible",recipientMatched:true,
      exactTextMatched:true,sent:true,deliveryReported:true},
    {verification:"android_accepted",recipientMatched:true,
      exactTextMatched:true,sent:true,deliveryReported:false}
  ]) assert.equal(evidenceForOwnerStep(call,{status:"success",data},true).safe,false);
});


test("multi-step native continuation requires independently observed app destination",async()=>{
  const request='Open WhatsApp then open chat with Mom then prepare WhatsApp message for Mom saying "Please call later"';
  const steps=parseOwnerMultiToolRequest(request);
  assert.equal(steps?.length,3);
  const planner=createOwnerSequentialPlanner(androidIntentPlanner);
  const claimed=steps[0];
  const unverified=await planner.next(request,[{
    call:claimed,outcome:{status:"success",data:{
      intentDispatched:true,packageId:"com.whatsapp",
      verification:"not_observed"}}
  }]);
  assert.equal(unverified.type,"clarify");
  assert.match(unverified.question,/App destination was not observed/i);
  const observed=await planner.next(request,[{
    call:claimed,outcome:{status:"success",data:{
      intentDispatched:true,packageId:"com.whatsapp",
      verification:"destination_package_observed"}}
  }]);
  assert.equal(observed.type,"tool");
  assert.equal(observed.call.toolId,"screen.open_whatsapp_chat");
  assert.deepEqual(observed.call.input,{recipientHint:"Mom"});
});

test("WhatsApp's full live Search label is treated as one recipient-search field",()=>{
  const label="Ask Meta AI or Search";
  const request=`Open WhatsApp then tap ${label} then type Amma into ${label} `+
    `then tap Amma then prepare a WhatsApp message for Amma saying `+
    `"Hi Mom i will call you later" then send it`;
  const steps=parseOwnerMultiToolRequest(request);
  assert.deepEqual(steps?.map(step=>step.toolId),[
    "device.open_app","screen.tap_label","screen.fill_text","screen.tap_label",
    "screen.compose_message","screen.send_message"
  ]);
  assert.deepEqual(steps?.[1].input,{label:"Search"});
  assert.deepEqual(steps?.[2].input,{targetLabel:"Search",exactText:"Amma"});
});

test("owner sequence recognizes native scroll-event and structural-change evidence",()=>{
  for(const direction of ["up","down","left","right"]){
    const call={callId:"scroll-step",toolId:"screen.scroll",input:{direction}};
    for(const verification of [
      "scroll_event_observed","structural_change_observed","observed_change"
    ]){
      const result=evidenceForOwnerStep(call,{status:"success",data:{
        actionAccepted:true,verification
      }},false);
      assert.equal(result.safe,true,`${direction}: ${verification}`);
    }
    const missing=evidenceForOwnerStep(call,{status:"success",data:{
      actionAccepted:true,verification:"not_observed"
    }},false);
    assert.equal(missing.safe,false,direction);
  }
});
