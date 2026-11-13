import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const whatsapp=readFileSync(root+'NexaWhatsAppChatActions.kt','utf8');
const message=readFileSync(root+'NexaMessageInputActions.kt','utf8');

test('chat row collision cannot merge different source identities solely by overlap',()=>{
  assert.match(whatsapp,/sameSourceOrAncestor\(it\.target,candidate\)/);
  assert.doesNotMatch(whatsapp,/\bsameRow\s*\(/);
  assert.match(whatsapp,/if\(queue\.isNotEmpty\(\)\)return Scan\(0,0,null,false\)/);
  assert.match(whatsapp,/return queue\.isEmpty\(\) && composers==1/);
});

test('WhatsApp final native click reacquires the unique source and checks window inside Pause fence',()=>{
  const fenced=whatsapp.slice(whatsapp.indexOf('val clicked=NexaNativeActionSafety.dispatchWithFence'));
  assert.ok(fenced.length>100);
  assert.match(fenced,/service\.externalWindowRoot\(\)/);
  assert.match(fenced,/fresh\.windowId==expectedWindow/);
  assert.match(fenced,/freshScan\.candidates==1 && !freshScan\.alreadyOpen/);
  assert.match(fenced,/candidate!=null && candidate==target/);
  assert.match(fenced,/freshScan\.bestEvidence==scan\.bestEvidence/);
  assert.match(fenced,/rect==expectedBounds/);
  assert.match(fenced,/candidate!!\.performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/);
  assert.doesNotMatch(fenced,/target\.performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/);
  assert.match(whatsapp,/val token=UUID\.randomUUID\(\)\.toString\(\)[\s\S]*?NexaUiActionJournal\.claim[\s\S]*?val clicked=NexaNativeActionSafety\.dispatchWithFence/);
});

test('draft input checks an empty field again within the last owner Pause fence',()=>{
  const fenced=message.slice(message.indexOf('val accepted=NexaNativeActionSafety.dispatchWithFence'));
  assert.match(message,/if\(queue\.isNotEmpty\(\)\)return Inspection\(0,null,false\)/);
  assert.match(message,/NexaMessageDraftState\.take\(\)/);
  assert.match(message,/NexaUiActionJournal\.claim/);
  assert.match(fenced,/service\.externalWindowRoot\(\)/);
  assert.match(fenced,/fresh\.windowId==approval\.windowId/);
  assert.match(fenced,/visible\.recipientVisible && visible\.matches==1/);
  assert.match(fenced,/candidate!=null && candidate==node/);
  assert.match(fenced,/Rect\(\)\.also \{ candidate\.getBoundsInScreen\(it\) \}==approval\.bounds/);
  assert.match(fenced,/candidate!!\.performAction\([\s\S]*?AccessibilityNodeInfo\.ACTION_SET_TEXT,args\)/);
  assert.doesNotMatch(fenced,/\bnode\.performAction\(AccessibilityNodeInfo\.ACTION_SET_TEXT/);
});

test('post-action message claim requires recipient, unique composer and exact observed text on a fresh window',()=>{
  assert.match(message,/private fun verifyCurrentDraft\(/);
  assert.match(message,/val root=try\{service\.externalWindowRoot\(\)\}/);
  assert.match(message,/val visible=inspect\(root,recipient,requireEmpty=false\)/);
  assert.match(message,/NexaEditableNode\.containsExactTextAfterDispatch\(field,expected\)/);
  assert.match(message,/verifyCurrentDraft\(service,approval,draft\.recipientHint,draft\.message\)/);
  assert.match(message,/NOT SENT/);
  assert.doesNotMatch(message,/ACTION_CLICK[\s\S]*?Send/);
});

test('exceptions after entering a native side-effect remain unknown, never safe to replay',()=>{
  for(const code of [whatsapp,message]) {
    assert.match(code,/var actionEntered=false/);
    assert.match(code,/actionEntered=true\s*\n\s*candidate!!\.performAction/);
    assert.match(code,/if\(actionEntered\)outcomeUnknown=true else stale=true/);
    assert.match(code,/if\(outcomeUnknown\)"unknown" else[\s\S]{0,100}"android_rejected"/m);
    assert.match(code,/no automatic retry|no retry/);
  }
});
