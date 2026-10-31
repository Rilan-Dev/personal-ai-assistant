import assert from "node:assert/strict";
import { test } from "node:test";
import { LangGraphStepPlanner } from "../src/graph.ts";
import { createPlanningServer } from "../src/server.ts";

const TOKEN = "test-only-" + "R".repeat(44);
const request = { request: "Check device status", observations: [], availableToolIds: ["device.status"] };
async function withServer(model, fn) {
  const server = createPlanningServer({ token: TOKEN, planner: new LangGraphStepPlanner(model) });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  try {
    await fn("http://127.0.0.1:" + server.address().port);
  } finally { await new Promise(resolve => server.close(resolve)); }
}
test("planner service denies callers without exact bearer token", async () => {
  await withServer({decide:async()=>({type:"done",response:"ok"})}, async url => {
    for (const token of ["", "wrong-token"]) {
      const response = await fetch(url+"/v1/plan",{
        method:"POST", headers: token? {authorization:"Bearer "+token}: {}, body:JSON.stringify(request)
      });
      assert.equal(response.status,401);
    }
  });
});
test("HTTP API graph only returns one permitted planning decision", async () => {
  await withServer({decide:async()=>({type:"tool",toolId:"device.status",input:{}})}, async url => {
    const response = await fetch(url+"/v1/plan",{
      method:"POST", headers:{"content-type":"application/json",authorization:"Bearer "+TOKEN},
      body: JSON.stringify(request)
    });
    assert.equal(response.status,200);
    assert.equal(response.headers.get("cache-control"),"no-store");
    assert.deepEqual((await response.json()).decision, {
      type:"tool",call:{callId:"step-1",toolId:"device.status",input:{}}
    });
  });
});
test("invalid body, too-large body and planner exceptions fail safely", async () => {
  await withServer({decide:async()=>{throw new Error("confidential backend token");}}, async url => {
    for (const [body,status] of [["{",400],[JSON.stringify({request:"",availableToolIds:[],observations:[]}),400],["x".repeat(33000),413]]) {
      const res=await fetch(url+"/v1/plan", {method:"POST",headers:{authorization:"Bearer "+TOKEN},body});
      assert.equal(res.status,status);
    }
    const res=await fetch(url+"/v1/plan",{method:"POST",headers:{authorization:"Bearer "+TOKEN},body:JSON.stringify(request)});
    assert.equal(res.status,502);
    assert.doesNotMatch(await res.text(),/confidential backend token/);
  });
});
test("server refuses short access credentials", () => {
  assert.throws(()=>createPlanningServer({token:"short",planner:new LangGraphStepPlanner({decide:async()=>({type:"done",response:"ok"})})}));
});
