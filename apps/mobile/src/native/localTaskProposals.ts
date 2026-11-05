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
  consumePendingOwnerTaskReview?():Promise<string|null>;
  admitOwnerReadOnlyPlan(planJson:string):Promise<boolean>;
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


/** Only stores trusted, low-risk, non-executable plans; Android independently
 * validates the task identity, policy, tool registry and every step.
 */
export async function admitLocalReadOnlyTaskPlan(plan:unknown):Promise<boolean>{
  const serialized=JSON.stringify(plan);
  if(serialized.length<32||serialized.length>12_000)
    throw Error('Task plan size invalid');
  const api=bridge();
  if(typeof api.admitOwnerReadOnlyPlan!=='function')
    throw Error('Native task coordinator storage unavailable');
  return await api.admitOwnerReadOnlyPlan(serialized)===true;
}


/** Typed requests and spoken owner transcripts only prefill an existing review. */
export function parseOwnerTaskReviewIntent(input:unknown):string|null {
  if(typeof input!=='string'||input.length<10||input.length>420||
     Array.from(input).some(char=>char.charCodeAt(0)<32))return null;
  const match=/^(?:hey\s+nexa[,!]?\s+)?(?:please\s+)?(?:save|create|add)\s+(?:a\s+)?task\s+(?:to\s+|:\s*)(.+)$/i.exec(input.trim());
  const goal=match?.[1]?.trim().replace(/[.!?]+$/,'').trim();
  return goal&&goal.length>=5&&goal.length<=320?goal:null;
}
/** No task is persisted until the owner taps Save in the existing Tools view. */
export async function consumePendingOwnerTaskReview():Promise<string|null>{
  if(Platform.OS!=='android')return null;
  const module=NativeModules.PAAConversationHistory as LocalTaskBridge|undefined;
  if(typeof module?.consumePendingOwnerTaskReview!=='function')return null;
  const value=await module.consumePendingOwnerTaskReview();
  if(value===null)return null;
  if(typeof value!=='string'||value.length<5||value.length>320||
     Array.from(value).some(char=>char.charCodeAt(0)<32))
    throw Error('Invalid spoken task review draft');
  return value;
}
