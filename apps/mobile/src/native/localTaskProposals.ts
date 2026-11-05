import {NativeModules,Platform} from 'react-native';

/** Manual, non-executable owner task — never a provider or model instruction. */
export type LocalTaskProposal={
  taskId:string;sessionId:string;origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  status:'proposed'|'cancelled'|'ready'|'running'|'completed'|'uncertain';policyVersion:string;goal:string;
  createdAt:number;updatedAt:number;revision?:number;
};
export type LocalTaskReviewDetail={
  taskId:string;status:'proposed'|'cancelled'|'ready'|'running'|'completed'|'uncertain';revision:number;updatedAt:number;
  steps:Array<{ordinal:number;toolId:string;status:string;evidence:string;
    dependencies?:number;missingDependencies?:number;dependenciesSatisfied?:boolean;executable?:false}>;
  events:Array<{kind:string;createdAt:number}>;
};
type LocalTaskBridge={
  createOwnerTaskProposal(goal:string):Promise<string>;
  createOwnerTaskFromHandoff?(token:string,goal:string):Promise<string>;
  discardOwnerTaskHandoff?(token:string):Promise<boolean>;
  recentOwnerTaskProposals(limit:number):Promise<string>;
  cancelOwnerTaskProposal(taskId:string):Promise<boolean>;
  clearCancelledOwnerTaskDetails?(taskId:string):Promise<boolean>;
  consumePendingOwnerTaskReview?():Promise<string|null>;
  admitOwnerReadOnlyPlan(planJson:string):Promise<boolean>;
  reviseOwnerTaskProposal(taskId:string,expectedRevision:number,goal:string):Promise<boolean>;
  ownerTaskReviewDetail(taskId:string):Promise<string>;
  reviewOwnerReadOnlyPlan?(taskId:string):Promise<string>;
  runReviewedOwnerReadOnlyPlan?(taskId:string,revision:number):Promise<string>;
  continueReviewedOwnerReadOnlyPlan?(taskId:string,revision:number):Promise<string>;
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

export async function saveLocalTaskProposal(goal:string,handoffToken?:string):Promise<string>{
  const description=goal.trim();
  if(description.length<1||description.length>500||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(description))
    throw Error('Task description must be 1–500 safe characters');
  const api=bridge();
  let taskId:string;
  if(handoffToken!==undefined){
    if(!/^[0-9a-f-]{36}$/.test(handoffToken)||
       typeof api.createOwnerTaskFromHandoff!=='function')
      throw Error('Native owner review handoff is unavailable');
    taskId=await api.createOwnerTaskFromHandoff(handoffToken,description);
  }else {
    taskId=await api.createOwnerTaskProposal(description);
  }
  if(typeof taskId!=='string'||!id.test(taskId))throw Error('Local task receipt could not be verified');
  return taskId;
}

/** Deliberately discard an unsaved native-origin task draft. No side effect. */
export async function discardPendingOwnerTaskHandoff(token:string):Promise<boolean>{
  if(!/^[0-9a-f-]{36}$/.test(token))throw Error('Invalid task review token');
  const api=bridge();
  if(typeof api.discardOwnerTaskHandoff!=='function')
    throw Error('Update Nexa to discard a pending task handoff');
  return (await api.discardOwnerTaskHandoff(token))===true;
}

function decodeProposal(value:unknown):LocalTaskProposal{
  if(!value||typeof value!=='object'||Array.isArray(value))throw Error('Malformed local task record');
  const item=value as Record<string,unknown>;
  if(typeof item.taskId!=='string'||!id.test(item.taskId)||
    typeof item.sessionId!=='string'||!id.test(item.sessionId)||
    !item.sessionId.startsWith('owner-')||
    !['chat','live-voice','assistant-role','overlay'].includes(String(item.origin))||
    !ownerReviewPolicy(item.policyVersion)||
    !['proposed','cancelled','ready','running','completed','uncertain'].includes(String(item.status))||
    typeof item.goal!=='string'||item.goal.length>500||
    !Number.isSafeInteger(item.createdAt)||!Number.isSafeInteger(item.updatedAt)||
    (item.revision!==undefined&&(!Number.isSafeInteger(item.revision)||Number(item.revision)<1)))
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


/** An owner correction never authorizes tool execution. The native database
 * compares the revision and invalidates any unclaimed draft plan steps.
 */
export async function reviseLocalTaskProposal(
  taskId:string,expectedRevision:number,goal:string
):Promise<boolean>{
  const text=goal.trim();
  if(!id.test(taskId)||!Number.isSafeInteger(expectedRevision)||expectedRevision<1||
     expectedRevision>1_000_000||text.length<1||text.length>500||
     /[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(text))
    throw Error('Invalid task correction');
  const api=bridge();
  if(typeof api.reviseOwnerTaskProposal!=='function')
    throw Error('Update Nexa to correct a private task');
  return await api.reviseOwnerTaskProposal(taskId,expectedRevision,text)===true;
}

/** Only event names and step metadata are exposed; sensitive arguments and
 * ciphertext never leave Android through this review endpoint.
 */
export async function readLocalTaskDetail(taskId:string):Promise<LocalTaskReviewDetail>{
  if(!id.test(taskId))throw Error('Invalid task identity');
  const api=bridge();
  if(typeof api.ownerTaskReviewDetail!=='function')
    throw Error('Update Nexa to view private task details');
  const raw=await api.ownerTaskReviewDetail(taskId);
  if(typeof raw!=='string'||raw.length>15_000)
    throw Error('Invalid task detail response');
  const value:unknown=JSON.parse(raw);
  if(!value||typeof value!=='object'||Array.isArray(value))
    throw Error('Invalid task detail record');
  const d=value as Record<string,unknown>;
  if(d.taskId!==taskId||!['proposed','cancelled','ready','running','completed','uncertain'].includes(String(d.status))||
     !Number.isSafeInteger(d.revision)||Number(d.revision)<1||
     !Number.isSafeInteger(d.updatedAt)||!Array.isArray(d.steps)||
     d.steps.length>8||!Array.isArray(d.events)||d.events.length>40)
    throw Error('Invalid task detail fields');
  for(const step of d.steps){
    if(!step||typeof step!=='object'||Array.isArray(step))throw Error('Invalid step');
    const x=step as Record<string,unknown>;
    if(!Number.isSafeInteger(x.ordinal)||Number(x.ordinal)<0||Number(x.ordinal)>7||
       typeof x.toolId!=='string'||x.toolId.length>80||
       typeof x.status!=='string'||x.status.length>40||
       typeof x.evidence!=='string'||x.evidence.length>40||
       (x.executable!==undefined&&x.executable!==false)||
       (x.dependencies!==undefined&&(!Number.isSafeInteger(x.dependencies)||Number(x.dependencies)<0||Number(x.dependencies)>8))||
       (x.missingDependencies!==undefined&&(!Number.isSafeInteger(x.missingDependencies)||Number(x.missingDependencies)<0||Number(x.missingDependencies)>8))||
       (x.dependenciesSatisfied!==undefined&&typeof x.dependenciesSatisfied!=='boolean'))
      throw Error('Invalid task step metadata');
  }
  for(const event of d.events){
    if(!event||typeof event!=='object'||Array.isArray(event))throw Error('Invalid event');
    const x=event as Record<string,unknown>;
    if(typeof x.kind!=='string'||!/^[a-z0-9_]{1,80}$/.test(x.kind)||
       !Number.isSafeInteger(x.createdAt))
      throw Error('Invalid task event metadata');
  }
  return value as LocalTaskReviewDetail;
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
/** A native-origin handoff is a short-lived one-shot review token, not an
 * approval to perform Android actions. Typed Chat requests do not have one.
 */
export type PendingOwnerTaskReview={
  goal:string;
  origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  token:string;
};
/** Nothing is persisted until the owner explicitly taps Save in Tools. */
export async function consumePendingOwnerTaskReview():Promise<PendingOwnerTaskReview|null>{
  if(Platform.OS!=='android')return null;
  const module=NativeModules.PAAConversationHistory as LocalTaskBridge|undefined;
  if(typeof module?.consumePendingOwnerTaskReview!=='function')return null;
  const raw=await module.consumePendingOwnerTaskReview();
  if(raw===null)return null;
  if(typeof raw!=='string'||raw.length>650)throw Error('Invalid owner review receipt');
  const value:unknown=JSON.parse(raw);
  if(!value||typeof value!=='object'||Array.isArray(value))
    throw Error('Invalid owner review receipt');
  const item=value as Record<string,unknown>;
  if(typeof item.goal!=='string'||item.goal.length<5||item.goal.length>320||
    Array.from(item.goal).some(x=>x.charCodeAt(0)<32)||
    !['chat','live-voice','assistant-role','overlay'].includes(String(item.origin))||
    typeof item.token!=='string'||!/^[0-9a-f-]{36}$/.test(item.token))
    throw Error('Untrusted owner review receipt');
  return value as PendingOwnerTaskReview;
}

/** Native-only reviewed plan metadata; plaintext arguments never reach React Native. */
export type OwnerReadOnlyStep={
  ordinal:number;
  toolId:'device.status'|'device.battery_status'|'device.storage_status';
  status:'planned'|'pending'|'outcome_unknown'|'verified'|'uncertain'|'cancelled';
  evidence:'native_reading';
};
export async function inspectOwnerReadOnlyPlan(taskId:string):Promise<OwnerReadOnlyStep[]>{
  if(!id.test(taskId))throw Error('Invalid task identity');
  const api=bridge();
  if(typeof api.reviewOwnerReadOnlyPlan!=='function')
    throw Error('Native plan review unavailable');
  const raw=await api.reviewOwnerReadOnlyPlan(taskId);
  if(typeof raw!=='string'||raw.length>8000)throw Error('Invalid plan review response');
  const value:unknown=JSON.parse(raw);
  if(!Array.isArray(value)||value.length>8)throw Error('Invalid step list');
  const steps=value as OwnerReadOnlyStep[];
  for(let index=0;index<steps.length;index++){
    const step=steps[index];
    if(!step||typeof step!=='object'||step.ordinal!==index||
      !['device.status','device.battery_status','device.storage_status'].includes(String(step.toolId))||
      !['planned','pending','outcome_unknown','verified','uncertain','cancelled'].includes(String(step.status))||
      step.evidence!=='native_reading')throw Error('Unrecognized native step');
  }
  return steps;
}
export async function runOwnerReviewedReadOnlyPlan(taskId:string,revision:number):Promise<string>{
  if(!id.test(taskId)||!Number.isSafeInteger(revision)||revision<1)
    throw Error('Invalid task revision');
  const api=bridge();
  if(typeof api.runReviewedOwnerReadOnlyPlan!=='function')
    throw Error('Native reviewed plan executor unavailable');
  const result=await api.runReviewedOwnerReadOnlyPlan(taskId,revision);
  if(typeof result!=='string'||!result||result.length>1200)
    throw Error('Unable to verify native reviewed plan outcome');
  return result;
}


/** Never delete the durable task ID, approvals, events or no-replay tombstones. */
export async function clearCancelledOwnerTaskDetails(taskId:string):Promise<boolean>{
  if(!id.test(taskId))throw Error('Invalid task identity');
  const api=bridge();
  if(typeof api.clearCancelledOwnerTaskDetails!=='function')
    throw Error('Update Nexa to clear encrypted task details');
  return (await api.clearCancelledOwnerTaskDetails(taskId))===true;
}


/** A deliberate owner-only continuation of still-unclaimed native read steps.
 * Already claimed or uncertain outcomes can never be retried through this.
 */
export async function continueOwnerReviewedReadOnlyPlan(
  taskId:string,revision:number
):Promise<string>{
  if(!id.test(taskId)||!Number.isSafeInteger(revision)||revision<1)
    throw Error('Invalid task continuation revision');
  const api=bridge();
  if(typeof api.continueReviewedOwnerReadOnlyPlan!=='function')
    throw Error('Update Nexa before continuing a native task');
  const result=await api.continueReviewedOwnerReadOnlyPlan(taskId,revision);
  if(typeof result!=='string'||!result||result.length>1200)
    throw Error('Unable to verify native task continuation result');
  return result;
}
