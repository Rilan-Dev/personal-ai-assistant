import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync} from "node:fs";

test("ALL GitHub Actions workflows require explicit owner-dispatched runs",()=>{
  const dir=".github/workflows";
  const files=readdirSync(dir).filter(file=>/\.ya?ml$/.test(file));
  assert.ok(files.length>=5);
  for(const file of files) {
    const yaml=readFileSync(dir+"/"+file,"utf8");
    const header=yaml.split(/^jobs:/m)[0];
    assert.match(header,/^on:\s*\n\s+workflow_dispatch:\s*$/m,file);
    assert.doesNotMatch(header,/^\s+(push|pull_request|schedule|workflow_run|repository_dispatch|create|delete):/m,file);
    assert.doesNotMatch(header,/^\s+branches:/m,file);
  }
});
test("Debug signing secret is gated to manual main-branch runs",()=>{
  const file=readFileSync(".github/workflows/android-debug.yml","utf8");
  assert.match(file,/github\.event_name == 'workflow_dispatch'/);
  assert.match(file,/github\.ref == 'refs\/heads\/main'/);
  assert.doesNotMatch(file,/github\.event_name == 'push'/);
});
test("manual Android debug compile builds native Android instrumentation tests too",()=>{
  const yml=readFileSync(".github/workflows/android-debug.yml","utf8");
  assert.match(yml,/:app:assembleDebug :app:assembleAndroidTest --no-daemon/);
  assert.match(yml,/workflow_dispatch:/);
});
test("manual emulator acceptance validates harness before starting emulator",()=>{
  const yml=readFileSync(".github/workflows/android-emulator.yml","utf8");
  assert.match(yml,/npm test/);
  assert.match(yml,/npm run typecheck/);
  assert.match(yml,/:app:connectedDebugAndroidTest/);
});
test("manual release APK contains source-commit provenance and signer verification",()=>{
  const yml=readFileSync(".github/workflows/android-release-test.yml","utf8");
  assert.match(yml,/\$GITHUB_SHA.*release-test-source-sha\.txt/);
  assert.match(yml,/release-test-sha256\.txt/);
  assert.match(yml,/release-test-verification\.txt/);
  assert.match(yml,/apksigner_bin.*verify/);
  assert.match(yml,/if-no-files-found: error/);
});
