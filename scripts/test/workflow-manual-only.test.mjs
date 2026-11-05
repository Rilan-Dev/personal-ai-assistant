import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync} from "node:fs";

const dir=".github/workflows";
const files=readdirSync(dir).filter(file=>/\.ya?ml$/.test(file));
const promotion="android-main-promotion.yml";
const source=file=>readFileSync(dir+"/"+file,"utf8");
const triggerHeader=file=>source(file).split(/^jobs:/m)[0];

test("all GitHub Actions remain explicit owner-triggered; branch synchronization never starts release",()=>{
  assert.ok(files.length>=6);
  assert.ok(files.includes(promotion));
  for(const file of files){
    const yaml=triggerHeader(file);
    assert.match(yaml,/^on:\s*\n/m,file);
    assert.match(yaml,/^\s+workflow_dispatch:\s*$/m,file);
    assert.doesNotMatch(yaml,/^\s+(pull_request|schedule|workflow_run|repository_dispatch|create|delete):/m,file);
    assert.doesNotMatch(yaml,/^\s+push:\s*$/m,file);
    assert.doesNotMatch(yaml,/^\s+branches:/m,file);
  }
});

test("main release runs test, emulator and signed APK serially on one runner",()=>{
  const file=source(promotion);
  const node=file.indexOf("npm test");
  const emulator=file.indexOf("Gate release on native Keystore");
  const release=file.indexOf(":app:assembleRelease");
  const upload=file.indexOf("Upload installable release-test APK");
  assert.ok(node>0&&emulator>node&&release>emulator&&upload>release);
  assert.match(file,/android-emulator-runner@v2/);
  assert.match(file,/bash scripts\/ci\/run-nexa-emulator-acceptance\.sh/);
  const runner=readFileSync("scripts/ci/run-nexa-emulator-acceptance.sh","utf8");
  assert.match(runner,/:app:connectedDebugAndroidTest/);
  assert.match(runner,/:app:assembleRelease/);
  assert.match(file,/release-test-sha256\.txt/);
  assert.match(file,/release-test-source-sha\.txt/);
  assert.match(file,/cancel-in-progress: true/);
  assert.match(file,/if-no-files-found: error/);
});

test("owner-only real-provider test requires a chosen live model without fixed presets",()=>{
  const file=source("agent-provider-acceptance.yml");
  assert.match(file,/inputs:/);
  assert.match(file,/model:/);
  assert.match(file,/inputs\.model \|\| vars\.AGENT_LLM_MODEL/);
  assert.match(file,/Missing AGENT_LLM_API_KEY secret/);
  assert.match(file,/Enter a model when running the workflow/);
  assert.ok(file.indexOf("Check owner-provided model")<file.indexOf("npm ci"));
  assert.doesNotMatch(file,/gemini-\d|gpt-\d|llama-\d/);
});

test("Debug signing secret is gated to manual main-branch runs",()=>{
  const file=source("android-debug.yml");
  assert.match(file,/github\.event_name == 'workflow_dispatch'/);
  assert.match(file,/github\.ref == 'refs\/heads\/main'/);
  assert.doesNotMatch(file,/github\.event_name == 'push'/);
});
test("manual Android debug compile builds native Android instrumentation tests too",()=>{
  const yml=source("android-debug.yml");
  assert.match(yml,/:app:assembleDebug :app:assembleAndroidTest --no-daemon/);
  assert.match(yml,/workflow_dispatch:/);
});
test("manual emulator acceptance validates harness before starting emulator",()=>{
  const yml=source("android-emulator.yml");
  assert.match(yml,/npm test/);
  assert.match(yml,/npm run typecheck/);
  assert.match(yml,/:app:connectedDebugAndroidTest/);
});
test("manual release APK contains source-commit provenance and signer verification",()=>{
  const yml=source("android-release-test.yml");
  assert.match(yml,/\$GITHUB_SHA.*release-test-source-sha\.txt/);
  assert.match(yml,/release-test-sha256\.txt/);
  assert.match(yml,/release-test-verification\.txt/);
  assert.match(yml,/bash scripts\/signing\/verify-release-apk\.sh/);
  assert.match(readFileSync("scripts/signing/verify-release-apk.sh","utf8"),/apksigner/);
  assert.match(yml,/if-no-files-found: error/);
});

test("manual native messaging QA checks out exact dispatched SHA, not old feature branch",()=>{
  const yml=source("nexa-message-native-qa.yml");
  assert.match(yml,/github\.event_name == 'workflow_dispatch' && github\.sha/);
  assert.match(yml,/test "\$\(git rev-parse HEAD\)" = "\$GITHUB_SHA"/);
  assert.match(yml,/github\.event_name == 'issue_comment'/);
  assert.doesNotMatch(yml,/^\s+ref:\s+feature\/nexa-message-routing-input-20261104\s*$/m);
});
