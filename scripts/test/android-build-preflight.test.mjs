import test from "node:test";
import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {readFileSync,mkdtempSync,writeFileSync,chmodSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {delimiter,join} from "node:path";

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

test("JDK version check runs under Python 3.6 using portable subprocess arguments",()=>{
  const work=mkdtempSync(join(tmpdir(),"paa-preflight-java-"));
  try {
    const windows=process.platform==="win32";
    const fake=join(work,windows?"java.cmd":"java");
    writeFileSync(fake,windows
      ? "@echo off\r\necho openjdk version \"21.0.12.1\" 1>&2\r\n"
      : ["#!/bin/sh", "echo 'openjdk version \"21.0.12.1\"' >&2", ""].join("\n"));
    if(!windows)chmodSync(fake,0o755);
    const result=spawnSync("python3",[script,"--mode","compile","--json"],{
      encoding:"utf8",timeout:10000,
      env:{...process.env,PATH:work+delimiter+process.env.PATH}
    });
    assert.ok([0,2].includes(result.status),result.stderr);
    assert.equal(JSON.parse(result.stdout).java_major,21);
    assert.doesNotMatch(result.stderr,/TypeError|capture_output|traceback/i);
  } finally {
    rmSync(work,{recursive:true,force:true});
  }
});
