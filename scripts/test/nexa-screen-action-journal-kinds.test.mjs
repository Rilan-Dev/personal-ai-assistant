import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const dir="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const source=(name)=>readFileSync(dir+name,"utf8");

test("named visible-label click uses the journal canonical tap kind",()=>{
  const labelTap=source("NexaScreenLabelTap.kt");
  const journal=source("NexaUiActionJournal.kt");
  assert.match(labelTap,/NexaUiActionJournal\.claim\(service,token,"tap",pkg,origin=origin\)/);
  assert.doesNotMatch(labelTap,/NexaUiActionJournal\.claim\(service,token,"tap_label",pkg\)/);
  assert.match(journal,/kind !in setOf\("tap","scroll_up","scroll_down","scroll_left","scroll_right",/);
});
test("launcher horizontal gesture uses the accepted scroll_left/scroll_right kinds",()=>{
  const launcher=source("NexaLauncherSwipe.kt");
  assert.match(launcher,/NexaUiActionJournal\.claim\(service,token,"scroll_\$direction",pkg\)/);
  assert.doesNotMatch(launcher,/"launcher_swipe_\$direction"/);
  assert.match(launcher,/NexaLauncherSwipe|class|object/);
  assert.match(launcher,/isHome\(service,root\)/);
  assert.match(launcher,/GestureDescription/);
});
test("only a reverified same-package launcher swipe may cross an Accessibility window ID",()=>{
  const launcher=source("NexaLauncherSwipe.kt");
  const verifier=source("NexaUiActionVerifier.kt");
  const policy=source("NexaUiActionEvidencePolicy.kt");
  assert.match(launcher,/allowLauncherWindowChange=true/);
  assert.match(verifier,/observation\.allowLauncherWindowChange\s*&&\s*NexaLauncherSwipe\.isHome\(service,root\)/);
  assert.match(verifier,/NexaUiActionEvidencePolicy\.sameEvidenceContext/);
  assert.match(policy,/fun sameEvidenceContext\(/);
  assert.match(policy,/expectedPackage != observedPackage/);
  assert.match(policy,/kind == "scroll" && allowLauncherWindowChange/);
});
test("one-time claim cannot be silently reused when Android reports uncertain results",()=>{
  const journal=source("NexaUiActionJournal.kt");
  assert.match(journal,/if \(entries\.getJSONObject\(i\)\.optString\("id"\) == id\) return@synchronized false/);
  assert.match(journal,/status","claimed"/);
});

test("reviewed messaging Send uses a high-risk digest-bound non-replay journal kind",()=>{
  const journal=source("NexaUiActionJournal.kt");
  const db=source("NexaConversationDb.kt");
  const task=source("NexaUiTaskAudit.kt");
  const action=source("NexaMessageInputActions.kt");
  assert.match(journal,/"message_fill","message_send","text_fill"/);
  assert.match(db,/"message_fill","message_send","text_fill"/);
  assert.match(task,/"message_fill","message_send","text_fill"/);
  assert.match(task,/val risk=if\(actionKind=="message_send"\)"high" else "medium"/);
  assert.match(action,/NexaUiActionJournal\.claim\(service,token,"message_send",\s*expectedPackage,digest,origin\)/);
  assert.match(action,/agentSendPackage\(appId\)/);
  assert.match(action,/NexaNativeActionSafety\.dispatchWithFence\(service,pauseGeneration\)/);
});
