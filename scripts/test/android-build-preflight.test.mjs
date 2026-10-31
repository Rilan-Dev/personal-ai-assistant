import test from "node:test";
import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {readFileSync} from "node:fs";

const script="scripts/acceptance/android_build_preflight.py";

test("Android build preflight is read-only and reports actionable results",()=>{
  const src=readFileSync(script,"utf8");
  assert.doesNotMatch(src,/sdkmanager|gradlew|git push|gh workflow run|rm -rf|subprocess\.Popen/);
  const r=spawnSync("python3",[script,"--mode","compile","--json"],{
    encoding:"utf8",timeout:10000
  });
  assert.ok([0,2].includes(r.status),r.stderr);
  const report=JSON.parse(r.stdout);
  assert.equal(report.mode,"compile");
  assert.equal(typeof report.safe_to_attempt,"boolean");
  assert.equal(typeof report.free_disk_gib,"number");
  assert.ok(Array.isArray(report.issues));
  assert.equal(r.status,report.safe_to_attempt?0:2);
  assert.doesNotMatch(r.stdout,/api.?key|bearer|secret|token/i);
});
test("Emulator preflight checks hardware acceleration and stricter capacity",()=>{
  const r=spawnSync("python3",[script,"--mode","emulator","--json"],{
    encoding:"utf8",timeout:10000
  });
  assert.ok([0,2].includes(r.status),r.stderr);
  const report=JSON.parse(r.stdout);
  assert.equal(report.minimum_memory_gib,6);
  assert.equal(report.minimum_disk_gib,12);
  assert.equal(typeof report.kvm_available,"boolean");
});
