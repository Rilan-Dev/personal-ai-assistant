import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";
const source=(name)=>readFileSync(name,"utf8").replace(/\r\n/g,"\n");
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
const gradle=source("apps/mobile/android/app/build.gradle");
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
  assert.ok(flow.includes('extendedWaitUntil:\n    visible: "Use Nexa Screen Assist (Debug)"'));
  assert.ok(flow.includes('- tapOn:\n          id: "android:id/switch_widget"\n          checked: false'));
  assert.doesNotMatch(flow,/- inputText: "open notification bar"/);
  assert.match(runtimeAcceptance,/open notification bar/);
  assert.match(runtimeAcceptance,/actual Android System UI notification shade appears/);
});
test("Flow 10 restarts its transient tab and scroll state without clearing owner app data",()=>{
  assert.ok(/launchApp:\n\s+stopApp: true\n\s+clearState: false/.test(flow),
    "reopen the live debug runtime at its default tab without wiping owner data or permission grants");
  assert.ok(/extendedWaitUntil:\n\s+visible: "Make your assistant yours\."\n\s+timeout: 90000/.test(flow),
    "Flow 10 must wait for the live debug JS bundle and explicitly opened Setup screen before querying it");
  assert.ok(flow.includes('element: "Show optional permissions and features"'));
  assert.ok(flow.includes('- tapOn:\n    text: "Show optional permissions and features"\n    point: "50%,50%"'));
  assert.ok(/takeScreenshot: nexa-screen-assist-owner-setting-enabled[\s\S]*?launchApp:\n\s+stopApp: false/.test(flow));
  assert.ok(flow.includes('- tapOn:\n    text: "Show optional permissions and features"\n    point: "50%,50%"\n- scrollUntilVisible:\n    element: "One-time screen assistance"\n    direction: DOWN\n    timeout: 30000\n- assertVisible: "One-time screen assistance"'),
    "after expanding optional setup, scroll the clipped first permission card into view before asserting it");
  assert.ok(flow.includes('element: "Open Accessibility.*One-time screen assistance.*"'));
  assert.ok(/element: "Open Accessibility\.\*One-time screen assistance\.\*"\n\s+direction: DOWN/.test(flow),
    "scroll dynamically to the live Screen Assist action instead of searching for the offscreen accordion header");
  assert.ok(flow.includes('text: "Open Accessibility.*One-time screen assistance.*"'));
  assert.match(flow,/text: "Open Accessibility\.\*One-time screen assistance\.\*"\n\s+point: "50%,50%"/);
  assert.ok(/checked: true[\s\S]*?tapOn: "Turn off"[\s\S]*?checked: false/.test(flow),
    "an already-enabled service must be explicitly restarted inside Android Settings");
  assert.match(flow,/visible: "Stop Nexa Screen Assist[\s\S]*?tapOn: "Stop"/,
    "OEM Accessibility settings may require an explicit Stop confirmation before restarting the service");
});

test("Flow 10 enters Setup explicitly from any already-open debug tab",()=>{
  assert.match(flow,/^appId: dev\.rilandev\.personalaiassistant\.debug$/m);
  assert.match(flow,/visible: "Pixel Launcher\.\*responding"[\s\S]*?tapOn: "Wait"/,
    "recover the Android launcher only when its boot-time ANR dialog is actually visible");
  assert.ok(flow.includes(String.raw`visible: "(?s).*(?:How can I help today\\?|Make your assistant yours\\.|Don.t allow|Nexa · Tools).*"`),
    "accept the initialized Chat, first-run Setup, or already-open Tools screen before selecting permission setup");
  assert.ok(flow.includes(String.raw`when:
      visible: "How can I help today\\?"
    commands:
      - tapOn: "Tools"`),
    "route an initialized app from Chat to Tools before opening its permission setup");
  assert.ok(flow.includes(String.raw`when:
      visible: "Make your assistant yours\\."
    commands:
      - tapOn: "Tools"`),
    "route a first-run Setup screen to Tools without assuming that it is the only startup state");
  assert.ok(flow.includes('timeout: 180000'));
  assert.ok(flow.includes('element: "Open permission setup"'));
  assert.ok(flow.includes('- tapOn:\n    text: "Open permission setup"\n    point: "50%,50%"'),
    "tap the live card's center by bounds instead of blocking on Android's accessibility click action");
  assert.match(flow,/text: "Open Nexa app info \(restricted settings\)"\n\s+point: "50%,50%"/);
});

