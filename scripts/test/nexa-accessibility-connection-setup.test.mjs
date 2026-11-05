import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";
const source=(name)=>readFileSync(name,"utf8");
const setup=source("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaSetupModule.kt");
const component=source("apps/mobile/src/components/NexaSetupScreen.tsx");
const bridge=source("apps/mobile/src/native/nexaSetup.ts");
const flow=source(".maestro/experimental/10-agent-notification-shade.yaml");
const service=source("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/ScreenAssistService.kt");

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
test("Maestro must observe bound Screen Assist before it asks Nexa to open notification bar",()=>{
  assert.ok(flow.includes('element: "Nexa Screen Assist connected\\\\."'),
    "The flow must wait for the real connected-service status shown by Setup");
  assert.ok(flow.indexOf("Nexa Screen Assist connected") < flow.indexOf("open notification bar"));
  assert.match(flow,/Confirm requested action/);
  assert.match(flow,/No notifications\|Notification settings\|Internet\|Clear all/);
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
  assert.ok(flow.indexOf("Nexa Screen Assist connected") < flow.indexOf("open notification bar"));
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
