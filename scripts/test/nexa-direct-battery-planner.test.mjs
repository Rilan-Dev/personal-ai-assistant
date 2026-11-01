import test from 'node:test';
import assert from 'node:assert/strict';
import {createDirectProviderPlanner} from '../../packages/agent-core/src/direct-provider.ts';

test('Sarvam battery percentage requests use Android read without asking LLM to guess a tool',async()=>{
  let providerCalled=false;
  const planner=createDirectProviderPlanner({
    settings:{version:1,provider:'sarvam',model:'sarvam-105b',apiKey:'test-key-123456789012345'},
    availableToolIds:['device.battery_status','device.status'],
    fetchImpl:async()=>{providerCalled=true;throw new Error('Unexpected provider request');}
  });
  for(const request of ['Check battery level','What is my charge percentage','What is my battery percentage?']){
    const decision=await planner.next(request,[]);
    assert.equal(decision.type,'tool',request);
    assert.equal(decision.call.toolId,'device.battery_status');
    assert.deepEqual(decision.call.input,{});
  }
  assert.equal(providerCalled,false);
});
test('unrelated or multi-step tasks are never promoted to native tool calls by the local shortcut',async()=>{
  let called=0;
  const planner=createDirectProviderPlanner({
    settings:{version:1,provider:'sarvam',model:'sarvam-105b',apiKey:'test-key-123456789012345'},
    availableToolIds:['device.battery_status'],
    fetchImpl:async()=>{called++;return {ok:true,text:async()=>JSON.stringify({choices:[{message:{content:JSON.stringify({type:'clarify',question:'Please clarify.'})}}]})};}
  });
  const result=await planner.next('What is my charge percentage and open WhatsApp',[]);
  assert.equal(result.type,'clarify');
  assert.equal(called,1);
});
