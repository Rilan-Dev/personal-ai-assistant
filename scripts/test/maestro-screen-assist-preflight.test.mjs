import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

test("QA2 Screen Assist reachability flow never force-stops the accessibility host app", () => {
  const source = readFileSync(".maestro/nexa/16-qa2-screen-assist-reachable.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.physicalqa2\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "reopening Tools must preserve the enabled Screen Assist service process");
  assert.match(source, /visible:\s*"Collapse"[\s\S]{0,120}tapOn:\s*"Collapse"/,
    "reachability flow must detect the visible collapse control even when the overlay title is absent");
  assert.match(source, /tapOn:\s*"Tools"/,
    "reachability flow must select the visible main app Tools label after collapsing the floating overlay");
  assert.match(source, /tapOn:\s*"Tools"[\s\S]{0,200}assertVisible:\s*"Device controls"/,
    "opening Tools must assert its already-visible Device controls section without scrolling it away");
  assert.doesNotMatch(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Device controls"/,
    "Device controls is already visible after selecting Tools; scrolling for it can move away from the target");
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

test("QA2 Accessibility Settings preflight safely refreshes either current service state", () => {
  const source = readFileSync(".maestro/nexa/19-qa2-screen-assist-settings-entry.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.physicalqa2\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false[\s\S]{0,140}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Chat"[\s\S]{0,80}timeout:\s*120000/,
    "the preflight must wait for the offline QA2 JavaScript app shell before navigation");
  assert.match(source, /visible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*true/,
    "preflight should detect an already-enabled QA2 service");
  assert.match(source, /visible:\s*"(?:Stop Nexa Screen Assist QA2|Turn off).*"[\s\S]{0,180}tapOn:\s*"(?:Stop|Turn off)"/,
    "preflight should confirm Android's service-disable prompt while refreshing it");
  assert.match(source, /visible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*false/,
    "preflight should detect the disabled service before re-enabling it");
  assert.match(source, /assertVisible:\s*\r?\n\s+id:\s*"android:id\/switch_widget"\s*\r?\n\s+checked:\s*true/,
    "preflight must finish with Android reporting the service enabled");
  assert.match(source, /tapOn:\s*\r?\n\s+text:\s*"Open Android Accessibility Settings"[\s\S]*?scrollUntilVisible:\s*\r?\n\s+element:\s*\r?\n\s+text:\s*"Nexa Screen Assist QA2"[\s\S]*?direction:\s*(?:UP|DOWN)[\s\S]*?assertVisible:\s*"Nexa Screen Assist QA2"/,
    "the Android service list must be scrolled dynamically before asserting the QA2 entry");
  assert.match(source, /pressKey:\s*back[\s\S]{0,120}pressKey:\s*back[\s\S]{0,200}scrollUntilVisible:\s*\r?\n\s+element:\s*"Review captured text"[\s\S]{0,120}assertVisible:\s*"Review captured text"/,
    "returning from Android Settings must scroll the Tools card into view before asserting its control");
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
});

test("QA2 Accessibility Settings flow collapses the expanded overlay before opening Tools", () => {
  const source = readFileSync(".maestro/nexa/19-qa2-screen-assist-settings-entry.yaml", "utf8");
  assert.match(source, /visible:\s*"System UI isn't responding"[\s\S]{0,120}tapOn:\s*"Wait"[\s\S]{0,260}visible:\s*"Don.t allow"[\s\S]{0,120}tapOn:\s*"Don.t allow"/,
    "the Settings preflight must recover from an emulator System UI dialog and deny unrelated runtime permission prompts before opening Nexa tabs");
  assert.match(source, /runFlow:\s*\r?\n\s+when:\s*\r?\n\s+visible:\s*"Collapse"\s*\r?\n\s+commands:\s*\r?\n\s+-\s+tapOn:\s*"Collapse"[\s\S]{0,120}tapOn:\s*"Tools"/,
    "the Settings preflight must dismiss an expanded floating assistant before tapping the underlying Tools tab");
});

test("QA2 floating Chat setup starts the bubble through Setup without stopping Nexa", () => {
  const source = readFileSync(".maestro/nexa/20-qa2-floating-chat-setup.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.physicalqa2\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "the QA2 overlay setup must preserve the running accessibility service host");
  assert.match(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Android permissions"[\s\S]{0,120}direction:\s*UP[\s\S]{0,160}tapOn:\s*"Android permissions"/,
    "the flow must navigate from Tools to Setup using the rendered permission-card label Maestro can see");
  assert.match(source, /tapOn:\s*"Explore 6 optional features"[\s\S]{0,260}scrollUntilVisible:\s*\r?\n\s+element:\s*"Show floating bubble"/,
    "the floating bubble action must be located after expanding the optional Setup section");
  assert.match(source, /tapOn:\s*"Show floating bubble"[\s\S]{0,180}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Refresh floating bubble"[\s\S]{0,100}assertVisible:\s*"Hide bubble"/,
    "the flow must verify that Android attached the bubble instead of assuming the tap worked");
});

test("QA2 local Chrome fixture flow waits through a browser ANR and asserts its safe target", () => {
  const source = readFileSync(".maestro/nexa/21-qa2-tap-fixture-chrome.yaml", "utf8");
  assert.match(source, /^appId:\s*com\.android\.chrome\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "the fixture flow must preserve the open synthetic Chrome tab");
  assert.match(source, /visible:\s*"Chrome isn't responding"[\s\S]{0,140}tapOn:\s*"Wait"/,
    "a Chrome ANR recovery must wait for the fixture renderer rather than close it");
  assert.match(source, /visible:\s*"Chrome isn't responding"[\s\S]{0,160}tapOn:\s*"Close app"[\s\S]{0,180}launchApp:\s*\r?\n\s+stopApp:\s*false/,
    "if waiting does not recover Chrome, the test must close and reopen only the synthetic browser app");
  assert.match(source, /assertNotVisible:\s*"Chrome isn't responding"[\s\S]{0,140}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Open QA details"[\s\S]{0,100}assertVisible:\s*"Open QA details"/,
    "the test must prove the browser ANR dialog is gone and the fixture target is accessible before Nexa acts");
  assert.doesNotMatch(source, /forceStop|clearState:\s*true/,
    "the local fixture's Chrome state must not be reset through app data clearing");
});

test("QA2 provider setup opens the live connection test without clearing app data", () => {
  const source = readFileSync(".maestro/nexa/23-qa2-open-ai-provider-settings.yaml", "utf8");
  assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.physicalqa2\s*$/m);
  assert.match(source, /launchApp:\s*\r?\n\s+stopApp:\s*false/);
  assert.match(source, /when:\s*\r?\n\s+visible:\s*"Nexa · Setup"[\s\S]{0,160}scrollUntilVisible:\s*\r?\n\s+element:\s*"Configure AI"[\s\S]{0,100}direction:\s*UP[\s\S]{0,140}tapOn:\s*"Configure AI"/,
    "provider setup should use the live Setup control instead of direct storage writes");
  assert.match(source, /scrollUntilVisible:\s*\r?\n\s+element:\s*"Save & Enable AI"[\s\S]{0,100}direction:\s*DOWN[\s\S]{0,160}extendedWaitUntil:\s*\r?\n\s+visible:\s*"Save & Enable AI"[\s\S]{0,100}assertVisible:\s*"Test connection"/,
    "the provider form must be scrolled into view before entering test credentials");
  assert.doesNotMatch(source, /clearState:\s*true|inputText:/,
    "the repeatable navigation flow must not persist a credential in Maestro YAML or logs");
});
