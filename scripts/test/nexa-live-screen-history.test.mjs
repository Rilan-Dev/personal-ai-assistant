import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const dir='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=(name)=>readFileSync(dir+name,'utf8');
const voice=read('RealtimeVoiceService.kt');
const sarvam=read('SarvamApiVoiceTransport.kt');
const policy=read('NexaLiveToolPolicy.kt');
const commands=read('NexaPhoneVoiceTools.kt');
const screen=read('NexaScreenVoiceOverview.kt');
const actions=read('NexaScreenLabelTap.kt');
const db=read('NexaConversationDb.kt');
const app=readFileSync('apps/mobile/App.tsx','utf8');
const manifest=readFileSync('apps/mobile/android/app/src/main/AndroidManifest.xml','utf8');

test('voice exposes and approves real screen inspection, gestures, app discovery and semantic tap',()=>{
  for (const tool of ['request_read_visible_screen','request_scroll_screen',
      'request_tap_safe_icon','request_tap_visible_label',
      'request_list_installed_apps','request_open_installed_app']) {
    assert.match(policy,new RegExp(tool));
  }
  assert.match(voice,/NexaScreenVoiceOverview\.readVisibleTextApproved/);
  assert.match(voice,/NexaScreenVoiceOverview\.scrollApproved/);
  assert.match(voice,/NexaScreenLabelTap\.approved/);
  assert.match(voice,/NexaPhoneVoiceTools\.installedAppsApproved/);
  assert.match(voice,/assistantPanelEnabled && pending != null/);
  assert.match(voice,/pendingApprovalSummary="Scroll/);
  assert.match(actions,/NexaUiActionJournal\.claim/);
  assert.match(actions,/targets\.size!=1/);
  assert.match(actions,/targets\[key\]=candidate/);
  assert.match(screen,/setOf\("up","down","left","right"\)/);
});
test('Sarvam routes user speech through the owner gate instead of disabling screen help',()=>{
  assert.match(sarvam,/callbacks\.ownerRequest\(restored\)/);
  assert.match(voice,/NexaPhoneVoiceTools\.wantsVisibleText\(text\)/);
  assert.match(voice,/NexaPhoneVoiceTools\.tapVisibleLabelIntent\(text\)/);
  assert.match(voice,/Screen Assist is disabled/);
  assert.match(commands,/fun wantsInstalledApps/);
  assert.match(commands,/fun wantsScreenHelp/);
  assert.doesNotMatch(sarvam,/Do not request contacts, private files, secrets or screen content/);
});
test('private custom.db is encrypted, exported only by owner and not sent to Postgres',()=>{
  assert.match(db,/"custom\.db"/);
  assert.match(db,/AndroidKeyStore/);
  assert.match(db,/AES\/GCM\/NoPadding/);
  assert.match(db,/conversation_entries/);
  assert.match(db,/fun shareAllHistory/);
  assert.match(db,/hasWindowFocus\(\)/);
  assert.match(db,/Intent\.ACTION_SEND/);
  assert.match(manifest,/conversationprovider/);
  assert.match(app,/View recent history/);
  assert.match(app,/Copy JSON/);
  assert.match(app,/Export conversations as JSON/);
  assert.match(sarvam,/NexaConversationDb\.append\(context,sessionId,"voice"/);
  assert.match(voice,/NexaConversationDb\.append\(this@RealtimeVoiceService/);
  assert.doesNotMatch(db,/postgresql|prisma|https?:\/\//i);
});
