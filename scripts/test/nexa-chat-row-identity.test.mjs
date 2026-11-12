import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';

const source=readFileSync('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaWhatsAppChatActions.kt','utf8');

test('accessibility duplicate labels with same clickable source are one chat row',()=>{
  assert.match(source,/private fun sameSourceOrAncestor\(/);
  assert.match(source,/if\(a==b\)return true/);
  assert.match(source,/rows\.indexOfFirst\s*\{\s*sameSourceOrAncestor\(it\.target,candidate\)/);
});
test('different clickable rows are not coalesced by a shared unrelated parent',()=>{
  assert.match(source,/candidate\.parent/);
  assert.match(source,/ancestor==current/);
  assert.doesNotMatch(source,/commonParent|sharedParent/);
  assert.match(source,/sameRow\(it\.bounds,rect\)/);
});
