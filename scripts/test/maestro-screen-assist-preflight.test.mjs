import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

test("debug Screen Assist reachability flow never force-stops the accessibility host app", () => {
  const source = readFileSync(".maestro/nexa/16-qa2-screen-assist-reachable.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "reopening Tools must preserve the enabled Screen Assist service process");
  assert.match(source, /visible:\s*"Pixel Launcher isn't responding"[\s\S]{0,140}tapOn:\s*"Wait"/,
    "reachability flow must wait through the emulator launcher ANR instead of mistaking its dialog for a Nexa rendering failure");
  assert.match(source, /visible:\s*"Collapse"[\s\S]{0,120}tapOn:\s*"Collapse"/,
    "reachability flow must detect the visible collapse control even when the overlay title is absent");
  assert.match(source, /tapOn:\s*"Tools"/,
    "reachability flow must select the visible main app Tools label after collapsing the floating overlay");
  assert.match(source, /tapOn:\s*"Tools"[\s\S]{0,240}scrollUntilVisible:\s*\r?\n\s+element:\s*"Device controls"\s*\r?\n\s+direction:\s*UP[\s\S]{0,120}assertVisible:\s*"Device controls"/,
    "opening Tools must dynamically scroll back to Device controls when the list restores a lower position");
  for (const [label, accessibilityText] of [
    ["Reviewed multi-step screen actions", "Reviewed multi-step screen actions, Tap safe named controls.*"],
    ["Read safe visible-control metadata", "Read safe visible-control metadata, Requires enabled Screen Assist.*"],
  ]) {
    const escapedLabel = label.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&");
    const escapedAccessibilityText = accessibilityText.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&");
    assert.match(source, new RegExp(`visible:\\s*\\r?\\n\\s+text:\\s*"${escapedAccessibilityText}"\\s*\\r?\\n\\s+checked:\\s*false[\\s\\S]{0,500}tapOn:\\s*"${escapedLabel}"[\\s\\S]{0,240}assertVisible:\\s*\\r?\\n\\s+text:\\s*"${escapedAccessibilityText}"\\s*\\r?\\n\\s+checked:\\s*true`),
      `the reachability flow must enable ${label} only when it is off, then verify it is on`);
  }
  assert.match(source, /assertVisible:\s*\r?\n\s+text:\s*"App launching, Open camera.*"\s*\r?\n\s+checked:\s*false/,
    "the focused screen-control preflight must leave general app launching disabled");
  assert.match(source, /Screen Assist binding is verified after the Maestro flow exits/,
    "Android's Accessibility rebind must be checked after Maestro releases its Accessibility driver");
  assert.doesNotMatch(source, /(?:assertVisible|extendedWaitUntil):\s*\r?\n\s+visible:\s*"Enabled"|assertVisible:\s*"Enabled"/,
    "Maestro's own Accessibility driver delays the Screen Assist bind, so this flow cannot assert the live badge");
  assert.match(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Review captured text"[\s\S]{0,100}direction:\s*DOWN[\s\S]{0,120}assertVisible:\s*"Review captured text"/,
    "the captured-text controls must be scrolled into view before the flow asserts they are reachable");
});

test("debug Accessibility Settings preflight safely refreshes either current service state", () => {
  const source = readFileSync(".maestro/nexa/19-qa2-screen-assist-settings-entry.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false[\s\S]{0,140}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Chat"[\s\S]{0,80}timeout:\s*120000/,
    "the preflight must wait for the current debug JavaScript app shell before navigation");
  assert.match(source, /visible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*true/,
    "preflight should detect an already-enabled debug service");
  assert.match(source, /visible:\s*"(?:Stop Nexa Screen Assist \(Debug\)|Turn off).*"[\s\S]{0,180}tapOn:\s*"(?:Stop|Turn off)"/,
    "preflight should confirm Android's service-disable prompt while refreshing it");
  assert.match(source, /visible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*false/,
    "preflight should detect the disabled service before re-enabling it");
  assert.match(source, /assertVisible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*true/,
    "preflight must finish with Android reporting the service enabled");
  assert.match(source, /tapOn:\s*\r?\n\s+text:\s*"Open Android Accessibility Settings"[\s\S]*?scrollUntilVisible:\s*\r?\n\s+element:\s*\r?\n\s+text:\s*"Nexa Screen Assist \(Debug\)"[\s\S]*?direction:\s*(?:UP|DOWN)[\s\S]*?assertVisible:\s*"Nexa Screen Assist \(Debug\)"/,
    "the Android service list must be scrolled dynamically before asserting the debug entry");
  assert.match(source, /pressKey:\s*back[\s\S]{0,120}pressKey:\s*back[\s\S]{0,200}scrollUntilVisible:\s*\r?\n\s+element:\s*"Review captured text"[\s\S]{0,120}assertVisible:\s*"Review captured text"/,
    "returning from Android Settings must scroll the Tools card into view before asserting its control");
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
});

test("debug Accessibility Settings flow collapses the expanded overlay before opening Tools", () => {
  const source = readFileSync(".maestro/nexa/19-qa2-screen-assist-settings-entry.yaml", "utf8");
  assert.match(source, /visible:\s*"System UI isn't responding"[\s\S]{0,120}tapOn:\s*"Wait"[\s\S]{0,260}visible:\s*"Don.t allow"[\s\S]{0,120}tapOn:\s*"Don.t allow"/,
    "the Settings preflight must recover from an emulator System UI dialog and deny unrelated runtime permission prompts before opening Nexa tabs");
  assert.match(source, /runFlow:\s*\r?\n\s+when:\s*\r?\n\s+visible:\s*"Collapse"\s*\r?\n\s+commands:\s*\r?\n\s+-\s+tapOn:\s*"Collapse"[\s\S]{0,120}tapOn:\s*"Tools"/,
    "the Settings preflight must dismiss an expanded floating assistant before tapping the underlying Tools tab");
});

test("debug floating Chat setup starts the bubble through Setup without stopping Nexa", () => {
  const source = readFileSync(".maestro/nexa/20-qa2-floating-chat-setup.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "the overlay setup must preserve the running accessibility service host");
  assert.match(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Android permissions"[\s\S]{0,120}direction:\s*UP[\s\S]{0,160}tapOn:\s*"Android permissions"/,
    "the flow must navigate from Tools to Setup using the rendered permission-card label Maestro can see");
  assert.match(source, /tapOn:\s*"Explore 6 optional features"[\s\S]{0,300}scrollUntilVisible:\s*\r?\n\s+element:\s*"Floating quick tools"\s*\r?\n\s+direction:\s*DOWN/,
    "the floating bubble controls must be revealed by scrolling through the expanded optional Setup section");
  assert.match(source, /when:\s*\r?\n\s+visible:\s*"Show floating bubble"[\s\S]{0,100}tapOn:\s*"Show floating bubble"[\s\S]{0,180}when:\s*\r?\n\s+visible:\s*"Refresh floating bubble"[\s\S]{0,100}tapOn:\s*"Refresh floating bubble"[\s\S]{0,180}assertVisible:\s*"Hide bubble"/,
    "the flow must handle both an already visible bubble and one that needs to be shown, then verify attachment");
});

test("debug floating Chat setup targets the single debug package and normalizes either bubble state", () => {
  const source = readFileSync(".maestro/nexa/27-qa2-floating-chat-setup.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
  assert.match(source, /assertVisible:\s*"Android permissions"\s*\r?\n- tapOn:\s*"Android permissions"/,
    "the flow should use the already-visible Tools permission card without scrolling it away");
  assert.match(source, /tapOn:\s*"Explore 6 optional features"[\s\S]{0,260}scrollUntilVisible:\s*\r?\n\s+element:\s*"Floating quick tools"\s*\r?\n\s+direction:\s*DOWN/);
  const quickToolsIndex = source.indexOf('element: "Floating quick tools"');
  const bubbleActionIndex = source.indexOf('element: "(Show|Refresh) floating bubble"');
  assert.ok(quickToolsIndex >= 0 && bubbleActionIndex > quickToolsIndex,
    "after reaching the Floating quick tools card, the flow must scroll to whichever bubble action is present");
  assert.match(source.slice(bubbleActionIndex, bubbleActionIndex + 140), /direction:\s*DOWN/,
    "the Show or Refresh action must be brought into view with a dynamic downward list scroll");
  assert.match(source, /visible:\s*"Show floating bubble"[\s\S]{0,100}tapOn:\s*"Show floating bubble"[\s\S]{0,180}visible:\s*"Refresh floating bubble"[\s\S]{0,100}tapOn:\s*"Refresh floating bubble"[\s\S]{0,180}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Hide bubble"/,
    "the flow should perform a visible show or refresh action when offered, then accept an already-active or newly-started bubble");
  assert.doesNotMatch(source, /(?:assertVisible|extendedWaitUntil):\s*(?:\r?\n\s+)?visible:\s*"Refresh floating bubble"/,
    "Refresh floating bubble is an optional action label, not a reliable post-action state");
});

test("debug local Chrome fixture flow waits through a browser ANR and asserts its safe target", () => {
  const source = readFileSync(".maestro/nexa/21-qa2-tap-fixture-chrome.yaml", "utf8");
  assert.match(source, /^appId:\s*com\.android\.chrome\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "the fixture flow must preserve the open synthetic Chrome tab");
  assert.match(source, /visible:\s*"Chrome isn't responding"[\s\S]{0,140}tapOn:\s*"Wait"/,
    "a Chrome ANR recovery must wait for the fixture renderer rather than close it");
  assert.match(source, /visible:\s*"Chrome isn't responding"[\s\S]{0,160}tapOn:\s*"Close app"[\s\S]{0,180}launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "if waiting does not recover Chrome, the test must close and reopen only the synthetic browser app");
  assert.match(source, /visible:\s*"Continue with this tab"[\s\S]{0,500}tapOn:\s*"Continue with this tab"/,
    "after a cold boot, Chrome must reopen the existing local fixture tab instead of asserting on its new-tab page");
  assert.match(source, /assertNotVisible:\s*"Chrome isn't responding"[\s\S]{0,140}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Open QA details"[\s\S]{0,100}assertVisible:\s*"Open QA details"/,
    "the test must prove the browser ANR dialog is gone and the fixture target is accessible before Nexa acts");
  assert.doesNotMatch(source, /forceStop|clearState:\s*true/,
    "the local fixture's Chrome state must not be reset through app data clearing");
});

test("QA2 overlay observes a synthetic screen and reviews only the exact model-proposed control", () => {
  const source = readFileSync(".maestro/nexa/25-qa2-overlay-chat-synthetic-action.yaml", "utf8");
  assert.match(source, /^appId:\s*com\.android\.chrome\s*$/m);
  assert.match(source, /inputText:\s*"Tap the Open QA details button"[\s\S]{0,100}tapOn:\s*"Send to Nexa AI"/,
    "the test task must target only the local synthetic fixture through shared Nexa Chat");
  assert.ok(source.includes("assertVisible: '(?s).*device\\.visible_controls.*'\n- tapOn: \"Approve once\""),
    "the read-only current-screen observation must be approved before provider planning");
  assert.ok(source.includes("visible: '(?s).*screen\\.tap_(observed|label).*Open QA details.*'"),
    "the planned tap must name the exact observed synthetic control");
  assert.ok(source.includes("tapOn: \"Approve once\"\n      - extendedWaitUntil:"),
    "the tap proposal must receive its own one-time owner approval");
  assert.ok(source.includes("provider rejected|HTTP 40[13]"),
    "provider authentication failure must be surfaced as a no-tap outcome");
  assert.match(source, /assertNotVisible:\s*"QA tap passed on the local fixture\./,
    "provider or screen-assist failure must not claim the synthetic tap succeeded");
  assert.doesNotMatch(source, /adb shell input|tapOn:\s*\(\d+\s*,|forceStop|clearState:\s*true/,
    "the synthetic screen action must not bypass the shared agent or use fixed coordinates");
});

test("debug provider setup opens the live connection test without clearing app data", () => {
  const source = readFileSync(".maestro/nexa/23-qa2-open-ai-provider-settings.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
  assert.match(source, /visible:\s*"Collapse"[\s\S]{0,120}tapOn:\s*"Collapse"[\s\S]{0,160}tapOn:\s*"Tools"[\s\S]{0,180}scrollUntilVisible:\s*\r?\n\s+element:\s*"Android permissions"[\s\S]{0,100}direction:\s*UP[\s\S]{0,140}tapOn:\s*"Android permissions"[\s\S]{0,180}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Nexa · Setup"/,
    "provider setup must recover from the floating overlay and navigate from the main Chat tab through Tools");
  assert.match(source, /when:\s*\r?\n\s+visible:\s*"Explore 6 optional features"[\s\S]{0,100}tapOn:\s*"Explore 6 optional features"[\s\S]{0,200}scrollUntilVisible:\s*\r?\n\s+element:\s*"Configure AI"[\s\S]{0,100}direction:\s*DOWN[\s\S]{0,140}tapOn:\s*"Configure AI"/,
    "provider setup must expand optional features and dynamically scroll to the live Configure AI control");
  assert.match(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Save & Enable AI"[\s\S]{0,100}direction:\s*DOWN[\s\S]{0,160}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Save & Enable AI"[\s\S]{0,100}assertVisible:\s*"Test connection"/,
    "the provider form must be scrolled into view before entering test credentials");
  assert.doesNotMatch(source, /clearState:\s*true|inputText:/,
    "the repeatable navigation flow must not persist a credential in Maestro YAML or logs");
});

test("debug provider verification saves through the Keystore and confirms a live response", () => {
  const source = readFileSync(".maestro/nexa/24-qa2-save-and-test-ai-provider.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
  assert.match(source, /tapOn:\s*"Save & Enable AI"[\s\S]{0,180}scrollUntilVisible:[\s\S]{0,160}element:\s*"AI provider saved in Android Keystore\.\*"[\s\S]{0,140}assertVisible:\s*"AI provider saved in Android Keystore\.\*"/,
    "the provider must be saved in Android Keystore before testing chat connectivity");
  assert.match(source, /tapOn:\s*"Test connection"[\s\S]{0,180}extendedWaitUntil:[\s\S]{0,160}visible:\s*"Connection successful\.\*"/,
    "a passing provider setup flow must require a real successful provider response");
  assert.doesNotMatch(source, /inputText:|sk_[A-Za-z0-9_-]{12,}|clearState:\s*true/,
    "provider credentials must not be written to QA YAML, screenshots, or a clear-state installation");
});
