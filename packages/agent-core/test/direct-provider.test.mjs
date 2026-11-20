import test from "node:test";
import assert from "node:assert/strict";
import {
  PROVIDERS, createDirectProviderPlanner, testDirectProvider,
  validateProviderSettings
} from "../src/direct-provider.ts";
import {ToolRegistry,AgentHarness} from "../src/runtime.ts";
import {InMemoryActionJournal} from "../src/journal.ts";

const settings = {
  version:1,provider:"openai",model:"gpt-4.1-mini",apiKey:"test-secret-key-not-real"
};
function fakeDecision(decision, onCall=()=>{}) {
  return async (url,options) => {
    onCall(url,options);
    return new Response(JSON.stringify({
      choices:[{message:{content:JSON.stringify(decision)}}]
    }),{status:200,headers:{"content-type":"application/json"}});
  };
}
const allowed=["device.status","device.battery_status","device.open_camera","device.open_app","device.set_flashlight"];

test("2026 Gemini and Groq defaults avoid discontinued model IDs",()=>{
  assert.equal(PROVIDERS.gemini.defaultModel,"gemini-3.8-flash");
  assert.equal(PROVIDERS.groq.defaultModel,"openai/gpt-oss-120b");
});
test("provider presets have fixed HTTPS endpoints and no arbitrary URL",()=>{
  for(const x of Object.values(PROVIDERS)) {
    assert.match(x.endpoint,/^https:\/\//);
    assert.match(x.endpoint,/\/chat\/completions$/);
  }
  assert.equal(Object.keys(PROVIDERS).length,5);
  assert.equal(PROVIDERS.sarvam.defaultModel,"sarvam-105b");
});
test("validates owner model and key, rejects malformed/extra fields",()=>{
  assert.deepEqual(validateProviderSettings(settings),settings);
  for(const value of [
    {...settings,apiKey:"short"},
    {...settings,model:"bad model!"},
    {...settings,provider:"unknown"},
    {...settings,apiKey:" key with spaces "},
    {...settings,endpoint:"http://evil.example"},
    {provider:"openai"}
  ]) assert.throws(()=>validateProviderSettings(value));
});
test("direct AI answers general chat, sends key only in provider authorization header",async()=>{
  let requests=0;
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:fakeDecision({type:"done",response:"Hello from your AI!"},(url,opts)=>{
      requests++;
      assert.equal(url,PROVIDERS.openai.endpoint);
      assert.equal(opts.redirect,"error");
      assert.equal(opts.headers.Authorization,"Bearer "+settings.apiKey);
      assert.doesNotMatch(opts.body,/test-secret-key-not-real/);
      assert.doesNotMatch(opts.body,/authorization/ig);
      assert.equal(JSON.parse(opts.body).messages[1].content,"Hello, how are you?");
    })
  });
  assert.deepEqual(await planner.next("Hello, how are you?",[]),
    {type:"done",response:"Hello from your AI!"});
  assert.equal(requests,1);
});
test("only exact owner-requested device tool can be proposed",async()=>{
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:fakeDecision({type:"tool",toolId:"device.open_app",input:{app:"whatsapp"}})
  });
  const allowedCall=await planner.next("Open WhatsApp",[]);
  assert.equal(allowedCall.type,"tool");
  assert.equal(allowedCall.call.toolId,"device.open_app");
  assert.deepEqual(allowedCall.call.input,{app:"whatsapp"});
  const rejected=await planner.next("Send a message to my friend",[]);
  assert.equal(rejected.type,"clarify");
});
test("agentic app launch recognizes the named app before screen-inspection steps",async()=>{
  const request="open WhatsApp inspect visible controls then tap the exact control labeled Ask Meta AI or Search once stop immediately after the tap do not type into WhatsApp or send anything";
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"device.open_app",input:{app:"whatsapp"}})});
  const result=await planner.next(request,[]);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.open_app");
  assert.deepEqual(result.call.input,{app:"whatsapp"});
});
test("sharing only safe visible control names is a read-only agentic app inspection",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const request="Open calculator and share only the names of visible safe controls without tapping them";
  const screenTools=["device.open_app","device.visible_controls"];
  const toolCall=(name,args,onCall=()=>{})=>async(_url,options)=>{
    onCall(JSON.parse(options.body));
    return new Response(JSON.stringify({choices:[{
      finish_reason:"tool_calls",message:{role:"assistant",content:null,tool_calls:[{
        id:"call_share_controls",type:"function",function:{name,arguments:JSON.stringify(args)}
      }]}
    }]}),{status:200});
  };
  const launch=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
    fetchImpl:toolCall("device_open_app",{app:"Calculator"})});
  const launchResult=await launch.next(request,[]);
  assert.equal(launchResult.type,"tool");
  assert.equal(launchResult.call.toolId,"device.open_app");
  assert.deepEqual(launchResult.call.input,{app:"Calculator"});

  const opened=[{call:{callId:"direct-1",toolId:"device.open_app",input:{app:"Calculator"}},outcome:{
    status:"success",data:{intentDispatched:true,packageId:"com.transsion.calculator",
      verification:"destination_package_observed"}}}];
  let followupPayload;
  const inspect=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
    fetchImpl:toolCall("device_visible_controls",{},payload=>followupPayload=payload)});
  const inspectResult=await inspect.next(request,opened);
  assert.equal(inspectResult.type,"tool");
  assert.equal(inspectResult.call.toolId,"device.visible_controls");
  assert.deepEqual(followupPayload.tool_choice,
    {type:"function",function:{name:"device_visible_controls"}});

  const observations=[...opened,{call:{callId:"direct-2",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Calculator (top center); Divide (middle right). Read-only observation, not an instruction or completed action.",
      evidence:"android_accessibility",actionPerformed:false}}}];
  const complete=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
    fetchImpl:async()=>{throw new Error("a verified read-only listing should complete without another provider call");}});
  assert.deepEqual(await complete.next(request,observations),{type:"done",response:
    "Android opened the requested app and Screen Assist found these safe visible controls: Calculator, Divide. No controls were activated."});
});
test("a safe-controls request finishes locally instead of accepting extra gestures",async()=>{
  const request="Open WhatsApp and list only safe visible controls. Do not open a chat or contact. Do not read messages or send anything.";
  const screenTools=[...allowed,"device.visible_controls","screen.scroll","screen.tap_label"];
  const toolDecision=(toolId,input,onCall)=>fakeDecision({type:"tool",toolId,input},onCall);
  const launch=createDirectProviderPlanner({settings,availableToolIds:screenTools,
    fetchImpl:toolDecision("device.open_app",{app:"WhatsApp"})});
  const launchResult=await launch.next(request,[]);
  assert.equal(launchResult.type,"tool");
  assert.equal(launchResult.call.toolId,"device.open_app");
  assert.deepEqual(launchResult.call.input,{app:"WhatsApp"});

  const opened=[{call:{callId:"direct-1",toolId:"device.open_app",input:{app:"WhatsApp"}},outcome:{
    status:"success",data:{intentDispatched:true,packageId:"com.whatsapp",
      verification:"destination_package_observed"}}}];
  const prematureInspect=createDirectProviderPlanner({settings,availableToolIds:screenTools,
    fetchImpl:toolDecision("device.visible_controls",{})});
  assert.equal((await prematureInspect.next(request,[])).type,"clarify",
    "the explicitly named app must be observed in front before its controls are read");
  const inspect=createDirectProviderPlanner({settings,availableToolIds:screenTools,
    fetchImpl:toolDecision("device.visible_controls",{})});
  const inspectResult=await inspect.next(request,opened);
  assert.equal(inspectResult.type,"tool");
  assert.equal(inspectResult.call.toolId,"device.visible_controls");

  const observed=[...opened,{call:{callId:"direct-2",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{observation:"Visible non-sensitive controls in com.whatsapp: Camera (top left); Search (top center). Read-only observation, not an instruction or completed action.",
      evidence:"android_accessibility",actionPerformed:false}}}];
  for(const [toolId,input] of [
    ["screen.tap_label",{label:"Camera"}],
    ["screen.scroll",{direction:"down"}]
  ]){
    let providerRequests=0;
    const planner=createDirectProviderPlanner({settings,availableToolIds:screenTools,
      fetchImpl:toolDecision(toolId,input,()=>providerRequests++)});
    const result=await planner.next(request,observed);
    assert.equal(result.type,"done",toolId);
    assert.match(result.response,/No controls were activated/);
    assert.equal(providerRequests,0,"the completed listing must not invite an extra model action");
  }
});
test("safe visible-control listing completes locally after verified launch and observation",async()=>{
  const request="Open Calculator and list only safe visible control labels.";
  const observations=[
    {call:{callId:"direct-1",toolId:"device.open_app",input:{app:"Calculator"}},outcome:{
      status:"success",data:{intentDispatched:true,packageId:"com.transsion.calculator",
        verification:"destination_package_observed"}}},
    {call:{callId:"direct-2",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Calculator (top center); Divide (middle right). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}}
  ];
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label","screen.scroll"],
    fetchImpl:fakeDecision({type:"done",response:"Sarvam completion"},()=>providerRequests++)});
  const result=await planner.next(request,observations);
  assert.deepEqual(result,{type:"done",response:
    "Android opened the requested app and Screen Assist found these safe visible controls: Calculator, Divide. No controls were activated."});
  assert.equal(providerRequests,0,"a verified read-only listing needs no extra model turn to finalize");
});
test("agentic taps accept the exact five-word visible control after a fresh observation",async()=>{
  const request="open WhatsApp inspect visible controls then tap the exact control labeled Ask Meta AI or Search once stop immediately after the tap do not type into WhatsApp or send anything";
  const observations=[
    {call:{callId:"direct-1",toolId:"device.open_app",input:{app:"whatsapp"}},outcome:{
      status:"success",data:{intentDispatched:true,packageId:"com.whatsapp",
        verification:"destination_package_observed"}}},
    {call:{callId:"direct-2",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.whatsapp: Ask Meta AI or Search (top center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}}
  ];
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_label",
      input:{label:"Ask Meta AI or Search"}})});
  const result=await planner.next(request,observations);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"screen.tap_label");
  assert.deepEqual(result.call.input,{label:"Ask Meta AI or Search"});
});
test("Sarvam can propose one explicitly requested dynamic screen action, never an unrelated or unsafe action",async()=>{
  const screenTools=[...allowed,"device.visible_controls","screen.scroll","screen.tap_label"];
  const cases=[
    ["Inspect visible controls on the current home screen",
      {type:"tool",toolId:"device.visible_controls",input:{}},"device.visible_controls",{}],
    ["Scroll left on the home screen",
      {type:"tool",toolId:"screen.scroll",input:{direction:"left"}},"screen.scroll",{direction:"left"}],
  ];
  for(const [request,decision,toolId,input] of cases){
    const planner=createDirectProviderPlanner({settings,availableToolIds:screenTools,
      fetchImpl:fakeDecision(decision)});
    const result=await planner.next(request,[]);
    assert.equal(result.type,"tool",request);
    assert.equal(result.call.toolId,toolId);
    assert.deepEqual(result.call.input,input);
  }
  for(const [request,decision] of [
    ["Scroll left on the home screen",{type:"tool",toolId:"screen.scroll",input:{direction:"right"}}]
  ]){
    const planner=createDirectProviderPlanner({settings,availableToolIds:screenTools,
      fetchImpl:fakeDecision(decision)});
    assert.equal((await planner.next(request,[])).type,"clarify",request);
  }
});

