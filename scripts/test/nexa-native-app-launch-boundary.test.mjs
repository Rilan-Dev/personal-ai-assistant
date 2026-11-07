import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';

const base='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=path=>readFileSync(path,'utf8');

test('only exact native same-generation Accessibility evidence advances a launched app',()=>{
  const native=read(base+'DeviceControlModule.kt');
  const observe=read(base+'NexaAppLaunchObservation.kt');
  assert.match(native,/private fun resolveObservedAppLaunch\(/);
  assert.match(native,/NexaNativeActionSafety\.approvalFenceCurrent\(/);
  assert.match(native,/NexaAppLaunchObservation\.expectedVisibleNow\(/);
  assert.match(native,/"destination_package_observed" else "not_observed"/);
  assert.match(observe,/root\.packageName\?\.toString\(\)/);
  assert.match(observe,/root\.isVisibleToUser/);
  assert.match(observe,/!ScreenAssistState\.connected/);
  assert.doesNotMatch(native,/fun observeLaunchedAppPackage\(/);
});

test('JS cannot reclassify a native unverified launch after Pause or app switch',()=>{
  const bridge=read('apps/mobile/src/native/androidTools.ts');
  assert.match(bridge,/const result=await getBridge\(\)\.openSupportedApp\(/);
  assert.match(bridge,/\["not_observed","destination_package_observed"\]/);
  assert.match(bridge,/return \{status:"success",data:result\}/);
  assert.doesNotMatch(bridge,/observeLaunchedAppPackage/);
  assert.doesNotMatch(bridge,/setTimeout\(resolve,1500\)/);
});
