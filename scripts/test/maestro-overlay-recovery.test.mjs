import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const flows = [
  ".maestro/nexa/05-overlay-bubble-end-to-end.yaml",
  ".maestro/nexa/18-aca-business-overlay-fail-closed.yaml"
];

for (const path of flows) {
  test(`${path} handles Android Settings ANR and accepts generated QA versions`, () => {
    const source = readFileSync(path, "utf8");
    assert.match(source, /^appId:\s*dev\.rilandev\.personalaiassistant\.debug\s*$/m,
      "overlay smoke flow must launch the single .debug package used by the live build");
    assert.match(source, /visible: "Settings isn't responding"[\s\S]{0,400}tapOn: "Close app"/);
    assert.match(source, /visible: "Pixel Launcher isn't responding"[\s\S]{0,250}tapOn: "Wait"/,
      "overlay smoke flow should dismiss the emulator launcher ANR with Android's Wait action");
    const firstLaunch = source.indexOf("- launchApp:");
    const launcherAnr = source.indexOf('visible: "Pixel Launcher isn\'t responding"');
    assert.ok(firstLaunch >= 0 && launcherAnr >= 0 && launcherAnr < firstLaunch,
      "recover a pre-existing launcher ANR before launchApp can wait on that launcher");
    assert.match(source, /visible: "Don.t allow"[\s\S]{0,200}tapOn: "Don.t allow"/,
      "overlay smoke flow should dismiss the first-run runtime permission prompt");
    assert.match(source,
      /tapOn: "Don.t allow"[\s\S]{0,350}visible: "Pixel Launcher isn't responding"[\s\S]{0,250}tapOn: "Wait"/,
      "overlay smoke flow should recover if the launcher ANRs again after the permission prompt");
    assert.ok(source.includes('"0\\\\.3\\\\.[0-9]{10}"'),
      "version assertion should match generated QA versionName values");
    assert.match(source, /element:\s*(?:"Floating quick tools"|text:\s*"Floating quick tools")\s+direction: DOWN\s+visibilityPercentage: 30\s+timeout: 60000/);
    assert.doesNotMatch(source, /2026111607/,
      "flow must not pin the old v1607 build");
  });
}

test("Flow05 recovers a repeated launcher ANR immediately before optional setup navigation", () => {
  const source = readFileSync(".maestro/nexa/05-overlay-bubble-end-to-end.yaml", "utf8");
  assert.match(source,
    /visible:\s*'Make your assistant yours\.'[\s\S]{0,180}visible:\s*"Pixel Launcher isn't responding"[\s\S]{0,120}tapOn:\s*"Wait"[\s\S]{0,180}tapOn:\s*"Show optional permissions and features"/,
    "Flow05 must clear a launcher ANR that reappears after the setup heading is visible");
});
