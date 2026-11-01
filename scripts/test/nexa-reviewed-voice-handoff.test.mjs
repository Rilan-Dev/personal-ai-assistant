import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const base='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=p=>readFileSync(p,'utf8');
const pending=read(base+'NexaToolOverlayModule.kt');
const parser=read(base+'NexaOverlayCommands.kt');
const assistant=read(base+'NexaAssistantService.kt');
const overlay=read(base+'NexaToolOverlayService.kt');
const bridge=read('apps/mobile/src/native/androidToolOverlay.ts');
const app=read('apps/mobile/App.tsx');

test('voice WhatsApp command only creates ephemeral, one-time local review state',()=>{
  assert.match(pending,/internal object NexaVoiceDraftState/);
  assert.match(pending,/fun parse\(raw: String\)/);
  assert.match(pending,/fun queue\(intent: Pair<String, String>/);
  assert.match(pending,/now \+ 60_000L/);
  assert.match(pending,/draft = null/);
  assert.match(pending,/fun take\(now: Long/);
  assert.match(pending,/@ReactMethod fun consumePendingVoiceDraft/);
  assert.match(pending,/result\.putString\("mode", "review_only"\)/);
  assert.doesNotMatch(pending,/putString\("message"/);
  assert.doesNotMatch(pending,/whatsapp\.send|sendTextMessage|ACTION_SEND/);
});
test('spoken message is not normalized or directly dispatched to WhatsApp',()=>{
  assert.match(parser,/NexaVoiceDraftState\.parse\(raw\)/);
  assert.match(parser,/NexaVoiceDraftState\.queue\(review\)/);
  assert.match(parser,/NexaVoiceDraftState\.clear\(\)/);
  assert.match(parser,/WhatsApp review opened in Nexa Tools/);
  assert.match(parser,/Nothing was sent/);
  assert.doesNotMatch(parser,/Uri\.Builder\(\).*wa\.me/);
  assert.match(assistant,/result\.startsWith\("WhatsApp review opened"\)/);
  assert.match(overlay,/result\.startsWith\("WhatsApp review opened"\)/);
});
test('RN receives a single-use draft and requires deliberate contact selection and manual WhatsApp send',()=>{
  assert.match(bridge,/consumePendingVoiceDraft\(\): Promise<ReviewedVoiceDraft \| null>/);
  assert.match(bridge,/mode !== 'review_only'/);
  assert.match(app,/consumeReviewedVoiceDraft\(\)/);
  assert.match(app,/setSelectedContact\(null\)/);
  assert.match(app,/setWhatsAppDraft\(pending\.message\)/);
  assert.match(app,/setTab\('tools'\)/);
  assert.match(app,/Choose the real contact in Android/);
  assert.match(app,/openSelectedWhatsAppDraft/);
  assert.match(read(base+'NexaWhatsAppComposeModule.kt'),/result\.putBoolean\("messageSent", false\)/);
});
