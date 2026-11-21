import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const gradle=readFileSync('apps/mobile/android/app/build.gradle','utf8');
const runner=readFileSync('scripts/qa/run-nexa-usb-instrumentation.ps1','utf8');

test('physical USB QA uses the one isolated .debug Nexa app identity',()=>{
  assert.match(gradle,/applicationId "dev\.rilandev\.personalaiassistant"/);
  assert.match(gradle,/applicationIdSuffix '\.debug'/);
  assert.doesNotMatch(gradle,/paaIsolatedPhysicalQa|physicalqa2?/i,
    'physical QA must not build a second Nexa application package');
  assert.match(runner,/\$package = 'dev\.rilandev\.personalaiassistant\.debug'/,
    'USB instrumentation must target the same .debug app as Maestro and the Nexa CLI');
});

test('single .debug app supports Metro live mode and a self-contained QA bundle',()=>{
  const reactBlock=gradle.slice(gradle.indexOf('react {'),
    gradle.indexOf('/**\n * Set this to true in release'));
  assert.match(gradle,/def useEmbeddedDebugBundle[\s\S]*?paaUseEmbeddedJsBundle/);
  assert.match(gradle,/PAA_USE_EMBEDDED_JS_BUNDLE[\s\S]*?useEmbeddedDebugBundle/);
  assert.match(reactBlock,/liveDebugBuild[\s\S]*?debuggableVariants = \["debug"\]/,
    'live .debug must use Metro so JS changes appear without reinstalling');
  assert.match(reactBlock,/useEmbeddedDebugBundle[\s\S]*?debuggableVariants = \[\]/,
    'the same .debug identity can embed JavaScript for offline instrumentation');
  assert.match(gradle,/applicationIdSuffix '\.debug'/);
});
