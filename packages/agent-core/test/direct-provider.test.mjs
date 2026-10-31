import test from "node:test";
import assert from "node:assert/strict";
import {
  PROVIDERS, createDirectProviderPlanner, testDirectProvider,
  validateProviderSettings
} from "../src/direct-provider.ts";

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
  for(const question of ["Send a WhatsApp message","Open camera","Turn flashlight on","Check battery level","Check device status"]) {
    const r=await planner.next(question,[]);
    assert.equal(r.type,"clarify");
    assert.match(r.question,/No device action was executed/);
  }
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
    settings:sarvam,availableToolIds:allowed,
    fetchImpl:fakeDecision({type:"done",response:"Namaste!"},(url,opts)=>{
      requests++;
      assert.equal(url,"https://api.sarvam.ai/v1/chat/completions");
      assert.equal(opts.headers["api-subscription-key"],sarvam.apiKey);
      assert.equal(opts.headers.Authorization,undefined);
      assert.equal(JSON.parse(opts.body).model,"sarvam-105b");
      assert.deepEqual(JSON.parse(opts.body).response_format,{type:"json_object"});
      assert.equal(JSON.parse(opts.body).max_tokens,2048);
      assert.doesNotMatch(opts.body,/test-secret-key-not-real/);
    })
  });
  assert.deepEqual(await planner.next("Say hello",[]),{type:"done",response:"Namaste!"});
  assert.equal(requests,1);
});
