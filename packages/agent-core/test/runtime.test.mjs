import test from "node:test";
import assert from "node:assert/strict";
import { ToolRegistry, AgentHarness, approvalFingerprint } from "../src/runtime.ts";
import { InMemoryActionJournal } from "../src/journal.ts";
import { createDemoTools, demoPlanner } from "../src/demo.ts";
const tool = (overrides = {}) => ({ id: "demo.tool", description: "test", risk: "low", capabilities: [], hasExternalSideEffect: false, validateInput: v => !!v && typeof v === "object", execute: async () => ({ status: "success", data: { ok: true } }), ...overrides });
const build = (aTool, planner) => {const r=new ToolRegistry();r.register(aTool);return new AgentHarness(r,planner)};
const run = (a, overrides={}) => a.run({request:"test",sessionId:"test",grantedCapabilities:new Set(),...overrides});
const plan = (call={callId:"1",toolId:"demo.tool",input:{}}) => ({next:async (_,observations)=>observations.length?{type:"done",response:"Done"}:{type:"tool",call}});
test("registry rejects duplicate ids",()=>{const r=new ToolRegistry();r.register(tool());assert.throws(()=>r.register(tool()),/already registered/)});
test("registry rejects invalid ids",()=>assert.throws(()=>new ToolRegistry().register(tool({id:"../bad"})),/Invalid/));
test("simple bounded tool finishes",async()=>{const result=await run(build(tool(),plan()));assert.equal(result.status,"completed");assert.equal(result.trace.filter(e=>e.type==="tool_result").length,1)});
test("unknown tool blocked",async()=>{const result=await run(build(tool(),plan({callId:"1",toolId:"unknown",input:{}})));assert.equal(result.status,"blocked")});
test("invalid input blocked",async()=>{const result=await run(build(tool({validateInput:()=>false}),plan()));assert.equal(result.status,"blocked")});
test("capability denial prevents execution",async()=>{let executed=false;const result=await run(build(tool({capabilities:["contacts.read"],execute:async()=>{executed=true;return {status:"success",data:1}}}),plan()));assert.equal(result.status,"blocked");assert.equal(executed,false)});
test("external side effects always require approval",async()=>{let executed=false;const result=await run(build(tool({hasExternalSideEffect:true,execute:async()=>{executed=true;return {status:"success",data:1}}}),plan()));assert.equal(result.status,"awaiting_approval");assert.equal(executed,false)});
test("resuming an approved action executes its saved proposal without asking the planner again",async()=>{
  const journal=new InMemoryActionJournal();
  const call={callId:"direct-1",toolId:"device.tap",input:{label:"Alarms"}};
  let plannerCalls=0;
  const executed=[];
  const planner={next:async(_request,observations)=>{
    if(observations.length)return {type:"done",response:"The Alarms tab is open."};
    plannerCalls+=1;
    return plannerCalls===1?{type:"tool",call}:{type:"clarify",question:"Provider changed its proposal."};
  }};
  const harness=build(tool({id:"device.tap",risk:"medium",capabilities:["device.interact"],
    hasExternalSideEffect:true,
    validateInput:value=>!!value&&typeof value==="object"&&value.label==="Alarms",
    execute:async input=>{executed.push(input.label);return {status:"success",data:{verification:"observed_change"}};}
  }),planner);
  const options={request:"Tap on the Alarms",sessionId:"saved-tap",grantedCapabilities:new Set(["device.interact"]),journal};
  const offered=await harness.run(options);
  assert.equal(offered.status,"awaiting_approval");
  assert.equal(executed.length,0);
  assert.equal(await journal.approveAction("saved-tap",call.callId,offered.fingerprint),true);

  const resumed=await harness.run({...options,resume:true});
  assert.equal(resumed.status,"completed");
  assert.equal(plannerCalls,1);
  assert.deepEqual(executed,["Alarms"]);
  assert.equal(journal.state("saved-tap",call.callId),"settled");
});
test("legacy bare fingerprint cannot authorize a side effect",async()=>{const call={callId:"1",toolId:"demo.tool",input:{name:"ROM"}};let sent=false;const result=await run(build(tool({hasExternalSideEffect:true,execute:async()=>{sent=true;return {status:"success",data:true}}}),plan(call)),{approvedFingerprints:new Set([approvalFingerprint(call,"test")])});assert.equal(result.status,"awaiting_approval");assert.equal(sent,false)});
test("approval cannot be reused with altered input",async()=>{const first={callId:"1",toolId:"demo.tool",input:{name:"ROM"}};const second={callId:"1",toolId:"demo.tool",input:{name:"Other"}};const result=await run(build(tool({hasExternalSideEffect:true}),plan(second)),{approvedFingerprints:new Set([approvalFingerprint(first,"test")])});assert.equal(result.status,"awaiting_approval")});
test("approval canonicalizes input property order",()=>assert.equal(approvalFingerprint({callId:"1",toolId:"t",input:{b:1,a:2}},"test"), approvalFingerprint({callId:"1",toolId:"t",input:{a:2,b:1}},"test")));
test("cancellation stops before plan",async()=>{const c=new AbortController();c.abort();assert.equal((await run(build(tool(),plan()),{signal:c.signal})).status,"cancelled")});
test("tool failures reported",async()=>{const result=await run(build(tool({execute:async()=>{throw Error("secret")}}),plan()));assert.equal(result.status,"failed");assert.ok(!JSON.stringify(result).includes("secret"))});
test("step budget terminates loop",async()=>{let n=0;const endless={next:async()=>({type:"tool",call:{callId:String(++n),toolId:"demo.tool",input:{}}})};const result=await run(build(tool(),endless),{maxSteps:2});assert.equal(result.status,"failed");assert.match(result.reason,/budget/)});
test("demo executes no real device actions",async()=>{const registry=new ToolRegistry();for(const t of createDemoTools())registry.register(t);const result=await run(new AgentHarness(registry,demoPlanner),{request:"open camera",grantedCapabilities:new Set(["apps.launch"])});assert.equal(result.status,"completed");assert.match(result.response,/simulated/)});

