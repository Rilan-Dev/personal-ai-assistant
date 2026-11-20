import test from "node:test";
import assert from "node:assert/strict";
import {createDirectProviderPlanner} from "../src/direct-provider.ts";

const settings={
  version:1,provider:"openai",model:"gpt-4.1-mini",apiKey:"test-secret-key-not-real"
};

test("dynamic launch accepts the visible capitalization of an explicitly named app",async()=>{
  const decision={type:"tool",toolId:"device.open_app",input:{app:"WhatsApp"}};
  const planner=createDirectProviderPlanner({settings,availableToolIds:["device.open_app"],
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{
      message:{content:JSON.stringify(decision)}
    }]}),{status:200,headers:{"content-type":"application/json"}})});

  const result=await planner.next(
    "Open WhatsApp then inspect visible controls and tap Search once. Do not type or send.",[]);

  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.open_app");
  assert.deepEqual(result.call.input,{app:"WhatsApp"});
});

test("dynamic launch accepts an explicitly named app followed by a sentence boundary",async()=>{
  const decision={type:"tool",toolId:"device.open_app",input:{app:"WhatsApp"}};
  const planner=createDirectProviderPlanner({settings,availableToolIds:["device.open_app"],
    fetchImpl:async()=>new Response(JSON.stringify({choices:[{
      message:{content:JSON.stringify(decision)}
    }]}),{status:200,headers:{"content-type":"application/json"}})});

  const result=await planner.next(
    "Open WhatsApp. Inspect visible controls. Tap Search once then stop. Do not type or send.",[]);

  assert.equal(result.type,"tool");
  assert.equal(result.call.toolId,"device.open_app");
  assert.deepEqual(result.call.input,{app:"WhatsApp"});
});
