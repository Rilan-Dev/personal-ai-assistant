import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root='apps/mobile/android/app/src/';
const native=readFileSync(root+'main/java/dev/rilandev/personalaiassistant/NexaMessageInputActions.kt','utf8');
const policy=readFileSync(root+'androidTest/java/dev/rilandev/personalaiassistant/NexaMessageFillClaimRecoveryAndroidTest.kt','utf8');
const live=readFileSync(root+'androidTest/java/dev/rilandev/personalaiassistant/NexaLiveWhatsAppReadOnlyAndroidTest.kt','utf8');
const confirm=native.slice(native.indexOf('fun confirm('));

test('message composer claim tracks all unsettled post-claim exceptions',()=>{
  assert.match(confirm,/var unsettledClaim:String\?=null/);
  assert.match(confirm,/fun settle\(token:String,outcome:String\):Boolean/);
  assert.match(confirm,/val persisted=NexaUiActionJournal\.finish\(service,token,outcome\)/);
  assert.match(confirm,/if\(persisted\)unsettledClaim=null/);
  assert.match(confirm,/unsettledClaim=approval\.token/);
  assert.match(confirm,/val token=unsettledClaim[\s\S]*?settle\(token,"unknown"\)/);
  assert.match(confirm,/try\{settle\(approval\.token,"unknown"\)\}catch/);
});
test('native text acceptance never reports journal-backed success when commit fails',()=>{
  assert.match(confirm,/val persisted=settle\(approval\.token,/);
  assert.match(confirm,/if\(!persisted\)\s+"Message text-entry journal outcome could not be stored/);
  assert.match(confirm,/if\(NexaUiActionJournal\.markObservation\(service,approval\.token,"observed_change"\)\)/);
  assert.match(confirm,/durable verification was not saved/);
  assert.match(confirm,/no retry or Send/);
  assert.doesNotMatch(confirm,/ACTION_CLICK|ACTION_SEND|pressSend|sendMessage\s*\(/);
});
test('physical no-replay test records unknown and refuses replay even after verified Android claim',()=>{
  assert.match(policy,/NexaUiActionJournal\.claim\(ctx,approval,"message_fill"/);
  assert.match(policy,/NexaUiActionJournal\.finish\(ctx,approval,"unknown"\)/);
  assert.match(policy,/assertFalse\(NexaUiActionJournal\.markObservation/);
  assert.match(policy,/assertFalse\(NexaUiActionJournal\.claim/);
  assert.doesNotMatch(policy,/performAction|ACTION_SET_TEXT|ACTION_CLICK|adb shell input|pm grant/);
});
test('live WhatsApp audit is strictly read-only and redacts all text',()=>{
  assert.match(live,/ScreenAssistState\.connected/);
  assert.match(live,/ScreenAssistService\.active/);
  assert.match(live,/service!!\.externalWindowRoot\(\)/);
  assert.match(live,/OWNER_ACCESSIBILITY_REQUIRED/);
  assert.doesNotMatch(live,/\bgetUiAutomation\s*\(|\bautomation\.windows\b|\brootInActiveWindow\b/);
  assert.match(live,/nexaRecipientHint/);
  assert.match(live,/NexaWhatsAppChatActions\.inspect\(root,recipient\)/);
  assert.match(live,/scan\.alreadyOpen \|\| scan\.candidates==1/);
  assert.doesNotMatch(live,/performAction|ACTION_SET_TEXT|ACTION_CLICK|dispatchWithFence|startActivity|sendMessage|takeScreenshot|pressSend|am start|ACTION_SEND/);
  assert.doesNotMatch(live,/println\([^)]*(?:recipient|node\.text|contentDescription|hintText)/);
});
