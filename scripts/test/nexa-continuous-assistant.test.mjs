import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const dir='apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const read=name=>readFileSync(dir+name,'utf8');
const session=read('NexaAssistantService.kt');
const realtime=read('RealtimeVoiceService.kt');
const policy=read('NexaLiveToolPolicy.kt');
const gate=read('NexaVoiceIdleGate.kt');

test('manual offline command controls remain bounded; automatic wake uses live AI',()=>{
  assert.match(session,/beginLocalConversation\(\)/);
  assert.match(session,/localConversationActive/);
  assert.match(session,/silenceWindowMs = 18_000L/);
  assert.match(session,/handler\.postDelayed\(silenceTimer, silenceWindowMs\)/);
  assert.match(session,/localTurnCount >= 16/);
  assert.match(session,/outputSpeech\?\.shutdown\(\)/);
  assert.match(session,/replyAndListen\(result\)/);
  assert.match(session,/TextToSpeech\.QUEUE_FLUSH/);
  assert.match(session,/SpeechRecognizer\.createOnDeviceSpeechRecognizer\(app\)/);
  assert.match(session,/if \(automaticLiveVoice\) startOwnerVoice\(\)/);
  assert.match(session,/else feedback\("Enable live AI after assistant invocation/);
  assert.match(session,/NexaWakeWordService\.assistantClosed\(\)/);
  assert.match(session,/stopConversation\(\)/);
});

test('voice plate is compact and has visible direct control of pending tool approval',()=>{
  assert.match(session,/setGravity\(Gravity\.BOTTOM\)/);
  assert.match(session,/WindowManager\.LayoutParams\.WRAP_CONTENT/);
  assert.match(session,/FLAG_NOT_TOUCH_MODAL/);
  assert.doesNotMatch(session,/Assistant gesture starts one offline device command by default/);
  assert.match(session,/Approve once/);
  assert.match(session,/Deny/);
  assert.match(session,/RealtimeVoiceService\.pendingApprovalSummary/);
});

test('Realtime tool loop responds to function_call_output and never invokes arbitrary tools',()=>{
  assert.match(realtime,/NexaLiveToolPolicy\.definitions\(\)/);
  assert.match(realtime,/response\.done/);
  assert.match(realtime,/function_call/);
  assert.match(realtime,/function_call_output/);
  assert.match(realtime,/conversation\.item\.create/);
  assert.match(realtime,/response\.create/);
  assert.match(realtime,/seenCallIds/);
  assert.match(realtime,/toolCallsInSession > 12/);
  assert.match(realtime,/pendingAppTool != null/);
  assert.match(realtime,/ACTION_APPROVE_TOOL/);
  assert.match(realtime,/ACTION_DENY_TOOL/);
  assert.match(realtime,/NexaOverlayCommands\.handle\(this, pending\.command\)/);
  assert.match(policy,/get_device_status/);
  assert.match(policy,/get_battery_level/);
  assert.match(policy,/request_open_app/);
  assert.match(policy,/NeedsApproval/);
  assert.match(policy,/setOf\("brave", "github", "whatsapp"\)/);
  assert.doesNotMatch(policy,/SmsManager|Runtime\.getRuntime|dispatchGesture|ACTION_SET_TEXT/);
  assert.doesNotMatch(realtime,/SmsManager|Runtime\.getRuntime|dispatchGesture|ACTION_SET_TEXT/);
});

test('live voice supports idle exit while retaining microphone disclosure',()=>{
  assert.match(realtime,/idleGate\.observePcm/);
  assert.match(realtime,/Conversation idle/);
  assert.match(realtime,/main\.removeCallbacks\(idleCheck\)/);
  assert.match(realtime,/stopWithStatus\("stopped"/);
  assert.match(gate,/lastSpeechMs/);
  assert.match(gate,/activeFrames >= 4/);
  assert.match(realtime,/FOREGROUND_SERVICE_TYPE_MICROPHONE/);
  assert.match(realtime,/makeNotification\("Microphone active/);
});

test('Realtime tool calls require a visible assistant approval surface',()=>{
  assert.match(session,/putExtra\(RealtimeVoiceService\.EXTRA_ASSISTANT_PANEL, true\)/);
  assert.match(realtime,/assistantPanelEnabled = intent\.getBooleanExtra\(EXTRA_ASSISTANT_PANEL, false\)/);
  assert.match(realtime,/if \(assistantPanelEnabled\)/);
  assert.match(realtime,/tool_choice/);
  assert.match(realtime,/assistantPanelEnabled && pending != null/);
  assert.match(realtime,/assistantPanelEnabled\) handleRealtimeToolCalls\(json\)/);
});
