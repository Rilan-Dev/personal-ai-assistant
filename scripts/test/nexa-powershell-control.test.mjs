import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

let control = null;
try {
  control = await import('../../packages/agent-core/src/nexa-powershell-control.ts');
} catch {}

test('PowerShell control parser accepts status and rejects malformed envelope', () => {
  assert.ok(control, 'nexaPowerShellControl.ts should exist');
  assert.deepEqual(
    control.parsePowerShellRequest({requestId:'12345678-1234-4123-8123-123456789abc',
      method:'GET',path:'/v1/status',body:''}),
    {kind:'status'}
  );
  assert.equal(control.parsePowerShellRequest({method:'POST',path:'/v1/tap',body:{}}).kind,'error');
});

test('screen read and interactions require explicit one-command confirmation', () => {
  assert.ok(control, 'nexaPowerShellControl.ts should exist');
  for (const envelope of [
    {requestId:'12345678-1234-4123-8123-123456789abc',method:'POST',path:'/v1/observe',body:{format:'json'}},
    {requestId:'12345678-1234-4123-8123-123456789abc',method:'POST',path:'/v1/tap',body:{snapshotId:'12345678-1234-4123-8123-123456789abc',index:2}}
  ]) {
    assert.equal(control.parsePowerShellRequest(envelope).error,'CONFIRM_REQUIRED');
  }
});

test('PowerShell tool allowlist excludes outbound sends and exposes app read/control tools', () => {
  assert.ok(control, 'nexaPowerShellControl.ts should exist');
  assert.equal(control.isPowerShellToolAllowed('screen.scroll'),true);
  assert.equal(control.isPowerShellToolAllowed('screen.tap_index'),true);
  assert.equal(control.isPowerShellToolAllowed('screen.inspect'),true);
  assert.equal(control.isPowerShellToolAllowed('device.battery_status'),true);
  assert.equal(control.isPowerShellToolAllowed('device.visible_controls'),false);
  assert.equal(control.isPowerShellToolAllowed('screen.tap_label'),false);
  assert.equal(control.isPowerShellToolAllowed('screen.send_whatsapp'),false);
  assert.equal(control.isPowerShellToolAllowed('screen.send_message'),false);
});

test('confirmed PowerShell invocation can request the full accessibility hierarchy tool', () => {
  assert.ok(control, 'nexaPowerShellControl.ts should exist');
  const request = control.parsePowerShellRequest({
    requestId:'12345678-1234-4123-8123-123456789abc',method:'POST',path:'/v1/invoke',
    body:{toolId:'screen.accessibility_tree',input:{},confirmed:true}
  });
  assert.deepEqual(request,{
    kind:'invoke',toolId:'screen.accessibility_tree',input:{},confirmed:true
  });
  assert.equal(control.isPowerShellToolAllowed('screen.accessibility_tree'),true);
  assert.equal(control.requiresPowerShellConfirmation({
    id:'screen.accessibility_tree',risk:'high',hasExternalSideEffect:true,
    capabilities:['device.observe']
  }),true);
});

test('confirmation policy requires confirmation for observation and side effects', () => {
  assert.ok(control, 'nexaPowerShellControl.ts should exist');
  assert.equal(control.requiresPowerShellConfirmation({id:'screen.inspect',risk:'medium',hasExternalSideEffect:true}),true);
  assert.equal(control.requiresPowerShellConfirmation({id:'device.battery_status',risk:'low',hasExternalSideEffect:false}),false);
});

test('named-key refusal preserves a safe native reason instead of hiding the failing guard', async () => {
  const source = await readFile(new URL(
    '../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt',
    import.meta.url
  ), 'utf8');
  assert.match(source, /promise\.reject\("KEY_ACTION_REFUSED",\s*outcome\)/,
    'the PowerShell caller needs the fixed native refusal reason to diagnose a rejected Back/Home/Notifications action');
});

