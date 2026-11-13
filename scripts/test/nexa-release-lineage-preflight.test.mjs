import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

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
  assert.match(preflight,/keytoolPath -list -v -keystore \$legacyKeystore/);
  assert.match(preflight,/legacyDigest -eq \$digests\[0\]/);
  assert.match(preflight,/LEGACY_DEBUG_SIGNER_MATCH=true/);
  assert.match(preflight,/Production key migration needs a separately verified Android signing lineage/);
  assert.match(preflight,/throw 'SIGNER_MISMATCH: in-place release upgrade NOT certified/);
  assert.doesNotMatch(preflight,/keytool\s+-importkeystore|keytool\s+-genkeypair|gh secret set|apksigner rotate/);
});
