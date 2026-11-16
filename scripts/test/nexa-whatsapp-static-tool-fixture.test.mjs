import test from "node:test";
import assert from "node:assert/strict";
import {existsSync, readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {
  AgentHarness,
  InMemoryActionJournal,
  ToolRegistry,
  androidIntentPlanner,
  createDirectProviderPlanner,
  createOwnerSequentialPlanner,
  parseOwnerMultiToolRequest
} from "../../packages/agent-core/src/index.ts";
import {PROVIDERS} from "../../packages/agent-core/src/direct-provider.ts";

const fixturePath=fileURLToPath(new URL("./fixtures/nexa-whatsapp-draft.json",import.meta.url));
const fakeSettings={version:1,provider:"sarvam",model:"sarvam-105b",apiKey:"fixture-only-key-not-real"};

function loadFixture(){
  assert.ok(existsSync(fixturePath),"static Nexa WhatsApp fixture is missing");
  return JSON.parse(readFileSync(fixturePath,"utf8"));
}

function registerFixtureTools(registry,steps,executions){
  for(const step of steps){
    registry.register({
      id:step.toolId,
      description:"Static fake for the exact Nexa Android tool; never controls a device",
      capabilities:step.toolId==="device.open_app"?["apps.launch"]:["device.interact"],
      risk:step.toolId==="screen.send_whatsapp"?"high":"medium",
      hasExternalSideEffect:true,
      validateInput:input=>JSON.stringify(input)===JSON.stringify(step.input),
      execute:async(input,context)=>{
        executions.push({toolId:step.toolId,input:{...input}});
        assert.equal(context.sessionId.length>0,true);
        assert.equal(context.callId.length>0,true);
        assert.equal(context.approvalFingerprint.length>=10,true);
        return {status:"success",data:step.evidence};
      }
    });
  }
}

async function runWithOneApprovalPerStep(harness,options,journal,maximumApprovals){
  let result=await harness.run(options);
  let approvals=0;
  while(result.status==="awaiting_approval"&&approvals<maximumApprovals){
    assert.ok(await journal.approveAction(options.sessionId,result.call.callId,result.fingerprint));
    approvals++;
    result=await harness.run({...options,resume:true});
  }
  return {result,approvals};
}

test("Sarvam static response is a native tool call with no assistant prose",async()=>{
  const fixture=loadFixture();
  const sample=fixture.sarvamScreenToolCall;
  assert.equal(sample.response.choices[0].message.content,null);
  assert.equal(sample.response.choices[0].message.tool_calls.length,1);

  let providerRequests=0;
  const planner=createDirectProviderPlanner({
    settings:fakeSettings,
    availableToolIds:sample.availableToolIds,
    fetchImpl:async(url,options)=>{
      providerRequests++;
      assert.equal(url,PROVIDERS.sarvam.endpoint);
      const body=JSON.parse(options.body);
      assert.equal(body.messages[1].content,sample.request);
      assert.equal(body.tool_choice,"auto");
      assert.ok(body.tools.some(tool=>tool.function.name==="screen_scroll"));
      return new Response(JSON.stringify(sample.response),{
        status:200,headers:{"content-type":"application/json"}
      });
    }
  });
  const decision=await planner.next(sample.request,[]);
  assert.equal(providerRequests,1);
  assert.equal(decision.type,"tool");
  assert.deepEqual(decision.call,{callId:"direct-1",...sample.expectedCall});
});

test("WhatsApp fixture runs the exact Android draft tools and reports per-step evidence",async()=>{
  const fixture=loadFixture();
  const scenario=fixture.whatsappDraft;
  const calls=parseOwnerMultiToolRequest(scenario.request);
  assert.deepEqual(calls,scenario.expectedToolCalls);

  const registry=new ToolRegistry();
  const executions=[];
  registerFixtureTools(registry,scenario.steps,executions);
  assert.equal(registry.get(scenario.unsupportedSend.toolId)===undefined,
    !scenario.unsupportedSend.expectedToolAvailable);

  const journal=new InMemoryActionJournal();
  const events=[];
  const harness=new AgentHarness(
    registry,createOwnerSequentialPlanner(androidIntentPlanner)
  );
  const options={
    request:scenario.request,
    sessionId:"static-whatsapp-fixture",
    journal,
    grantedCapabilities:new Set(["apps.launch","device.interact"]),
    maxSteps:scenario.steps.length+1,
    onEvent:event=>events.push(event)
  };
  const {result,approvals}=await runWithOneApprovalPerStep(
    harness,options,journal,scenario.steps.length
  );

  assert.equal(result.status,"completed");
  assert.equal(approvals,scenario.steps.length);
  assert.deepEqual(executions,scenario.steps.map(({toolId,input})=>({toolId,input})));
  assert.deepEqual(events.filter(event=>event.type==="executing")
    .map(event=>event.call.toolId),scenario.steps.map(step=>step.toolId));
  assert.deepEqual(events.filter(event=>event.type==="tool_result")
    .map(event=>event.outcome.data),scenario.steps.map(step=>step.evidence));
  assert.match(result.response,/WhatsApp shows the exact requested message|delivery status was not verified/i);
  assert.equal(scenario.steps.at(-1).evidence.sent,true);
  assert.equal(scenario.steps.at(-1).evidence.deliveryReported,false);
});

test("WhatsApp fixture rejects Send when there is no approved matching draft",async()=>{
  const fixture=loadFixture();
  const scenario=fixture.whatsappDraft;
  assert.equal(parseOwnerMultiToolRequest(scenario.unsupportedSend.request),null);

  const registry=new ToolRegistry();
  const executions=[];
  registerFixtureTools(registry,scenario.steps,executions);
  const harness=new AgentHarness(
    registry,createOwnerSequentialPlanner(androidIntentPlanner)
  );
  const result=await harness.run({
    request:scenario.unsupportedSend.request,
    sessionId:"static-whatsapp-send-rejected",
    grantedCapabilities:new Set(["apps.launch","device.interact"]),
    maxSteps:scenario.steps.length+1
  });
  assert.equal(result.status,"completed");
  assert.match(result.response,/messaging|supported|clarification/i);
  assert.deepEqual(executions,[]);
  assert.equal(registry.get(scenario.unsupportedSend.toolId)===undefined,
    !scenario.unsupportedSend.expectedToolAvailable);
});
