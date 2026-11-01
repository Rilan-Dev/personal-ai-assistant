import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const dir='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const transport=readFileSync(dir+'SarvamAudioTransport.kt','utf8');
const realtime=readFileSync(dir+'RealtimeVoiceService.kt','utf8');
const gate=readFileSync(dir+'NexaVoiceIntentGate.kt','utf8');
const wake=readFileSync(dir+'NexaWakeFallbackOverlay.kt','utf8');
const assistant=readFileSync(dir+'NexaAssistantService.kt','utf8');
test('Sarvam official SDK transcript event is used without trusting bot speech',()=>{
  assert.match(transport, /server\.event\.transcription/);
  assert.match(transport,/role=="user" && content\.length in 1\.\.250/);
  assert.match(transport,/callbacks\.transcript\(role,content\)/);
  assert.match(realtime,/override fun transcript\(role: String, content: String\)/);
  assert.match(realtime,/selectedProvider=="sarvam" && role=="user"/);
  assert.doesNotMatch(transport,/client\.action\.tool_result|client\.event\.tool_result/);
});
test('user transcriptions are validated and require a visible owner approval before launch',()=>{
  assert.match(gate,/appRequest\(raw: String\)/);
  assert.match(gate,/whatsapp business\|whatsapp\|github\|brave/);
  assert.match(gate,/and\|then\|or\|also\|after\|before\|send\|text\|message/);
  assert.match(realtime,/NexaVoiceIntentGate\.appRequest\(transcript\)/);
  assert.match(realtime,/!assistantPanelEnabled \|\| pendingAppTool != null/);
  assert.match(realtime,/PendingAppTool\("local-sarvam"/);
  assert.match(realtime,/pendingApprovalSummary = "Open/);
  assert.match(realtime,/if \(active\.get\(\) && assistantPanelEnabled && pending != null\)/);
  assert.match(realtime,/if \(selectedProvider == "openai"\) \{/);
  assert.match(assistant,/Approve app/);
  assert.match(wake,/Approve app/);
});
test('Hey Nexa is wake-only and cannot silently revert to local speech commands',()=>{
  assert.match(assistant,/if \(automaticLiveVoice\) startOwnerVoice\(\)/);
  assert.match(assistant,/else feedback\("Enable live AI after assistant invocation/);
  assert.match(wake,/!providerReady\) return false/);
  assert.doesNotMatch(wake,/switching to offline commands/);
  assert.match(wake,/Live AI unavailable|Live conversation ended|Voice could not start/);
});

test('live startup does not contend with wake mic and transport failures stay caught',()=>{
  const wake=readFileSync(dir+'NexaWakeWordService.kt','utf8');
  assert.match(wake,/fun liveMicReleased\(\): Boolean/);
  assert.match(wake,/audioReleasing = true/);
  assert.match(wake,/audioReleasing = false/);
  assert.match(realtime,/if \(!NexaWakeWordService\.liveMicReleased\(\)\)/);
  assert.match(realtime,/main\.postDelayed\(\{ startAudio\(\) \},100L\)/);
  assert.match(realtime,/micReleaseRetries > 20/);
  assert.match(transport,/catch \(_:\s*Exception\) \{ false \}/);
  assert.match(transport,/Sarvam authorization response could not be processed/);
  assert.match(transport,/Sarvam session event could not be processed/);
});
