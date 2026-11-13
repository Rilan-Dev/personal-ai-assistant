import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const base='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const reader=readFileSync(base+'NexaSarvamResponseBounds.kt','utf8');
const transport=readFileSync(base+'SarvamApiVoiceTransport.kt','utf8');
const readiness=readFileSync(base+'SarvamModelApiReadiness.kt','utf8');
const android=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaSarvamResponseBoundsAndroidTest.kt','utf8');

test('Sarvam parser caps bytes in the HTTP stream before String allocation',()=>{
  assert.match(reader,/response\.peekBody\(limit\+1L\)/);
  assert.match(reader,/if\(bounded\.contentLength\(\)>limit\) null else bounded\.string\(\)/);
  assert.match(reader,/if\(limit !in 1\.\.MAX_CHAT_BYTES\)return null/);
  assert.match(reader,/const val MAX_CHAT_BYTES=140_000L/);
  assert.match(reader,/const val MAX_ERROR_BYTES=5_000L/);
});
test('live model and error responses cannot call unbounded body.string()',()=>{
  assert.match(transport,/NexaSarvamResponseBounds\.textOrNull\(it,\s*NexaSarvamResponseBounds\.MAX_ERROR_BYTES\)/);
  assert.match(transport,/NexaSarvamResponseBounds\.textOrNull\(it,\s*NexaSarvamResponseBounds\.MAX_CHAT_BYTES\)/);
  assert.doesNotMatch(transport,/it\.body\?\.string\(\)/);
  assert.doesNotMatch(transport,/response\.body\?\.string\(\)/);
});
test('owner-tapped readiness uses the same bounded diagnostic classifier',()=>{
  assert.match(readiness,/NexaSarvamResponseBounds\.textOrNull\(it,\s*NexaSarvamResponseBounds\.MAX_ERROR_BYTES\)/);
  assert.doesNotMatch(readiness,/it\.body\?\.string\(\)/);
  assert.match(readiness,/No device tool was executed/);
});
test('Android synthetic provider tests cover exact limits and oversized responses',()=>{
  assert.match(android,/NexaSarvamResponseBoundsAndroidTest/);
  assert.match(android,/repeat\(5_001\)/);
  assert.match(android,/repeat\(140_001\)/);
  assert.match(android,/repeat\(140_000\)/);
  assert.match(android,/Long\.MAX_VALUE/);
  assert.doesNotMatch(android,/AudioRecord|api-subscription-key|RECORD_AUDIO|startForegroundService/);
});
