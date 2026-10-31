import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const read = path => readFileSync(path,"utf8");

test("Android AI keys use Keystore-backed native vault, not plaintext storage",()=>{
  const kotlin=read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/AgentJournalModule.kt");
  const bridge=read("apps/mobile/src/native/providerVault.ts");
  const app=read("apps/mobile/App.tsx");
  assert.match(kotlin,/saveProviderSettings\(data: String, p: Promise\)/);
  assert.match(kotlin,/loadProviderSettings\(p: Promise\)/);
  assert.match(kotlin,/clearProviderSettings\(p: Promise\)/);
  assert.match(kotlin,/write\(name\("private", "provider-config"\), data\)/);
  assert.match(kotlin,/AndroidKeyStore/);
  assert.match(kotlin,/AES\/GCM\/NoPadding/);
  assert.match(bridge,/validateProviderSettings\(JSON\.parse\(value\)\)/);
  assert.match(app,/secureTextEntry editable/);
  assert.match(app,/AI Configuration & API Key/);
  assert.match(app,/saveProviderToVault\(config\)/);
  assert.match(app,/clearProviderFromVault\(\)/);
  assert.doesNotMatch(bridge,/import.*AsyncStorage|localStorage\.|console\.log/);
  assert.doesNotMatch(kotlin,/Log\.(d|i|v)\([^\n]*apiKey/);
});
test("release workflow produces signed optimized APK with separate test key",()=>{
  const workflow=read(".github/workflows/android-release-test.yml");
  const gradle=read("apps/mobile/android/app/build.gradle");
  assert.match(workflow,/workflow_dispatch:/);
  assert.match(workflow,/branches: \[main\]/);
  assert.match(workflow,/npm test/);
  assert.match(workflow,/npm run typecheck/);
  assert.match(workflow,/:app:assembleRelease/);
  assert.match(workflow,/apksigner/);
  assert.match(workflow,/personal-ai-assistant-release-test-apk/);
  assert.match(workflow,/PAA_TEST_RELEASE_KEYSTORE_B64/);
  assert.match(workflow,/if-no-files-found: error/);
  assert.match(gradle,/testing-release\.keystore/);
  assert.match(gradle,/signingConfigs\.testRelease : null/);
  assert.doesNotMatch(gradle,/release\s*\{[^}]*signingConfig signingConfigs\.debug/s);
});
