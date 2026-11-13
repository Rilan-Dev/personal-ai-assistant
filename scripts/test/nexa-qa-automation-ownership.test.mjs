import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';

const source=readFileSync('apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaQaAccessibilityRoot.kt','utf8');
test('test-only UI automation keeps owner accessibility services running',()=>{
  assert.match(source,/UiAutomation\.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES/);
  assert.match(source,/instrument\.getUiAutomation\(/);
  assert.doesNotMatch(source,/instrument\.uiAutomation/);
  assert.doesNotMatch(source,/settings\s+put\s+secure|pm\s+clear|svc\s+accessibility/i);
});
test('fixture interaction uses a single guarded automation accessor',()=>{
  assert.match(source,/private fun qaAutomation\(/);
  assert.match(source,/val automation=qaAutomation\(\)/);
  assert.match(source,/qaAutomation\(\)\.executeShellCommand/);
});
