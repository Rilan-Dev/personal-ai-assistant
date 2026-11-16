import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {
  AgentHarness,
  InMemoryActionJournal,
  ToolRegistry,
  createOwnerSequentialPlanner
} from "../../packages/agent-core/src/index.ts";
import {createReviewedMessageTools} from "../../apps/mobile/src/native/androidMessageTools.ts";

const fixturePath=fileURLToPath(new URL("./fixtures/nexa-multimessenger.json",import.meta.url));
const fixture=JSON.parse(readFileSync(fixturePath,"utf8"));

function expectedSteps({id,packageName}){
  return [
    {toolId:"device.open_app",input:{app:id},evidence:{
      intentDispatched:true,packageId:packageName,verification:"destination_package_observed"
    }},
    {toolId:"screen.tap_label",input:{label:fixture.searchField},evidence:{
      actionAccepted:true,verification:"observed_change"
    }},
    {toolId:"screen.fill_text",input:{targetLabel:fixture.searchField,exactText:fixture.contact},evidence:{
      submitted:false,verification:"exact_text_visible"
    }},
    {toolId:"screen.tap_label",input:{label:fixture.contact},evidence:{
      actionAccepted:true,verification:"observed_change"
    }},
    {toolId:"screen.compose_message",input:{appId:id,recipientHint:fixture.contact,
      exactText:fixture.message},evidence:{
      appId:id,verification:"exact_text_visible",recipientMatched:true,submitted:false,sent:false
    }},
    {toolId:"screen.send_message",input:{appId:id,recipientHint:fixture.contact,
      exactText:fixture.message},evidence:{
      appId:id,verification:"sent_message_visible",recipientMatched:true,exactTextMatched:true,
      sent:true,deliveryReported:false
    }}
  ];
}

function registerStaticTools(registry,steps,executed){
  const record=(toolId,input,context)=>{
    const index=executed.length;
    const step=steps[index];
    assert.equal(step.toolId,toolId);
    assert.deepEqual(input,step.input);
    if(context){
      assert.equal(context.sessionId.length>0,true);
      assert.equal(context.callId.length>0,true);
      assert.equal(context.approvalFingerprint.length>=10,true);
    }
    executed.push({toolId,input:{...input}});
    return step.evidence;
  };
  const bridge={
    async performReviewedMessageDraft(appId,recipientHint,exactText,sessionId,callId,fingerprint){
      return record("screen.compose_message",{appId,recipientHint,exactText},
        {sessionId,callId,approvalFingerprint:fingerprint});
    },
    async performReviewedMessageSend(appId,recipientHint,exactText,sessionId,callId,fingerprint){
      return record("screen.send_message",{appId,recipientHint,exactText},
        {sessionId,callId,approvalFingerprint:fingerprint});
    }
  };
  for(const tool of createReviewedMessageTools(()=>bridge))registry.register(tool);
  const ids=[...new Set(steps.map(step=>step.toolId))]
    .filter(id=>!['screen.compose_message','screen.send_message'].includes(id));
  for(const toolId of ids){
    registry.register({
      id:toolId,
      description:"Static fake for an existing Nexa Android tool; never controls a device",
      capabilities:toolId==="device.open_app"?["apps.launch"]:["device.interact"],
      risk:toolId==="screen.send_message"?"high":"medium",
      hasExternalSideEffect:true,
      validateInput:input=>steps.some(step=>step.toolId===toolId&&
        JSON.stringify(step.input)===JSON.stringify(input)),
      execute:async(input,context)=>({status:"success",
        data:record(toolId,input,context)})
    });
  }
}

test("multi-app static fixture is tool-call-only and never uses provider prose",()=>{
  const message=fixture.providerResponse.choices[0].message;
  assert.equal(message.content,null);
  assert.equal(message.tool_calls.length,1);
  assert.equal(message.tool_calls[0].function.name,"screen_scroll");
  assert.deepEqual(JSON.parse(message.tool_calls[0].function.arguments),{direction:"left"});
  assert.deepEqual(fixture.apps.map(app=>app.id),["whatsapp","imo","botim"]);
  assert.equal(fixture.contact,"Nexa QA Contact");
  assert.equal(fixture.message,"Nexa QA test message.");
});

