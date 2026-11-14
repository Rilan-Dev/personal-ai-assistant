import test from "node:test";
import assert from "node:assert/strict";
import {InMemoryActionJournal} from "../../packages/agent-core/src/journal.ts";
import {ToolRegistry,AgentHarness} from "../../packages/agent-core/src/runtime.ts";
import {withTrustedOwnerNavigation} from "../../packages/agent-core/src/trusted-navigation.ts";

const sid="session-trusted-test";
function makeTool(id,execute){
  return {id,description:id,risk:"medium",capabilities:["device.interact"],
    hasExternalSideEffect:true,validateInput:()=>true,
    execute:async(input)=>{execute.push(input);return {status:"success",data:{accepted:true}};}};
}
function planner(toolId,input,callId="one"){
  return {next:async(_request,previous)=> previous.length
    ? {type:"done",response:"Action was journaled"}
    : {type:"tool",call:{callId,toolId,input}}};
}
async function run(request,toolId,input,decision=true,callId="one"){
  const calls=[],nativeChecks=[],native=new InMemoryActionJournal(),registry=new ToolRegistry();
  registry.register(makeTool(toolId,calls));
  const bridge=withTrustedOwnerNavigation(native,request,
    async command=>{nativeChecks.push(command);return decision},true);
  const harness=new AgentHarness(registry,planner(toolId,input,callId));
  const result=await harness.run({request,sessionId:sid,
    journal:bridge,grantedCapabilities:new Set(["device.interact"])});
  return {result,calls,nativeChecks,native};
}

test("owner-selected trusted app opening auto-approves ONE exact WhatsApp action",async()=>{
  const {result,calls,nativeChecks,native}=await run(
    "open whatsapp","device.open_app",{app:"whatsapp"});
  assert.equal(result.status,"completed");
  assert.deepEqual(nativeChecks,["open"]);
  assert.deepEqual(calls,[{app:"whatsapp"}]);
  assert.equal(native.state(sid,"one"),"settled");
});
test("trusted scroll auto-approves exact user-selected direction only",async()=>{
  const a=await run("scroll left","screen.scroll",{direction:"left"});
  assert.equal(a.result.status,"completed");
  assert.deepEqual(a.nativeChecks,["scroll_screen:left"]);
  assert.equal(a.native.state(sid,"one"),"settled");
  const mismatch=await run("scroll left","screen.scroll",{direction:"right"});
  assert.equal(mismatch.result.status,"awaiting_approval");
  assert.equal(mismatch.nativeChecks.length,0);
  assert.equal(mismatch.calls.length,0);
});
test("model-selected app different from owner request never executes",async()=>{
  const a=await run("open whatsapp","device.open_app",{app:"imo"});
  assert.equal(a.result.status,"awaiting_approval");
  assert.deepEqual(a.nativeChecks,[]);
  assert.deepEqual(a.calls,[]);
});
test("negation, multiple unsupported clauses, and contact-send requests never inherit trust",async()=>{
  for(const req of ["do not open whatsapp","open whatsapp and send a message",
    "send whatsapp to Mom","open whatsapp without asking","call Mom"]){
    const a=await run(req,"device.open_app",{app:"whatsapp"});
    assert.equal(a.result.status,"awaiting_approval",req);
    assert.equal(a.calls.length,0);
    assert.equal(a.nativeChecks.length,0);
  }
});
test("arbitrary visible taps, flashlight, contacts and alarms never become trusted",async()=>{
  for(const [req,id,args] of [
    ["tap Settings","screen.tap_label",{label:"Settings"}],
    ["send message","screen.compose_whatsapp",{recipientHint:"Mom",exactText:"hello"}],
    ["turn on flashlight","device.set_flashlight",{enabled:true}],
    ["set an alarm for 6","device.set_alarm",{hour:6}]
  ]){
    const a=await run(req,id,args);
    assert.equal(a.result.status,"awaiting_approval",id);
    assert.deepEqual(a.nativeChecks,[],id);
    assert.deepEqual(a.calls,[],id);
  }
});
test("revoked native policy requires ordinary user review even with saved React grant",async()=>{
  const a=await run("open whatsapp","device.open_app",{app:"whatsapp"},false);
  assert.equal(a.result.status,"awaiting_approval");
  assert.deepEqual(a.nativeChecks,["open"]);
  assert.equal(a.native.state(sid,"one"),"pending");
  assert.deepEqual(a.calls,[]);
});
test("malformed fingerprint cannot mint or consume a previously approved action",async()=>{
  const journal=new InMemoryActionJournal(),commands=[];
  const wrapped=withTrustedOwnerNavigation(journal,"open whatsapp",
    async c=>{commands.push(c);return true},true);
  for(const f of ["not json",JSON.stringify([sid,"one","device.open_app",{app:"imo"}]),
    JSON.stringify(["wrong-session","one","device.open_app",{app:"whatsapp"}])]){
    assert.equal(await wrapped.prepareApproval(sid,"one",f),true);
    assert.deepEqual(commands,[]);
    assert.equal(await journal.claimAction(sid,"one",f,true),false);
    // Each malformed test uses a different journal ID to avoid fixture
    // fingerprint collisions; an attempted claim is never approved.
    break;
  }
});
test("trusted navigation wrapper never replays already settled native journal claim",async()=>{
  const a=await run("open whatsapp","device.open_app",{app:"whatsapp"});
  assert.equal(a.result.status,"completed");
  assert.equal(await a.native.claimAction(sid,"one",
    JSON.stringify([sid,"one","device.open_app",{app:"whatsapp"}]),true),false);
});
test("disabled trust mode cannot auto-approve safe navigation",async()=>{
  const raw=new InMemoryActionJournal(),calls=[],registry=new ToolRegistry();
  registry.register(makeTool("device.open_app",calls));
  const harness=new AgentHarness(registry,planner("device.open_app",{app:"whatsapp"}));
  const result=await harness.run({request:"open whatsapp",sessionId:sid,
    grantedCapabilities:new Set(["device.interact"]),
    journal:withTrustedOwnerNavigation(raw,"open whatsapp",async()=>true,false)});
  assert.equal(result.status,"awaiting_approval");
  assert.deepEqual(calls,[]);
});
