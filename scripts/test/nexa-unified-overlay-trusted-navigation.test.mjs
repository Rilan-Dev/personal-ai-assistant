import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const base="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const read=(name)=>readFileSync(base+name,"utf8");
const relay=read("NexaOverlayChatRelay.kt");
const service=read("NexaToolOverlayService.kt");
const module=read("NexaToolOverlayModule.kt");
const policy=read("NexaVoicePermissionPolicy.kt");
const nativeHome=read("NexaHomeFiveAppObservation.kt");
const safeContext=read("NexaSafeContextCache.kt");
const accessibility=read("ScreenAssistService.kt");
const kotlinTests=readFileSync("apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaUnifiedOverlayPermissionAndroidTest.kt","utf8");
const app=readFileSync("apps/mobile/App.tsx","utf8");
const shared=readFileSync("apps/mobile/src/native/androidToolOverlay.ts","utf8");
const screens=readFileSync("apps/mobile/src/native/screenAssist.ts","utf8");

test("floating Chat submits to exactly the same React Chat send() pipeline",()=>{
  assert.match(service,/private fun sendSharedChat\(raw:String\)/);
  assert.match(service,/NexaOverlayChatRelay\.offer\(query\)/);
  assert.match(service,/sendSharedChat\(input\.text\.toString\(\)\)/);
  assert.match(shared,/NEXA_SHARED_OVERLAY_CHAT_EVENT = 'NexaOverlayChatRequest'/);
  assert.match(module,/NexaOverlayChatRelay\.attach\(context\)/);
  assert.match(module,/fun completeChatRequest\(id:String,response:String,p:Promise\)/);
  assert.match(app,/DeviceEventEmitter\.addListener\(NEXA_SHARED_OVERLAY_CHAT_EVENT/);
  assert.match(app,/void send\(request\)/);
  assert.match(app,/overlayReplyToken\.current=null;\s+void completeSharedOverlayChat\(token,text\)/);
  const chatSection=service.split('"chat" -> {')[1]?.split('"tools" -> {')[0];
  assert.ok(chatSection);
  assert.doesNotMatch(chatSection,/button\("Send"\) \{ sendLocal/);
});
test("floating Chat relay is one-shot, bounded, in RAM and does not retry uncertain tasks",()=>{
  assert.match(relay,/const val TTL_MS=60_000L/);
  assert.match(relay,/if\(isPending\(\)\)return false/);
  assert.match(relay,/pending=Request\(token,SystemClock\.elapsedRealtime\(\)\)/);
  assert.match(relay,/if\(!cancel\(token\)\)return false/);
  assert.match(relay,/No automatic replay|inspect the task journal before retrying/i);
  assert.doesNotMatch(relay,/openConnection|grantRuntimePermission|performAction|ACTION_CLICK|sendText|startActivity|okhttp|HttpURLConnection/);
  assert.match(kotlinTests,/unclaimedFloatingChatReplyCannotForgeTaskAuthorization/);
});
test("trusted navigation is device-local, revocable and restricted to safe categories",()=>{
  assert.match(policy,/TRUST_NAVIGATION="trusted_navigation"/);
  assert.match(policy,/fun clearGrants\(context:Context\)/);
  assert.match(policy,/NexaNativeActionSafety\.permitsNewAction\(context\)/);
  assert.match(policy,/ScreenAssistState\.connected && ScreenAssistService\.active!=null/);
  assert.match(policy,/fun isSafeCategory\(category:String\):Boolean=category in setOf\(\s*"screen\.scroll","screen\.navigate","apps\.open"\)/);
  assert.match(service,/Always allow safe navigation/);
  assert.match(service,/NexaVoicePermissionPolicy\.setMode\(/);
  assert.match(service,/button\("Ask every time"\)/);
  assert.match(service,/button\("Revoke saved grants"\)/);
  assert.match(service,/Calls, messages, contacts, alarms, arbitrary taps/);
  assert.doesNotMatch(service,/grantRuntimePermission|pm grant|WRITE_SECURE_SETTINGS|AccessibilityService\.disableSelf/);
});
test("low-risk trusted actions still use final native target identity and durable journal",()=>{
  assert.match(service,/NexaScreenScroll\.prepare\(it,direction\)/);
  assert.match(service,/NexaVoicePermissionPolicy\.canExecute\(this,"scroll_screen:\$direction"\)/);
  assert.match(service,/NexaScreenScroll\.confirm\(service,origin="overlay"\)/);
  assert.match(service,/NexaScreenActionPolicy\.permits\(item\)/);
  assert.match(service,/NexaVoicePermissionPolicy\.canExecute\(this,"tap_safe_icon:"\+item\.semantic\)/);
  assert.match(service,/NexaScreenActions\.confirm\(service,snap\.token,origin="overlay"\)/);
});
test("home pilot inspects exactly five app icon classes on the currently selected launcher only",()=>{
  for(const target of ['"WhatsApp"','"IMO"','"Contacts"','"Calls"','"Clock/Alarm"'])
    assert.ok(nativeHome.includes(target),target);
  assert.match(nativeHome,/NexaLauncherSwipe\.isHome\(service,root\)/);
  assert.match(nativeHome,/if\(queue\.isNotEmpty\(\)\)/);
  assert.match(nativeHome,/HOME_PILOT_READ_ONLY/);
  assert.match(app,/await inspectFiveHomeApps\(\)/);
  assert.match(screens,/inspectFiveHomeApps\(\): Promise<string>/);
  assert.match(accessibility,/NexaHomeFiveAppObservation\.observe\(context\)/);
  assert.match(service,/Inspect 5 home apps \(read-only\)/);
  assert.doesNotMatch(nativeHome,/performAction|dispatchGesture|ACTION_CLICK|ACTION_SET_TEXT|startActivity|openSupportedApp|ACTION_SEND|READ_CONTACTS/);
});
test("opt-in continuous context is ephemeral and cannot silently leak private screen text",()=>{
  assert.match(safeContext,/const val TTL_MS=8_000L/);
  assert.match(safeContext,/const val THROTTLE_MS=1_500L/);
  assert.match(safeContext,/NexaTaskScreenObservation\.observe\(context\)/);
  assert.match(safeContext,/if\(!observed\.startsWith\("Visible non-sensitive controls in \$eventPackage: "\)\)/);
  assert.match(safeContext,/NexaToolOverlayService\.visible/);
  assert.match(safeContext,/ScreenAssistState\.connected && ScreenAssistService\.active!=null/);
  assert.match(accessibility,/NexaSafeContextCache\.capture\(this,pkg\)/);
  assert.match(accessibility,/NexaSafeContextCache\.clear\(\)/);
  assert.match(service,/Enable safe screen context/);
  assert.match(service,/Never shared with a provider automatically/);
  assert.doesNotMatch(safeContext,/HttpURLConnection|OkHttpClient|fetch\(|WebSocket|ContactsContract|MediaProjection|takeScreenshot/);
});
