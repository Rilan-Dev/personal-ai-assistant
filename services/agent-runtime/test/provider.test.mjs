import assert from "node:assert/strict";
import { test } from "node:test";
import { createConfiguredModel } from "../src/provider.ts";

test("provider refuses missing model or API key", () => {
  assert.throws(() => createConfiguredModel({}), /AGENT_LLM_API_KEY/);
  assert.throws(() => createConfiguredModel({ AGENT_LLM_API_KEY: "test-not-real" }), /AGENT_LLM_MODEL/);
});
test("provider rejects HTTP transport to non-loopback hosts", () => {
  assert.throws(() => createConfiguredModel({
    AGENT_LLM_API_KEY: "not-a-real-key",
    AGENT_LLM_MODEL: "test-model",
    AGENT_LLM_BASE_URL: "http://api.example.com/v1"
  }), /HTTPS or local loopback/);
});
test("provider can be constructed against local OpenAI-compatible models without network requests", () => {
  let provider;
  assert.doesNotThrow(() => {
    provider = createConfiguredModel({
      AGENT_LLM_API_KEY: "not-a-real-key",
      AGENT_LLM_MODEL: "test-model",
      AGENT_LLM_BASE_URL: "http://127.0.0.1:11434/v1"
    });
  });
  assert.equal(typeof provider.decide, "function");
});
test("LangChain structured-output provider adapter interoperates with an OpenAI-compatible local mock", async () => {
  const { createServer } = await import("node:http");
  let seen = 0;
  const server = createServer(async (req,res) => {
    seen++;
    assert.equal(req.method, "POST");
    assert.equal(req.url, "/v1/chat/completions");
    assert.equal(req.headers.authorization, "Bearer not-a-real-key");
    let raw = "";
    for await (const data of req) raw += data;
    const body=JSON.parse(raw);
    assert.equal(body.model, "mock-structured-model");
    assert.ok(Array.isArray(body.messages));
    const reply = {
      id:"chatcmpl-fixture",
      object:"chat.completion",
      created: 1,
      model:"mock-structured-model",
      choices:[{index:0,finish_reason:"stop",message:{role:"assistant",content:JSON.stringify({
        type:"tool", toolId:"device.open_camera", input:{}
      })}}],
      usage:{prompt_tokens:12,completion_tokens:9,total_tokens:21}
    };
    res.writeHead(200,{"content-type":"application/json"});
    res.end(JSON.stringify(reply));
  });
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  try {
    const model=createConfiguredModel({
      AGENT_LLM_API_KEY:"not-a-real-key",
      AGENT_LLM_MODEL:"mock-structured-model",
      AGENT_LLM_BASE_URL:"http://127.0.0.1:"+server.address().port+"/v1"
    });
    const decision=await model.decide({
      request:"Open camera",
      previousActions:[],
      tools:[{id:"device.open_camera",description:"Open camera",parameters:{type:"object",properties:{},additionalProperties:false}}]
    });
    assert.deepEqual(decision,{type:"tool",toolId:"device.open_camera",input:{}});
    assert.equal(seen,1);
  } finally {
    await new Promise(resolve=>server.close(resolve));
  }
});