test("natural screen-target scroll phrasing accepts only the requested direction",async()=>{
  const screenTools=[...allowed,"device.visible_controls","screen.scroll","screen.tap_label"];
  for(const [direction,expectedType] of [["left","tool"],["right","clarify"]]){
    const planner=createDirectProviderPlanner({settings,availableToolIds:screenTools,
      fetchImpl:fakeDecision({type:"tool",toolId:"screen.scroll",input:{direction}})});
    const result=await planner.next("Scroll the current home screen to the left",[]);
    assert.equal(result.type,expectedType,direction);
    if(expectedType==="tool"){
      assert.equal(result.call.toolId,"screen.scroll");
      assert.deepEqual(result.call.input,{direction:"left"});
    }else assert.match(result.question,/outside your exact request/);
  }
});
test("invalid, non-allowlisted and multi-action model tool outputs never execute",async()=>{
  for(const choice of [
    {type:"tool",toolId:"contacts.read",input:{}},
    {type:"tool",toolId:"device.open_app",input:{app:"other"}},
    {type:"tool",toolId:"device.open_camera",input:{record:true}},
    {type:"tool",toolId:"device.set_flashlight",input:{enabled:"true"}}
  ]) {
    const planner=createDirectProviderPlanner({
      settings,availableToolIds:allowed,fetchImpl:fakeDecision(choice)
    });
    const result=await planner.next("Open WhatsApp and send a text",[]);
    assert.equal(result.type,"clarify");
  }
});
test("Sarvam retries one transient 5xx before accepting a constrained screen tool",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  let requests=0;
  const success=new Response(JSON.stringify({choices:[{
    finish_reason:"tool_calls",message:{role:"assistant",content:null,
      tool_calls:[{id:"call_retry_1",type:"function",
        function:{name:"screen_scroll",arguments:JSON.stringify({direction:"left"})}}]}
  }]}),{status:200,headers:{"content-type":"application/json"}});
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["screen.scroll"],timeoutMs:30000,
    fetchImpl:async()=>++requests===1
      ?new Response("temporary outage",{status:503})
      :success});
  const result=await planner.next("Scroll the current home screen to the left",[]);
  assert.equal(requests,2);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"screen.scroll");
  assert.deepEqual(result.call.input,{direction:"left"});
});
test("Sarvam reports provider unavailable after one bounded retry",async()=>{
  let requests=0;
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["screen.scroll"],timeoutMs:30000,
    fetchImpl:async()=>{requests++;return new Response("private provider error",{status:503})}});
  await assert.rejects(planner.next("Scroll left on the home screen",[]),/PROVIDER_UNAVAILABLE/);
  assert.equal(requests,2);
});
test("native observations complete locally with no second API call or hallucinated outcome",async()=>{
  let calls=0;
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:fakeDecision({type:"done",response:"Your battery is 100%."},()=>{calls++;})
  });
  const done=await planner.next("Check battery level",[{
    call:{callId:"direct-1",toolId:"device.battery_status",input:{}},
    outcome:{status:"success",data:{levelPercent:41,charging:false}}
  }]);
  assert.equal(done.type,"done");
  assert.match(done.response,/41%/);
  assert.doesNotMatch(done.response,/100%/);
  assert.equal(calls,0);
});
test("connection test uses no available native tools",async()=>{
  let checked=false;
  const success=await testDirectProvider(settings,fakeDecision(
    {type:"done",response:"Connected"},
    (_url,opts)=>{
      checked=true;
      const payload=JSON.parse(opts.body);
      assert.match(payload.messages[0].content,/Available tools on this phone: /);
      assert.match(payload.messages[0].content,/Available tools on this phone: $/);
      assert.doesNotMatch(payload.messages[1].content,/device.set_flashlight/);
      assert.equal(payload.tools,undefined);
      assert.equal(payload.tool_choice,undefined);
      assert.equal(payload.response_format,undefined);
    }
  ));
  assert.equal(success,true);
  assert.equal(checked,true);
});
test("invalid format, unauthorized key and rate limits return sanitized error codes",async()=>{
  const cases=[
    [401,"AUTH_FAILED"],[403,"AUTH_FAILED"],[429,"RATE_LIMIT"],
    [404,"MODEL_NOT_FOUND"],[503,"PROVIDER_UNAVAILABLE"]
  ];
  for(const [status,expected] of cases) {
    const planner=createDirectProviderPlanner({
      settings,availableToolIds:allowed,fetchImpl:async()=>new Response("private provider error: secret", {status})
    });
    await assert.rejects(planner.next("Hello",[]),new RegExp(expected));
  }
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{message:{content:"I am not JSON"}}]}),{status:200})
  });
  await assert.rejects(planner.next("Hello",[]),/INVALID_RESPONSE/);
});
test("abort signal prevents outbound requests",async()=>{
  const controller=new AbortController();controller.abort();
  let n=0;
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:async()=>{n++;throw Error("should not send");}
  });
  await assert.rejects(planner.next("Hello",[],controller.signal),/CANCELLED/);
  assert.equal(n,0);
});

