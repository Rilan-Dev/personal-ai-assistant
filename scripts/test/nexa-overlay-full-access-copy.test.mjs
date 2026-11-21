import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const overlay = readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt",
  "utf8"
);
const permissionCopy = readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayPermissionCopy.kt",
  "utf8"
);

test("floating chat derives its approval notice from the selected permission mode", () => {
  assert.ok(
    /NexaToolOverlayPermissionCopy\.note\(\s*NexaVoicePermissionPolicy\.mode\(this@NexaToolOverlayService\)\s*\)/.test(overlay),
    "the overlay should read the saved permission mode each time it renders"
  );
  assert.ok(
    !overlay.includes('text("On-device actions need your approval"'),
    "the overlay must not show Ask-every-time copy in every permission mode"
  );
  assert.ok(
    permissionCopy.includes("NexaVoicePermissionPolicy.FULL_ACCESS") &&
      permissionCopy.includes("Full Access: routine tools run without Nexa approval") &&
      permissionCopy.includes("protected sends and Android grants still apply"),
    "Full Access copy must state both the skipped routine review and remaining boundaries"
  );
});
