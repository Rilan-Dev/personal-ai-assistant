import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';

const gradle=readFileSync('apps/mobile/android/app/build.gradle','utf8');
const identity=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaBuildIdentityAndroidTest.kt','utf8');

test('second QA identity is explicitly opt-in and cannot downgrade an existing app',()=>{
  assert.match(gradle,/findProperty\('paaIsolatedPhysicalQaV2'\)/);
  assert.match(gradle,/applicationIdSuffix '\.physicalqa2'/);
  assert.match(gradle,/findProperty\('paaIsolatedPhysicalQa'\)/);
  assert.match(gradle,/applicationIdSuffix '\.physicalqa'/);
  assert.match(gradle,/findProperty\('paaInstallDebugAlongsideRelease'\)/);
  assert.match(gradle,/applicationIdSuffix '\.debug'/);
  const reactBlock=gradle.slice(gradle.indexOf('react {'),gradle.indexOf('enableBundleCompression'));
  assert.match(reactBlock,/paaIsolatedPhysicalQaV2[\s\S]*?debuggableVariants = \[\]/,
    'isolated QA2 must bundle JS; never require a Metro server on the device');
  const debugBlock=gradle.slice(gradle.indexOf('buildTypes {'));
  assert.ok(debugBlock.indexOf("findProperty('paaIsolatedPhysicalQaV2')")<
    debugBlock.indexOf("findProperty('paaIsolatedPhysicalQa')"),
    'the new test-only slot must have a deterministic applicationId priority');
});

test('reviewed revision instrumentation accepts only explicit disposable QA app IDs',()=>{
  assert.match(identity,/assertEquals\("USB QA APK was not assembled from the selected source"/);
  assert.match(identity,/expected,actual/);
  assert.match(identity,/"dev\.rilandev\.personalaiassistant\.physicalqa"/);
  assert.match(identity,/"dev\.rilandev\.personalaiassistant\.physicalqa2"/);
  assert.doesNotMatch(identity,/\bBuildConfig\.APPLICATION_ID\s*==\s*"dev\.rilandev\.personalaiassistant\.debug"/);
});

test('standalone synthetic instrumentation process bundles the Kotlin runtime',()=>{
  assert.match(gradle,/androidTestImplementation\("org\.jetbrains\.kotlin:kotlin-stdlib:2\.2\.10"\)/);
});