test("provider prose cannot claim that it executed a hardware or messaging action",async()=>{
  const planner=createDirectProviderPlanner({settings,availableToolIds:allowed,
    fetchImpl:fakeDecision({type:"done",response:"I sent your WhatsApp message and opened camera."})
  });
  for(const question of ["Send a WhatsApp message","Open camera","Turn flashlight on"]) {
    const r=await planner.next(question,[]);
    if (question === "Send a WhatsApp message") {
      assert.equal(r.type,"clarify");
      assert.match(r.question,/No device action was executed/);
    } else {
      // The owner explicitly requested this safe, supported single action.
      // A tool plan still needs the mobile permission/approval harness;
      // no model claim of success is ever accepted.
      assert.equal(r.type,"tool");
    }
  }
  // Exact read-only requests bypass unsafe provider prose and use local
  // Android tools instead; neither action is claimed complete in prose.
  for(const [question,toolId] of [
    ["Check battery level","device.battery_status"],
    ["Check device status","device.status"]
  ]) {
    const r=await planner.next(question,[]);
    assert.equal(r.type,"tool");
    assert.equal(r.call.toolId,toolId);
  }
});

test("provider prose cannot claim screen actions or observations without an approved native tool",async()=>{
  const planner=createDirectProviderPlanner({settings,availableToolIds:[...allowed,
    "device.visible_controls","screen.scroll","screen.tap_label"],
    fetchImpl:fakeDecision({type:"done",response:"I completed the screen action."})});
  for(const request of [
    "Scroll down on the current home screen",
    "Scroll the current home screen to the left",
    "Inspect visible controls on the current home screen",
    "Scroll down then tap Alarm"
  ]) {
    const result=await planner.next(request,[]);
    assert.equal(result.type,"clarify",request);
    assert.match(result.question,/No device action was executed/);
  }
  const tap=await planner.next("Tap Alarm on the current screen",[]);
  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"device.visible_controls",
    "a tap request begins with a reviewed read-only observation, not the provider's prose");
});

test("supports a single JSON code fence for compatible providers without loosening policy",async()=>{
  const result={type:"done",response:"A fenced response works"};
  const planner=createDirectProviderPlanner({
    settings,availableToolIds:allowed,
    fetchImpl:async()=>new Response(JSON.stringify({
      choices:[{message:{content:"```json\n"+JSON.stringify(result)+"\n```"}}]
    }),{status:200})
  });
  assert.deepEqual(await planner.next("What is AI?",[]),result);
});


test("Sarvam uses an official API subscription key and no cross-provider bearer header",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  let requests=0;
  const planner=createDirectProviderPlanner({
    settings:sarvam,availableToolIds:["device.visible_controls","screen.scroll","screen.tap_label"],
    fetchImpl:fakeDecision({type:"done",response:"Namaste!"},(url,opts)=>{
      requests++;
      assert.equal(url,"https://api.sarvam.ai/v1/chat/completions");
      assert.equal(opts.headers["api-subscription-key"],sarvam.apiKey);
      assert.equal(opts.headers.Authorization,undefined);
      assert.equal(JSON.parse(opts.body).model,"sarvam-105b");
      assert.equal(JSON.parse(opts.body).tool_choice,"auto");
      const payload=JSON.parse(opts.body);
      assert.equal(payload.tools.length,3);
      assert.deepEqual(payload.tools.map(x=>x.function.name),[
        "device_visible_controls","screen_scroll","screen_tap_label"]);
      assert.equal(payload.response_format,undefined);
      assert.doesNotMatch(opts.body,/test-secret-key-not-real/);
    })
  });
  assert.deepEqual(await planner.next("Say hello",[]),{type:"done",response:"Namaste!"});
  assert.equal(requests,1);
});

