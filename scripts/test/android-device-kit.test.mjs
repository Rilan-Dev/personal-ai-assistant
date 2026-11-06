import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import {python3Command} from "./python-command.mjs";

test("physical Android UAT kit is opt-in, reads no PII, and never controls hardware automatically", () => {
  const source = readFileSync("scripts/acceptance/android_device.py","utf8");
  const ignore = readFileSync(".gitignore","utf8");
  assert.match(source,/--check/);
  assert.match(source,/--walkthrough/);
  assert.match(source,/adb\("shell", "pm", "path", PACKAGE\)/);
  assert.match(source,/input\("Observed outcome \[pass\/fail\/skip\]: "\)/);
  assert.match(source,/origin|evidence/);
  assert.match(ignore,/device-acceptance-\*\.json/);
  assert.match(ignore,/__pycache__\//);
  assert.doesNotMatch(source,/adb\("shell", "pm", "grant"/);
  assert.doesNotMatch(source,/input\(".*serial/);
  assert.doesNotMatch(source,/am", "broadcast"|sms|content", "query"/);
});
test("owner acceptance kit fails closed when no ADB/phone is connected", () => {
  const windows=process.platform==="win32";
  const python=python3Command();
  const result = spawnSync(python,["scripts/acceptance/android_device.py","--check"],
    {encoding:"utf8",env:{...process.env,PATH:windows?"":"/usr/bin:/bin"}});
  // No authorized device is connected on CI; invalid setups must not claim success.
  assert.equal(result.status,2);
  assert.match(result.stderr,/ADB missing|Connect exactly one|ADB could not discover devices|App not installed/);
});
test("Android debug APK can use a stable owner-held signing secret without publishing key bytes", () => {
  const workflow=readFileSync(".github/workflows/android-debug.yml","utf8");
  const ignore=readFileSync(".gitignore","utf8");
  assert.match(workflow,/secrets\.PAA_DEBUG_KEYSTORE_B64/);
  assert.match(workflow,/github\.event_name == 'workflow_dispatch'/);
  assert.match(workflow,/github\.ref == 'refs\/heads\/main'/);
  assert.match(workflow,/base64 --decode > "\$path"/);
  assert.match(workflow,/keytool -list -keystore "\$path"/);
  assert.match(workflow,/if \[\[ -n "\$PAA_DEBUG_KEYSTORE_B64" \]\]/);
  assert.match(workflow,/Temporary DEBUG signer used/);
  assert.doesNotMatch(workflow,/echo "\$PAA_DEBUG_KEYSTORE_B64"/);
  assert.match(ignore,/\*\.keystore/);
});
