import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const native='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=(name)=>readFileSync(path.join(root,native,name),'utf8');

test('floating chat routes relative requests before broad tap labels',()=>{
  const overlay=read('NexaToolOverlayService.kt');
  const start=overlay.indexOf('private fun sendLocal(raw: String)');
  const relative=overlay.indexOf('NexaMaestroRelativeTap.parse(query)',start);
  const plain=overlay.indexOf('NexaPhoneVoiceTools.tapVisibleLabelIntent(query)',start);
  assert.ok(start>=0&&relative>start&&plain>relative,
    'relative selection must not fall through into unscoped label lookup');
  assert.match(overlay,/proposeOwnerDeviceAction\(\s*"tap_relative:\$\{relative.direction\}/);
});
test('relative action uses the same approval expiry and native action journal',()=>{
  const overlay=read('NexaToolOverlayService.kt');
  const tap=read('NexaScreenLabelTap.kt');
  assert.match(overlay,/NexaMaestroRelativeTap.fromAction\(action\)/);
  assert.match(overlay,/NexaScreenLabelTap.approvedRelative\(/);
  assert.match(tap,/NexaMaestroTargetSelection.choose\([\s\S]*?query,grounded,contextAnchors/);
  assert.match(tap,/NexaUiActionJournal.claim\(/);
  assert.match(tap,/NexaNativeActionSafety.dispatchWithFence\(/);
  assert.doesNotMatch(tap,/performGlobalAction\(/);
});
test('the live tap path refuses ambiguous relative anchor and unsafe editable nodes',()=>{
  const tap=read('NexaScreenLabelTap.kt');
  assert.match(tap,/relative!=null && anchors.size!=1/);
  assert.match(tap,/if\(relative!=null \|\| candidates.size!=1/);
  assert.match(tap,/NexaMaestroRelativeTap.fromAction\(relative.toAction\(\)\)/);
  assert.match(tap,/currentBounds!=reviewedBounds/);
});

test('relative anchor is freshly revalidated inside the owner Pause fence',()=>{
  const tap=read('NexaScreenLabelTap.kt');
  assert.match(tap,/private fun anchorStillUnique\(/);
  assert.match(tap,/anchorStillUnique\(liveRoot,relative.anchor,reviewedAnchorBounds\)/);
  assert.match(tap,/NexaNativeActionSafety.dispatchWithFence\([\s\S]*?anchorStillUnique/);
});

test('invalid contextual command fails closed before generic label parsing',()=>{
  const overlay=read('NexaToolOverlayService.kt');
  const start=overlay.indexOf('private fun sendLocal(raw: String)');
  const failed=overlay.indexOf('NexaMaestroRelativeTap.looksRelative(query)',start);
  const generic=overlay.indexOf('NexaPhoneVoiceTools.tapVisibleLabelIntent(query)',start);
  assert.ok(start>=0&&failed>start&&generic>failed);
  assert.match(overlay,/Relative target\/anchor is invalid, sensitive or ambiguous/);
});

test('contextual tapping must not add fuzzy WhatsApp contact selection',()=>{
  const tap=read('NexaScreenLabelTap.kt');
  assert.match(tap,/relative==null && pkg=="com\.whatsapp" &&[\s\S]*?NexaWhatsAppChatMatch\.evidence/);
});
