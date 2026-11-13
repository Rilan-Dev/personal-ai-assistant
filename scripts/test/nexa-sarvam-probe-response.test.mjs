import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const base='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const parser=readFileSync(base+'NexaSarvamProbeResponse.kt','utf8');
const ready=readFileSync(base+'SarvamModelApiReadiness.kt','utf8');
const phone=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaSarvamProbeResponseAndroidTest.kt','utf8');

test('text-only synthetic completion requires one assistant choice with nonempty content',()=>{
  assert.match(parser,/choices\.length\(\)!=1/);
  assert.match(parser,/message\.optString\("role"\)!="assistant"/);
  assert.match(parser,/val content=message\.opt\("content"\) as\? String \?:return false/);
  assert.match(parser,/content\.trim\(\)\.length in 1\.\.3_000/);
  assert.match(parser,/choice\.optString\("finish_reason"\)=="length"/);
  assert.match(parser,/toolCalls!=null && toolCalls\.length\(\)!=0/);
});
test('both readiness probe stages verify content, not only a successful HTTP status',()=>{
  assert.equal((ready.match(/NexaSarvamProbeResponse\.hasNonExecutingAssistantCompletion\(reply\)/g)||[]).length,2);
  assert.equal((ready.match(/NexaSarvamResponseBounds\.MAX_CHAT_BYTES/g)||[]).length,2);
  assert.match(ready,/finish\(false,"MODEL_TOOL_RESPONSE"/);
  assert.match(ready,/finish\(false,"MODEL_RESPONSE"/);
  assert.match(ready,/WebSocket handshakes and two synthetic text-only model completions/);
});
test('one-time readiness neither executes model-proposed tools nor records private audio',()=>{
  assert.doesNotMatch(ready,/NexaSarvamReadTools\.execute|ACTION_CLICK|ACTION_SET_TEXT|AudioRecord/);
  assert.match(ready,/No phone tool was executed/);
  assert.match(phone,/malformed|MisleadingChoices/i);
  assert.match(phone,/reply\(call=true\)/);
  assert.match(phone,/reply\(finish="length"\)/);
  assert.match(phone,/reply\(content=JSONObject\.NULL\)/);
});
