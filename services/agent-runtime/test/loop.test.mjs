import assert from "node:assert/strict";
import { test } from "node:test";
import { AgentHarness, ToolRegistry, createRemotePlanner, validateAgentEndpoint } from "../../../packages/agent-core/src/index.ts";
import { LangGraphStepPlanner } from "../src/graph.ts";
import { createPlanningServer } from "../src/server.ts";

const token = "unit-testing-only-" + "X".repeat(40);
async function withServer(model, fn) {
  const server = createPlanningServer({ token, planner: new LangGraphStepPlanner(model) });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  try { await fn("http://127.0.0.1:" + server.address().port); }
  finally { await new Promise(resolve => server.close(resolve)); }
}
const registry = (execute) => {
  const tools = new ToolRegistry();
  tools.register({
    id:"device.open_app",
    description:"Launch allowlisted Android application",
    capabilities:["apps.launch"],
    risk:"low",
    hasExternalSideEffect:false,
    validateInput:v=>!!v && typeof v==="object" && v.app==="whatsapp" && Object.keys(v).length===1,
    execute
  });
  return tools;
};
test("real LangGraph graph plans and existing mobile AgentHarness executes/observes in a bounded loop", async () => {
  let modelCalls = 0, toolCalls = 0;
  const model = {async decide(ctx) {
    modelCalls++;
    if (!ctx.previousActions.length) return { type:"tool",toolId:"device.open_app",input:{app:"whatsapp"} };
    assert.equal(ctx.previousActions[0].outcome.data.verification, "not_observed");
    return {type:"done",response:"Yes, WhatsApp is open and message sent."};
  }};
  await withServer(model, async url => {
    const planner=createRemotePlanner({baseUrl:url,token,availableToolIds:["device.open_app"]});
    const harness=new AgentHarness(registry(async () => {
      toolCalls++;
      return {status:"success",data:{intentDispatched:true,verification:"not_observed",secret:"never-sent"}};
    }),planner);
    const result=await harness.run({
      request:"Open WhatsApp",sessionId:"device-1",
      grantedCapabilities:new Set(["apps.launch"]),maxSteps:4
    });
    assert.equal(result.status,"completed");
    assert.match(result.response,/not been verified/);
    assert.equal(modelCalls,2);
    assert.equal(toolCalls,1);
  });
});
test("capability denial blocks execution even with a model tool suggestion", async () => {
  let count=0;
  await withServer({decide:async()=>({type:"tool",toolId:"device.open_app",input:{app:"whatsapp"}})}, async url=>{
    const harness=new AgentHarness(registry(async()=>{count++;return {status:"success",data:{}}}),createRemotePlanner({baseUrl:url,token,availableToolIds:["device.open_app"]}));
    const r=await harness.run({request:"Open WhatsApp",sessionId:"device-2",grantedCapabilities:new Set()});
    assert.equal(r.status,"blocked");
    assert.equal(count,0);
  });
});
test("client refuses unsafe remote URL and never implicitly enables the service",()=>{
  for(const url of ["http://example.com","http://192.168.0.100:8741","https://user:pass@example.com","https://example.com/v1/plan"])
    assert.throws(()=>validateAgentEndpoint(url));
  assert.equal(validateAgentEndpoint("https://example.com/"),"https://example.com");
  assert.equal(validateAgentEndpoint("http://127.0.0.1:8123"),"http://127.0.0.1:8123");
});
test("client rejects model responses that attempt unknown tools", async()=>{
  const original=globalThis.fetch;
  try{
    globalThis.fetch=async()=>new Response(JSON.stringify({decision:{type:"tool",call:{callId:"x",toolId:"device.shell",input:{cmd:"rm -rf /"}}}}),{status:200});
    const planner=createRemotePlanner({baseUrl:"https://example.com",token,availableToolIds:["device.open_app"]});
    await assert.rejects(planner.next("list files",[]),/Remote planner request failed/);
  }finally{globalThis.fetch=original}
});
test("compromised planning service cannot translate unrelated user speech into native launch", async () => {
  const original = globalThis.fetch;
  let observed = 0;
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({
      decision:{type:"tool",call:{callId:"step-1",toolId:"device.open_app",input:{app:"whatsapp"}}}
    }),{status:200});
    const planner = createRemotePlanner({
      baseUrl:"https://example.com",token,availableToolIds:["device.open_app"]
    });
    const harness=new AgentHarness(registry(async()=>{observed++;return {status:"success",data:{intentDispatched:true}}}),planner);
    const result=await harness.run({request:"Where am I?",sessionId:"owner",grantedCapabilities:new Set(["apps.launch"])});
    assert.equal(result.status,"completed");
    assert.match(result.response,/outside this request/);
    assert.equal(observed,0);
  }finally{globalThis.fetch=original}
});
