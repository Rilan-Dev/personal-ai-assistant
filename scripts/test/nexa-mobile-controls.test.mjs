import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const file = p => readFileSync(p,'utf8');
const root='apps/mobile/android/app/src/main/';
const launcher=file(root+'java/dev/rilandev/personalaiassistant/NexaLauncherNavigationService.kt');
const whatsapp=file(root+'java/dev/rilandev/personalaiassistant/NexaWhatsAppComposeModule.kt');
const manifest=file(root+'AndroidManifest.xml');
const launchXml=file(root+'res/xml/nexa_launcher_navigation_config.xml');
const ui=file('apps/mobile/App.tsx');

test('launcher-only accessible automation is separate and opt-in',()=>{
  assert.match(manifest,/android:name="\.NexaLauncherNavigationService"/);
  assert.match(manifest,/android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/);
  assert.match(launchXml,/canPerformGestures="true"/);
  assert.match(launcher,/currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(launcher,/category\(Intent.CATEGORY_HOME\)|addCategory\(Intent.CATEGORY_HOME\)/);
  assert.match(launcher,/LauncherNavigationState\.allows\(pkg\)/);
  assert.match(launcher,/swipeCount >= 8/);
  assert.match(launcher,/25_000/);
  assert.match(launcher,/name\.equals\("GitHub", ignoreCase = true\)/);
  assert.doesNotMatch(launcher,/ACTION_CLICK|ACTION_SET_TEXT|performGlobalAction|QUERY_ALL_PACKAGES/);
  assert.doesNotMatch(launcher,/sendMessage\(|readNotifications|READ_CONTACTS/);
  assert.doesNotMatch(manifest,/QUERY_ALL_PACKAGES|ACCESS_BACKGROUND_LOCATION/);
});
test('WhatsApp draft dispatch is not an automatic send',()=>{
  assert.match(whatsapp,/currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(whatsapp,/context\.packageManager\.getLaunchIntentForPackage\("com.whatsapp"\)/);
  assert.match(whatsapp,/appendQueryParameter\("text", message\)/);
  assert.match(whatsapp,/intent\.resolveActivity\(context\.packageManager\)/);
  assert.match(whatsapp,/result\.putBoolean\("messageSent", false\)/);
  assert.doesNotMatch(whatsapp,/AccessibilityService|ACTION_CLICK|dispatchGesture|sendTextMessage/);
  assert.match(ui,/Open reviewed WhatsApp draft/);
  assert.match(ui,/Message was NOT sent by Nexa/);
  assert.match(ui,/Select one contact/);
  assert.match(file('apps/mobile/src/native/androidWhatsAppDraft.ts'),/contact\?\.source !== 'android-contact-picker'/);
});
test('offline voice tools and generic agent tools do not silently acquire automation',()=>{
  assert.doesNotMatch(file(root+'java/dev/rilandev/personalaiassistant/SarvamAudioTransport.kt'),
    /NexaLauncherModule|NexaWhatsAppComposeModule/);
  assert.doesNotMatch(file('packages/agent-core/src/direct-provider.ts'),
    /NexaLauncher|openReviewedDraft|device\.send_whatsapp/);
  assert.doesNotMatch(launcher,/MediaRecorder|AudioRecord|RECORD_AUDIO/);
});