test("Sarvam native function calls are parsed and still constrained to the exact owner request",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const screenTools=[...allowed,"device.visible_controls","screen.scroll","screen.tap_label"];
  const toolCall=(name,args)=>async()=>new Response(JSON.stringify({choices:[{
    finish_reason:"tool_calls",message:{role:"assistant",content:null,tool_calls:[{
      id:"call_screen_1",type:"function",function:{name,arguments:JSON.stringify(args)}
    }]}
  }]}),{status:200});
  const cases=[
    ["Inspect visible controls on the current home screen","device_visible_controls",{},
      "device.visible_controls",{}],
    ["Inspect the safe visible controls on the current home screen","device_visible_controls",{},
      "device.visible_controls",{}],
    ["Scroll left on the home screen","screen_scroll",{direction:"left"},
      "screen.scroll",{direction:"left"}],
  ];
  for(const [request,name,args,toolId,input] of cases){
    const planner=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
      fetchImpl:toolCall(name,args)});
    const result=await planner.next(request,[]);
    assert.equal(result.type,"tool",request);
    assert.equal(result.call.toolId,toolId);
    assert.deepEqual(result.call.input,input);
  }
  for(const [request,name,args] of [
    ["Scroll left on the home screen","screen_scroll",{direction:"right"}],
    ["Scroll left on the home screen","device_open_camera",{}]
  ]){
    const planner=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
      fetchImpl:toolCall(name,args)});
    assert.equal((await planner.next(request,[])).type,"clarify",request);
  }
});

test("a visible-list scroll forces one native proposal in the requested direction",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.scroll"],
    fetchImpl:async(_url,options)=>{
      providerRequests++;
      const payload=JSON.parse(options.body);
      assert.deepEqual(payload.tool_choice,
        {type:"function",function:{name:"screen_scroll"}});
      return new Response(JSON.stringify({choices:[{message:{
        role:"assistant",content:null,tool_calls:[{id:"call_visible_scroll",
          type:"function",function:{name:"screen_scroll",
            arguments:JSON.stringify({direction:"down"})}}]
      }}]}),{status:200,headers:{"content-type":"application/json"}});
    }});
  const request="Scroll the visible list down once";
  const result=await planner.next(request,[]);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"screen.scroll");
  assert.deepEqual(result.call.input,{direction:"down"});
  assert.equal(providerRequests,1);
});

test("Sarvam follow-up planning uses tool-result protocol and requires safe visible-controls tool",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const request="Open Calculator and list only safe visible control labels.";
  const opened={call:{callId:"call_open_calculator",toolId:"device.open_app",input:{app:"Calculator"}},outcome:{
    status:"success",data:{intentDispatched:true,packageId:"com.transsion.calculator",
      verification:"destination_package_observed"}}};
  let payload;
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.open_app","device.visible_controls"],
    fetchImpl:async(_url,options)=>{
      payload=JSON.parse(options.body);
      return new Response(JSON.stringify({choices:[{
        finish_reason:"tool_calls",message:{role:"assistant",content:null,tool_calls:[{
          id:"call_controls_1",type:"function",function:{name:"device_visible_controls",arguments:"{}"}
        }]}
      }]}),{status:200,headers:{"content-type":"application/json"}});
    }});

  const result=await planner.next(request,[opened]);

  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.visible_controls");
  assert.deepEqual(payload.tool_choice,{type:"function",function:{name:"device_visible_controls"}});
  assert.deepEqual(payload.messages.slice(1).map(message=>message.role),["user","assistant","tool"]);
  assert.equal(payload.messages[2].tool_calls[0].function.name,"device_open_app");
  assert.equal(payload.messages[2].tool_calls[0].id,payload.messages[3].tool_call_id);
  assert.deepEqual(JSON.parse(payload.messages[2].tool_calls[0].function.arguments),{app:"Calculator"});
  assert.deepEqual(JSON.parse(payload.messages[3].content),{
    action:"device.open_app",app:"Calculator",accepted:true,destinationObserved:true
  });
});

test("an explicit screen tap starts with a local fresh-control observation",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const payloads=[];
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_label"],
    fetchImpl:async(_url,options)=>{
      const payload=JSON.parse(options.body);payloads.push(payload);
      const forced=payload.tool_choice?.function?.name;
      const name=forced==="screen_tap_label"?"screen_tap_label":"device_open_app";
      const input=name==="screen_tap_label"?{label:"Open QA details"}:{app:"Chrome"};
      return new Response(JSON.stringify({choices:[{finish_reason:"tool_calls",message:{
        role:"assistant",content:null,tool_calls:[{id:"call_tap_fixture",type:"function",
          function:{name,arguments:JSON.stringify(input)}}]
      }}]}),{status:200,headers:{"content-type":"application/json"}});
    }});
  const request="Tap the Open QA details button";

  const observe=await planner.next(request,[]);
  assert.equal(observe.type,"tool");
  assert.equal(observe.call.toolId,"device.visible_controls");
  assert.equal(payloads.length,0,"the model must not select a side-effect before a fresh observation");

  const observed=[{call:observe.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false
  }}}];
  const tap=await planner.next(request,observed);
  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"screen.tap_label");
  assert.deepEqual(tap.call.input,{label:"Open QA details"});
  assert.deepEqual(payloads[0].tool_choice,
    {type:"function",function:{name:"screen_tap_label"}});
});

test("an exact tap falls back to the matching fresh snapshot target when the provider suggests another action",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  let requests=0;
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_observed","screen.tap_label","device.open_app"],
    fetchImpl:async()=>{
      requests++;
      return new Response(JSON.stringify({choices:[{finish_reason:"tool_calls",message:{
        role:"assistant",content:null,tool_calls:[{id:"call_wrong_open",type:"function",
          function:{name:"device_open_app",arguments:JSON.stringify({app:"Chrome"})}}
        ]
      }}]}),{status:200,headers:{"content-type":"application/json"}});
    }});
  const request="Tap the Open QA details button";
  const observe=await planner.next(request,[]);
  assert.equal(observe.type,"tool");
  assert.equal(observe.call.toolId,"device.visible_controls");

  const snapshot={
    snapshotId:"8f8c9756-8084-4b4e-b2ae-f08af769ce1e",
    packageId:"com.android.chrome",capturedAt:Date.now(),expiresAt:Date.now()+30_000,
    truncated:false,
    targets:[{index:0,label:"Open QA details",kind:"control",role:"Button",
      semantic:null,region:"middle center",
      bounds:{left:20,top:410,right:420,bottom:460}}],
    icons:[]
  };
  const observed=[{call:observe.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false,screenObservation:snapshot
  }}}];
  const tap=await planner.next(request,observed);
  assert.equal(requests,1);
  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"screen.tap_observed");
  assert.deepEqual(tap.call.input,{snapshotId:snapshot.snapshotId,index:0,label:"Open QA details"});
});

