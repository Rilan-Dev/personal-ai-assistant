import test from 'node:test';
import assert from 'node:assert/strict';
import {scoreIconEvidence,inspectGuidedScreenPlan} from '../src/screen-confidence.ts';

const box={left:10,top:20,right:60,bottom:70};
const icon=(semantic='unknown',id='icon-0')=>({
 id,label:semantic==='unknown'?'Unknown icon':'Search',semantic,
 boundsPx:box,clickable:true,evidence:'accessibility'
});
const snap=(changes={})=>({
 version:1,snapshotId:'observed',packageName:'com.example.app',windowId:9,
 capturedAtWallMs:1000,expiresInMs:60000,icons:[icon()],...changes
});
const view=(changes={})=>({
 snapshotId:'crops',packageName:'com.example.app',windowId:9,
 capturedAtWallMs:2000,expiresInMs:60000,
 crops:[{iconId:'crop-0',boundsPx:box}],...changes
});
const hint={label:'Search',meaning:'Search UI',verified:false,provider:'openai'};
test('accessibility agrees with unverified image label: higher corroboration, never authorization',()=>{
 const result=scoreIconEvidence(snap({icons:[icon('search')]}),view(),hint,'crop-0',3000);
 assert.equal(result.status,'corroborated');
 assert.ok(result.score>60 && result.score<=85);
 assert.equal(result.executable,false);
 assert.equal(result.modelConfidenceProbability,null);
 assert.equal(result.matchedIconId,'icon-0');
});
test('unknown accessibility icon: plausible but weaker than a confirmed label',()=>{
 const tentative=scoreIconEvidence(snap(),view(),hint,'crop-0',3000);
 const agreed=scoreIconEvidence(snap({icons:[icon('search')]}),view(),hint,'crop-0',3000);
 assert.equal(tentative.status,'tentative');
 assert.ok(tentative.score<agreed.score);
 assert.ok(tentative.score<=55);
 assert.equal(tentative.executable,false);
});
test('ambiguity, source conflict and wrong label all score zero',()=>{
 const ambiguous=scoreIconEvidence(snap({icons:[icon(),icon('unknown','icon-1')]}),view(),hint,'crop-0',3000);
 assert.equal(ambiguous.score,0); assert.equal(ambiguous.status,'blocked');
 for(const args of [
  [snap(),view({windowId:12}),hint],
  [snap({icons:[icon('call')]}),view(),hint],
  [snap(),view(),{...hint,label:'wire money'}]
 ]) {
  const v=scoreIconEvidence(args[0],args[1],args[2],'crop-0',3000);
  assert.equal(v.score,0); assert.equal(v.executable,false);
 }
});
test('missing window ID lowers score and approaching expiry never raises it',()=>{
 const source=scoreIconEvidence(snap({windowId:undefined}),view({windowId:undefined}),hint,'crop-0',3000);
 const strict=scoreIconEvidence(snap(),view(),hint,'crop-0',3000);
 assert.ok(source.score<strict.score);
 const ending=scoreIconEvidence(snap({expiresAtWallMs:3100}),view({expiresAtWallMs:3100}),hint,'crop-0',3050);
 assert.ok(ending.score<=strict.score);
});
test('screen navigation preview marks absent or duplicate initial step without guessing',()=>{
 const missing=inspectGuidedScreenPlan('find search in menu',snap({icons:[icon('search')]}),3000);
 assert.equal(missing.status,'needs_new_scan');
 assert.equal(missing.nextSemantic,'menu');
 assert.equal(missing.executable,false);
 const ambiguous=inspectGuidedScreenPlan('find search in menu',snap({icons:[icon('menu'),icon('menu','b')]}),3000);
 assert.equal(ambiguous.status,'ambiguous');
 const ready=inspectGuidedScreenPlan('find search in menu',snap({icons:[icon('menu')]}),3000);
 assert.equal(ready.status,'ready_to_review');
 assert.equal(ready.steps.length,2);
 assert.match(ready.message,/review/i);
 assert.equal('targetToken' in ready,false);
});
test('navigation preview fails closed on expired and unsupported requests',()=>{
 assert.equal(inspectGuidedScreenPlan('tap send',snap(),3000).status,'unsupported');
 assert.equal(inspectGuidedScreenPlan('find search in menu',snap(),100000).status,'expired');
});
