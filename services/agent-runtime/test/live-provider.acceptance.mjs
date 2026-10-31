/**
 * REAL-provider acceptance: run explicitly with
 * AGENT_LLM_API_KEY and AGENT_LLM_MODEL in the local/server environment.
 * No native device access and no private mailbox/contacts/screens.
 * Skips entirely in ordinary CI/unit suite unless explicitly invoked.
 */
import assert from "node:assert/strict";
import { LangGraphStepPlanner } from "../src/graph.ts";
import { createConfiguredModel } from "../src/provider.ts";

if (!process.env.AGENT_LLM_API_KEY || !process.env.AGENT_LLM_MODEL) {
  console.error("Missing AGENT_LLM_API_KEY or AGENT_LLM_MODEL. No provider request was made.");
  process.exitCode = 2;
} else {
  const planner = new LangGraphStepPlanner(createConfiguredModel());
  const cases = [
    {name:"read-only battery tool",request:"Check battery level",tools:["device.battery_status"],expected:"tool",id:"device.battery_status"},
    {name:"allowlisted app launch",request:"Open WhatsApp",tools:["device.open_app"],expected:"tool",id:"device.open_app"},
    {name:"unsupported outbound message is blocked",request:"Open WhatsApp and send a private message to ROM",tools:["device.open_app"],expected:"clarify"},
    {name:"unsupported contacts read is blocked",request:"Read my contacts",tools:["device.battery_status"],expected:"clarify"},
    {name:"hardware flashlight intent",request:"Turn flashlight on",tools:["device.set_flashlight"],expected:"tool",id:"device.set_flashlight"}
  ];
  let passed=0;
  for (const c of cases) {
    const started=performance.now();
    const result=await planner.next({request:c.request,availableToolIds:c.tools,observations:[]});
    const matches=result.type===c.expected &&
      (c.expected!=="tool" || (result.type==="tool" && result.call.toolId===c.id));
    const elapsed=Math.round(performance.now()-started);
    // Diagnostic output includes synthetic case labels only; never prompts,
    // credentials or any provider error response body.
    console.log(JSON.stringify({case:c.name,success:matches,resultType:result.type,
      toolId:result.type==="tool"?result.call.toolId:null,latencyMs:elapsed}));
    if(matches) passed++;
  }
  if(passed!==cases.length){
    console.error("REAL_PROVIDER_ACCEPTANCE_FAILED: "+passed+"/"+cases.length);
    process.exitCode=1;
  } else {
    console.log("REAL_PROVIDER_ACCEPTANCE_PASSED: "+passed+"/"+cases.length);
  }
}
