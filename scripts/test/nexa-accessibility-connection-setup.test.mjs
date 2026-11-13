import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";
const source=(name)=>readFileSync(name,"utf8");
const normalizedSource=(name)=>source(name).replace(/\r\n/g,"\n");
const setup=source("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaSetupModule.kt");
const component=source("apps/mobile/src/components/NexaSetupScreen.tsx");
const bridge=source("apps/mobile/src/native/nexaSetup.ts");
const app=source("apps/mobile/App.tsx");
const flow=source(".maestro/experimental/10-agent-notification-shade.yaml");
const reviewFlows=[
  normalizedSource(".maestro/experimental/11-whatsapp-owner-review.yaml"),
  normalizedSource(".maestro/experimental/12-message-app-chooser.yaml"),
  normalizedSource(".maestro/experimental/13-reviewed-generic-text-field.yaml"),
];
const debugManifest=source("apps/mobile/android/app/src/debug/AndroidManifest.xml");
const service=source("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/ScreenAssistService.kt");
const runtimeAcceptance=source("docs/qa/NEXA_SCREEN_ASSIST_RUNTIME_ACCEPTANCE_20261104.md");

test("setup status distinguishes Settings enabled from genuinely connected Accessibility service",()=>{
  assert.match(setup,/screenAssistConnected/);
  assert.match(setup,/ScreenAssistState\.connected\s*&&\s*ScreenAssistService\.active != null/);
  assert.match(service,/override fun onServiceConnected\(\)/);
  assert.match(service,/ScreenAssistState\.connected = true/);
  assert.match(service,/ScreenAssistState\.connected = false/);
});
test("user-facing Setup requires connected service before claiming native screen tools are ready",()=>{
  assert.match(bridge,/screenAssistConnected: boolean/);
  assert.match(bridge,/typeof data\.screenAssistConnected !== 'boolean'/);
  assert.match(component,/granted:Boolean\(s\?\.screenAssist && s\?\.screenAssistConnected\)/);
  assert.match(component,/Nexa Screen Assist disconnected/);
  assert.match(component,/Toggle the service off and on/);
});
test("one-time local screen reading is available without configuring a live AI provider",()=>{
  assert.ok(/\{screenAssistAvailable\(\)&&<>/.test(app),
    "owner-armed local observation must not depend on a cloud/voice provider being live");
  assert.ok(!/\{engine\.live&&screenAssistAvailable\(\)&&<>/.test(app));
});
test("WhatsApp intent is parsed into a local review even when the optional composer bridge is absent",()=>{
  const start=app.indexOf("// Explicit owner text routes only to a local message review UI.");
  const end=app.indexOf("// A generic message request is NOT an instruction",start);
  const route=app.slice(start,end);
  assert.ok(start>=0 && end>start);
  assert.ok(/const whatsappPreview=parseReviewedWhatsAppIntent\(request\)/.test(app));
  assert.ok(/if\s*\(!restore\s*&&\s*engine\.live\)\s*\{[\s\S]*?if\s*\(whatsappPreview\)/.test(route),
    "the quoted request should produce its local contact review regardless of composer capability");
  assert.ok(!route.includes("whatsAppDraftAvailable()"),
    "optional draft-launch support must not suppress the local review preview");
});
test("Maestro grants Screen Assist in Settings; the bound service and shade are checked after Maestro exits",()=>{
  assert.ok(flow.includes('extendedWaitUntil:\n    visible: "Use Nexa Screen Assist \\\\(Debug\\\\)"'));
  assert.ok(flow.includes('- tapOn:\n          id: "android:id/switch_widget"\n          checked: false'));
  assert.doesNotMatch(flow,/- inputText: "open notification bar"/);
  assert.match(runtimeAcceptance,/open notification bar/);
  assert.match(runtimeAcceptance,/actual Android System UI notification shade appears/);
});
test("Flow 10 expands optional setup without wiping owner app state",()=>{
  assert.ok(/launchApp:\n\s+stopApp: true\n\s+clearState: false/.test(flow));
  assert.ok(/extendedWaitUntil:\n\s+visible: "Make your assistant yours\."\n\s+timeout: 90000/.test(flow),
    "Flow 10 must wait for the debug JS bundle and first-run Setup screen before querying it");
  assert.ok(flow.includes('element: "Show optional permissions and features"'));
  assert.ok(flow.includes('- tapOn: "Show optional permissions and features"'));
  assert.ok(/takeScreenshot: nexa-screen-assist-owner-setting-enabled[\s\S]*?launchApp:\n\s+stopApp: false/.test(flow));
  assert.ok(flow.includes('element: "Hide optional features"'));
  assert.ok(flow.includes('element: "One-time screen assistance"'));
  assert.ok(/element: "One-time screen assistance"\n\s+direction: UP/.test(flow));
  assert.ok(flow.includes('text: "Open Accessibility.*One-time screen assistance.*"'));
  assert.ok(/checked: true[\s\S]*?tapOn: "Turn off"[\s\S]*?checked: false/.test(flow),
    "an already-enabled service must be explicitly restarted inside Android Settings");
});

test("debug Screen Assist has a distinct Settings label and Flow 10 selects it",()=>{
  assert.match(debugManifest,/service android:name="\.ScreenAssistService"[\s\S]*?android:label="@string\/screen_assist_label_debug"/);
  assert.ok(flow.includes('element: "Nexa Screen Assist \\\\(Debug\\\\)"'));
  assert.ok(flow.includes('visible: "Use Nexa Screen Assist \\\\(Debug\\\\)"'));
  assert.ok(flow.includes('id: "android:id/switch_widget"\n        checked: false'));
  assert.ok(flow.includes('id: "android:id/switch_widget"\n    checked: true'));
});

test("the ordered experimental suite targets the same current debug APK in every flow",()=>{
  for(const reviewFlow of reviewFlows) {
    assert.match(reviewFlow,/^appId: dev\.rilandev\.personalaiassistant\.debug$/m);
    assert.doesNotMatch(reviewFlow,/clearState:\s*true/,
      "message and text review flows must preserve Android's Accessibility grant");
  }
});

test("message review flows wait for the cold-started Chat tab before tapping it",()=>{
  for(const reviewFlow of reviewFlows) {
    assert.ok(reviewFlow.includes('- extendedWaitUntil:\n    visible: "Chat"\n    timeout: 90000\n- tapOn: "Chat"'),
      "the debug JS bundle may render after launchApp has returned");
  }
});

test("generic text review scrolls the clipped exact-text row fully into view",()=>{
  const genericFlow=reviewFlows[2];
  assert.ok(genericFlow.includes('- scrollUntilVisible:\n    element:\n      text: "Exact text: project update"\n    direction: DOWN\n    timeout: 30000\n- assertVisible: "Target input: Search"\n- assertVisible: "Exact text: project update"'),
    "checking only the card header can leave the exact-text row clipped under the bottom navigation");
});

test("message review scrolls the exact draft fully into view before asserting it",()=>{
  const messageFlow=reviewFlows[1];
  assert.ok(messageFlow.includes('- scrollUntilVisible:\n    element:\n      text: "Exact draft: I am in the meeting i will call you later"\n    direction: DOWN\n    timeout: 30000\n- assertVisible: "Requested recipient: Mom"\n- assertVisible: "Exact draft: I am in the meeting i will call you later"'),
    "the contact-review heading can be visible while its exact draft remains clipped beneath bottom navigation");
});

test("destroying an old Screen Assist instance cannot disconnect a replacement",()=>{
  const destruction=service.slice(service.indexOf("override fun onDestroy()"),service.indexOf("override fun onAccessibilityEvent("));
  assert.ok(destruction.includes("if (active === this) {"));
  assert.ok(destruction.indexOf("if (active === this)") < destruction.indexOf("ScreenAssistState.connected = false"));
  assert.ok(destruction.includes("active = null"));
});
test("Android 13 restricted settings are consented in Android UI, not sideload bypass",()=>{
  assert.ok(component.includes("s.sdkLevel>=33"));
  assert.ok(component.includes("Allow restricted settings"));
  assert.ok(component.includes("Nexa cannot grant this permission automatically"));
  assert.ok(flow.includes('visible: "Restricted setting"'));
  assert.ok(flow.includes('tapOn: "Allow restricted settings"'));
  assert.doesNotMatch(flow,/adb shell|appops set|settings put secure/);
  assert.doesNotMatch(flow,/- inputText: "open notification bar"/);
});

test("onUnbind invalidates only the current Screen Assist service instance",()=>{
  const unbind=service.slice(service.indexOf("override fun onUnbind("),service.indexOf("override fun onDestroy("));
  assert.match(unbind,/override fun onUnbind\(intent: Intent\?\): Boolean/);
  assert.ok(unbind.includes("if (active === this) {"));
  assert.ok(unbind.indexOf("if (active === this)") < unbind.indexOf("ScreenAssistState.connected = false"));
  assert.match(unbind,/ScreenAssistState\.clear\(\)/);
  assert.match(unbind,/return super\.onUnbind\(intent\)/);
});
test("React Native retries real connection status after resuming from Android Settings",()=>{
  const app=source("apps/mobile/App.tsx");
  assert.match(app,/pollScreenAssistConnection=async\(remaining:number,generation:number\)/);
  assert.match(app,/current\.screenAssist && !current\.screenAssistConnected && remaining>0/);
  assert.match(app,/readNexaSetupStatus\(\)/);
  assert.match(app,/AppState\.currentState!=='active'/);
  assert.match(app,/setTimeout\(\(\)=>\{void pollScreenAssistConnection\(remaining-1,generation\);\},750\)/);
  assert.match(app,/cancelConnectionPoll\(\)/);
  assert.ok(app.includes("if (state==='active')"));
  assert.ok(!app.includes("setSetupStatus({...status,screenAssistConnected:true})"),
    "Never fabricate a bound Accessibility-service state");
});
