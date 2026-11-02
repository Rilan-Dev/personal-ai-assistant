import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const assistant=readFileSync(root+'NexaAssistantService.kt','utf8');
const fallback=readFileSync(root+'NexaWakeFallbackOverlay.kt','utf8');
const voice=readFileSync(root+'RealtimeVoiceService.kt','utf8');
const tools=readFileSync(root+'NexaPhoneVoiceTools.kt','utf8');
const manifest=readFileSync('apps/mobile/android/app/src/main/AndroidManifest.xml','utf8');

test('explicit offline Hey Nexa wake can start consented AI without assistant gesture auto-toggle',()=>{
  assert.match(assistant,/args\?\.getBoolean\("nexa_hotword"\) == true/);
  assert.match(assistant,/val automaticLiveVoice = hasCredentials && \(cameFromWake &&/);
  assert.match(assistant,/NexaWakeWordService\.enabled\(app\) \|\| saved/);
  assert.match(assistant,/if \(automaticLiveVoice\) startOwnerVoice\(\)/);
  assert.match(fallback,/val providerReady = try \{/);
  assert.match(fallback,/NexaWakeWordService\.enabled\(service\)/);
  assert.match(fallback,/if \(providerReady && Build\.VERSION\.SDK_INT >= 26\)/);
  assert.match(fallback,/handler\.removeCallbacks\(maximum\)/);
  assert.doesNotMatch(fallback,/val optedIn = service\.getSharedPreferences/);
  assert.match(assistant,/AgentJournalModule\.openVoiceCredentials\(app\)/);
});
test('pending one-time app approval is shown in native assistant overlay above shortcuts',()=>{
  const heading=assistant.indexOf('approvalBanner = NexaOverlayTheme.label');
  const toolsRow=assistant.indexOf('button("Battery")');
  assert.ok(heading>0 && heading<toolsRow,'approval must precede other controls');
  assert.match(assistant,/APPROVAL REQUIRED/);
  assert.match(assistant,/button\("Approve once"\)/);
  assert.match(assistant,/button\("Deny"\)/);
  assert.match(assistant,/approvalBanner\?\.visibility = if \(pending != null\) View\.VISIBLE else View\.GONE/);
  assert.match(voice,/age !in 0\.\.60_000L/);
  assert.match(voice,/Approved|ACTION_APPROVE_TOOL/);
});
test('phone tool additions are narrowly scoped and keep arbitrary app launches review-only',()=>{
  assert.match(tools,/StatFs\(Environment\.getDataDirectory\(\)\.path\)/);
  assert.match(tools,/BatteryManager\.BATTERY_PROPERTY_CAPACITY/);
  assert.match(tools,/Intent\.ACTION_MAIN/);
  assert.match(tools,/Intent\.CATEGORY_LAUNCHER/);
  assert.match(tools,/Decision\.NeedsApproval/);
  assert.match(voice,/NexaPhoneVoiceTools\.findLaunchableApp/);
  assert.match(voice,/NexaPhoneVoiceTools\.launchApproved\(this,pending\.installedApp\)/);
  assert.match(manifest,/android\.intent\.category\.LAUNCHER/);
  assert.doesNotMatch(manifest,/QUERY_ALL_PACKAGES/);
  assert.doesNotMatch(tools,/dispatchGesture|ACTION_CALL|AccessibilityService|READ_CONTACTS/);
});
