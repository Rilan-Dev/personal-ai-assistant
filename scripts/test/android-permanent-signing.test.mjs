import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const load=p=>readFileSync(p,'utf8');
const ci=[
  '.github/workflows/android-main-promotion.yml',
  '.github/workflows/android-release-test.yml'
];
const fingerprint=load('scripts/signing/nexa-release-cert.sha256').trim();

test('Nexa Android release certificate is pinned and structurally valid',()=>{
  assert.match(fingerprint,/^[0-9a-f]{64}$/);
  assert.equal(fingerprint,'6a00d6d2abaebc80709b0891d755030ce289efad22f0f9488e537d8f0f6722bc');
  const loader=load('scripts/signing/load-ci-release-key.sh');
  const verifier=load('scripts/signing/verify-release-apk.sh');
  assert.match(loader,/keytool -exportcert -rfc/);
  assert.match(loader,/actual.*expected/);
  assert.match(loader,/Refusing release/);
  assert.match(verifier,/certificate mismatch/);
  assert.match(verifier,/apksigner/);
});
test('both APK workflows require identical centrally stored secrets, without ephemeral signer fallback',()=>{
  for(const path of ci){
    const source=load(path);
    for(const secret of ['NEXA_RELEASE_KEYSTORE_B64','NEXA_RELEASE_STORE_PASSWORD','NEXA_RELEASE_KEY_PASSWORD'])
      assert.match(source,new RegExp('secrets\\.'+secret));
    assert.match(source,/bash scripts\/signing\/load-ci-release-key\.sh/);
    assert.match(source,/bash scripts\/signing\/verify-release-apk\.sh/);
    if(path.endsWith('android-main-promotion.yml')){
      assert.match(source,/bash scripts\/signing\/release-version-code\.sh/);
      assert.match(source,/PAA_BUILD_VERSION_CODE=\$\(bash scripts\/signing\/release-version-code\.sh\)/);
    }else{
      assert.match(source,/bash \.\.\/\.\.\/\.\.\/scripts\/signing\/release-version-code\.sh/);
    }
    assert.doesNotMatch(source,/NEXA_RELEASE_[A-Z_]+:[^\n]*openssl rand/);
    assert.doesNotMatch(source,/PAA Temporary Release Test|Temporary test-only release signer/);
    assert.match(source,/release-test-signing-certificate\.txt/);
  }
});
test('bootstrap is one-time only; GitHub upload never prints private signing material',()=>{
  const bootstrap=load('scripts/signing/bootstrap-nexa-signing.sh');
  assert.match(bootstrap,/SIGNING_IDENTITY_ALREADY_EXISTS/);
  assert.match(bootstrap,/refusing rotation/);
  assert.match(bootstrap,/validity 36500/);
  assert.match(bootstrap,/keysize 4096/);
  assert.match(bootstrap,/chmod 0600/);
  const uploader=load('scripts/signing/upload-secrets-to-github.sh');
  assert.match(uploader,/gh auth status/);
  assert.match(uploader,/gh secret set/);
  assert.match(uploader,/cmp -s/);
  assert.doesNotMatch(uploader,/echo "\$NEXA_RELEASE_STORE_PASSWORD"/);
  const ignored=load('.gitignore');
  assert.match(ignored,/\*\.p12/);
  assert.match(ignored,/\*\.keystore/);
});
test('Gradle release uses PKCS12 and a non-debug signing config',()=>{
  const gradle=load('apps/mobile/android/app/build.gradle');
  assert.match(gradle,/storeType 'pkcs12'/);
  assert.match(gradle,/PAA_TEST_APK_KEY_ALIAS/);
  assert.match(gradle,/signingConfig signingConfigs\.testRelease/);
  assert.doesNotMatch(gradle,/release\s*\{\s*signingConfig signingConfigs\.debug/);
});
