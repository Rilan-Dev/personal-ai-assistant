import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=(path)=>readFileSync(path,'utf8');
test('manual Nexa task proposals are always explicit, encrypted and non-executable',()=>{
 const bridge=read('apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaConversationDb.kt');
 const client=read('apps/mobile/src/native/localTaskProposals.ts');
 const ui=read('apps/mobile/src/components/LocalTaskProposals.tsx');
 const app=read('apps/mobile/App.tsx');
 for(const method of ['createOwnerTaskProposal','recentOwnerTaskProposals','cancelOwnerTaskProposal']){
  assert.ok(bridge.includes('@ReactMethod fun '+method),method);
  assert.ok(client.includes(method),method);
 }
 assert.ok(bridge.includes('reactContext.currentActivity?.hasWindowFocus()!=true'));
 assert.ok(bridge.includes('"owner-review-v1"'));
 assert.ok(bridge.includes('NexaConversationDb.saveProposedAgentTask'));
 assert.ok(bridge.includes('NexaConversationDb.cancelProposedAgentTask'));
 assert.ok(ui.includes('Save for review') && ui.includes('Cancel proposal'));
 assert.match(ui,/accessibilityLabel="Proposal safety: no AI agent, message or phone action runs from a proposal\."/);
 assert.ok(read('.maestro/nexa/14-local-task-proposals.yaml').includes('Proposal safety: no AI agent, message or phone action runs from a proposal.'));
 assert.match(app,/engine\.live\s*&&\s*<LocalTaskProposals(?:\s+[^>]*)?\/>/);
 for(const forbidden of ['startOwnerLiveVoice(', 'queueWhatsApp', 'sendMessage(', 'NEXA_TASK_V2_ENABLED=true']){
  assert.equal(ui.includes(forbidden),false);
 }
});
