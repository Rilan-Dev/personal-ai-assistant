import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizePlannerTaskDecision, createTaskAdmissionPlanner
} from "../src/task-admission.ts";
import {TaskPlanRejected} from "../../agent-contracts/src/task-plan.ts";

const now=1_800_000_000_000;
const tools=[
  {id:"device.battery_status",description:"Battery",risk:"low",
    capabilities:["device.observe"],hasExternalSideEffect:false},
  {id:"device.open_app",description:"Open app",risk:"medium",
    capabilities:["apps.launch"],hasExternalSideEffect:true},
  {id:"messaging.send",description:"Send",risk:"high",
    capabilities:["messaging.send"],hasExternalSideEffect:true}
];
const context=()=>({
  taskId:"task-owner-123456789",
  ownerRequestId:"owner-request-123456789",
  origin:"chat",policyVersion:"nexa-plan-v1",
  tools,nowMs:now
});
const choice=(toolId="device.battery_status",input={})=>({
  type:"tool",call:{callId:"provider-generated-id",toolId,input}
});
test("unifies deterministic, BYOK and remote tool proposals without execution",()=>{
  for(const source of ["android-intent","direct-byok","langgraph"]){
    const plan=normalizePlannerTaskDecision(choice(),context());
    assert.equal(plan.executable,false,source);
    assert.equal(plan.ownerRequestId,"owner-request-123456789");
    assert.equal(plan.steps[0].expectedEvidence,"native_reading");
    assert.equal(plan.steps[0].risk,"low");
    assert.equal(plan.steps[0].requiresApproval,false);
    assert.equal("callId" in plan.steps[0],false);
  }
});
test("denies model-selected unknown, harmful or unapproved medium-risk tools",()=>{
  for(const call of [
    choice("messaging.send",{text:"unsafe"}),
    choice("invented.tool"),
    choice("device.open_app",{app:"whatsapp"})
  ])assert.throws(()=>normalizePlannerTaskDecision(call,context()),TaskPlanRejected);
});
test("medium-risk app launch remains non-executable and requires review when explicitly allowed",()=>{
  const plan=normalizePlannerTaskDecision(choice("device.open_app",{app:"whatsapp"}),{
    ...context(),allowMediumRisk:true
  });
  assert.equal(plan.executable,false);
  assert.equal(plan.steps[0].requiresApproval,true);
  assert.equal(plan.steps[0].expectedEvidence,"foreground_package");
});
test("bad caller identity cannot be minted by provider decision",()=>{
  const p=context();p.ownerRequestId="not valid";
  assert.throws(()=>normalizePlannerTaskDecision(choice(),p),TaskPlanRejected);
});
test("disabled adapter preserves existing Android planner behavior and call identity",async()=>{
  const original={next:async()=>choice()};
  const wrapped=createTaskAdmissionPlanner(original,{enabled:false,context});
  assert.strictEqual(wrapped,original);
  assert.deepEqual(await wrapped.next("battery",[]),choice());
});
test("enabled proposal adapter persists before returning review-only response",async()=>{
  let writes=[];
  const original={next:async()=>choice()};
  const planner=createTaskAdmissionPlanner(original,{
    enabled:true,context,
    persistProposal:async plan=>{writes.push(plan);}
  });
  const response=await planner.next("battery",[]);
  assert.equal(response.type,"clarify");
  assert.match(response.question,/saved for your review/i);
  assert.equal(writes.length,1);
  assert.equal(writes[0].executable,false);
});
test("storage failure, missing persistence, or forged high-risk tool fail closed",async()=>{
  for(const opts of [
    {enabled:true,context},
    {enabled:true,context,persistProposal:async()=>{throw Error("down");}},
  ]){
    const planner=createTaskAdmissionPlanner({next:async()=>choice()},opts);
    const r=await planner.next("battery",[]);
    assert.equal(r.type,"clarify");
    assert.match(r.question,/not admitted|disabled/i);
  }
  const p=createTaskAdmissionPlanner({next:async()=>choice("messaging.send")},{
    enabled:true,context,persistProposal:async()=>{throw Error("unexpected call");}
  });
  const r=await p.next("send",[]);
  assert.match(r.question,/not admitted/i);
});
test("ordinary replies and clarifications remain unchanged",async()=>{
  for(const expected of [
    {type:"done",response:"hello"},
    {type:"clarify",question:"which app?"}
  ]){
    const p=createTaskAdmissionPlanner({next:async()=>expected},{
      enabled:true,context,persistProposal:async()=>{throw Error("must not persist");}
    });
    assert.deepEqual(await p.next("hi",[]),expected);
  }
});
test("abort before or after provider response has no proposal writes",async()=>{
  let count=0;
  const controller=new AbortController();
  controller.abort();
  const p=createTaskAdmissionPlanner({next:async()=>{count++;return choice();}},{
    enabled:true,context,persistProposal:async()=>{count+=100;}
  });
  const r=await p.next("battery",[],controller.signal);
  assert.equal(r.type,"clarify");
  assert.equal(count,0);
});
