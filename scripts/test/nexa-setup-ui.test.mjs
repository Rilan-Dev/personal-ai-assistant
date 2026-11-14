import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=path=>readFileSync(path,'utf8');
const app=read('apps/mobile/App.tsx');
const setup=read('apps/mobile/src/components/NexaSetupScreen.tsx');
const native=read('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaSetupModule.kt');
const gradle=read('apps/mobile/android/app/build.gradle');
const workflow=read('.github/workflows/android-main-promotion.yml');
const releaseWorkflow=read('.github/workflows/android-release-test.yml');

test('old Personal AI header replaced with Nexa compact permission center',()=>{
  assert.match(app,/tab === 'chat' \? 'Nexa AI'/);
  assert.match(app,/accessibilityLabel=\"Open permission setup\"/);
  assert.match(app,/tab === 'setup' && <NexaSetupScreen/);
  assert.match(app,/setupStatus\?\.sourceRevision\?\.slice\(0,10\)/);
  assert.doesNotMatch(app,/Personal AI Assistant · Android prototype/);
});
test('first run is opt-in and status refreshes after Android settings',()=>{
  assert.match(app,/engine\.live\?'setup':'chat'/);
  assert.match(app,/if \(status\.setupAcknowledged\) setTab/);
  assert.match(app,/AppState\.addEventListener\('change',state/);
  assert.match(app,/await acknowledgeNexaSetup\(\)/);
  assert.match(setup,/Continue to Nexa/);
  assert.match(setup,/Skip for now/);
  assert.match(setup,/stickyFooter/);
  assert.match(setup,/Explore 6 optional features/);
  assert.match(setup,/Advanced voice & wake settings/);
  assert.match(setup,/Android app settings/);
});
test('setup exposes each real Android permission independently and cannot grant system roles silently',()=>{
  for(const term of ['microphone','notifications','camera','approximateLocation',
    'screenAssist','launcherNavigation','assistantSelected','sourceRevision','versionCode'])
    assert.match(native,new RegExp(term));
  assert.match(native,/checkSelfPermission/);
  assert.match(native,/Settings\.Secure\.ENABLED_ACCESSIBILITY_SERVICES/);
  assert.match(native,/NexaAccessibilityServiceStatus\.isEnabled/);
  assert.match(native,/ScreenAssistState\.connected && ScreenAssistService\.active != null/);
  assert.match(native,/currentActivity\?\.hasWindowFocus\(\) != true/);
  assert.match(native,/getSharedPreferences\("nexa_setup"/);
  assert.doesNotMatch(native,/requestPermissions\(|dispatchGesture\(|startForegroundService\(/);
  assert.match(setup,/Choose Nexa assistant/);
  assert.match(setup,/All grants remain under your control/);
  assert.match(setup,/One-time screen assistance/);
  assert.match(setup,/Home-screen app search/);
});
test('APK embeds its real commit and uses comparable Android version codes',()=>{
  assert.match(gradle,/PAA_SOURCE_REVISION/);
  assert.match(gradle,/PAA_BUILD_VERSION_CODE/);
  for(const ci of [workflow,releaseWorkflow]){
    if(ci===workflow){
      assert.match(ci,/echo "PAA_BUILD_SOURCE_REVISION=\$GITHUB_SHA" >> "\$GITHUB_ENV"/);
      assert.match(ci,/Pin release version and source for emulator and distributable APK/);
    } else {
      assert.match(ci,/PAA_BUILD_SOURCE_REVISION: \$\{\{ github\.sha \}\}/);
    }
    assert.match(ci,/release-version-code\.sh/);
    assert.match(ci,/release-test-signing-certificate\.txt/);
    assert.match(ci,/Load permanent Nexa release signer/);
    assert.doesNotMatch(ci,/Temporary test-only release signer|PAA Temporary Release Test/);
  }
});

test('Expo identity and Android screen permission name use the new Nexa brand',()=>{
  const config=JSON.parse(read('apps/mobile/app.json'));
  assert.equal(config.expo.name,'Nexa AI');
  assert.equal(config.expo.web.name,'Nexa AI');
  assert.equal(config.expo.android.package,'dev.rilandev.personalaiassistant');
  const manifest=read('apps/mobile/android/app/src/main/AndroidManifest.xml');
  assert.match(manifest,/android:label="\$\{screenAssistServiceLabel\}"/);
  assert.match(gradle,/screenAssistServiceLabel:[\s\S]*?'Nexa Screen Assist/);
  assert.doesNotMatch(manifest,/Personal AI Screen Assist/);
});
