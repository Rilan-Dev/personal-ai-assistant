import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const repo = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const read = (path) => readFileSync(join(repo, path), 'utf8');
const appPermissions = read('.maestro/physical-qa/00-device-preflight.yaml');
const accessibility = read('.maestro/physical-qa/01-enable-accessibility-services.yaml');
const readyCheck = read('.maestro/physical-qa/02-ready-check.yaml');
const runner = read('scripts/test/run-physical-qa-preflight.ps1');
const flows = [appPermissions, accessibility, readyCheck];

test('physical preflight is split into short QA2-only Maestro stages', () => {
  for (const flow of flows) {
    assert.match(flow, /^appId: dev\.rilandev\.personalaiassistant\.physicalqa2/m);
    assert.match(flow, /- physical\n/);
    assert.match(flow, /- no-external-action\n/);
    assert.doesNotMatch(flow, /Send message|WhatsApp|SMS|tap.*Submit/i);
    assert.doesNotMatch(flow, /clearState:\s*true/);
  }
});

test('app and overlay stage grants requested runtime permissions and enables QA controls', () => {
  assert.match(appPermissions, /permissions:\s*\n\s+all: allow/);
  for (const control of [
    'App launching, Open camera.*',
    'Reviewed multi-step screen actions, Tap safe named controls.*',
    'Read safe visible-control metadata, Requires enabled Screen Assist.*',
  ]) assert.match(appPermissions, new RegExp(control.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(appPermissions, /Allow display over apps/);
  const expandOptions = appPermissions.indexOf('- tapOn: "Show optional permissions and features"');
  const overlayScroll = appPermissions.indexOf('- scrollUntilVisible:', expandOptions);
  const overlayScrollEnd = appPermissions.indexOf('timeout: 20000', overlayScroll);
  const afterExpand = appPermissions.slice(expandOptions, overlayScrollEnd);
  assert.ok(expandOptions >= 0 && overlayScroll > expandOptions);
  assert.match(afterExpand, /floating bubble|Allow display over apps/);
  assert.doesNotMatch(afterExpand, /extendedWaitUntil/,
    'the expand control scrolls out of view when optional permission rows appear');
});

test('Accessibility stage enables both isolated QA2 services through Android Settings', () => {
  assert.match(accessibility, /Nexa Screen Assist QA2/);
  assert.match(accessibility, /Nexa Launcher Navigation QA2/);
  const resetToolsScroll = accessibility.indexOf('- tapOn: "Chat tab"');
  const reopenTools = accessibility.indexOf('- tapOn: "Tools tab"', resetToolsScroll + 1);
  const openSettings = accessibility.indexOf('- tapOn: "Open Android Accessibility Settings button"');
  assert.ok(resetToolsScroll >= 0 && reopenTools > resetToolsScroll && openSettings > reopenTools,
    'remount Tools after a persistent deep scroll before searching for Screen Assist');
  assert.match(accessibility, /id: "android:id\/switch_widget"[\s\S]*?checked: true/);
  assert.match(accessibility, /tapOn: "Allow"/);
});

test('postflight verifies Android permissions and binding after Maestro releases UiAutomation', () => {
  assert.match(runner, /--device=\$DeviceId/);
  assert.match(runner, /--no-reinstall-driver/);
  assert.match(runner, /dumpsys package/);
  assert.match(runner, /SYSTEM_ALERT_WINDOW/);
  assert.match(runner, /dumpsys accessibility/);
  assert.match(runner, /Crashed services/);
  assert.doesNotMatch(runner, /settings put secure/);
  assert.doesNotMatch(runner, /pm check-permission/);
});

test('overlay postflight accepts default app-op only when PackageManager confirms the grant', () => {
  assert.ok(runner.includes("$overlayPermissionGranted = $packageDump -match 'android\\.permission\\.SYSTEM_ALERT_WINDOW:\\s+granted=true'"));
  assert.ok(runner.includes("$overlayExplicitlyDenied = $overlayState -match 'SYSTEM_ALERT_WINDOW:\\s+(deny|ignore)'"));
  assert.ok(runner.includes('if ($LASTEXITCODE -ne 0 -or -not $overlayPermissionGranted -or $overlayExplicitlyDenied)'));
});

test('Accessibility grant runs after every other Maestro stage so teardown cannot revert it', () => {
  const readyCheck = runner.indexOf('02-ready-check.yaml');
  const accessibility = runner.indexOf('01-enable-accessibility-services.yaml');
  const postflight = runner.indexOf('$requiredRuntimePermissions =');
  assert.ok(readyCheck >= 0 && accessibility > readyCheck,
    'the last Maestro UI session must enable Accessibility services');
  assert.ok(postflight > runner.indexOf('& $MaestroCli test'),
    'Android state must be verified only after the final Maestro session releases UiAutomation');
});

test('ready check returns the QA2 app to its Screen Assistant tools without sampling connection mid-Maestro', () => {
  assert.match(readyCheck, /Nexa · Tools/);
  assert.match(readyCheck, /Screen assistant/);
  assert.match(readyCheck, /Open Android Accessibility Settings button/);
  assert.match(readyCheck, /scrollUntilVisible:\s*\n\s+element: "Open Android Accessibility Settings button"\s*\n\s+direction: DOWN\s*\n\s+visibilityPercentage: 50\s*\n\s+timeout: 60000/,
    'the accessibility launch button sits below the Screen Assistant heading on shorter viewports');
  assert.doesNotMatch(readyCheck, /Nexa Screen Assist connected/);
});