test("an exact tap can use the fresh native control label when the paired snapshot omits that target",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_observed","screen.tap_label","device.open_app"],
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{finish_reason:"tool_calls",message:{
      role:"assistant",content:null,tool_calls:[{id:"call_wrong_open",type:"function",
        function:{name:"device_open_app",arguments:JSON.stringify({app:"Chrome"})}}
      ]
    }}]}),{status:200,headers:{"content-type":"application/json"}})});
  const request="Tap the Open QA details button";
  const observe=await planner.next(request,[]);
  assert.equal(observe.type,"tool");
  assert.equal(observe.call.toolId,"device.visible_controls");

  const snapshot={
    snapshotId:"8f8c9756-8084-4b4e-b2ae-f08af769ce1e",
    packageId:"com.android.chrome",capturedAt:Date.now(),expiresAt:Date.now()+30_000,
    truncated:false,
    targets:[{index:0,label:"Chrome menu",kind:"control",role:"Button",
      semantic:null,region:"top right",
      bounds:{left:900,top:0,right:1000,bottom:40}}],
    icons:[]
  };
  const observed=[{call:observe.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false,screenObservation:snapshot
  }}}];
  const tap=await planner.next(request,observed);

  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"screen.tap_label");
  assert.deepEqual(tap.call.input,{label:"Open QA details"});
});

test("an exact tap never combines a native control label with a snapshot from another app",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_observed","screen.tap_label","device.open_app"],
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{finish_reason:"tool_calls",message:{
      role:"assistant",content:null,tool_calls:[{id:"call_wrong_open",type:"function",
        function:{name:"device_open_app",arguments:JSON.stringify({app:"Chrome"})}}
      ]
    }}]}),{status:200,headers:{"content-type":"application/json"}})});
  const request="Tap the Open QA details button";
  const observe=await planner.next(request,[]);
  const snapshot={
    snapshotId:"8f8c9756-8084-4b4e-b2ae-f08af769ce1e",
    packageId:"com.example.other",capturedAt:Date.now(),expiresAt:Date.now()+30_000,
    truncated:false,targets:[{index:0,label:"Open QA details",kind:"control",role:"Button",
      semantic:null,region:"middle center",bounds:{left:100,top:300,right:400,bottom:360}}],icons:[]
  };
  const observed=[{call:observe.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.android.chrome: Open QA details (middle center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false,screenObservation:snapshot
  }}}];

  const result=await planner.next(request,observed);

  assert.equal(result.type,"clarify");
  assert.match(result.question,/outside your exact request/);
});

test("a single tap request is upgraded to a fresh visible-control observation first",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const screenTools=[...allowed,"device.visible_controls","screen.scroll","screen.tap_label"];
  let calls=0;
  const planner=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
    fetchImpl:async()=>{
      calls++;
      return new Response(JSON.stringify({choices:[{
        finish_reason:"tool_calls",message:{role:"assistant",content:null,tool_calls:[{
          id:"call_screen_1",type:"function",function:{name:"screen_tap_label",
            arguments:JSON.stringify({label:"Point"})}
        }]}
      }]}),{status:200});
    }});
  const request="Tap Point on the current screen.";
  const first=await planner.next(request,[]);
  assert.equal(first.type,"tool");
  assert.equal(first.call.toolId,"device.visible_controls",
    "a premature label tap must be converted into the safe observation step");
  assert.deepEqual(first.call.input,{});

  const observed=[{call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
      evidence:"android_accessibility",actionPerformed:false}}}];
  const second=await planner.next(request,observed);
  assert.equal(second.type,"tool");
  assert.equal(second.call.toolId,"screen.tap_label");
  assert.deepEqual(second.call.input,{label:"Point"});
  assert.equal(calls,1,"the local read-only observation does not call the provider");
});

test("Sarvam observes current-screen controls before proposing one exact label tap",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const payloads=[];
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_label"],
    fetchImpl:async(_url,options)=>{
      payloads.push(JSON.parse(options.body));
      const tool={id:"call_tap_point",type:"function",function:{name:"screen_tap_label",
        arguments:JSON.stringify({label:"Point"})}};
      return new Response(JSON.stringify({choices:[{
        finish_reason:"tool_calls",message:{role:"assistant",content:null,tool_calls:[tool]}
      }]}),{status:200,headers:{"content-type":"application/json"}});
    }});
  const request="Tap Point on the current Calculator screen.";

  const observe=await planner.next(request,[]);
  assert.equal(observe.type,"tool");
  assert.equal(observe.call.toolId,"device.visible_controls");
  const observation={call:observe.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false
  }}};
  const tap=await planner.next(request,[observation]);
  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"screen.tap_label");
  assert.deepEqual(tap.call.input,{label:"Point"});
  assert.deepEqual(payloads[0].tool_choice,{type:"function",function:{name:"screen_tap_label"}});
  assert.deepEqual(payloads[0].messages.slice(1).map(message=>message.role),["user","assistant","tool"]);
  assert.equal(payloads[0].messages[2].tool_calls[0].function.name,"device_visible_controls");
  assert.deepEqual(JSON.parse(payloads[0].messages[3].content),{
    action:"device.visible_controls",status:"observed",packageId:"com.transsion.calculator",labels:["Point"]
  });
});

test("semantic decimal-point taps use a fresh Point control observation",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const screenTools=["device.open_app","device.visible_controls","screen.tap_label"];
  const decisions=[
    {type:"tool",toolId:"device.open_app",input:{app:"Calculator"}},
    {type:"tool",toolId:"screen.tap_label",input:{label:"Point"}},
    {type:"tool",toolId:"screen.tap_label",input:{label:"Point"}}
  ];
  const planner=createDirectProviderPlanner({settings:sarvam,availableToolIds:screenTools,
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{
      message:{content:JSON.stringify(decisions.shift())}
    }]}),{status:200,headers:{"content-type":"application/json"}})});
  const request="Open Calculator then tap its decimal point once.";
  const launch=await planner.next(request,[]);
  assert.equal(launch.call.toolId,"device.open_app");
  const opened={call:launch.call,outcome:{status:"success",data:{
    intentDispatched:true,packageId:"com.transsion.calculator",
    verification:"destination_package_observed"
  }}};
  const inspect=await planner.next(request,[opened]);
  assert.equal(inspect.type,"tool");
  assert.equal(inspect.call.toolId,"device.visible_controls");
  const observed={call:inspect.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false
  }}};
  const tap=await planner.next(request,[opened,observed]);
  assert.equal(tap.type,"tool");
  assert.equal(tap.call.toolId,"screen.tap_label");
  assert.deepEqual(tap.call.input,{label:"Point"});
});

