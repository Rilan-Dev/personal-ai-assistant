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
  assert.match(flow,/Nexa Screen Assist connected\.\*native screen actions ready/);
  assert.ok(flow.indexOf("Nexa Screen Assist connected") < flow.indexOf("open notification bar"));
  assert.match(flow,/Confirm requested action/);
  assert.match(flow,/No notifications\|Notification settings\|Internet\|Clear all/);
});

test("destroying an old Screen Assist instance cannot disconnect a replacement",()=>{
  const destruction=service.slice(service.indexOf("override fun onDestroy()"),service.indexOf("override fun onAccessibilityEvent("));
  assert.match(destruction,/if \\(active === this\\) \\{/);
  assert.ok(destruction.indexOf("if (active === this)") < destruction.indexOf("ScreenAssistState.connected = false"));
  assert.match(destruction,/active = null/);
});
test("Android 13 restricted settings are consented in Android UI, not sideload bypass",()=>{
  assert.match(component,/s\\.sdkLevel>=33/);
  assert.match(component,/Allow restricted settings/);
  assert.match(component,/Nexa cannot grant this permission automatically/);
  assert.match(flow,/when:\\s+visible: "Restricted setting"/);
  assert.match(flow,/tapOn: "Allow restricted settings"/);
  assert.doesNotMatch(flow,/adb shell|appops set|settings put secure/);
  assert.ok(flow.indexOf("Nexa Screen Assist connected") < flow.indexOf("open notification bar"));
});
