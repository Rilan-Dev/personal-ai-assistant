import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';

const app=readFileSync('apps/mobile/App.tsx','utf8').replace(/\r\n/g,'\n');
const relay=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaOverlayChatRelay.kt',
  'utf8'
).replace(/\r\n/g,'\n');
const bridge=readFileSync('apps/mobile/src/native/androidToolOverlay.ts','utf8').replace(/\r\n/g,'\n');

test('overlay timeout is relayed to the shared Chat engine',()=>{
  const schedule=relay.slice(relay.indexOf('private fun scheduleTimeout'),
    relay.indexOf('\n    private fun cancel(',relay.indexOf('private fun scheduleTimeout')));
  assert.match(relay,/CHAT_TIMEOUT_EVENT\s*=\s*"NexaOverlayChatTimeout"/,
    'the native relay publishes one stable timeout event name');
  assert.match(schedule,/emit\(CHAT_TIMEOUT_EVENT,event\)/,
    'a native timeout must notify the React owner task that owns the request token');
  assert.ok(schedule.indexOf('emit(CHAT_TIMEOUT_EVENT,event)')<
    schedule.indexOf('showSharedChatReply'),
    'the stale shared-chat owner must be released before the overlay displays timeout feedback');
  assert.match(bridge,/NEXA_SHARED_OVERLAY_CHAT_TIMEOUT_EVENT\s*=\s*'NexaOverlayChatTimeout'/,
    'the native timeout event name is exposed by the Android bridge');
});

test('timed-out overlay reviews cancel only the matching unapproved checkpoint',()=>{
  assert.match(app,/NEXA_SHARED_OVERLAY_CHAT_TIMEOUT_EVENT/,
    'React Chat listens for the matching native timeout event');
  assert.match(app,/overlayPendingApprovalToken\.current===id/,
    'only the review belonging to the expired overlay request may be canceled');
  assert.match(app,/status:\s*'cancelled',\s*pendingCall:\s*undefined/,
    'an expired unapproved step is durably canceled so it cannot reappear as executable work');
  assert.match(app,/activeAbort\.current\?\.abort\(\)/,
    'a planner still running at timeout is stopped before a later overlay task starts');
  assert.match(app,/overlayReplyToken\.current=null/,
    'the expired shared-chat token is released to prevent permanent busy state');
});