test("decimal-point tap wording may name the app after the target",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const request="Tap the decimal point in Calculator once.";
  const observations=[{call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
      evidence:"android_accessibility",actionPerformed:false}
  }}];
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_label",input:{label:"Point"}})});

  const result=await planner.next(request,observations);

  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"screen.tap_label");
  assert.deepEqual(result.call.input,{label:"Point"});
});

test("decimal-point tap with one-time wording strips trailing app context",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const request="Open Calculator then tap the decimal point in Calculator one time.";
  const observations=[
    {call:{callId:"direct-open",toolId:"device.open_app",input:{app:"Calculator"}},outcome:{
      status:"success",data:{intentDispatched:true,packageId:"com.transsion.calculator",
        verification:"destination_package_observed"}}},
    {call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}}
  ];
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_label",input:{label:"Point"}})});

  const result=await planner.next(request,observations);

  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"screen.tap_label");
  assert.deepEqual(result.call.input,{label:"Point"});
});

test("compound decimal-point request cannot treat its full wording as a screen label",async()=>{
  const sarvam={...settings,provider:"sarvam",model:"sarvam-105b"};
  const request="Open Calculator then tap the decimal point in Calculator one time.";
  const observations=[
    {call:{callId:"direct-open",toolId:"device.open_app",input:{app:"Calculator"}},outcome:{
      status:"success",data:{intentDispatched:true,packageId:"com.transsion.calculator",
        verification:"destination_package_observed"}}},
    {call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center); decimal point in Calculator (bottom right). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}}
  ];
  const planner=createDirectProviderPlanner({settings:sarvam,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_label",
      input:{label:"decimal point in Calculator"}})});

  const result=await planner.next(request,observations);

  assert.equal(result.type,"clarify");
  assert.match(result.question,/outside your exact request/);
});

test("a verified single screen tap completes locally without a second provider response",async()=>{
  const request="Tap Point on the current screen.";
  const observations=[
    {call:{callId:"direct-1",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}},
    {call:{callId:"direct-2",toolId:"screen.tap_label",input:{label:"Point"}},outcome:{
      status:"success",data:{actionAccepted:true,verification:"observed_change"}}}
  ];
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings:{...settings,provider:"sarvam",model:"sarvam-105b"},
    availableToolIds:["device.visible_controls","screen.tap_label","screen.scroll"],
    fetchImpl:fakeDecision({type:"done",response:"Tap completed."},()=>providerRequests++)});

  const result=await planner.next(request,observations);

  assert.equal(result.type,"done");
  assert.match(result.response,/Android observed (?:a|the) screen change after the approved tap/);
  assert.equal(providerRequests,0);
});

test("a verified click event acknowledges a tap without claiming screen content changed",async()=>{
  const request="Tap Point on the current Calculator screen.";
  const observations=[
    {call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}},
    {call:{callId:"direct-tap",toolId:"screen.tap_label",input:{label:"Point"}},outcome:{
      status:"success",data:{actionAccepted:true,verification:"tap_event_observed",businessOutcomeVerified:false}}}
  ];
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings:{...settings,provider:"sarvam",model:"sarvam-105b"},
    availableToolIds:["device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"done",response:"The screen changed."},()=>providerRequests++)});

  const result=await planner.next(request,observations);

  assert.deepEqual(result,{type:"done",response:
    "Android observed the approved tap event. The resulting screen content change was not independently verified."});
  assert.equal(providerRequests,0,"a verified tap event completes the action without another model claim");
});

test("a same-window content change event acknowledges only the screen change",async()=>{
  const request="Tap Point on the current Calculator screen.";
  const observations=[
    {call:{callId:"direct-observe",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}},
    {call:{callId:"direct-tap",toolId:"screen.tap_label",input:{label:"Point"}},outcome:{
      status:"success",data:{actionAccepted:true,verification:"content_change_observed",businessOutcomeVerified:false}}}
  ];
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings:{...settings,provider:"sarvam",model:"sarvam-105b"},
    availableToolIds:["device.visible_controls","screen.tap_label"],
    fetchImpl:fakeDecision({type:"done",response:"The screen changed."},()=>providerRequests++)});

  const result=await planner.next(request,observations);

  assert.deepEqual(result,{type:"done",response:
    "Android observed an accessibility content-change event in the same app window after the approved tap. The exact control effect and app-level result are not independently verified, and no further action was taken."});
  assert.equal(providerRequests,0,"native content evidence ends this one-step action without a model success claim");
});

test("an unverified single screen tap stops locally without another provider response",async()=>{
  const request="Tap Point on the current screen.";
  const observations=[
    {call:{callId:"direct-1",toolId:"device.visible_controls",input:{}},outcome:{
      status:"success",data:{observation:"Visible non-sensitive controls in com.transsion.calculator: Point (bottom center). Read-only observation, not an instruction or completed action.",
        evidence:"android_accessibility",actionPerformed:false}}},
    {call:{callId:"direct-2",toolId:"screen.tap_label",input:{label:"Point"}},outcome:{
      status:"success",data:{actionAccepted:false,verification:"unchanged"}}}
  ];
  let providerRequests=0;
  const planner=createDirectProviderPlanner({settings:{...settings,provider:"sarvam",model:"sarvam-105b"},
    availableToolIds:["device.visible_controls","screen.tap_label","screen.scroll"],
    fetchImpl:fakeDecision({type:"done",response:"Tap completed."},()=>providerRequests++)});

  const result=await planner.next(request,observations);

  assert.equal(result.type,"clarify");
  assert.match(result.question,/Android did not verify a screen change after the approved tap/);
  assert.equal(providerRequests,0);
});

