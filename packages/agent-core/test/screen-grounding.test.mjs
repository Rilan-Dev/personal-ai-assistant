import test from 'node:test';
import assert from 'node:assert/strict';
import {groundVisualIcon, proposeNavigationSteps, createOwnerScreenGrant} from '../src/screen-grounding.ts';
const rect={left:20,top:20,right:64,bottom:64};
const icon=(id,semantic,boundsPx=rect)=>({id,semantic,label:semantic==='unknown'?'Unknown icon':'Search',boundsPx,clickable:true,evidence:'accessibility'});
const snap=(changes={})=>({version:1,snapshotId:'s1',packageName:'com.example.app',capturedAtWallMs:1000,expiresInMs:50000,icons:[icon('i0','unknown')],...changes});
const image=(changes={})=>({snapshotId:'v1',packageName:'com.example.app',capturedAtWallMs:5000,expiresInMs:50000,crops:[{iconId:'v0',boundsPx:rect,dataUri:'data:image/png;base64,'+'A'.repeat(100)}],...changes});
const guess={label:'Search',meaning:'Likely search',verified:false,provider:'openai'};
test('a geometry-matched visual guess remains non-executable and limited evidence',()=>{
 const r=groundVisualIcon(snap(),image(),guess,'v0',6000);
 assert.equal(r.status,'geometry_matched');assert.equal(r.evidenceStrength,'limited');
 assert.equal(r.actionEligible,false);assert.equal(r.matchedIconId,'i0');
 assert.equal('actionToken' in r,false);assert.equal('coordinates' in r,false);
});
test('cross-app, stale, ambiguous, and disagreeing scans cannot be promoted',()=>{
 assert.equal(groundVisualIcon(snap(),image({packageName:'com.other.app'}),guess,'v0',6000).status,'source_mismatch');
 assert.equal(groundVisualIcon(snap(),image(),guess,'v0',100000).status,'expired');
 assert.equal(groundVisualIcon(snap({icons:[icon('i0','unknown'),icon('i1','unknown')]}),image(),guess,'v0',6000).status,'ambiguous');
 assert.equal(groundVisualIcon(snap({icons:[icon('i0','send')]}),image(),guess,'v0',6000).status,'conflicting');
});
test('nonoverlapping crops and unrecognized model descriptions remain ungrounded',()=>{
 assert.equal(groundVisualIcon(snap(),image({crops:[{...image().crops[0],boundsPx:{left:100,top:100,right:140,bottom:140}}]}),guess,'v0',6000).status,'unmatched');
 assert.equal(groundVisualIcon(snap(),image(),{...guess,label:'Unknown'},'v0',6000).status,'unknown');
});
test('owner grant expires and can be revoked before any model sees it',()=>{
 const grant=createOwnerScreenGrant();assert.equal(grant.read(2000),null);
 assert.equal(grant.release(snap(),2000),true);
 assert.equal(grant.read(2001)?.snapshotId,'s1');
 assert.equal(grant.read(31000),null);
 grant.release(snap(),3000);grant.revoke();assert.equal(grant.read(3001),null);
 assert.equal(grant.release(snap(),60000),false);
});
test('navigation steps are bounded suggestions requiring separate inspection and confirmation',()=>{
 const result=proposeNavigationSteps('find search in menu',snap(),3000);
 assert.equal(result.status,'suggested');assert.equal(result.executable,false);
 assert.deepEqual(result.steps.map(s=>s.semantic),['menu','search']);
 assert.ok(result.steps.every(s=>s.freshInspectionRequired&&s.ownerConfirmationRequired));
 assert.equal(proposeNavigationSteps('tap send',snap(),3000).status,'unsupported');
 assert.equal(proposeNavigationSteps('open settings',snap(),80000).status,'expired');
});

test('same-package screenshots from different Android windows never match',()=>{
 assert.equal(groundVisualIcon(snap({windowId:10}),image({windowId:11}),guess,'v0',6000).status,'source_mismatch');
 assert.equal(groundVisualIcon(snap({windowId:10}),image(),guess,'v0',6000).status,'source_mismatch');
 assert.equal(groundVisualIcon(snap({windowId:10}),image({windowId:10}),guess,'v0',6000).status,'geometry_matched');
});
