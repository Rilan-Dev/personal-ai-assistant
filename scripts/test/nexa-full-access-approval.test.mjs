import test from "node:test";
import assert from "node:assert/strict";
import {InMemoryActionJournal} from "../../packages/agent-core/src/journal.ts";
import {ToolRegistry,AgentHarness} from "../../packages/agent-core/src/runtime.ts";
import {withFullAccessApprovals} from "../../packages/agent-core/src/full-access.ts";

const sessionId="full-access-test-session";

function makeTool(id,capabilities,executed){
  return {
    id,description:id,risk:"high",capabilities,hasExternalSideEffect:true,
    validateInput:input=>input&&typeof input==="object"&&!Array.isArray(input),
    execute:async input=>{executed.push(input);return {status:"success",data:{accepted:true}};}
  };
}

function planner(toolId,input){
  return {next:async(_request,observations)=>observations.length
    ?{type:"done",response:"Completed"}
    :{type:"tool",call:{callId:"one",toolId,input}}};
}

async function run({toolId,capabilities=["device.interact"],active=true,
                    canAuthorize=async()=>true}){
  const executed=[],authorizationChecks=[],registry=new ToolRegistry();
  registry.register(makeTool(toolId,capabilities,executed));
  const nativeJournal=new InMemoryActionJournal();
  const journal=withFullAccessApprovals(nativeJournal,registry.describe(),async id=>{
    authorizationChecks.push(id);
    return canAuthorize(id);
  },active);
  const harness=new AgentHarness(registry,planner(toolId,{target:"Clock"}));
  const result=await harness.run({request:"Open Clock",sessionId,
    journal,grantedCapabilities:new Set(capabilities)});
  return {result,executed,authorizationChecks,nativeJournal};
}

test("full access executes a registered non-outbound tool without a review wait",async()=>{
  const {result,executed,authorizationChecks,nativeJournal}=await run({
    toolId:"device.open_app"
  });
  assert.equal(result.status,"completed");
  assert.deepEqual(executed,[{target:"Clock"}]);
  assert.deepEqual(authorizationChecks,["device.open_app"]);
  assert.equal(nativeJournal.state(sessionId,"one"),"settled");
});

test("full access still requires direct review for outbound communication and payment",async()=>{
  for(const [toolId,capabilities] of [
    ["screen.send_whatsapp",["device.interact"]],
    ["account.send_message",["messaging.send"]],
    ["phone.place_call",["calls.place"]],
    ["wallet.make_payment",["device.interact"]]
  ]){
    const {result,executed,authorizationChecks}=await run({toolId,capabilities});
    assert.equal(result.status,"awaiting_approval",toolId);
    assert.deepEqual(executed,[],toolId);
    assert.deepEqual(authorizationChecks,[],toolId);
  }
});

test("full access fails closed when native pause or unlock gate refuses authorization",async()=>{
  const {result,executed,authorizationChecks}=await run({
    toolId:"device.open_app",canAuthorize:async()=>false
  });
  assert.equal(result.status,"blocked");
  assert.deepEqual(executed,[]);
  assert.deepEqual(authorizationChecks,["device.open_app"]);
});

test("disabled full access preserves the ordinary approval wait",async()=>{
  const {result,executed,authorizationChecks}=await run({
    toolId:"device.open_app",active:false
  });
  assert.equal(result.status,"awaiting_approval");
  assert.deepEqual(executed,[]);
  assert.deepEqual(authorizationChecks,[]);
});

test("full access resumes an already approved exact action without replaying it",async()=>{
  const executed=[],registry=new ToolRegistry();
  const tool=makeTool("device.open_app",["device.interact"],executed);
  registry.register(tool);
  const nativeJournal=new InMemoryActionJournal();
  const harness=new AgentHarness(registry,planner(tool.id,{target:"Clock"}));
  const first=await harness.run({request:"Open Clock",sessionId,
    journal:withFullAccessApprovals(nativeJournal,registry.describe(),async()=>true,false),
    grantedCapabilities:new Set(["device.interact"])});
  assert.equal(first.status,"awaiting_approval");
  assert.equal(await nativeJournal.approveAction(
    sessionId,first.call.callId,first.fingerprint),true);
  const resumed=await harness.run({request:"Open Clock",sessionId,resume:true,
    journal:withFullAccessApprovals(nativeJournal,registry.describe(),async()=>true,true),
    grantedCapabilities:new Set(["device.interact"])});
  assert.equal(resumed.status,"completed");
  assert.deepEqual(executed,[{target:"Clock"}]);
  assert.equal(nativeJournal.state(sessionId,"one"),"settled");
});
