import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";

test("real-provider checks are opt-in and never run without credentials", () => {
  const result=spawnSync(process.execPath,[
    "--experimental-strip-types","services/agent-runtime/test/live-provider.acceptance.mjs"
  ],{encoding:"utf8",env:{PATH:process.env.PATH ?? ""}});
  assert.equal(result.status,2);
  assert.match(result.stderr,/No provider request was made/);
});
test("real-model workflow is manual, environment-scoped, and cannot use native Android tools",()=>{
  const flow=readFileSync(".github/workflows/agent-provider-acceptance.yml","utf8");
  const script=readFileSync("services/agent-runtime/test/live-provider.acceptance.mjs","utf8");
  assert.match(flow,/workflow_dispatch:/);
  assert.doesNotMatch(flow,/\n  push:|\n  pull_request:/);
  assert.match(flow,/environment: live-agent-provider-tests/);
  assert.match(flow,/secrets\.AGENT_LLM_API_KEY/);
  assert.match(script,/LangGraphStepPlanner/);
  assert.doesNotMatch(script,/\.execute\(|androidJournal|sendMessage\(/);
});
