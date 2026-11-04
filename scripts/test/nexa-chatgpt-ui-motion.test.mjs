import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(path, 'utf8');
const app = read('apps/mobile/App.tsx');
const motion = read('apps/mobile/src/components/NexaMotion.tsx');
const overlay = read('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt');
const theme = read('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaOverlayTheme.kt');
const capture = read('scripts/ci/capture-nexa-ui.sh');
const emulatorRunner = read('scripts/ci/run-nexa-emulator-acceptance.sh');
const workflow = read('.github/workflows/android-main-promotion.yml');

test('chat uses response-first text, no repeated shortcut rail in every conversation', () => {
  assert.match(app, /NexaReveal/);
  assert.match(app, /messages\.length === 0 && <NexaReveal>/);
  assert.match(app, /messageAssistant:\{backgroundColor:'transparent'/);
  assert.match(app, /messageText:\{fontFamily:'sans-serif',color:'#E9EDF7',fontSize:15,lineHeight:24/);
  assert.match(app, /composerRow:\{[^\n]*borderRadius:27/);
  assert.match(app, /composerSend:\{[^\n]*borderRadius:22/);
  assert.doesNotMatch(app, /quickPrompts\.slice\(0,6\)\.map/);
  assert.match(app, /pendingApproval && <View/);
});

test('Nexa motion gives distinct voice states and honors Reduce motion', () => {
  assert.match(app, /<NexaVoiceOrb state=\{voiceVisualState\}/);
  assert.match(motion, /AccessibilityInfo\.isReduceMotionEnabled/);
  assert.match(motion, /reduceMotionChanged/);
  assert.match(motion, /Animated\.loop/);
  for(const state of ['idle','connecting','listening','speaking','muted','error'])
    assert.match(motion,new RegExp(state));
  assert.match(motion, /Android's mic indicator/);
  assert.doesNotMatch(motion, /AudioRecord|MediaRecorder|requestPermissions/);
});

test('Android bubble animates only presentation and respects system disabled animations', () => {
  assert.match(overlay, /lastRenderedExpanded != expanded/);
  assert.match(overlay, /ValueAnimator\.areAnimatorsEnabled\(\)/);
  assert.match(overlay, /if \(view === fittedLayout && fittedLayout\.isAttachedToWindow\)/);
  assert.match(overlay, /setDuration\(175L\)/);
  assert.match(overlay, /How can I help?/);
  assert.match(theme, /RippleDrawable/);
  assert.match(overlay, /TYPE_APPLICATION_OVERLAY/);
  assert.match(overlay, /Settings\.canDrawOverlays/);
});

test('release CI takes actual on-device screenshots of five tabs without granting permissions', () => {
  assert.match(capture, /adb install "\$release_apk"/);
  assert.match(capture, /sha256sum "\$release_apk"/);
  assert.match(workflow, /Pin release version and source for emulator and distributable APK/);
  assert.match(workflow, /:app:assembleRelease --no-daemon --stacktrace/);
  assert.match(capture, /adb exec-out screencap -p/);
  assert.match(capture, /adb shell uiautomator dump/);
  assert.match(capture, /for item in chat voice tools setup settings/);
  assert.match(capture, /adb shell pm clear/);
  assert.match(capture, /wait_for_nav_ui/);
  assert.match(capture, /pixel launcher isn't responding/);
  assert.match(capture, /text.*lower\(\)=="wait"/);
  assert.match(capture, /return 1/);
  assert.doesNotMatch(capture, /pm grant|appops set|input keyevent 223|MEDIA_PROJECTION/);
  assert.match(workflow, /bash scripts\/ci\/run-nexa-emulator-acceptance\.sh/);
  assert.match(emulatorRunner, /bash scripts\/ci\/capture-nexa-ui\.sh/);
  assert.match(emulatorRunner, /:app:connectedDebugAndroidTest/);
  assert.match(workflow, /name: nexa-ui-screenshots/);
  assert.match(workflow, /if: always\(\)/);
});
