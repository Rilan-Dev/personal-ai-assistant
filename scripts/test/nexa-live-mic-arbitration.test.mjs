import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const native = 'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const wake = readFileSync(native + 'NexaWakeWordService.kt', 'utf8');
const live = readFileSync(native + 'RealtimeVoiceService.kt', 'utf8');

test('live provider reserves the mic before capture and yields it after audio release', () => {
  const start = live.indexOf('NexaWakeWordService.pauseForLiveVoice()');
  assert.ok(start > 0, 'live service must request microphone handoff');
  assert.ok(start < live.indexOf('active.set(true)'), 'handoff must occur before live state');
  const stop = live.indexOf('NexaWakeWordService.resumeAfterLiveVoice()');
  assert.ok(stop > live.indexOf('try { recorder?.release() }'), 'wake resumes only after live recorder release');
  assert.ok(stop > live.indexOf('player = null'), 'wake resumes only after speaker release');
  assert.match(live, /override fun onDestroy\(\)/);
});

test('wake detector stops capture when live voice claims the microphone', () => {
  assert.match(wake, /@Volatile private var liveVoiceOwnsMic = false/);
  assert.match(wake, /fun pauseForLiveVoice\(\)/);
  assert.match(wake, /fun resumeAfterLiveVoice\(\)/);
  // Companion 'listening' state belongs to NexaWakeWordService, not an
  // instance member. Kotlin rejects wake.listening as an unresolved reference.
  assert.match(wake, /return !listening && !wake\.audioReleasing && wake\.audio == null/);
  assert.doesNotMatch(wake, /wake\.listening/);
  assert.match(wake, /liveVoiceOwnsMic = true[\s\S]*?releaseAudio\(\)/);
  assert.match(wake, /if \(handoffPending \|\| liveVoiceOwnsMic \|\| RealtimeVoiceService\.status/);
  assert.match(wake, /while \(running\.get\(\) && !handoffPending && !liveVoiceOwnsMic\)/);
  assert.match(wake, /if \(!running\.get\(\) \|\| handoffPending \|\| liveVoiceOwnsMic\) break/);
  assert.match(wake, /if \(running\.get\(\) && !handoffPending && !liveVoiceOwnsMic\)/);
});

test('live handoff does not use OEM assistant timeout or force wake mode on', () => {
  const body = wake.split('fun pauseForLiveVoice() {')[1]?.split('fun resumeAfterLiveVoice()')[0] || '';
  assert.doesNotMatch(body, /handoffStartedAt|handoffPending\s*=|persistEnabled/);
  assert.match(wake, /fun enabled\(ctx: Context\): Boolean/);
  assert.match(wake, /if \(handoffPending && !assistantSessionVisible/);
});
