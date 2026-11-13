import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const gradle=readFileSync('apps/mobile/android/app/build.gradle','utf8');

test('USB device QA gets a separately installed package without altering release/debug identities',()=>{
  assert.match(gradle,/applicationId "dev\.rilandev\.personalaiassistant"/);
  assert.match(gradle,/findProperty\('paaIsolatedPhysicalQa'\)/);
  assert.match(gradle,/applicationIdSuffix '\.physicalqa'/);
  assert.match(gradle,/else if \(\(findProperty\('paaInstallDebugAlongsideRelease'\)/);
  assert.match(gradle,/applicationIdSuffix '\.debug'/);
  assert.match(gradle,/signingConfig signingConfigs\.debug/);
  assert.match(gradle,/if \(file\('testing-release\.keystore'\)\.exists\(\)\)/);
  assert.match(gradle,/debuggableVariants = \[\]/);
});

test('physical QA debug build embeds its own JS instead of assuming a live Metro server',()=>{
  const reactBlock=gradle.slice(gradle.indexOf('react {'),
    gradle.indexOf('/**\n * Set this to true in release'));
  assert.match(reactBlock,/paaIsolatedPhysicalQa/);
  assert.match(reactBlock,/debuggableVariants = \[\]/);
  assert.match(reactBlock,/bundleCommand = "export:embed"/);
});