test("Maestro navigation selectors match the rendered Chat and Tools labels",()=>{
  const navigationFlows=[
    flow,
    source(".maestro/nexa/16-qa2-screen-assist-reachable.yaml"),
    source(".maestro/physical-qa/00-device-preflight.yaml"),
    source(".maestro/physical-qa/01-enable-accessibility-services.yaml"),
    source(".maestro/physical-qa/02-ready-check.yaml"),
  ];
  assert.ok(flow.includes('visible: "Make your assistant yours."'),
    "anchor cold start on the rendered Setup heading rather than the application package label");
  assert.ok(flow.includes('- tapOn: "Tools"'));
  const bottomNavStart=app.indexOf('<View style={styles.bottomNav}>');
  const bottomNavEnd=app.indexOf('</SafeAreaView>',bottomNavStart);
  const bottomNav=app.slice(bottomNavStart,bottomNavEnd);
  assert.ok(bottomNavStart>=0&&bottomNavEnd>bottomNavStart,
    "the primary navigation must have a rendered container");
  assert.match(bottomNav,/accessibilityLabel=\{item\.label\}/,
    "the visible tab label is its accessible name; the tab role supplies the role without a duplicate suffix");
  for(const navigationFlow of navigationFlows) {
    assert.doesNotMatch(navigationFlow,/"(?:Chat|Tools) tab"/,
      "the rendered accessible hierarchy exposes the visible labels Chat and Tools without a tab suffix");
  }
});

test("Setup optional-feature accordion exposes its current action label to accessibility",()=>{
  assert.match(component,/accessibilityLabel=\{showOptional \? 'Hide optional features' : 'Show optional permissions and features'\}/,
    "the expanded label must match visible text so assistive tools can verify the disclosure state");
  assert.match(component,/accessibilityState=\{\{expanded:showOptional\}\}/);
});

test("Setup clips offscreen permission cards so Android accessibility queries stay bounded",()=>{
  assert.match(component,/<ScrollView[^>]*removeClippedSubviews=\{true\}/,
    "the setup list must detach offscreen cards instead of exposing every optional card in one accessibility tree");
});

test("the single debug Screen Assist Settings label is selected by Flow 10",()=>{
  assert.match(debugManifest,/service android:name="\.ScreenAssistService"[\s\S]*?android:label="\$\{screenAssistServiceLabel\}"/);
  assert.match(gradle,/screenAssistServiceLabel:[\s\S]*?'Nexa Screen Assist \(Debug\)'/);
  assert.doesNotMatch(gradle,/Nexa Screen Assist QA2/);
  assert.ok(flow.includes('element: "Nexa Screen Assist (Debug)"'));
  const serviceSelection=flow.slice(flow.indexOf('notVisible: "Use Nexa Screen Assist (Debug)"'),
    flow.indexOf('visible: "Use Nexa Screen Assist (Debug)"'));
  assert.match(serviceSelection,/element: "Nexa Screen Assist \(Debug\)"[\s\S]*?direction: DOWN/,
    "the Infinix Accessibility list places Nexa below its current position, so the flow must scroll to lower entries");
  assert.ok(flow.includes('visible: "Use Nexa Screen Assist (Debug)"'));
  assert.ok(flow.includes('id: "android:id/switch_widget"\n        checked: false'));
  assert.ok(flow.includes('id: "android:id/switch_widget"\n    checked: true'));
});