test("approval cannot cross a session",async()=>{const call={callId:"1",toolId:"demo.tool",input:{name:"ROM"}};const result=await run(build(tool({hasExternalSideEffect:true}),plan(call)),{sessionId:"different",approvedFingerprints:new Set([approvalFingerprint(call,"test")])});assert.equal(result.status,"awaiting_approval")});
test("duplicate tool calls blocked",async()=>{let n=0;const dup={next:async()=>({type:"tool",call:{callId:"dup",toolId:"demo.tool",input:{}}})};const result=await run(build(tool({execute:async()=>{n++;return {status:"success",data:true}}}),dup));assert.equal(result.status,"blocked");assert.equal(n,1)});
test("tool blocked result stops execution",async()=>{const result=await run(build(tool({execute:async()=>({status:"blocked",reason:"Not available"})}),plan()));assert.equal(result.status,"blocked")});
test("planner failures do not expose exception messages",async()=>{const result=await run(build(tool(),{next:async()=>{throw Error("private-data")}}));assert.equal(result.status,"failed");assert.ok(!JSON.stringify(result).includes("private-data"))});
test("planner diagnostics expose only allowlisted error codes",async()=>{
  const seen=[];
  const result=await run(build(tool(),{next:async()=>{throw Error("PROVIDER_HTTP_400")}}),{
    onPlanningError:code=>seen.push(code)
  });
  assert.equal(result.reason,"Planning failed");
  assert.deepEqual(seen,["PROVIDER_HTTP_400"]);
  assert.ok(!JSON.stringify(result).includes("PROVIDER_HTTP_400"));
});
test("planner diagnostics suppress arbitrary exception text",async()=>{
  const seen=[];
  await run(build(tool(),{next:async()=>{throw Error("private-data")}}),{
    onPlanningError:code=>seen.push(code)
  });
  assert.deepEqual(seen,[]);
});
test("tool diagnostics expose only known native error codes",async()=>{
  const seen=[];
  const error=Object.assign(new Error("private screen details"),{
    code:"SCROLL_DISPATCH_REJECTED"
  });
  const result=await run(build(tool({execute:async()=>{throw error}}),plan()),{
    onToolError:code=>seen.push(code)
  });
  assert.equal(result.status,"failed");
  assert.deepEqual(seen,["SCROLL_DISPATCH_REJECTED"]);
  assert.ok(!JSON.stringify(result).includes("private screen details"));
});
test("reviewed screen and messaging diagnostics expose safe failure categories",async()=>{
  for (const code of ["TEXT_FIELD_UNVERIFIED","TEXT_OUTCOME_UNCERTAIN",
    "TEXT_PARENT_EVIDENCE_FAILED","TEXT_REVIEW_INVALID","APP_NOT_UNIQUE_OR_INSTALLED",
    "APP_LAUNCH_OUTCOME_UNKNOWN","APP_LAUNCH_REFUSED","INVALID_SEARCH",
    "CHROME_NOT_VERIFIED","NOTIFICATION_SHADE_FAILED","NO_CAMERA","CAMERA_DENIED",
    "CAMERA_OUTCOME_UNKNOWN","SCREEN_OBSERVATION_UNAVAILABLE","CHAT_STEP_NOT_CLAIMED",
    "CHAT_ROW_UNVERIFIED","CHAT_HEADER_NOT_VERIFIED","CHAT_EVIDENCE_NOT_COMMITTED",
    "CHAT_VERIFICATION_UNCERTAIN","CHAT_NAVIGATION_UNCERTAIN","MESSAGE_STEP_NOT_CLAIMED",
    "MESSAGE_RECIPIENT_UNVERIFIED","MESSAGE_COMPOSER_UNVERIFIED","MESSAGE_DRAFT_UNCERTAIN",
    "MESSAGE_EVIDENCE_NOT_COMMITTED","MESSAGE_SEND_NOT_CLAIMED","MESSAGE_APP_NOT_SUPPORTED",
    "MESSAGE_SEND_UNCERTAIN"]) {
    const seen=[];
    const error=Object.assign(new Error("private screen details"),{code});
    const result=await run(build(tool({execute:async()=>{throw error}}),plan()),{
      onToolError:diagnostic=>seen.push(diagnostic)
    });
    assert.equal(result.status,"failed");
    assert.deepEqual(seen,[code]);
    assert.ok(!JSON.stringify(result).includes("private screen details"));
  }
});
test("tool diagnostics suppress arbitrary native error codes and messages",async()=>{
  const seen=[];
  const error=Object.assign(new Error("private-data"),{code:"PRIVATE_DATA"});
  await run(build(tool({execute:async()=>{throw error}}),plan()),{
    onToolError:code=>seen.push(code)
  });
  assert.deepEqual(seen,[]);
});
test("high risk action requires approval",async()=>{const result=await run(build(tool({risk:"high"}),plan()));assert.equal(result.status,"awaiting_approval")});
test("large accessibility results stay available to the caller but checkpoints keep only the requested projection",async()=>{
  const journal=new InMemoryActionJournal();
  const call={callId:"tree-call",toolId:"screen.accessibility_tree",input:{}};
  const largeTree={packageName:"com.transsion.XOSLauncher",nodeCount:512,
    nodes:Array.from({length:512},(_,index)=>({id:String(index),text:"screen-label-".repeat(12)}))};
  const registry=new ToolRegistry();
  registry.register(tool({id:call.toolId,risk:"high",hasExternalSideEffect:true,
    execute:async()=>({status:"success",data:largeTree})}));
  const harness=new AgentHarness(registry,plan(call));
  const options={request:"read current accessibility tree",sessionId:"tree-large-result",
    grantedCapabilities:new Set(),journal,
    checkpointObservations:observations=>observations.map(observation=>({
      ...observation,
      outcome:observation.call.toolId===call.toolId&&observation.outcome.status==="success"
        ?{status:"success",data:{packageName:largeTree.packageName,nodeCount:largeTree.nodeCount,
          checkpointResultRedacted:true}}
        :observation.outcome
    }))};
  const pending=await harness.run(options);
  assert.equal(pending.status,"awaiting_approval");
  assert.equal(await journal.approveAction("tree-large-result",call.callId,pending.fingerprint),true);
  const result=await harness.run({...options,resume:true});
  assert.equal(result.status,"completed");
  const toolResult=result.trace.find(event=>event.type==="tool_result");
  assert.equal(toolResult.outcome.data.nodes.length,512);
  assert.equal(toolResult.outcome.data.nodes[0].text,"screen-label-".repeat(12));
  const checkpoint=await journal.loadSession("tree-large-result");
  assert.deepEqual(checkpoint.observations[0].outcome.data,{
    packageName:"com.transsion.XOSLauncher",nodeCount:512,checkpointResultRedacted:true
  });
  assert.ok(JSON.stringify(checkpoint).length<64*1024);
});
