import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';

const gradle=readFileSync('apps/mobile/android/app/build.gradle','utf8');
const identity=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaBuildIdentityAndroidTest.kt','utf8');
const cli=readFileSync('nexa.ps1','utf8');

test('only the isolated .debug app can be used for physical Nexa QA',()=>{
  assert.match(gradle,/applicationIdSuffix '\.debug'/);
  assert.doesNotMatch(gradle,/paaIsolatedPhysicalQa|paaLivePhysicalQaV2|physicalqa2?/i,
    'obsolete physical QA packages must not be buildable');
  assert.match(identity,/assertEquals\("Physical QA must use the single isolated debug app",\s*"dev\.rilandev\.personalaiassistant\.debug",\s*BuildConfig\.APPLICATION_ID\)/);
  assert.doesNotMatch(identity,/personalaiassistant\.physicalqa2?/);
  assert.match(cli,/\[string\]\$PackageId = 'dev\.rilandev\.personalaiassistant\.debug'/);
  assert.match(cli,/PackageId -ne 'dev\.rilandev\.personalaiassistant\.debug'/,
    'the Nexa bridge must not silently connect to another QA package');
});

test('separate-process AndroidTest fixtures remain self-contained Java',()=>{
  for(const name of ['NexaChatListQaActivity','NexaInputQaActivity']) {
    const base='apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/'+name;
    let kotlinExists=true;
    try { readFileSync(base+'.kt'); } catch { kotlinExists=false; }
    assert.equal(kotlinExists,false,
      'No Kotlin fixture may crash in the independent test-app process');
    const text=readFileSync(base+'.java','utf8');
    assert.match(text,/extends Activity/);
  }
  assert.doesNotMatch(gradle,/androidTestImplementation\("org\.jetbrains\.kotlin:kotlin-stdlib:/);
});
