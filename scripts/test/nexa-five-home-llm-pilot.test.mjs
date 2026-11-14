import test from "node:test";
import assert from "node:assert/strict";
import {runFiveAppHomePilot,HOME_PILOT_TARGETS} from "../../packages/agent-core/src/home-pilot.ts";

const revision=n=>`home-revision-${String(n).padStart(4,"0")}`;
const view=(n,unique,ambiguous=[])=>({
  kind:"home",revision:revision(n),unique,ambiguous
});
const p=(observations,model=()=>({kind:"swipe",direction:"left"}))=>{
  let index=0,dispatches=0,allow=true;
  const port={
    observe:async()=>observations[Math.min(index,observations.length-1)],
    propose:async(v,remaining,attempts)=>model(v,remaining,attempts),
    dispatchReviewed:async(action,fromRevision)=>{
      dispatches++;
      index++;
      return {status:"observed_change",fromRevision,toRevision:revision(index)};
    },
    ownerAllowsNavigation:async()=>allow
  };
  return {port,stop:()=>{allow=false},get dispatches(){return dispatches}};
};

test("five apps pass only after distinct, unique home revisions were observed",async()=>{
  const fixture=p([
    view(0,["whatsapp"]),view(1,["imo"]),view(2,["contacts"]),
    view(3,["calls"]),view(4,["clock"])]);
  const result=await runFiveAppHomePilot(fixture.port);
  assert.equal(result.status,"passed");
  assert.equal(result.swipes,4);
  assert.deepEqual(result.observed,[...HOME_PILOT_TARGETS]);
  assert.deepEqual(result.missing,[]);
  assert.equal(fixture.dispatches,4);
});
test("prompt injection to tap, call, send, alarm, or coordinates fails closed without dispatch",async()=>{
  const failures=[
    {kind:"tap",target:"whatsapp"},
    {kind:"call",target:"contacts"},
    {kind:"send",to:"mom"},
    {kind:"set_alarm",hour:6},
    {kind:"swipe",direction:"diagonal"},
    {kind:"swipe",direction:"left",x:122,y:450},
    "swipe left",
    {kind:"scroll",direction:"down"}
  ];
  for(const malicious of failures){
    const fixture=p([view(0,["whatsapp"])],()=>malicious);
    const result=await runFiveAppHomePilot(fixture.port);
    assert.equal(result.status,"blocked",JSON.stringify(malicious));
    assert.equal(fixture.dispatches,0);
  }
});
test("missing owner navigation trust fails before model or screen is queried",async()=>{
  let observations=0,modelCalls=0;
  const result=await runFiveAppHomePilot({
    ownerAllowsNavigation:async()=>false,
    observe:async()=>{observations++;return view(0,[])},
    propose:async()=>{modelCalls++;return {kind:"swipe",direction:"left"}},
    dispatchReviewed:async()=>{throw Error("Unsafe dispatch");}
  });
  assert.equal(result.status,"blocked");
  assert.equal(observations,0);assert.equal(modelCalls,0);
});
test("unknown, accepted-only, or conflicting Android swipe evidence is NOT a passed task",async()=>{
  for(const receipt of [
    {status:"unknown"},
    {status:"blocked"},
    {status:"unverified"},
    {status:"observed_change",fromRevision:revision(0),toRevision:revision(0)},
    {status:"observed_change",fromRevision:revision(2),toRevision:revision(1)}
  ]){
    let index=0;
    const result=await runFiveAppHomePilot({
      ownerAllowsNavigation:async()=>true,
      observe:async()=>view(index++,["whatsapp"]),
      propose:async()=>({kind:"swipe",direction:"left"}),
      dispatchReviewed:async()=>receipt
    });
    assert.equal(result.status,"uncertain",JSON.stringify(receipt));
    assert.notEqual(result.status,"passed");
  }
});
test("duplicate unchanged home pages cannot loop or fabricate progress",async()=>{
  let proposals=0;
  const fixture=p([view(0,["whatsapp"]),view(0,["whatsapp"])],()=>{proposals++;return {kind:"swipe",direction:"left"}});
  const result=await runFiveAppHomePilot(fixture.port);
  assert.equal(result.status,"uncertain");
  assert.equal(fixture.dispatches,1);
  assert.equal(proposals,1);
});
test("missing app is not silently marked installed; at most eight one-shot swipes",async()=>{
  const f=p(Array.from({length:9},(_,n)=>view(n,["whatsapp","imo","contacts","calls"])));
  const result=await runFiveAppHomePilot(f.port);
  assert.equal(result.status,"incomplete");
  assert.deepEqual(result.missing,["clock"]);
  assert.equal(result.swipes,8);
});
test("untrusted app, lockscreen, invalid or ambiguous labels never count as successful navigation",async()=>{
  const examples=[
    {kind:"locked",revision:revision(0),unique:[],ambiguous:[]},
    view(0,["contacts","contacts"]),
    view(0,["contacts"],["contacts"]),
    view(0,["contacts","bank"]),
    {kind:"home",revision:"",unique:[],ambiguous:[]}
  ];
  for(const item of examples){
    const result=await runFiveAppHomePilot(p([item]).port);
    assert.equal(result.status,"blocked");
  }
});
test("LLM sees only five sanitized keys and revision, never raw UI labels",async()=>{
  let delivered;
  const fixture=p([view(0,["whatsapp"],["imo"]),view(1,["whatsapp","imo","contacts","calls","clock"])],
    (visible,remaining)=>{delivered={visible,remaining};return {kind:"swipe",direction:"right"}});
  const result=await runFiveAppHomePilot(fixture.port);
  assert.equal(result.status,"passed");
  assert.deepEqual(Object.keys(delivered.visible),["kind","revision","unique","ambiguous"]);
  assert.deepEqual(delivered.remaining,["imo","contacts","calls","clock"]);
  assert.doesNotMatch(JSON.stringify(delivered),/phone_number|body|sender|contact_name|screen_text|coordinates|otp/i);
});

test("different observed home revision cannot inherit a verified swipe receipt",async()=>{
  const fixture=p([
    view(0,["whatsapp"]),
    // Android receipt claims revision 1, but this observation is revision 3.
    view(3,["whatsapp","imo","contacts","calls","clock"])
  ]);
  const result=await runFiveAppHomePilot(fixture.port);
  assert.equal(result.status,"uncertain");
  assert.equal(result.swipes,1);
  assert.deepEqual(result.observed,["whatsapp"]);
});