test('PowerShell client advertises the full command set and contains no shell/raw-input dispatch', async () => {
  const script = await readFile(new URL('../../nexa.ps1', import.meta.url), 'utf8');
  for (const command of ['status','tools','observe','tree','tap','scroll','key','invoke']) {
    assert.match(script, new RegExp('\\b' + command + '\\b'));
  }
  const code = script.replace(/<#[\s\S]*?#>/g, '').replace(/^\s*#.*$/gm, '');
  assert.match(code,/forward/);
  assert.match(code,/PackageId/);
  assert.match(code,/nexa-qa-control-/);
  assert.match(code,/TcpListener/);
  assert.match(code,/\$status\.data/);
  assert.match(code,/status request failed/);
  assert.match(code,/bring it forward once and retry/i,
    'an unreachable app bridge should explain how to recover without using shell input');
  assert.doesNotMatch(code,/^\s*&?\s*adb(?:\.exe)?\s+shell\b/im);
  assert.doesNotMatch(code,/input\s+(?:tap|swipe|keyevent)|uiautomator/i);
});

test('PowerShell tree command is accepted and only reaches the Nexa bridge', (t) => {
  const candidates = process.platform === 'win32' ? ['pwsh', 'powershell.exe'] : ['pwsh'];
  const shell = candidates.find((candidate) => {
    const probe = spawnSync(candidate, ['-NoProfile', '-Command', 'exit 0'], {
      encoding: 'utf8', windowsHide: true
    });
    return !probe.error && probe.status === 0;
  });
  if (!shell) {
    t.skip('PowerShell is unavailable in this environment');
    return;
  }
  const scriptPath = fileURLToPath(new URL('../../nexa.ps1', import.meta.url));
  const quotedScriptPath = `'${scriptPath.replace(/'/g, "''")}'`;
  const result = spawnSync(shell, ['-NoProfile', '-Command',
    `& ${quotedScriptPath} tree -Serial qa-validation-device -Format json -Confirm`], {
    encoding: 'utf8', windowsHide: true
  });
  assert.equal(result.error, undefined, result.error?.message);
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  assert.doesNotMatch(output, /Cannot validate argument on parameter 'Command'/i,
    'tree must be a supported top-level PowerShell command');
  assert.match(output, /ADB port forwarding failed|Nexa app control bridge is unavailable|adb was not found/i,
    'the command must proceed to the Nexa-only bridge path without device shell input');
});

test('PowerShell tap refuses an empty target index before contacting the device', (t) => {
  const candidates = process.platform === 'win32' ? ['pwsh', 'powershell.exe'] : ['pwsh'];
  const shell = candidates.find((candidate) => {
    const probe = spawnSync(candidate, ['-NoProfile', '-Command', 'exit 0'], {
      encoding: 'utf8', windowsHide: true
    });
    return !probe.error && probe.status === 0;
  });
  if (!shell) {
    t.skip('PowerShell is unavailable in this environment');
    return;
  }

  const scriptPath = fileURLToPath(new URL('../../nexa.ps1', import.meta.url));
  const quotedScriptPath = `'${scriptPath.replace(/'/g, "''")}'`;
  const command = `& ${quotedScriptPath} tap -Serial qa-validation-device ` +
    '-SnapshotId 12345678-1234-4123-8123-123456789abc -Index $null -Confirm';
  const result = spawnSync(shell, ['-NoProfile', '-Command', command], {
    encoding: 'utf8', windowsHide: true
  });
  assert.equal(result.error, undefined, result.error?.message);
  assert.notEqual(result.status, 0, 'an empty selected target must stop before dispatch');
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  assert.match(output, /tap requires an -Index from the current observation/i);
  assert.doesNotMatch(output, /Nexa app control bridge is unavailable/i,
    'invalid target selection must be rejected before attempting a device connection');
});

test('indexed screen taps revalidate the observed target while scroll keeps strict snapshot freshness', async () => {
  const base = '../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
  const [snapshot, tap, runtime] = await Promise.all([
    readFile(new URL(base + 'NexaPowerShellScreenSnapshot.kt', import.meta.url), 'utf8'),
    readFile(new URL(base + 'NexaScreenLabelTap.kt', import.meta.url), 'utf8'),
    readFile(new URL('../../packages/agent-core/src/runtime.ts', import.meta.url), 'utf8')
  ]);
  const resolveCurrent = snapshot.slice(snapshot.indexOf('fun resolveCurrent'), snapshot.indexOf('fun isCurrent'));
  const isCurrent = snapshot.slice(snapshot.indexOf('fun isCurrent'), snapshot.indexOf('fun unchanged'));

  assert.match(snapshot, /data class Target\(val label: String, val claim: Claim, val bounds: Rect\)/,
    'an indexed observation must retain the exact bounds the owner reviewed');
  assert.match(resolveCurrent, /sameWindow\(root, snapshot\.claim\)/,
    'tap resolution must remain bound to the same observed app window');
  assert.doesNotMatch(resolveCurrent, /fingerprint\(root\)/,
    'unrelated dynamic hierarchy changes must not invalidate an otherwise unchanged tap target');
  assert.match(resolveCurrent, /Rect\(clickable\.bounds\)/,
    'the returned target must copy the snapshotted geometry');
  assert.match(snapshot, /depth\+\+\s*<\s*MAX_CLICKABLE_ANCESTOR_HOPS/,
    'snapshot capture must use the shared clickable-ancestor search limit');
  assert.match(tap, /expectedTargetBounds:Rect\?/,
    'the semantic tap executor must receive the reviewed geometry');
  assert.match(tap, /repeat\(NexaPowerShellScreenSnapshot\.MAX_CLICKABLE_ANCESTOR_HOPS\)/,
    'tap resolution must use the same ancestor limit as indexed observation');
  assert.match(tap, /sameWindow\(root,expectedSnapshot\)/,
    'the tap executor must still reject a different package or window');
  assert.match(tap, /reviewedBounds!=expectedTargetBounds/,
    'the tap executor must reject a moved target before claiming dispatch');
  assert.match(isCurrent, /fingerprint\(root\)\s*==\s*snapshot\.structureFingerprint/,
    'scroll freshness must continue requiring the complete observed hierarchy');
  assert.match(runtime, /"STALE_SCREEN_SNAPSHOT"/,
    'PowerShell callers must receive a safe stale-observation diagnostic');
});

test('named Home and Notifications stay bound to the observed window without a volatile screen hash', async () => {
  const [navigation, taskAudit] = await Promise.all([
    readFile(new URL(
    '../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaBrowserTaskActions.kt',
    import.meta.url
    ), 'utf8'),
    readFile(new URL(
      '../../apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaUiTaskAudit.kt',
      import.meta.url
    ), 'utf8')
  ]);
  assert.match(navigation, /structure\.takeIf\s*\{\s*name\s*==\s*"back"\s*\}/,
    'only Back must require the exact hierarchy captured before global navigation');
  assert.match(navigation, /structure\s*==\s*null\s*\|\|\s*NexaUiActionVerifier\.fingerprint\(root\)\s*==\s*structure/,
    'Home and Notifications must still verify the same package/window but tolerate volatile content');
  assert.match(taskAudit, /"nav_back","nav_home","nav_notifications"/,
    'the task audit must persist a Home action accepted by Android and its native action journal');
});


