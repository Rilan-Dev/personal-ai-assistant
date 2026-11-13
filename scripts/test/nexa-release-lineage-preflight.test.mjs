import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const preflight = readFileSync(
  'scripts/acceptance/verify-installed-nexa-signer.ps1','utf8');
const pinned = readFileSync(
  'scripts/signing/nexa-release-cert.sha256','utf8').trim();

test('release signer lineage check uses repository-pinned full certificate digest', () => {
  assert.match(pinned,/^[0-9a-f]{64}$/);
  assert.match(preflight,/nexa-release-cert\.sha256/);
  assert.match(preflight,/expected.+-notmatch.+\[0-9a-f\]\{64\}/);
  assert.match(preflight,/SIGNER_MISMATCH: in-place release upgrade NOT certified/);
});
test('installed signer preflight is read-only and always erases its temporary APK', () => {
  assert.match(preflight,/shell pm path \$package/);
  assert.match(preflight,/verify --print-certs/);
  assert.match(preflight,/SHA-256 digest:/);
  assert.match(preflight,/Get-Command adb/);
  assert.match(preflight,/pull \$remotePath \$apk/);
  assert.match(preflight,/finally\s*\{\s*Remove-Item -LiteralPath \$temporary -Recurse -Force/);
  assert.doesNotMatch(preflight,/^\s*(?:&\s*\$adb|adb)\s+(?:install|uninstall|shell\s+pm\s+clear|shell\s+pm\s+grant|shell\s+appops\s+set)/m);
  assert.doesNotMatch(preflight,/am force-stop|pm clear|adb uninstall|adb install/);
});
test('preflight is fixed to the Nexa main identity; no unrelated package inspection', () => {
  assert.match(preflight,/\$package = 'dev\.rilandev\.personalaiassistant'/);
  assert.doesNotMatch(preflight,/param\([\s\S]*\$PackageName\s*=/);
  assert.match(preflight,/selected ADB device is unavailable or unauthorized/i);
  assert.match(preflight,/signExit -ne 0/);
  assert.match(preflight,/digests\.Count -ne 1/);
});

test('legacy Windows debug key is identified read-only but never accepted as production signer', () => {
  assert.match(preflight,/apps\\mobile\\android\\app\\debug\.keystore/);
  assert.match(preflight,/Start-Process -FilePath \$keytoolPath -ArgumentList \$arguments/);
  assert.match(preflight,/\$keytoolProcess\.WaitForExit\(10000\)/);
  assert.match(preflight,/\$keytoolProcess\.Kill\(\)/);
  assert.match(preflight,/LEGACY_DEBUG_KEYTOOL_TIMEOUT=true/);
  assert.match(preflight,/-RedirectStandardOutput \$legacyOutput -RedirectStandardError \$legacyErrors/);
  assert.match(preflight,/legacyDigest -eq \$digests\[0\]/);
  assert.match(preflight,/LEGACY_DEBUG_SIGNER_MATCH=true/);
  assert.match(preflight,/Production key migration needs a separately verified Android signing lineage/);
  assert.match(preflight,/throw 'SIGNER_MISMATCH: in-place release upgrade NOT certified/);
  assert.doesNotMatch(preflight,/keytool\s+-importkeystore|keytool\s+-genkeypair|gh secret set|apksigner rotate/);
});

test('Windows PowerShell parses the actual signer preflight with zero syntax errors', {
  skip: process.platform !== 'win32'
}, () => {
  const ps = [
    '$tokens=$null; $errors=$null;',
    "[System.Management.Automation.Language.Parser]::ParseFile('scripts/acceptance/verify-installed-nexa-signer.ps1',[ref]$tokens,[ref]$errors)|Out-Null;",
    'if($errors.Count -gt 0) {',
    '$errors | ForEach-Object { Write-Error $_.Message }; exit 1',
    '}; exit 0'
  ].join(' ');
  const actual=spawnSync('powershell.exe', [
    '-NoProfile','-NonInteractive','-Command',ps
  ],{encoding:'utf8',timeout:15000});
  assert.equal(actual.status,0,
    'Release signer preflight must parse before any ADB usage: '+(actual.stderr||actual.error?.message||''));
});

test('legacy signer comparison has exactly one bounded digest predicate', () => {
  assert.match(preflight,/\$legacyDigest -match '\^\[0-9a-f\]\{64\}\$' -and/);
  assert.equal((preflight.match(/LEGACY_DEBUG_SIGNER_MATCH=true/g)||[]).length,1);
  assert.equal((preflight.match(/SIGNER_MATCH: installed Nexa main package matches/g)||[]).length,1);
});