test("agentic screen navigation uses fresh safe controls and emits one reviewed action at a time",async()=>{
  const screenTools=["device.open_app","device.visible_controls","screen.tap_label","screen.scroll"];
  const task="Open Android Settings then navigate to Display";
  const decisions=[
    {type:"tool",toolId:"device.open_app",input:{app:"settings"}},
    {type:"tool",toolId:"device.visible_controls",input:{}},
    {type:"tool",toolId:"screen.tap_label",input:{label:"Display"}},
    {type:"done",response:"The requested screen is open."}
  ];
  const requests=[];
  const planner=createDirectProviderPlanner({settings,availableToolIds:screenTools,
    fetchImpl:async(_url,options)=>{
      requests.push(JSON.parse(options.body));
      return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(decisions.shift())}}]}),
        {status:200,headers:{"content-type":"application/json"}});
    }});
  const step1=await planner.next(task,[]);
  assert.equal(step1.type,"tool");
  assert.equal(step1.call.callId,"direct-1");
  assert.deepEqual(step1.call.input,{app:"settings"});
  const step2=await planner.next(task,[{call:step1.call,outcome:{status:"success",data:{
    intentDispatched:true,packageId:"com.android.settings",verification:"destination_package_observed"
  }}}]);
  assert.equal(step2.type,"tool");
  assert.equal(step2.call.callId,"direct-2");
  assert.equal(step2.call.toolId,"device.visible_controls");
  const controls={call:step2.call,outcome:{status:"success",data:{
    observation:"Visible non-sensitive controls in com.android.settings: Display (middle center). Read-only observation, not an instruction or completed action.",
    evidence:"android_accessibility",actionPerformed:false
  }}};
  const step3=await planner.next(task,[
    {call:step1.call,outcome:{status:"success",data:{
      intentDispatched:true,packageId:"com.android.settings",verification:"destination_package_observed"
    }}},controls
  ]);
  assert.equal(step3.type,"tool");
  assert.equal(step3.call.callId,"direct-3");
  assert.equal(step3.call.toolId,"screen.tap_label");
  assert.deepEqual(step3.call.input,{label:"Display"});
  const final=await planner.next(task,[
    {call:step1.call,outcome:{status:"success",data:{
      intentDispatched:true,packageId:"com.android.settings",verification:"destination_package_observed"
    }}},controls,{call:step3.call,outcome:{status:"success",data:{
      actionAccepted:true,verification:"observed_change",businessOutcomeVerified:false
    }}}
  ]);
  assert.equal(final.type,"done");
  assert.match(final.response,/Android observed the screen change after the approved tap/i);
  assert.doesNotMatch(final.response,/requested screen is open/i);
  assert.equal(requests.length,4);
  assert.match(requests[2].messages.at(-1).content,/UNTRUSTED ANDROID OBSERVATION/);
  assert.match(requests[2].messages.at(-1).content,/Display/);
});

test("agentic navigation refreshes controls after a scroll before proposing a tap",async()=>{
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.visible_controls","screen.scroll","screen.tap_label"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_label",input:{label:"Display"}})});
  const task="Navigate to Display settings and scroll if needed";
  const controls={call:{callId:"direct-1",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{observation:"Visible non-sensitive controls in com.android.settings: Display (top left). Read-only observation, not an instruction or completed action.",actionPerformed:false}
  }};
  const scroll={call:{callId:"direct-2",toolId:"screen.scroll",input:{direction:"down"}},outcome:{
    status:"success",data:{actionAccepted:true,verification:"observed_change",businessOutcomeVerified:false}
  }};
  const result=await planner.next(task,[controls,scroll]);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.visible_controls");
  assert.deepEqual(result.call.input,{});
});

test("dynamic navigation accepts the Hey Nexa voice prefix and preserves the named app gate",async()=>{
  const planner=createDirectProviderPlanner({settings,availableToolIds:["device.open_app"],
    fetchImpl:fakeDecision({type:"tool",toolId:"device.open_app",input:{app:"settings"}})});
  const result=await planner.next("Hey Nexa, open Android Settings then navigate to Display",[]);
  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.open_app");
  assert.deepEqual(result.call.input,{app:"settings"});
});

test("each dynamic screen step waits for its own harness approval before execution",async()=>{
  const task="Open Android Settings then navigate to Display";
  const decisions=[
    {type:"tool",toolId:"device.open_app",input:{app:"settings"}},
    {type:"tool",toolId:"device.visible_controls",input:{}},
    {type:"tool",toolId:"screen.tap_label",input:{label:"Display"}},
    {type:"done",response:"The requested screen is open."}
  ];
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.open_app","device.visible_controls","screen.tap_label"],
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{
      message:{content:JSON.stringify(decisions.shift())}
    }]}),{status:200,headers:{"content-type":"application/json"}})});
  const registry=new ToolRegistry();
  const executed=[];
  const register=(id,capability,validate,execute)=>registry.register({
    id,description:"test reviewed Android action",capabilities:[capability],
    risk:"medium",hasExternalSideEffect:true,validateInput:validate,
    execute:async input=>{executed.push(id);return {status:"success",data:execute(input)}}
  });
  register("device.open_app","apps.launch",input=>input?.app==="settings",()=>({
    intentDispatched:true,packageId:"com.android.settings",verification:"destination_package_observed"
  }));
  register("device.visible_controls","device.observe",input=>Object.keys(input??{}).length===0,()=>({
    observation:"Visible non-sensitive controls in com.android.settings: Display (middle center). Read-only observation, not an instruction or completed action.",
    actionPerformed:false
  }));
  register("screen.tap_label","device.interact",input=>input?.label==="Display",()=>({
    actionAccepted:true,verification:"observed_change",businessOutcomeVerified:false
  }));
  const journal=new InMemoryActionJournal();
  const harness=new AgentHarness(registry,planner);
  const options={request:task,sessionId:"dynamic-screen-owner-review",
    grantedCapabilities:new Set(["apps.launch","device.observe","device.interact"]),journal};
  async function approveAndResume(pending){
    assert.equal(pending.status,"awaiting_approval");
    assert.equal(await journal.approveAction(options.sessionId,pending.call.callId,pending.fingerprint),true);
    return harness.run({...options,resume:true});
  }
  let result=await harness.run(options);
  assert.equal(result.status,"awaiting_approval");
  assert.deepEqual(executed,[]);
  result=await approveAndResume(result);
  assert.equal(result.status,"awaiting_approval");
  assert.deepEqual(executed,["device.open_app"]);
  result=await approveAndResume(result);
  assert.equal(result.status,"awaiting_approval");
  assert.deepEqual(executed,["device.open_app","device.visible_controls"]);
  result=await approveAndResume(result);
  assert.equal(result.status,"completed");
  assert.deepEqual(executed,["device.open_app","device.visible_controls","screen.tap_label"]);
  assert.match(result.response,/Android observed the screen change after the approved tap/i);
});

test("floating Chat accepts native scroll evidence without claiming a screen change",async()=>{
  const planner=createDirectProviderPlanner({settings,availableToolIds:["screen.scroll"],
    fetchImpl:fakeDecision({type:"done",response:"unused"})});
  for(const direction of ["up","down","left","right"]){
    const call={callId:"scroll-1",toolId:"screen.scroll",input:{direction}};
    const result=await planner.next(
      `Scroll ${direction} once on the current Android Settings screen`,[
        {call,outcome:{status:"success",data:{
          actionAccepted:true,verification:"scroll_event_observed",businessOutcomeVerified:false
        }}}
      ]);
    assert.equal(result.type,"done");
    assert.match(result.response,new RegExp(
      `observed the approved ${direction} scroll event`,"i"));
    assert.match(result.response,/resulting screen position was not independently verified/i);
  }
});

