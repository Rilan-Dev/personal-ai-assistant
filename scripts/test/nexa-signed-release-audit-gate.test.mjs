import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const workflows=[
  '.github/workflows/android-main-promotion.yml',
  '.github/workflows/android-release-test.yml'
];

test('manual signed Nexa release workflows fail on high/critical advisories before exposing signing keys',()=>{
  for(const path of workflows){
    const yml=readFileSync(path,'utf8').replace(/\r\n/g,'\n');
    assert.match(yml,/^\s+workflow_dispatch:\s*$/m,path+' must stay manual-only');
    assert.doesNotMatch(yml,/^\s+(?:push|pull_request|schedule):\s*$/m,
      path+' must not enable automatic release');
    const install=yml.indexOf('- run: npm ci');
    const audit=yml.indexOf('- name: Block high and critical npm dependency advisories');
    const command=yml.indexOf('run: npm audit --audit-level=high');
    const privateKey=yml.indexOf('- name: Load permanent Nexa release signer');
    const build=yml.indexOf('- name: Assemble optimized signed Android release variant');
    assert.ok(install>=0 && install<audit && audit<command &&
      command<privateKey && privateKey<build,path+' must audit after npm ci and before signing/build');
    const auditStep=yml.slice(audit,privateKey);
    assert.doesNotMatch(auditStep,/continue-on-error:\s*true|npm audit fix|\|\|\s*true|--audit-level=(?:low|moderate)/,
      path+' must never bypass or silently modify dependency findings');
    assert.match(yml,/NEXA_RELEASE_KEYSTORE_B64/);
    assert.match(yml,/scripts\/signing\/verify-release-apk\.sh/);
  }
});
test('the audit gate never writes signing secrets to the repository or auto-promotes main',()=>{
  for(const path of workflows){
    const yml=readFileSync(path,'utf8');
    assert.doesNotMatch(yml,/^\s+(?:git push|gh pr merge|git merge)\b/m);
    assert.doesNotMatch(yml,/^\s+run:.*(?:echo|printenv).*(?:STORE_PASSWORD|KEY_PASSWORD|KEYSTORE_B64)/m);
  }
});
