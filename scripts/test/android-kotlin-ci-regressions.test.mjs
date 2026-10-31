import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const source = name=>readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/"+name,
  "utf8"
);

test("Android secure journal revokes voice service through ReactApplicationContext",()=>{
  const text=source("AgentJournalModule.kt");
  const revocations=text.match(/reactApplicationContext\.stopService\(/g)||[];
  assert.equal(revocations.length,4,
    "provider and Sarvam agent updates and deletions must stop live voice");
  assert.doesNotMatch(text,/(?<!\w)context\.stopService\(/,
    "Kotlin primary constructor argument is not an instance property");
  assert.match(text,/android\.content\.Intent\(reactApplicationContext, RealtimeVoiceService::class\.java\)/);
});

test("one-shot Accessibility expiry callback has explicit nonrecursive return type",()=>{
  const text=source("ScreenAssistService.kt");
  assert.match(text,/private val expire = Runnable \{ clear\(\) \}/);
  assert.match(text,/fun clear\(\): Unit = synchronized\(lock\)/,
    "recursively inferred synchronized return type must be explicitly declared");
  assert.match(text,/expiryHandler\.removeCallbacks\(expire\)/);
});
