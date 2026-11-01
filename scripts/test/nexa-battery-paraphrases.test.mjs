import test from 'node:test';
import assert from 'node:assert/strict';
import {requestAllowsNativeTool} from '../../packages/agent-contracts/src/intent-policy.ts';

test('natural questions about phone charging percentage are authorized read-only battery requests',()=>{
 for(const question of [
   'Check battery level', 'What is my charge percentage',
   "What's my battery percentage?", "How much battery do I have?",
   'How much charge is left', 'Show phone battery status'
 ]) assert.equal(requestAllowsNativeTool(question,'device.battery_status',{}),true,question);
});
test('battery paraphrases never authorize device writes or multi-intent actions',()=>{
 for(const request of [
   'What is my charge percentage and open WhatsApp',
   'How much battery do I have then text mom',
   'Show battery and toggle flashlight',
 ]) assert.equal(requestAllowsNativeTool(request,'device.battery_status',{}),false,request);
 assert.equal(requestAllowsNativeTool('What is my charge percentage','device.set_flashlight',{enabled:true}),false);
});
