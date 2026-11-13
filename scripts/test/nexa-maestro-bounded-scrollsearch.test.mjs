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
  const guided = overlay.indexOf('guidedNavigation.cancel()', begin);
  const selected = overlay.indexOf('NexaScreenActions.reset()', begin);
  assert.ok(guided > begin && guided < review && selected > begin && selected < review,
    'new owner goals must invalidate older guided navigation and selected icons');
});

test('reviewed scroll requires two stable target readings and fresh dispatch-window verification', () => {
  const policy=read('NexaBoundedScrollSearch.kt');
  const observation=read('NexaTaskScreenObservation.kt');
  const overlay=read('NexaToolOverlayService.kt');
  const scroll=read('NexaScreenScroll.kt');
  assert.match(policy,/MIN_STABLE_MS\s*=\s*250L/);
  assert.match(policy,/stableBoundsKey/);
  assert.match(policy,/uniqueBoundsKey\.isNullOrBlank\(\)/);
  assert.match(observation,/val uniqueBoundsKey:String\?=null/);
  assert.match(observation,/uniqueBounds\.singleOrNull\(\)/);
  assert.match(overlay,/observed\.uniqueBoundsKey/);
  assert.match(overlay,/if\(attempt<3\) inspectBoundedScrollOutcome\(token,attempt\+1\)/);
  assert.match(scroll,/private fun reviewedTarget\(/);
  assert.match(scroll,/queue\.isEmpty\(\) && matching==1/);
  assert.match(scroll,/NexaNativeActionSafety\.dispatchWithFence\([\s\S]*?val freshRoot=safeRoot\(service\)/);
  assert.match(scroll,/freshRoot\.windowId!=approved\.windowId/);
  assert.match(scroll,/reviewedTarget\(freshRoot,approved\)\?\.performAction\(action\)/);
  assert.doesNotMatch(scroll,/scrollTarget\.performAction\(action\)/);
});
