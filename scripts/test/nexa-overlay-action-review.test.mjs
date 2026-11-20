import assert from "node:assert/strict";
import test from "node:test";
import {isSafeSharedOverlayActionSummary} from "../../apps/mobile/src/native/overlayActionReview.ts";

test("shared overlay review accepts one safe named tap and one directional scroll",()=>{
  assert.equal(isSafeSharedOverlayActionSummary("screen.tap_label label=Alarms"),true);
  assert.equal(isSafeSharedOverlayActionSummary("screen.tap_label label=1"),true,
    "one-digit calculator buttons are actionable visible labels");
  assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify({
    toolId:"screen.tap_label",input:{label:"1"}
  })),true);
  assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify({
    toolId:"screen.fill_text",input:{targetLabel:"1",exactText:"safe test"}
  })),false,"digits can be tapped but are not accepted as writable field labels");
  assert.equal(isSafeSharedOverlayActionSummary("screen.tap_label label=12"),false,
    "multi-digit strings remain excluded to avoid exposing numeric data");
  assert.equal(isSafeSharedOverlayActionSummary("screen.scroll direction=up"),true);
});

test("shared overlay review binds an observed tap to its exact fresh snapshot and safe label",()=>{
  const approved={toolId:"screen.tap_observed",input:{
    snapshotId:"550e8400-e29b-41d4-a716-446655440000",index:0,label:"Open QA details"
  }};
  assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify(approved)),true);
  for(const input of [
    {...approved.input,index:40},
    {...approved.input,index:1.5},
    {...approved.input,snapshotId:"not-a-snapshot"},
    {...approved.input,label:"Send"},
    {...approved.input,extra:true},
    {index:0,label:"Open QA details"}
  ]) assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify({
    toolId:"screen.tap_observed",input
  })),false,JSON.stringify(input));
});

test("shared overlay review can show exact app, recipient and message tool arguments",()=>{
  const contact={toolId:"screen.open_whatsapp_chat",input:{recipientHint:"Amma"}};
  const draft={toolId:"screen.compose_message",input:{
    appId:"whatsapp",recipientHint:"Amma",exactText:"Hi Mom i will call you later"
  }};
  const send={toolId:"screen.send_message",input:{...draft.input}};
  const app={toolId:"device.open_app",input:{app:"whatsapp"}};
  const titledApp={toolId:"device.open_app",input:{app:"WhatsApp"}};
  const fill={toolId:"screen.fill_text",input:{
    targetLabel:"Search",exactText:"Amma"
  }};
  for(const action of [app,titledApp,contact,draft,send,fill])
    assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify(action)),true,
      action.toolId);
});

test("shared overlay review accepts case-preserved installed app labels without weakening input checks",()=>{
  for(const app of ["WhatsApp","Google Maps","Clock"]){
    assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify({
      toolId:"device.open_app",input:{app}
    })),true,app);
  }
  for(const app of ["WhatsApp then send","WhatsApp then Send","WhatsApp; send all","install WhatsApp"]){
    assert.equal(isSafeSharedOverlayActionSummary(JSON.stringify({
      toolId:"device.open_app",input:{app}
    })),false,app);
  }
});

test("shared overlay review rejects consequential, malformed, and compound actions",()=>{
  for(const summary of [
    "screen.tap_label label=Send",
    "screen.tap_label label=Delete account",
    "screen.tap_label label=Allow",
    "screen.tap_label label=Alarms then Send",
    "screen.tap_label label=Alarms; app=Messages",
    "screen.tap_label label=écran",
    "screen.tap_label label="+"A".repeat(46),
    "screen.scroll direction=diagonal",
    "screen.scroll direction=up; label=Alarms",
    "device.open_app app=Clock"
  ]) assert.equal(isSafeSharedOverlayActionSummary(summary),false,summary);
});

test("shared overlay review rejects malformed or unbound message summaries",()=>{
  for(const summary of [
    JSON.stringify({toolId:"screen.send_message",input:{
      appId:"sms",recipientHint:"Amma",exactText:"Hi"
    }}),
    JSON.stringify({toolId:"screen.send_message",input:{
      appId:"whatsapp",recipientHint:"Amma",exactText:"Hi",extra:true
    }}),
    JSON.stringify({toolId:"screen.send_message",input:{
      appId:"whatsapp",recipientHint:"send",exactText:"Hi"
    }}),
    JSON.stringify({toolId:"screen.compose_message",input:{
      appId:"whatsapp",recipientHint:"Amma",exactText:"x".repeat(501)
    }}),
    JSON.stringify({toolId:"screen.tap_label",input:{label:"Send"}}),
    JSON.stringify({toolId:"device.open_app",input:{app:""}}),
    JSON.stringify({toolId:"device.open_app",input:{app:"Messages; send all"}}),
    JSON.stringify({toolId:"device.open_app",input:{app:"whatsapp",extra:true}}),
    JSON.stringify({toolId:"screen.fill_text",input:{targetLabel:"Password",exactText:"secret"}}),
    JSON.stringify({toolId:"screen.tap_label",input:{label:"Back"},extra:true}),
    "{not-json"
  ]) assert.equal(isSafeSharedOverlayActionSummary(summary),false,summary);
});