test("static AgentHarness tasks use the production no-LLM owner planner for all three apps",async()=>{
  const allSteps=fixture.apps.flatMap(expectedSteps);
  const registry=new ToolRegistry();
  const executed=[];
  registerStaticTools(registry,allSteps,executed);
  const events=[];
  let providerFallbackCalls=0;
  let approvals=0;
  for(const app of fixture.apps){
    const steps=expectedSteps(app);
    const sessionId=`static-multimessenger-${app.id}`;
    const journal=new InMemoryActionJournal();
    const fallback={async next(){
      providerFallbackCalls++;
      return {type:"clarify",question:"Static fixture must not call an LLM"};
    }};
    const harness=new AgentHarness(registry,createOwnerSequentialPlanner(fallback));
    const options={request:app.ownerRequest,sessionId,journal,
      grantedCapabilities:new Set(["apps.launch","device.interact"]),
      maxSteps:steps.length+1,onEvent:event=>events.push(event)};

    let result=await harness.run(options);
    let appApprovals=0;
    while(result.status==="awaiting_approval"&&appApprovals<steps.length){
      assert.ok(await journal.approveAction(sessionId,result.call.callId,result.fingerprint));
      appApprovals++;
      result=await harness.run({...options,resume:true});
    }
    assert.equal(result.status,"completed",`${app.id}: ${result.status}`);
    assert.equal(appApprovals,steps.length);
    approvals+=appApprovals;
  }

  assert.equal(providerFallbackCalls,0,"static exact requests must not call an LLM/provider");
  assert.equal(approvals,allSteps.length);
  assert.deepEqual(executed,allSteps.map(({toolId,input})=>({toolId,input})));
  assert.deepEqual(events.filter(event=>event.type==="executing")
    .map(event=>event.call.toolId),fixture.apps.flatMap(()=>fixture.expectedActionsPerApp));
  assert.deepEqual(events.filter(event=>event.type==="tool_result")
    .map(event=>event.outcome.data),allSteps.map(step=>step.evidence));
  assert.equal(events.filter(event=>event.type==="tool_result"&&
    event.outcome.data?.verification==="sent_message_visible").length,3);
  assert.equal(allSteps.filter(step=>step.toolId==="screen.send_message")
    .every(step=>step.evidence.deliveryReported===false),true);
});

test("production message tools enforce exact app/text inputs and high-risk Send evidence",async()=>{
  const calls=[];
  const bridge={
    async performReviewedMessageDraft(...args){
      calls.push(["draft",...args]);
      return {appId:args[0],verification:"exact_text_visible",recipientMatched:true,
        submitted:false,sent:false};
    },
    async performReviewedMessageSend(...args){
      calls.push(["send",...args]);
      return {appId:args[0],verification:"sent_message_visible",recipientMatched:true,
        exactTextMatched:true,sent:true,deliveryReported:false};
    }
  };
  const tools=createReviewedMessageTools(()=>bridge);
  const draft=tools.find(tool=>tool.id==="screen.compose_message");
  const send=tools.find(tool=>tool.id==="screen.send_message");
  const input={appId:"imo",recipientHint:"Amma",exactText:"Hi Mom i will call you later"};
  assert.equal(draft.risk,"medium");
  assert.equal(send.risk,"high");
  assert.equal(send.hasExternalSideEffect,true);
  assert.equal(draft.validateInput(input),true);
  assert.equal(send.validateInput(input),true);
  for(const invalid of [
    {...input,appId:"sms"},
    {...input,recipientHint:"send"},
    {...input,extra:"unbound"},
    {...input,exactText:"\u0001unsafe"},
    {...input,exactText:"x".repeat(501)}
  ]){
    assert.equal(draft.validateInput(invalid),false);
    assert.equal(send.validateInput(invalid),false);
  }
  const context={sessionId:"qa-session",callId:"qa-send",approvalFingerprint:"approved-fingerprint"};
  await draft.execute(input,context);
  await send.execute(input,context);
  assert.deepEqual(calls,[
    ["draft","imo","Amma",input.exactText,"qa-session","qa-send","approved-fingerprint"],
    ["send","imo","Amma",input.exactText,"qa-session","qa-send","approved-fingerprint"]
  ]);
  const invalidNative=createReviewedMessageTools(()=>({...bridge,
    async performReviewedMessageSend(...args){
      calls.push(["uncertain",...args]);
      return {appId:args[0],verification:"sent_message_visible",recipientMatched:true,
        exactTextMatched:true,sent:true,deliveryReported:true};
    }
  })).find(tool=>tool.id==="screen.send_message");
  await assert.rejects(invalidNative.execute(input,context),error=>
    error.code==="MESSAGE_SEND_UNCERTAIN");
});

test("production tool registry and native bridge expose the guarded multi-app tools",()=>{
  const js=readFileSync("apps/mobile/src/native/androidTools.ts","utf8");
  const messageTools=readFileSync("apps/mobile/src/native/androidMessageTools.ts","utf8");
  const device=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt","utf8");
  const audit=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaAgentActionAudit.kt","utf8");
  const messages=readFileSync("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaMessageInputActions.kt","utf8");
  assert.match(js,/createReviewedMessageTools/);
  assert.match(messageTools,/id:"screen\.compose_message"/);
  assert.match(messageTools,/id:"screen\.send_message"[\s\S]*?risk:"high"/);
  assert.match(device,/fun performReviewedMessageDraft\(/);
  assert.match(device,/fun performReviewedMessageSend\(/);
  assert.match(audit,/screen\.compose_message[\s\S]{0,100}screen\.send_message/);
  assert.match(messages,/fun sendReviewedMessage\(/);
});
