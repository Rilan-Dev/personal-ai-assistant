import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const source=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaWhatsAppChatActions.kt","utf8"
);
const physical=readFileSync(
  "apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaWhatsAppClaimRecoveryAndroidTest.kt","utf8"
);
const approved=source.slice(source.indexOf("fun openApproved("),source.indexOf("/** Fresh screen, matching header"));

test("a claimed chat-row approval is tracked until its journal outcome persists",()=>{
  assert.match(approved,/var unsettledClaim:String\?=null/);
  assert.match(approved,/fun settle\(token:String,outcome:String\):Boolean/);
  assert.match(approved,/val persisted=NexaUiActionJournal\.finish\(service,token,outcome\)/);
  assert.match(approved,/if\(persisted\)unsettledClaim=null/);
  assert.match(approved,/unsettledClaim=token\s+claimedToken\(token\)/);
  assert.match(approved,/NexaUiActionVerifier\.begin\(token,"tap","com\.whatsapp"/);
});
test("a failure after claim records unknown and never retries or sends a message",()=>{
  assert.match(approved,/val token=unsettledClaim\s+if\(token!=null\)/);
  assert.match(approved,/NexaUiActionVerifier\.cancel\(token\)/);
  assert.match(approved,/try\{settle\(token,"unknown"\)\}catch/);
  assert.match(approved,/no automatic retry or Send/);
  assert.doesNotMatch(approved,/ACTION_SEND|ACTION_SET_TEXT/);
  assert.doesNotMatch(approved,/performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)[\s\S]*performAction\(AccessibilityNodeInfo\.ACTION_CLICK\)/);
});
test("failed journal completion is explicitly uncertain in every dispatch branch",()=>{
  assert.match(approved,/if\(persisted\) "Owner paused/);
  assert.match(approved,/if\(persisted\)\s+"Android accepted/);
  assert.match(approved,/if\(!persisted\) "WhatsApp tap outcome could not be recorded/);
  assert.match(approved,/if\(outcomeUnknown\)"unknown" else "android_rejected"/);
});
test("Android acceptance proves encrypted claimed→unknown is one-shot without external click",()=>{
  assert.match(physical,/class NexaWhatsAppClaimRecoveryAndroidTest/);
  assert.match(physical,/NexaUiActionJournal\.claim\(ctx,token,"tap","com\.whatsapp"/);
  assert.match(physical,/NexaUiActionJournal\.finish\(ctx,token,"unknown"\)/);
  assert.match(physical,/NexaUiActionJournal\.outcomeFor\(ctx,token\)/);
  assert.match(physical,/assertFalse\(NexaUiActionJournal\.claim/);
  assert.doesNotMatch(physical,/ACTION_CLICK|ACTION_SET_TEXT|am instrument|pm grant/);
});
