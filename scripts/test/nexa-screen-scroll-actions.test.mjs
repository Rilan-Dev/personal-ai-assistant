import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const source=readFileSync(resolve(root,
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaScreenScroll.kt'),'utf8');
const deviceControl=readFileSync(resolve(root,
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt'),'utf8');

test('vertical scrolling accepts directional and logical Android accessibility actions',()=>{
  const up=source.match(/"up"\s*->\s*listOf\(([^)]*)\)/s)?.[1];
  const down=source.match(/"down"\s*->\s*listOf\(([^)]*)\)/s)?.[1];
  assert.ok(up,'up should declare its supported accessibility actions');
  assert.ok(down,'down should declare its supported accessibility actions');
  assert.ok(up.indexOf('ACTION_SCROLL_UP')<up.indexOf('ACTION_SCROLL_BACKWARD'));
  assert.ok(down.indexOf('ACTION_SCROLL_DOWN')<down.indexOf('ACTION_SCROLL_FORWARD'));
});

test('scroll preflight and confirmation traverse the complete bounded accessibility tree',()=>{
  const budget=source.match(/private const val MAX_SCROLL_NODES\s*=\s*(\d+)/)?.[1];
  assert.ok(budget,'scroll traversal should use one named safety bound');
  assert.ok(Number(budget)>=512,'the 284-node live Nexa Tools hierarchy must fit under the bound');
  const prepare=source.slice(source.indexOf('fun prepare'),source.indexOf('fun isPending'));
  const reviewed=source.slice(source.indexOf('private fun reviewedTarget'),source.indexOf('fun confirm'));
  assert.match(prepare,/visited\s*<\s*MAX_SCROLL_NODES/);
  assert.match(reviewed,/visited\s*<\s*MAX_SCROLL_NODES/);
  assert.match(reviewed,/queue\.isEmpty\(\)\s*&&\s*matching\s*==\s*1/);
});

test('scrolling chooses the focused Nexa window before stale background app windows',()=>{
  const safeRoot=source.slice(source.indexOf('private fun safeRoot'),source.indexOf('fun prepare'));
  assert.ok(safeRoot.indexOf('assistantOwnerWindowRoot')<safeRoot.indexOf('externalWindowRoot'));
});

test('debug observation uses Nexa while its activity is focused, before background app roots',()=>{
  const capture=deviceControl.slice(deviceControl.indexOf('fun capturePowerShellScreen'),
    deviceControl.indexOf('fun capturePowerShellAccessibilityTree'));
  const policy=deviceControl.slice(deviceControl.indexOf('private fun shouldObserveFocusedNexaWindow'),
    deviceControl.indexOf('fun capturePowerShellScreen'));
  assert.match(policy,/BuildConfig\.DEBUG\s*&&\s*context\.currentActivity\?\.hasWindowFocus\(\)\s*==\s*true/);
  assert.ok(capture.indexOf('assistantOwnerWindowRoot')<capture.indexOf('externalWindowRoot'));
  assert.ok(capture.indexOf('shouldObserveFocusedNexaWindow()')<capture.indexOf('externalWindowRoot'));
});

test('post-action observations keep the same focused-owner-first screen context',()=>{
  const capture=deviceControl.slice(deviceControl.indexOf('private fun freshAssistantScreenObservation'),
    deviceControl.indexOf('private fun finishUiStepAfterObservation'));
  assert.ok(capture.indexOf('assistantOwnerWindowRoot')<capture.indexOf('externalWindowRoot'));
  assert.ok(capture.indexOf('shouldObserveFocusedNexaWindow()')<capture.indexOf('externalWindowRoot'));
});

