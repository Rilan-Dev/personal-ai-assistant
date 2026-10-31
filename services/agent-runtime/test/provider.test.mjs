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
