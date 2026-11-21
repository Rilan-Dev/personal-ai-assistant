import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("synthetic Screen Assist instrumentation restores the live connection flag", () => {
  const source = readFileSync(
    "apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/BackgroundAssistSafetyAndroidTest.kt",
    "utf8",
  );

  for (const name of [
    "ownerArmedVisibleTextSnapshotIsOneTimeAndOnlyInMemory",
    "screenSnapshotIsBoundedAndOwnerCanDiscardWithoutReading",
  ]) {
    const start = source.indexOf(`@Test fun ${name}()`);
    assert.notEqual(start, -1, `missing synthetic test ${name}`);
    const next = source.indexOf("@Test fun ", start + 1);
    const body = source.slice(start, next === -1 ? undefined : next);
    assert.match(body, /val initialConnection\s*=\s*ScreenAssistState\.connected/,
      `${name} must preserve the current Accessibility binding state`);
    assert.match(body, /ScreenAssistState\.connected\s*=\s*initialConnection/,
      `${name} must restore the live binding state during cleanup`);
    assert.doesNotMatch(body, /ScreenAssistState\.connected\s*=\s*false/,
      `${name} must not force a live service to appear disconnected`);
  }
});
