import assert from "node:assert/strict";
import {test} from "node:test";
import {readFileSync} from "node:fs";
const src=(name)=>readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/"+name+".kt",
  "utf8");

test("every approved external messaging draft uses the same native fence",()=>{
  const sms=src("NexaMessagingModule");
  const whatsApp=src("NexaWhatsAppComposeModule");
  const guard=src("NexaApprovedExternalAction");
  assert.match(sms,/NexaApprovedExternalAction\.launch\(context,intent,pkg,"chat"\)/);
  assert.match(sms,/NexaApprovedExternalAction\.launch\(context,launch,pkg,"chat"\)/);
  assert.match(whatsApp,/NexaApprovedExternalAction\.launch\(context,intent,\s*"com\.whatsapp","chat"\)/);
  assert.match(sms,/messageSent",false/);
  assert.match(whatsApp,/messageSent",false/);
  assert.match(guard,/NexaUiActionJournal\.claim\(/);
  assert.match(guard,/NexaNativeActionSafety\.dispatchWithFence\(/);
  assert.doesNotMatch(sms,/context\.startActivity\(/);
  assert.doesNotMatch(whatsApp,/context\.startActivity\(/);
});

test("launcher discovery uses one-shot Intent launch and Pause-safe gestures",()=>{
  const launcher=src("NexaLauncherNavigationService");
  const overlay=src("NexaOverlayCommands");
  assert.match(launcher,/NexaApprovedExternalAction\.launch\(context,intent,expected,"chat"\)/);
  assert.match(overlay,/NexaApprovedExternalAction\.launch\(context,home,pkg,origin\)/);
  assert.match(launcher,/NexaUiActionJournal\.claim\(this,token,kind,homePackage,origin="chat"\)/);
  assert.match(launcher,/NexaNativeActionSafety\.dispatchWithFence\(this,fence\)/);
  assert.match(launcher,/LauncherNavigationState\.allows\(homePackage\)/);
  assert.doesNotMatch(launcher,/context\.startActivity\(intent\.addFlags/);
});

test("cross-interface Android nav actions are native one-shot claims only",()=>{
  const browser=src("NexaBrowserTaskActions");
  const device=src("DeviceControlModule");
  const overlay=src("NexaToolOverlayService");
  const voice=src("RealtimeVoiceService");
  assert.match(browser,/NexaUiActionJournal\.claim\(context,token,kind,packageName,origin=origin\)/);
  assert.match(browser,/NexaNativeActionSafety\.dispatchWithFence\(context,generation\)/);
  assert.match(device,/notificationsApproved\(context,origin="chat"\)/);
  assert.match(overlay,/notificationsApproved\(this,origin="overlay"\)/);
  assert.match(voice,/NexaVoiceSessionGuard\.during\(operationSession\)/);
  assert.match(voice,/"com\.android\.systemui"/);
});
