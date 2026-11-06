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