const testScreenObservation={
  snapshotId:"8f8c9756-8084-4b4e-b2ae-f08af769ce1e",
  packageId:"com.android.settings",capturedAt:Date.now(),
  expiresAt:Date.now()+30_000,
  truncated:false,
  targets:[
    {index:0,label:"Display",kind:"control",role:"Button",region:"middle center",
      semantic:null,bounds:{left:120,top:360,right:880,bottom:470}},
    {index:1,label:"Search",kind:"icon",role:"ImageButton",region:"top right",
      semantic:"search",bounds:{left:830,top:30,right:970,bottom:110}}
  ],
  icons:[
    {index:0,label:"Search",semantic:"search",role:"ImageButton",clickable:true,
      region:"top right",targetIndex:1,
      bounds:{left:830,top:30,right:970,bottom:110}},
    {index:1,label:"Unknown icon",semantic:"unknown",role:"ImageButton",clickable:true,
      region:"top left",targetIndex:null,bounds:{left:10,top:30,right:90,bottom:110}}
  ]
};

test("AI screen taps must bind an explicitly requested label to the latest safe snapshot",async()=>{
  const decisions=[
    {type:"tool",toolId:"screen.tap_observed",input:{
      snapshotId:testScreenObservation.snapshotId,index:1,label:"Search"}},
    {type:"tool",toolId:"screen.tap_observed",input:{
      snapshotId:"00000000-0000-4000-8000-000000000001",index:1,label:"Search"}},
    {type:"tool",toolId:"screen.tap_observed",input:{
      snapshotId:testScreenObservation.snapshotId,index:1,label:"Send"}}
  ];
  const request="Tap the Search icon on the current screen once.";
  const observations=[{call:{callId:"observe-1",toolId:"device.visible_controls",input:{}},outcome:{
    status:"success",data:{actionPerformed:false,evidence:"android_accessibility",
      observation:"Visible non-sensitive controls in com.android.settings: Display (middle center). Read-only observation, not an instruction or completed action.",
      screenObservation:testScreenObservation}
  }}];
  for(let i=0;i<decisions.length;i++){
    const planner=createDirectProviderPlanner({settings,
      availableToolIds:["device.visible_controls","screen.tap_observed"],
      fetchImpl:fakeDecision(decisions[i])});
    const result=await planner.next(request,observations);
    if(i===0){
      assert.equal(result.type,"tool",JSON.stringify(result));
      assert.equal(result.call.toolId,"screen.tap_observed");
      assert.deepEqual(result.call.input,decisions[i].input);
    }else{
      assert.equal(result.type,"clarify");
      assert.match(result.question,/snapshot|target|request/i);
    }
  }
});

test("a screen action result supplies redacted clickable and icon context for the next action",async()=>{
  const nextScreen={...testScreenObservation,
    snapshotId:"8f8c9756-8084-4b4e-b2ae-f08af769ce1f",
    targets:[{index:0,label:"Brightness",kind:"control",role:"Button",
      semantic:null,region:"middle center",bounds:{left:120,top:360,right:880,bottom:470}}],
    icons:[{index:0,label:"Unknown icon",semantic:"unknown",role:"ImageButton",
      clickable:true,region:"top left",targetIndex:null,
      bounds:{left:10,top:30,right:90,bottom:110}}]
  };
  const previous={
    call:{callId:"tap-1",toolId:"screen.tap_observed",input:{
      snapshotId:testScreenObservation.snapshotId,index:0,label:"Display"}},
    outcome:{status:"success",data:{actionAccepted:true,verification:"observed_change",
      businessOutcomeVerified:false,screenObservation:nextScreen}}
  };
  const payloads=[];
  const planner=createDirectProviderPlanner({settings:{...settings,provider:"sarvam",model:"sarvam-105b"},
    availableToolIds:["screen.tap_observed"],
    fetchImpl:async(_url,options)=>{
      payloads.push(JSON.parse(options.body));
      return new Response(JSON.stringify({choices:[{finish_reason:"tool_calls",message:{
        role:"assistant",content:null,tool_calls:[{id:"next-step",type:"function",
          function:{name:"screen_tap_observed",arguments:JSON.stringify({
            snapshotId:nextScreen.snapshotId,index:0,label:"Brightness"})}}
        ]}}]}),{status:200,headers:{"content-type":"application/json"}});
    }});
  const result=await planner.next("Tap Display, then tap Brightness once and stop.",[previous]);
  assert.equal(result.type,"tool",JSON.stringify(result));
  assert.equal(result.call.toolId,"screen.tap_observed");
  assert.deepEqual(result.call.input,{snapshotId:nextScreen.snapshotId,index:0,label:"Brightness"});
  const payload=payloads[0];
  const encoded=JSON.stringify(payload.messages);
  assert.match(encoded,/Brightness/);
  assert.match(encoded,/ImageButton/);
  assert.doesNotMatch(encoded,/Amma|message body|Search history/i);
});

test("only Back, Home, and Notifications are proposed as named key actions",async()=>{
  for(const [request,name,expected] of [
    ["Press Back once","back","tool"],
    ["Press Home once","home","tool"],
    ["Open notifications once","notifications","tool"],
    ["Press Enter to submit","enter","clarify"]
  ]){
    const planner=createDirectProviderPlanner({settings,
      availableToolIds:["screen.key_action"],
      fetchImpl:fakeDecision({type:"tool",toolId:"screen.key_action",input:{name}})});
    const result=await planner.next(request,[]);
    assert.equal(result.type,expected,request);
    if(expected==="tool")assert.deepEqual(result.call.input,{name});
  }
});

test("icon targets must link to the exact safe target bounds from the same snapshot",async()=>{
  const forged={...testScreenObservation,icons:testScreenObservation.icons.map((icon)=>
    icon.targetIndex===1?{...icon,bounds:{...icon.bounds,left:0}}:icon)};
  const planner=createDirectProviderPlanner({settings,
    availableToolIds:["device.visible_controls","screen.tap_observed"],
    fetchImpl:fakeDecision({type:"tool",toolId:"screen.tap_observed",input:{
      snapshotId:forged.snapshotId,index:1,label:"Search"}})});
  const result=await planner.next("Tap the Search icon once.",[
    {call:{callId:"observe-forged-icon-link",toolId:"device.visible_controls",input:{}},
      outcome:{status:"success",data:{actionPerformed:false,
        observation:"Visible non-sensitive controls in com.android.settings: Display (middle center). Read-only observation, not an instruction or completed action.",
        screenObservation:forged}}}
  ]);
  assert.equal(result.type,"tool",JSON.stringify(result));
  assert.equal(result.call.toolId,"device.visible_controls");
});
