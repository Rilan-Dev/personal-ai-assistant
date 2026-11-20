import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('Android JS bundle cache tracks shared agent-core and agent-contracts sources',async()=>{
  const gradle=await readFile(new URL('../../apps/mobile/android/app/build.gradle',import.meta.url),'utf8');
  const bundleInputs=gradle.match(/if\s*\(task\.name\s*==~\s*\/createBundle\.\*JsAndAssets\/\)\s*\{([\s\S]*?)\n\s*\}/)?.[1]??'';
  assert.match(bundleInputs,/inputs\.files\(/,
    'workspace dependency files must participate in the bundle task cache key');
  assert.match(bundleInputs,/packages\/agent-core\/src/);
  assert.match(bundleInputs,/packages\/agent-contracts\/src/);
  assert.match(bundleInputs,/withPropertyName\(["']paaWorkspaceBundleSources["']\)/);
});
