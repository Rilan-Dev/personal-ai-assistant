import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
const read = path => readFileSync(new URL("../../" + path, import.meta.url), "utf8");
const build = read("apps/mobile/android/app/build.gradle");
const app = read("apps/mobile/app.json");
const manifest = read("apps/mobile/android/app/src/main/AndroidManifest.xml");
const module = read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt");

test("Android package IDs align with Expo config", () => {
  assert.match(build, /applicationId "dev\.rilandev\.personalaiassistant"/);
  assert.equal(JSON.parse(app).expo.android.package, "dev.rilandev.personalaiassistant");
});
test("explicit Kotlin ReactPackage is registered", () => {
  assert.match(read("apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/MainApplication.kt"),
    /add\(DeviceControlPackage\(\)\)/);
});
test("AndroidManifest requests minimal package visibility, not dangerous permissions", () => {
  assert.match(manifest, /<package android:name="com\.whatsapp"\s*\/>/);
  assert.match(manifest, /<package android:name="com\.whatsapp\.w4b"\s*\/>/);
  assert.doesNotMatch(manifest, /READ_SMS|SEND_SMS|READ_CALL_LOG|QUERY_ALL_PACKAGES|SYSTEM_ALERT_WINDOW/);
});
test("native operations are restricted to camera and allowlisted apps", () => {
  assert.match(module, /"whatsapp" -> "com\.whatsapp"/);
  assert.match(module, /"whatsapp-business" -> "com\.whatsapp\.w4b"/);
  assert.match(module, /INTENT_ACTION_STILL_IMAGE_CAMERA/);
  assert.match(module, /UNSUPPORTED_APP/);
  assert.doesNotMatch(module, /sendMessage|ACTION_CALL|READ_CONTACTS|sendTextMessage/);
});
test("iOS bundle ID aligns with Expo config", () => {
  const ios = read("apps/mobile/ios/mobile.xcodeproj/project.pbxproj");
  assert.match(ios, /PRODUCT_BUNDLE_IDENTIFIER = dev\.rilandev\.personalaiassistant;/);
  assert.equal(JSON.parse(app).expo.ios.bundleIdentifier, "dev.rilandev.personalaiassistant");
});
