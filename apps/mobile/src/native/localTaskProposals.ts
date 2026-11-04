import {NativeModules,Platform} from 'react-native';

/** Manual, non-executable owner task — never a provider or model instruction. */
export type LocalTaskProposal={
  taskId:string;sessionId:string;origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  status:'proposed'|'cancelled';policyVersion:string;goal:string;
  createdAt:number;updatedAt:number;
};
type LocalTaskBridge={
  createOwnerTaskProposal(goal:string):Promise<string>;
  recentOwnerTaskProposals(limit:number):Promise<string>;
  cancelOwnerTaskProposal(taskId:string):Promise<boolean>;
};
const id=/^[a-zA-Z0-9][a-zA-Z0-9_-]{7,99}$/;
const ownerReviewPolicy=(value:unknown)=>value==='owner-review-v1'||value==='owner-preview-v1';
const bridge=():LocalTaskBridge=>{
  const api=NativeModules.PAAConversationHistory;
  if(Platform.OS!=='android'||typeof api?.createOwnerTaskProposal!=='function'||
    typeof api?.recentOwnerTaskProposals!=='function'||
    typeof api?.cancelOwnerTaskProposal!=='function')
    throw Error('Update Nexa to review encrypted local task proposals');
  return api as LocalTaskBridge;
};
export const localTaskProposalsAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAConversationHistory?.createOwnerTaskProposal==='function' &&
  typeof NativeModules.PAAConversationHistory?.recentOwnerTaskProposals==='function' &&
  typeof NativeModules.PAAConversationHistory?.cancelOwnerTaskProposal==='function';

export async function saveLocalTaskProposal(goal:string):Promise<string>{
  const description=goal.trim();
  if(description.length<1||description.length>500||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(description))
    throw Error('Task description must be 1–500 safe characters');
  const taskId=await bridge().createOwnerTaskProposal(description);
  if(typeof taskId!=='string'||!id.test(taskId))throw Error('Local task receipt could not be verified');
  return taskId;
}
function decodeProposal(value:unknown):LocalTaskProposal{
  if(!value||typeof value!=='object'||Array.isArray(value))throw Error('Malformed local task record');
  const item=value as Record<string,unknown>;
  if(typeof item.taskId!=='string'||!id.test(item.taskId)||
    typeof item.sessionId!=='string'||!id.test(item.sessionId)||
    item.origin!=='chat'||!ownerReviewPolicy(item.policyVersion)||
    !['proposed','cancelled'].includes(String(item.status))||
    typeof item.goal!=='string'||item.goal.length>500||
    !Number.isSafeInteger(item.createdAt)||!Number.isSafeInteger(item.updatedAt))
    throw Error('Unrecognized local task record');
  return item as LocalTaskProposal;
}
export async function listLocalTaskProposals():Promise<LocalTaskProposal[]>{
  const raw=await bridge().recentOwnerTaskProposals(50);
  if(typeof raw!=='string'||raw.length>150_000)throw Error('Local task history response too large');
  const records:unknown=JSON.parse(raw);
  if(!Array.isArray(records)||records.length>50)throw Error('Unexpected local task history');
  // Defense in depth: native history already scopes this to manual owner records.
  // Continue to support any preview-only entries created on an earlier APK.
  return records.filter(x=>x&&typeof x==='object'&&
    ownerReviewPolicy((x as {policyVersion?:unknown}).policyVersion))
   .map(decodeProposal);
}
export async function cancelLocalTaskProposal(idToCancel:string):Promise<boolean>{
  if(!id.test(idToCancel))throw Error('Invalid local task identity');
  return await bridge().cancelOwnerTaskProposal(idToCancel)===true;
}
