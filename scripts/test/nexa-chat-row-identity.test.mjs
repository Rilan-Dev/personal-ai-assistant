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
  assert.doesNotMatch(source,/sameRow\(it\.bounds,rect\)/,
    'Distinct sources may overlap; geometry alone cannot authorize contact deduplication');
  assert.match(source,/if\(queue\.isNotEmpty\(\)\)return Scan\(0,0,null,false\)/);
});

test('synthetic recipient fixture requires stable matching Accessibility observations',()=>{
  const fixture=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaWhatsAppChatUiAndroidTest.kt','utf8');
  assert.match(fixture,/var previousSignature:String\?=null/);
  assert.match(fixture,/signature!=null && signature==previousSignature/);
  assert.match(fixture,/scan\.candidates==expectedRows/);
  assert.match(fixture,/scan\.bestEvidence==evidence/);
  assert.match(fixture,/if\(consecutive>=3\)return Settled\(current,scan\)/);
  assert.match(fixture,/window\.contains\(rect\)/);
});

test('overlapping synthetic rows retain two recipient identities',()=>{
  const fixture=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaWhatsAppChatUiAndroidTest.kt','utf8');
  assert.match(fixture,/overlappingDistinctRowsNeverBecomeOneApprovedRecipient/);
  assert.match(fixture,/assertEquals\("Overlapping sibling contacts remain ambiguous",2,observed\.scan\.candidates\)/);
  assert.match(fixture,/assertNull\("No unsafe chat-row target may be chosen",observed\.scan\.target\)/);
});
