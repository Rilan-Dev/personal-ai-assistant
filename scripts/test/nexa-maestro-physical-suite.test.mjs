import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';
const script=readFileSync('scripts/maestro-physical-qa.ps1','utf8');
test('physical QA never clears data, uninstalls, grants OS permissions, or sends',()=>{
  assert.doesNotMatch(script, /pm\s+clear|uninstall|settings\s+put|appops\s+set|input\s+tap|input\s+text|SEND_SMS|am\s+force-stop/i);
  assert.match(script, /am.*instrument/);
  assert.match(script, /OK \\\([0-9]+ tests?\\\)/);
  assert.match(script, /FAILURES!!!|Process crashed/);
});
test('physical QA runs isolated synthetic WhatsApp cases, rejecting false-positive adb exit',()=>{
  assert.match(script,/duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader/);
  assert.match(script,/twoIdenticalChatRowsRemainAmbiguousAndAreNeverTapped/);
  assert.match(script,/uncertainSpeechVariantCanBeFoundButNotWrongAlias/);
  assert.match(script,/\$LASTEXITCODE/);
  assert.match(script,/if\(\$failed -gt 0\)/);
});
