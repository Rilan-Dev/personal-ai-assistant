import test from 'node:test';
import assert from 'node:assert/strict';
import {createReadOnlyScreenTools,createScreenAwarePlanner} from '../src/screen-agent.ts';
import {createOwnerScreenGrant} from '../src/screen-grounding.ts';
const snapshot={version:1,snapshotId:'s1',packageName:'com.example.app',
 capturedAtWallMs:1000,expiresInMs:60000,icons:[]};
const resolver=()=>({status:'found',answer:'Search identified. No action was taken.',elementIds:['i1']});
test('tools block unapproved observations and are side-effect free',async()=>{
 const grant=createOwnerScreenGrant();
 const tools=createReadOnlyScreenTools(grant,resolver,()=>2000);
 assert.equal(tools.length,2);
 for(const t of tools){assert.equal(t.hasExternalSideEffect,false);assert.equal(t.risk,'low')}
 const observe=tools.find(t=>t.id==='screen.observe');
 assert.equal((await observe.execute({question:'find search'},{sessionId:'a'})).status,'blocked');
});
test('strict input rejects coordinates, tap instructions, and sends no target identifiers',async()=>{
 const grant=createOwnerScreenGrant();grant.release(snapshot,2000);
 const observe=createReadOnlyScreenTools(grant,resolver,()=>2001).find(x=>x.id==='screen.observe');
 assert.equal(observe.validateInput({question:'find search'}),true);
 assert.equal(observe.validateInput({question:'tap search'}),false);
 assert.equal(observe.validateInput({question:'find search',coordinates:{x:1,y:2}}),false);
 const result=await observe.execute({question:'find search'},{sessionId:'a'});
 assert.equal(result.status,'success');assert.equal(result.data.actionEligible,false);
 assert.equal('elementIds' in result.data,false);assert.equal('coordinates' in result.data,false);
});
test('agent invokes deterministic read-only screen tool without consulting model',async()=>{
 const grant=createOwnerScreenGrant();grant.release(snapshot,2000);
 let calls=0;const base={next:async()=>{calls++;return {type:'done',response:'BASE'}}};
 const p=createScreenAwarePlanner(base,grant,resolver,()=>2001);
 const first=await p.next('Where is the search icon?',[]);
 assert.equal(first.type,'tool');assert.equal(first.call.toolId,'screen.observe');assert.equal(calls,0);
 const second=await p.next('Where is the search icon?',[
  {call:first.call,outcome:{status:'success',data:{answer:'Search is top right',actionEligible:false}}}]);
 assert.equal(second.type,'done');assert.equal(second.response,'Search is top right');assert.equal(calls,0);
});
test('unrelated actions use original policy while explicit menu plan is non-executable',async()=>{
 const grant=createOwnerScreenGrant();grant.release(snapshot,2000);
 const base={next:async()=>({type:'done',response:'BASE'})};
 const p=createScreenAwarePlanner(base,grant,resolver,()=>2001);
 assert.equal((await p.next('tap the send icon',[])).response,'BASE');
 assert.equal((await p.next('find my GitHub app',[])).response,'BASE');
 assert.equal((await p.next('open search from menu',[])).call.toolId,'screen.suggest_navigation');
 const absent=createScreenAwarePlanner(base,createOwnerScreenGrant(),resolver,()=>2001);
 assert.equal((await absent.next('Where is the search icon?',[])).type,'clarify');
});

test('old or wrong-request screen observations cannot be replayed into current conversation',async()=>{
 const grant=createOwnerScreenGrant();grant.release(snapshot,2000);
 const base={next:async()=>({type:'done',response:'BASE'})};
 const p=createScreenAwarePlanner(base,grant,resolver,()=>2001);
 const foreign={call:{callId:'bad',toolId:'screen.observe',input:{question:'find menu'}},
   outcome:{status:'success',data:{answer:'STALE PRIVATE ANSWER',actionEligible:false}}};
 const result=await p.next('Where is the search icon?',[foreign]);
 assert.equal(result.type,'clarify');
 assert.equal(JSON.stringify(result).includes('STALE PRIVATE ANSWER'),false);
 grant.revoke();
 const revoked=await p.next('Where is the search icon?',[
  {call:{callId:'screen-observe-1',toolId:'screen.observe',
   input:{question:'Where is the search icon?'}},outcome:{status:'success',
    data:{answer:'STALE PRIVATE ANSWER',actionEligible:false}}}]);
 assert.equal(revoked.type,'clarify');
});
test('menu plan describes ambiguous first step and does not promise execution',async()=>{
 const grant=createOwnerScreenGrant();
 const current={...snapshot,icons:[
  {id:'a',label:'Menu',semantic:'menu',clickable:true,evidence:'accessibility',boundsPx:{left:0,top:0,right:50,bottom:50}},
  {id:'b',label:'Menu',semantic:'menu',clickable:true,evidence:'accessibility',boundsPx:{left:100,top:0,right:150,bottom:50}}]};
 grant.release(current,2000);
 const tools=createReadOnlyScreenTools(grant,resolver,()=>2001);
 const tool=tools.find(t=>t.id==='screen.suggest_navigation');
 const outcome=await tool.execute({request:'find search in menu'},{sessionId:'s'});
 assert.equal(outcome.data.status,'ambiguous');
 assert.equal(outcome.data.actionEligible,false);
 assert.match(outcome.data.answer,/multiple/i);
});
