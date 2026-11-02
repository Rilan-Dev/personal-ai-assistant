import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';

const root='apps/mobile/android/app/src/';
const smoke=readFileSync(root+'androidTest/java/dev/rilandev/personalaiassistant/NexaLiveVoiceStartupAndroidTest.kt','utf8');
const host=readFileSync(root+'debug/java/dev/rilandev/personalaiassistant/NexaVoiceTestHostActivity.kt','utf8');
const debugManifest=readFileSync(root+'debug/AndroidManifest.xml','utf8');
const releaseManifest=root+'release/AndroidManifest.xml';
const mainManifest=readFileSync(root+'main/AndroidManifest.xml','utf8');

test('live voice instrumentation uses a foreground native host, never ReactActivity without Metro',()=>{
  assert.match(smoke,/ActivityScenario\.launch\(NexaVoiceTestHostActivity::class\.java\)/);
  assert.doesNotMatch(smoke,/ActivityScenario\.launch\(MainActivity::class\.java\)/);
  assert.match(host,/class NexaVoiceTestHostActivity : Activity\(\)/);
  assert.doesNotMatch(host,/import com\.facebook\.react|: ReactActivity\b|ReactHostImpl/);
});

test('emulator-only activity is not shipped as a production application surface',()=>{
  assert.match(debugManifest,/<activity android:name="\.NexaVoiceTestHostActivity"/);
  assert.match(debugManifest,/android:exported="false"/);
  assert.doesNotMatch(mainManifest,/NexaVoiceTestHostActivity/);
  assert.equal(existsSync(root+'main/java/dev/rilandev/personalaiassistant/NexaVoiceTestHostActivity.kt'),false);
  if (existsSync(releaseManifest)) {
    assert.doesNotMatch(readFileSync(releaseManifest,'utf8'),/NexaVoiceTestHostActivity/);
  }
});
