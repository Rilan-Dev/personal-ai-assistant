import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const base = 'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read = name => readFileSync(base + name, 'utf8');

test('bounded scroll search is an owner-confirmed, evidence-gated native guide', () => {
  const policy = read('NexaBoundedScrollSearch.kt');
  assert.match(policy, /MAX_SCROLLS\s*=\s*4/);
  assert.match(policy, /NexaTaskScreenObservation\.safeLabel/);
  assert.match(policy, /scroll_event_observed/);
  assert.match(policy, /pauseGeneration/);
  assert.match(policy, /fun onEvidence\(/);
  assert.match(policy, /fun confirmable\(/);
  assert.doesNotMatch(policy, /performAction\(|dispatchGesture\(|startActivity\(/);
});

test('floating Chat reuses the existing reviewed scroll action and only advances from native journal evidence', () => {
  const overlay = read('NexaToolOverlayService.kt');
  assert.match(overlay, /NexaBoundedScrollSearch\.parse\(query\)/);
  assert.match(overlay, /NexaScreenScroll\.prepare\(/);
  assert.match(overlay, /NexaScreenScroll\.confirm\(/);
  assert.match(overlay, /NexaUiActionJournal\.outcomeFor\(/);
  assert.match(overlay, /NexaTaskScreenObservation\.exactVisibleControl/);
  assert.match(overlay, /Cancel scroll search/);
  assert.match(overlay, /Confirm scroll/);
});

test('bounded search refuses ambiguous scroll containers and disabled launcher swipes', () => {
  const scroll = read('NexaScreenScroll.kt');
  const overlay = read('NexaToolOverlayService.kt');
  assert.match(scroll, /requireUniqueContainer:Boolean=false/);
  assert.match(scroll, /eligibleContainers\.size!=1/);
  assert.match(scroll, /!requireUniqueContainer\s*&&\s*NexaLauncherSwipe\.isHome/);
  assert.match(overlay, /NexaScreenScroll\.prepare\(service,request\.direction,requireUniqueContainer=true\)/);
  assert.match(overlay, /NexaScreenScroll\.prepare\(nextService,nextRequest\.direction,requireUniqueContainer=true\)/);
});

test('a new floating scroll request invalidates older pending owner actions', () => {
  const overlay = read('NexaToolOverlayService.kt');
  const entry = overlay.indexOf('private fun sendLocal(raw: String)');
  const query = overlay.indexOf('val query = raw.trim().take(150)', entry);
  const reset = overlay.indexOf('proposedDeviceAction=null', entry);
  const route = overlay.indexOf('NexaBoundedScrollSearch.parse(query)', entry);
  assert.ok(entry >= 0 && query > entry && reset > entry && reset < route,
    'the prior owner action must be revoked before admitting a new scroll task');
  assert.match(overlay, /private fun startBoundedScrollSearch\(query:String\) \{[\s\S]*?NexaMessageInputActions\.cancel\(\)/);
  assert.match(overlay, /private fun startBoundedScrollSearch\(query:String\) \{[\s\S]*?NexaTextInputActions\.cancel\(\)/);
});

test('a fresh owner message cancels old bounded-search approvals before any task branch', () => {
  const overlay = read('NexaToolOverlayService.kt');
  const begin = overlay.indexOf('private fun sendLocal(raw: String)');
  const review = overlay.indexOf('val task=NexaOwnerTaskReview.parse(raw.trim())', begin);
  const cancel = overlay.indexOf('cancelBoundedScrollSearch()', begin);
  const clear = overlay.indexOf('proposedDeviceAction=null', begin);
  assert.ok(begin >= 0 && cancel > begin && cancel < review,
    'new owner message must cancel the previous scroll guide before any task');
  assert.ok(clear > begin && clear < review,
    'old owner device-action proposals must be revoked before task review');
});
