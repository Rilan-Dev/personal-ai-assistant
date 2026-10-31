import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

test("dependency audit is reported without hiding unresolved high findings", () => {
  const dir = mkdtempSync(join(tmpdir(), "paa-security-audit-"));
  try {
    const input=join(dir,"input.json"),out=join(dir,"summary.md");
    writeFileSync(input,JSON.stringify({
      metadata:{vulnerabilities:{critical:0,high:2,moderate:1,low:0}},
      vulnerabilities:{
        "@expo/cli":{severity:"high",isDirect:false,via:["zod"]},
        "example-lib":{severity:"moderate",isDirect:true,via:[]}
      }
    }));
    const result=spawnSync(process.execPath,["scripts/security/audit-summary.mjs",input,out],{encoding:"utf8"});
    assert.equal(result.status,0,result.stderr);
    const report=readFileSync(out,"utf8");
    assert.match(report,/High:\*\* 2/);
    assert.match(report,/\@expo\/cli \| high/);
    assert.match(report,/Open advisories require risk assessment/);
  } finally { rmSync(dir,{recursive:true,force:true}); }
});
test("malformed or missing npm audit evidence cannot be silently accepted", () => {
  const dir = mkdtempSync(join(tmpdir(), "paa-security-audit-"));
  try {
    const result=spawnSync(process.execPath,["scripts/security/audit-summary.mjs",join(dir,"missing.json"),join(dir,"summary.md")],{encoding:"utf8"});
    assert.notEqual(result.status,0);
    assert.match(result.stderr,/Invalid npm audit report/);
  } finally { rmSync(dir,{recursive:true,force:true}); }
});
