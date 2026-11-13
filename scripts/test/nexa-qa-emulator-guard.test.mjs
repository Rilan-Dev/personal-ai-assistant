import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, chmodSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const guard = resolve('scripts/acceptance/require-synthetic-emulator.sh');
// On Windows, Git Bash may be installed without bash.exe in PATH.
// Keep running the SAME shell guard against a fake ADB; never invoke real ADB.
const gitBash = join(process.env.ProgramFiles || 'C:\\Program Files', 'Git', 'bin', 'bash.exe');
const bashExecutable = process.platform === 'win32' && existsSync(gitBash) ? gitBash : 'bash';
const bashPath = p => process.platform === 'win32'
  ? p.replace(/\\/g, '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase())
  : p;
const makeFakeAdb = () => {
  const dir = mkdtempSync(join(tmpdir(), 'nexa-adb-guard-'));
  const adb = join(dir, 'adb');
  writeFileSync(adb, `#!/bin/sh
case "$1" in
  devices)
    printf 'List of devices attached\\n'
    case "$QA_ADB_SCENARIO" in
      phone) printf '099344034S003788\\tdevice\\n';;
      both) printf 'emulator-5554\\tdevice\\n099344034S003788\\tdevice\\n';;
      none) : ;;
      *) printf 'emulator-5554\\tdevice\\n';;
    esac
    ;;
  -s)
    case "$3" in
      get-state) printf 'device\\n';;
      shell)
        if [ "$4" = getprop ] && [ "$5" = ro.kernel.qemu ]; then
          if [ "$QA_ADB_SCENARIO" = nonqemu ]; then printf '0\\n'
          else printf '1\\n'; fi
        else exit 20; fi
        ;;
      *) exit 21;;
    esac
    ;;
  *) exit 22;;
esac
`, 'utf8');
  chmodSync(adb, 0o755);
  return dir;
};
const run = (scenario, serial = '') => {
  const bin = makeFakeAdb();
  try {
    return spawnSync(bashExecutable, ['-ec', 'export PATH="$1:$PATH"; source "$2"; echo "GUARD_PASSED:$ANDROID_SERIAL"', '--', bashPath(bin), bashPath(guard)], {
      encoding: 'utf8',
      // Git Bash startup competes with parallel native/TS workers on Windows.\n      // The fake-ADB script cannot touch a real device; allow startup slack.\n      timeout: 15000,
      env: { ...process.env,
        QA_ADB_SCENARIO: scenario, ANDROID_SERIAL: serial },
    });
  } finally { rmSync(bin, { recursive:true, force:true }); }
};

test('real connected USB phone is denied even with explicit target serial', () => {
  const result = run('phone', '099344034S003788');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /REFUSED: target is not an Android emulator/);
  assert.doesNotMatch(result.stdout, /GUARD_PASSED/);
});
test('auto-discovered physical USB phone is denied without running any mutations', () => {
  const result = run('phone');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /REFUSED/);
});
test('ambiguous emulator plus phone is rejected unless a safe emulator is explicitly selected', () => {
  const ambiguous = run('both');
  assert.notEqual(ambiguous.status, 0);
  assert.match(ambiguous.stderr, /exactly one authorized emulator/);
  const selected = run('both', 'emulator-5554');
  assert.equal(selected.status, 0, selected.stderr);
  assert.match(selected.stdout, /GUARD_PASSED:emulator-5554/);
});
test('emulator serial with real-device QEMU property is rejected', () => {
  const result = run('nonqemu','emulator-5554');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /does not confirm the QEMU emulator property/);
});
test('authorized Android emulator with QEMU property passes and pins serial', () => {
  const result = run('emulator');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /GUARD_PASSED:emulator-5554/);
});
test('no authorized device is rejected', () => {
  const result = run('none');
  assert.notEqual(result.status, 0);
});
test('both destructive QA entrypoints source guard before any ADB mutation', () => {
  for (const path of [
    'scripts/acceptance/nexa-message-feature-qa.sh',
    'scripts/ci/capture-nexa-ui.sh',
  ]) {
    const source = readFileSync(path, 'utf8');
    const guardIndex = source.indexOf('source "$ROOT/scripts/acceptance/require-synthetic-emulator.sh"') >= 0
      ? source.indexOf('source "$ROOT/scripts/acceptance/require-synthetic-emulator.sh"')
      : source.indexOf('source "$root/scripts/acceptance/require-synthetic-emulator.sh"');
    assert.ok(guardIndex >= 0, `missing mandatory guard: ${path}`);
    // Match executable command lines, not documentation comments that mention
    // the prohibited operations before the guard.
    const mutations = [...source.matchAll(/^\s*adb\s+(?:uninstall|install|shell\s+(?:pm\s+clear|am\s+force-stop))\b/gm)];
    assert.ok(mutations.length > 0, `Expected destructive test operations: ${path}`);
    for (const operation of mutations) {
      assert.ok(operation.index > guardIndex,
        `${operation[0].trim()} is reachable before the emulator guard in ${path}`);
    }
  }
});
