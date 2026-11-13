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
  assert.ok(gradle.indexOf("findProperty('paaIsolatedPhysicalQaV2')")<
    gradle.indexOf("findProperty('paaIsolatedPhysicalQa')"),
    'the new test-only slot must have a deterministic priority');
});

test('reviewed revision instrumentation accepts only explicit disposable QA app IDs',()=>{
  assert.match(identity,/assertEquals\("USB QA APK was not assembled from the selected source"/);
  assert.match(identity,/expected,actual/);
  assert.match(identity,/"dev\.rilandev\.personalaiassistant\.physicalqa"/);
  assert.match(identity,/"dev\.rilandev\.personalaiassistant\.physicalqa2"/);
  assert.doesNotMatch(identity,/\bBuildConfig\.APPLICATION_ID\s*==\s*"dev\.rilandev\.personalaiassistant\.debug"/);
});
