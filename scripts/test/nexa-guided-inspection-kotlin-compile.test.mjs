import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const file=readFileSync('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt','utf8');

test('guided icon inspection destructures nullable Pair once before smart casting',()=>{
  const section=file.slice(file.indexOf('private fun inspectVisibleIcons()'),
    file.indexOf('private fun selectVisibleIcon('));
  assert.match(section,/val \(snapshot, description\) = NexaScreenActions\.inspect\(service\)/);
  assert.match(section,/snapshot != null/);
  assert.match(section,/guidedNavigation\.bindPackage\(snapshot\.packageName,now\)/);
  assert.doesNotMatch(section,/result\.first\.packageName/);
});
