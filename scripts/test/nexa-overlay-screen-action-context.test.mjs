import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const source=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/DeviceControlModule.kt",
  "utf8"
).replace(/\r\n/g,"\n");
const app=readFileSync("apps/mobile/App.tsx","utf8").replace(/\r\n/g,"\n");
const overlayBridge=readFileSync("apps/mobile/src/native/androidToolOverlay.ts","utf8").replace(/\r\n/g,"\n");
const relay=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaOverlayChatRelay.kt",
  "utf8"
).replace(/\r\n/g,"\n");
const overlayService=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayService.kt",
  "utf8"
).replace(/\r\n/g,"\n");
const overlayModule=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaToolOverlayModule.kt",
  "utf8"
).replace(/\r\n/g,"\n");

test("reviewed overlay screen actions do not require Nexa Activity window focus",()=>{
  const start=source.indexOf("private fun sequencedScreenService(");
  const end=source.indexOf("\n    @ReactMethod",start);
  assert.ok(start>=0 && end>start,"native screen-action gate exists");
  const gate=source.slice(start,end);
  assert.doesNotMatch(gate,/currentActivity\?\.hasWindowFocus\(\)/,
    "the floating overlay must be able to act while the external app owns focus");
  assert.match(source,/NexaAgentActionAudit\.reserveReviewedUiStep\(context,/,
    "the exact consumed one-shot owner approval remains mandatory before dispatch");
});

test("floating Chat reviews and resumes one exact screen action in place",()=>{
  assert.match(app,/function overlayChatApprovalSummary\(/,
    "overlay approval details must be narrowly formatted from the exact planner tool");
  assert.match(app,/if\(toolId==="screen\.tap_label"\)/,
    "the overlay may format only safe named-control taps in addition to reviewed scrolls");
  assert.match(app,/screen\.tap_label label=\$\{label\}/,
    "the owner sees the exact named control before approving a tap");
  for(const toolId of ["device.open_app","screen.open_whatsapp_chat","screen.fill_text",
    "screen.compose_whatsapp","screen.send_whatsapp","screen.compose_message",
    "screen.send_message"])
    assert.match(app,new RegExp('"'+toolId.replaceAll('.','\\.')+'"'),
      `floating Chat can stage the exact ${toolId} request for owner review`);
  assert.match(app,/JSON\.stringify\(\{toolId,input:args\}\)/,
    "message reviews show the exact app, recipient, and message arguments");
  assert.match(app,/isSafeSharedOverlayActionSummary\(summary\)/,
    "the exact JSON arguments pass the same constrained overlay validator");
  assert.match(app,/label\.length<2\s*\|\|\s*label\.length>45/,
    "tap labels outside the bounded action contract are not approved in place");
  assert.match(app,/send\|submit\|pay\|purchase\|buy\|delete\|remove\|call\|dial\|transfer\|confirm\|allow\|grant\|install\|uninstall\|post\|share\|password\|passcode\|pin\|otp\|logout\|sign out\|payment\|bank\|security\|verify/i,
    "high-impact labels remain outside the overlay one-tap review path");
  assert.match(app,/requestSharedOverlayChatApproval\(\s*stagedReview\.token,\s*stagedReview\.summary\)/,
    "the shared task must hold its reply token while the owner reviews the action");
  assert.match(app,/NEXA_SHARED_OVERLAY_CHAT_APPROVAL_EVENT/,
    "the one-time overlay choice must return to the shared React task engine");
  assert.match(app,/androidActionJournal\.approveAction\(/,
    "approval must still be stored against the exact durable fingerprint");
  assert.match(app,/send\(saved\.request,\s*saved,\s*id\)/,
    "continue must resume the existing task without foregrounding Nexa over Home");
  assert.match(overlayBridge,/requestSharedOverlayChatApproval\(/,
    "the JS bridge may request a review but cannot execute a device action");
  assert.match(overlayModule,/fun requestChatApproval\(/,
    "native bridge must bind the review to the outstanding overlay token");
  assert.match(relay,/fun requestApproval\(/);
  assert.match(relay,/"screen\.send_message"[\s\S]*?safeRecipient\(input\.opt\("recipientHint"\)\)[\s\S]*?exactText\(input\.opt\("exactText"\)\)/,
    "native overlay validation binds the exact recipient and body before a send review can appear");
  assert.match(relay,/fun decideApproval\(/);
  assert.match(relay,/decisionSubmitted/,
    "the native overlay must accept at most one approval decision per review");
  assert.match(relay,/NexaOverlayChatApproval/,
    "native controls must relay an approval choice to React, not dispatch a swipe");
  assert.doesNotMatch(relay,/NexaLauncherSwipe|NexaScreenScroll|performGlobalAction/,
    "the relay never executes Android actions");
  assert.match(overlayService,/button\("Approve once"\)\s*\{\s*decideSharedChatApproval\(approvalToken,\s*true\)/s,
    "the floating overlay presents a clearly labeled one-time action approval");
  assert.match(overlayService,/button\("Decline"\)\s*\{\s*decideSharedChatApproval\(approvalToken,\s*false\)/s,
    "the owner can decline without opening the main Chat activity");
});

test("floating Chat dispatches overlay requests through the latest Chat callback",()=>{
  const listenerStart=app.indexOf("DeviceEventEmitter.addListener(NEXA_SHARED_OVERLAY_CHAT_EVENT");
  const listenerEnd=app.indexOf("return ()=>sub.remove();",listenerStart);
  assert.ok(listenerStart>=0&&listenerEnd>listenerStart,"shared Chat listener exists");
  const listener=app.slice(listenerStart,listenerEnd);
  assert.match(app,/const sharedOverlaySendRef\s*=\s*useRef\(send\)/,
    "a stable ref holds the current Chat callback");
  assert.match(app,/sharedOverlaySendRef\.current\s*=\s*send;/,
    "the ref refreshes after ACA/provider Chat state changes");
  assert.match(listener,/sharedOverlaySendRef\.current\(request,\s*undefined,\s*id\)/,
    "the long-lived event listener reads the current callback when a request arrives");
  assert.doesNotMatch(listener,/\bsend\(request,\s*undefined,\s*id\)/,
    "the listener must not call a render-time closure that captured stale ACA state");
});

test("a follow-up overlay review reopens after returning to the external app",()=>{
  const reviewStart=overlayService.indexOf("fun showSharedChatApproval(");
  const reviewEnd=overlayService.indexOf("\n    private lateinit var wm",reviewStart);
  assert.ok(reviewStart>=0&&reviewEnd>reviewStart,"in-place review entry point exists");
  const review=overlayService.slice(reviewStart,reviewEnd);
  assert.match(review,/service\.expanded\s*=\s*true/,
    "each next action review must reopen the bubble after the previous approval collapsed it");
  assert.match(review,/service\.render\(\)/,
    "the newly visible in-place review is rendered over the still-foreground external app");
});

test("overlay chat timeout renews for owner review and bounded action completion",()=>{
  assert.match(relay,/private enum class Phase \{ PLANNING, REVIEW, EXECUTING \}/,
    "the shared request tracks its current bounded handoff phase");
  assert.match(relay,/private const val RESPONSE_TTL_MS=60_000L/,
    "initial model planning remains bounded");
  assert.match(relay,/private const val REVIEW_TTL_MS=120_000L/,
    "owner review receives its own bounded window");
  assert.match(relay,/private const val EXECUTION_TTL_MS=60_000L/,
    "an approved action result remains bounded");
  assert.match(relay,/deadlineAt/,
    "pending validity is measured from the current phase deadline");
  assert.match(relay,/scheduleTimeout\(token,generation,RESPONSE_TTL_MS\)/,
    "the initial timeout is tied to its generation");
  assert.match(relay,/request\.phase=Phase\.REVIEW/,
    "showing owner review advances the request phase");
  assert.match(relay,/request\.phase=Phase\.EXECUTING/,
    "an owner decision advances the request phase");
  assert.match(relay,/scheduleTimeout\(token,generation,REVIEW_TTL_MS\)/,
    "review replaces the planning timeout");
  assert.match(relay,/scheduleTimeout\(token,generation,EXECUTION_TTL_MS\)/,
    "execution replaces the review timeout");
  assert.doesNotMatch(relay,/elapsedRealtime\(\)-request\.startedAt/,
    "later phases must not expire against the initial request timestamp");
});