test("the ordered experimental suite targets the same current .debug APK in every flow",()=>{
  for(const reviewFlow of reviewFlows) {
    assert.match(reviewFlow,/^appId: dev\.rilandev\.personalaiassistant\.debug$/m);
    assert.doesNotMatch(reviewFlow,/clearState:\s*true/,
      "message and text review flows must preserve Android's Accessibility grant");
  }
  for(const reviewFlow of reviewFlows.slice(1)) {
    assert.match(reviewFlow,/- launchApp:\n\s+stopApp: true\n\s+clearState: false/,
      "message-channel and text-entry flows reset ephemeral request state without clearing Android grants");
  }
  assert.match(reviewFlows[0],/- launchApp:\n\s+stopApp: false\n\s+clearState: false/,
    "the local WhatsApp review uses the already-running live app so it does not force a slow Metro cold start");
});

test("message review flows wait for Chat and assert its persistent composer before typing",()=>{
  for(const reviewFlow of reviewFlows) {
    assert.match(reviewFlow,/- extendedWaitUntil:\n\s+visible:\s*"Chat"\n(?:\s*#.*\n)*\s+timeout:\s*\d+\n- tapOn:\s*"Chat"\n- assertVisible: "Assistant command"/,
      "the composer remains available after the app has retained older conversation history");
  }
});

test("experimental flows reset Flow 10 scroll state and assert the persistent Chat composer",()=>{
  assert.match(flow,/- launchApp:\n\s+stopApp: true\n\s+clearState: false/,
    "Flow 10 must reset stale tab and scroll state before opening Android permission setup");
  for(const reviewFlow of reviewFlows) {
    assert.match(reviewFlow,/- assertVisible: "Assistant command"/,
      "the composer is stable when Chat contains older messages");
    assert.doesNotMatch(reviewFlow,/assertVisible:\s*"How can I help today\?"/,
      "the empty-chat greeting is not present after any prior conversation");
  }
});
test("generic text review scrolls the clipped exact-text row fully into view",()=>{
  const genericFlow=reviewFlows[2];
  const scrollBlocks=[...genericFlow.matchAll(/- scrollUntilVisible:\n([\s\S]*?)(?=\n- |\n#|$)/g)].map(match=>match[0]);
  for(const target of [
    "Review text entry into another app",
    "Exact text: project update",
    "Prepare approved text entry",
    "Discard text",
  ]) {
    assert.ok(scrollBlocks.some(block=>block.includes(`text: "${target}"`)&&block.includes("timeout: 90000")),
      `the physical Tools page needs a longer dynamic search for ${target}`);
  }
  assert.ok(genericFlow.includes('- scrollUntilVisible:\n    element:\n      text: "Exact text: project update"\n    direction: DOWN\n    timeout: 90000\n- assertVisible: "Target input: Search"\n- assertVisible: "Exact text: project update"'),
    "checking only the card header can leave the exact-text row clipped under the bottom navigation");
});

test("message review discard control exposes a stable accessible name for Maestro",()=>{
  const at=app.indexOf("Discard message review — do not send");
  assert.ok(at>=0,"the visible no-send discard action exists");
  const start=app.lastIndexOf("<Pressable",at);
  const pressable=app.slice(start,at);
  assert.match(pressable,/accessibilityRole="button"/);
  assert.match(pressable,/accessibilityLabel="Discard message review, do not send"/,
    "the button needs a stable ASCII accessible name independent of the dash glyph");
  const messageFlow=reviewFlows[1];
  assert.match(messageFlow,/element: "Discard message review, do not send"\n\s+direction: DOWN\n\s+visibilityPercentage: 50\n\s+timeout: 90000/);
  assert.match(messageFlow,/assertVisible: "Discard message review, do not send"/);
});
test("message review selectors avoid a platform-specific dash glyph",()=>{
  const messageFlow=reviewFlows[1];
  assert.doesNotMatch(messageFlow,/Discard message review — do not send/);
  assert.match(messageFlow,/element: "Discard message review, do not send"/);
});
test("message review scrolls the exact draft, decision and discard controls into view",()=>{
  const messageFlow=reviewFlows[1];
  for(const target of [
    "Exact draft: I am in the meeting i will call you later",
    "Messaging app decision",
    "Discard message review, do not send",
  ]) {
    const visibleIndex=messageFlow.indexOf(`- assertVisible: "${target}"`);
    const scrollBlocks=[...messageFlow.matchAll(/- scrollUntilVisible:\n([\s\S]*?)(?=\n- |\n#|$)/g)];
    assert.ok(visibleIndex>=0&&scrollBlocks.some(block=>
      (block[0].includes(`text: "${target}"`)||block[0].includes(`element: "${target}"`))&&
      block.index<visibleIndex),
      `scroll the exact review target into view before asserting: ${target}`);
  }
});

test("WhatsApp owner preview stays directly visible for local review",()=>{
  const whatsappFlow=reviewFlows[0];
  assert.ok(whatsappFlow.includes('inputText: \'Send a WhatsApp message to my mom "Nexa QA local review only"\''),
    "the local test uses unique text so stale in-memory review state cannot satisfy it");
  assert.ok(whatsappFlow.includes('assertVisible:\n    text: "Requested contact: mom.*Nothing was sent.*"'),
    "the owner review flow verifies the local no-send result directly");
  assert.ok(whatsappFlow.includes('assertVisible: "Draft text: Nexa QA local review only"'),
    "the exact message text remains visible in Nexa for owner review");
  assert.ok(!whatsappFlow.includes('scrollUntilVisible'),
    "the review result is near the top of Tools and is not buried under history");
});

test("pending WhatsApp review appears before unbounded Tools history",()=>{
  const tools=app.slice(app.indexOf("{tab === 'tools'"));
  const introAt=tools.indexOf("Tools & permissions");
  const previewAt=tools.indexOf("whatsAppDraftNotice!==''");
  const deviceControlsAt=tools.indexOf(">Device controls</Text>");
  assert.ok(previewAt>introAt && previewAt<deviceControlsAt,
    "the local message review must render near the top of Tools before long history cards");
  assert.ok(tools.includes("Draft text: {whatsAppDraft}"),
    "the review card shows the exact local draft for confirmation");
  assert.equal(tools.indexOf("whatsAppDraftNotice!==''",previewAt+1),-1,
    "the notice is rendered only once instead of appearing again far down the page");
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
test("Tools reflects the bound Screen Assist service even when live voice is off",()=>{
  const statusCall=app.indexOf("const refresh=(remaining=80)=>getScreenAssistStatus()");
  const effectStart=app.lastIndexOf("useEffect(()=>{",statusCall);
  const effectEnd=app.indexOf("},[tab]);",statusCall)+"},[tab]);".length;
  const effect=app.slice(effectStart,effectEnd);
  assert.ok(statusCall>0&&effectStart>=0&&effectEnd>statusCall,"the Screen Assist status refresh effect must exist");
  assert.match(effect,/if\(!screenAssistAvailable\(\)\)return;/);
  assert.doesNotMatch(effect,/if\(!engine\.live\|\|!screenAssistAvailable\(\)\)return;/,
    "Screen Assist status is independent of whether live voice is running");
  assert.match(effect,/setScreenAssistEnabled\(value\.enabled\)/);
  assert.match(app,/const screenAssistSetupNotice='Enable Nexa Screen Assist in Android Accessibility settings\. Return and arm a single capture\.'/);
  assert.match(effect,/if\(value\.enabled\)setScreenAssistNotice\(notice=>notice===screenAssistSetupNotice\?'':notice\)/,
    "once Android confirms the service is connected, clear only the stale setup prompt and retain capture/review feedback");
  assert.match(effect,/AppState\.addEventListener\('change'/);
  assert.match(effect,/if\(!value\.enabled&&remaining>0\)[\s\S]{0,160}setTimeout\(\(\)=>\{void refresh\(remaining-1\);\},1500\)/,
    "the badge must keep polling for up to two minutes to cover Android's delayed post-Maestro service rebind, with a fixed upper bound");
  assert.match(effect,/\},\[tab\]\);/);
  const toolsTab=app.slice(app.indexOf('<View style={styles.bottomNav}>'));
  assert.match(toolsTab,/onPress=\{\(\)=>\{\s*navigate\(item\.id\);\s*if\(item\.id==='tools'\)screenAssistRefreshRef\.current\?\.\(\)/,
    "selecting Tools again must restart the bounded Screen Assist connection refresh even if the selected tab did not change");
});
