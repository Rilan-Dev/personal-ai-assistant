import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';

const service=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/ScreenAssistService.kt',
  'utf8');
const module=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt',
  'utf8');
const snapshots=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaPowerShellScreenSnapshot.kt',
  'utf8');
const tap=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaScreenLabelTap.kt',
  'utf8');
const scroll=readFileSync(
  'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaScreenScroll.kt',
  'utf8');

test('post-key and initial debug observations can return a safe owner-app snapshot',()=>{
  assert.ok(service.includes('assistantOwnerWindowRoot()'),
    'Screen Assist needs a separately scoped root for its own focused application window');
  assert.ok(/assistantOwnerWindowRoot\(\)[\s\S]*?TYPE_APPLICATION[\s\S]*?window\.isFocused[\s\S]*?window\.isActive[\s\S]*?candidate\.isVisibleToUser/.test(service),
    'owner observation must be limited to its visible, active, focused application window');
  assert.ok(/freshAssistantScreenObservation\(\)[\s\S]*?assistantOwnerWindowRoot\(\)/.test(module),
    'post-action observations need an owner-window fallback after external apps');
  assert.ok(/capturePowerShellScreen\(promise:Promise\)[\s\S]*?assistantOwnerWindowRoot\(\)/.test(module),
    'the debug CLI must be able to start an observation while Nexa itself is foreground');
  assert.ok(/captureOwnerWindow\([\s\S]*?captureSnapshot\(root,\s*includeText\s*=\s*false,\s*allowOwnerApp\s*=\s*true\)/.test(snapshots),
    'owner snapshots must include interaction targets/icons while omitting chat text');
});

test('fresh debug owner snapshots can ground the next tap or scroll without opening a loophole',()=>{
  assert.match(snapshots,/private fun rootForClaim\([\s\S]*?BuildConfig\.DEBUG[\s\S]*?claim\.packageName==BuildConfig\.APPLICATION_ID[\s\S]*?assistantOwnerWindowRoot\(\)/,
    'snapshot validation must reacquire Nexa only for the exact debug owner claim');
  assert.match(snapshots,/fun resolveCurrent\([\s\S]*?rootForClaim\(service,snapshot\.claim\)/,
    'the indexed CLI tap must validate its owner snapshot against the live focused window');
  assert.match(snapshots,/fun isCurrent\([\s\S]*?rootForClaim\(service,snapshot\.claim\)/,
    'scroll must accept only a current owner snapshot before native dispatch');
  assert.match(tap,/val ownerWindow=BuildConfig\.DEBUG &&\s+expectedSnapshot\?\.packageName==BuildConfig\.APPLICATION_ID/,
    'only a snapshot-bound debug tap can target Nexa itself');
  assert.match(tap,/if\(ownerWindow\)[\s\S]{0,80}assistantOwnerWindowRoot\(\)[\s\S]{0,80}else[\s\S]{0,40}externalWindowRoot\(\)/,
    'the tap must reacquire the same scoped window immediately before dispatch');
  assert.match(scroll,/val root=\(if\(BuildConfig\.DEBUG\)service\.assistantOwnerWindowRoot\(\) else null\)\s*\?:\s*service\.externalWindowRoot\(\)/,
    'debug scrolls must prefer Nexa only when its focused owner window is current, then fall back to the external app');
});

test('named-key fallback trusts Screen Assist window identity instead of Activity focus',()=>{
  assert.match(module,/val ownerRoot=try\s*\{\s*service\?\.assistantOwnerWindowRoot\(\)\s*\}/,
    'named navigation needs a current focused owner Accessibility window when no external app is visible');
  assert.match(module,/ownerWindowFocused=ownerWindowId!=null/,
    'Activity.hasWindowFocus must not decide whether the bound owner screen is the fallback surface');
  assert.match(module,/val ownerWindowStillFocused:[\s\S]{0,600}windowId==ownerWindowId/,
    'the final global action must revalidate the same owner Accessibility window');
});

test('notification shade observation recognizes the Android panel root and exposes safe metadata only',()=>{
  assert.match(service,/notification_panel/,
    'Screen Assist must recognize Android notification-panel roots when Android omits the accessibility window title');
  assert.match(service,/findAccessibilityNodeInfosByViewId/,
    'notification-panel recognition must come from a visible System UI node, not package name alone');
  const captureMethod=module.slice(module.indexOf('fun capturePowerShellScreen'),
    module.indexOf('fun capturePowerShellAccessibilityTree'));
  assert.match(captureMethod,/notificationShadeRoot\(\)[\s\S]*?captureAssistantNotificationShade/,
    'the debug observation path must return a bounded shade snapshot when the shade is foreground');
  assert.match(snapshots,/captureAssistantNotificationShade[\s\S]{0,300}systemUiSafeMode\s*=\s*true/,
    'notification observations must retain System UI content and action suppression');
});

test('an open notification panel blocks obscured app roots and wins screen observation priority',()=>{
  const externalRoot=service.slice(service.indexOf('internal fun externalWindowRoot()'),
    service.indexOf('internal fun assistantOwnerWindowRoot()'));
  assert.match(externalRoot,/notificationShadeRoot\(\)/,
    'screen actions must not target an app window hidden behind System UI notifications');
  const captureStart=module.indexOf('fun capturePowerShellScreen');
  const captureEnd=module.indexOf('fun capturePowerShellAccessibilityTree',captureStart);
  const captureMethod=module.slice(captureStart,captureEnd);
  assert.ok(captureMethod.indexOf('notificationShadeRoot()') < captureMethod.indexOf('externalWindowRoot()'),
    'CLI observation must report an expanded shade before considering the covered app');
  const freshStart=module.indexOf('private fun freshAssistantScreenObservation');
  const freshEnd=module.indexOf('private fun finishUiStepAfterObservation',freshStart);
  const freshMethod=module.slice(freshStart,freshEnd);
  assert.ok(freshMethod.indexOf('notificationShadeRoot()') < freshMethod.indexOf('externalWindowRoot()'),
    'post-action observations must report the visible notification shade instead of covered app content');
});

test('the CLI accepts Android notification-shade dispatch as a successful named key action',()=>{
  const keyMethodStart=module.indexOf('fun performReviewedPowerShellKeyAction');
  const keyMethodEnd=module.indexOf('fun performReviewedScreenScroll',keyMethodStart);
  const keyMethod=module.slice(keyMethodStart,keyMethodEnd);
  assert.match(keyMethod,/name=="notifications"[\s\S]{0,240}Android accepted the notification shade request/,
    'notification shade acceptance must not be rejected by the Back/Home result-text guard');
});
