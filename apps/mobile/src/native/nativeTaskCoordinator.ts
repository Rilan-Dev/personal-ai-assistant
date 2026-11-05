import {NativeModules,Platform} from 'react-native';

const battery=new Set([
  'battery','battery status','battery level','battery percentage',
  'check battery','check my battery','what is my battery',
  'what is my battery percentage','how much battery do i have',
  'how much battery is left',"what's my battery percentage"
]);
const storage=new Set([
  'storage','phone storage','storage status','device storage',
  'check storage','check my storage','how much storage do i have',
  'how much storage is free','how much storage is left',
  'what is my storage','what is my phone storage'
]);
/** Exact natural-language owner intent; never a provider-supplied function call. */
export function ownerDeviceFactKind(value:unknown):'battery'|'storage'|null {
  if(typeof value!=='string'||value.length<3||value.length>150||
    Array.from(value).some(char=>char.charCodeAt(0)<32))return null;
  const normalized=value.trim().toLowerCase().replace(/^hey nexa(?:,|\s)+/,'')
    .replace(/[.?!,]+$/,'').trim();
  return battery.has(normalized)?'battery':storage.has(normalized)?'storage':null;
}
/** Exact owner request for non-sensitive device diagnostics. A model tool
 * selection cannot broaden this grammar into screen or application controls.
 */
export function ownerDeviceStatusIntent(value:unknown):boolean{
  if(typeof value!=='string'||value.length<5||value.length>150||
    Array.from(value).some(char=>char.charCodeAt(0)<32))return false;
  const normalized=value.trim().toLowerCase()
    .replace(/^hey nexa(?:,|\s)+/,'').replace(/[.?!,]+$/,'').trim();
  return ['device status','check device status','phone status',
    'check my phone status'].includes(normalized);
}

type TaskNativeBridge={
  runOwnerDeviceRead(request:string):Promise<string>;
  runOwnerReadOnlyPlan(request:string):Promise<string>;
  recentNativeTaskTimeline(limit:number):Promise<string>;
  recentUnifiedTaskTimeline(limit:number):Promise<string>;
  redactTerminalLegacyVoiceTask?(taskId:string):Promise<boolean>;
  revokeNativeTask(taskId:string):Promise<boolean>;
};
export const nativeTaskCoordinatorAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAConversationHistory?.runOwnerDeviceRead==='function';

/**
 * Native Kotlin owns creation, mandate check, pre-claim, Android observation,
 * evidence and final status. JavaScript cannot select task IDs or statuses.
 */
export async function runOwnerDeviceRead(request:string):Promise<string>{
  if(!ownerDeviceFactKind(request))throw Error('Unsupported device reading');
  if(!nativeTaskCoordinatorAvailable())
    throw Error('Update Nexa to use the on-device task coordinator');
  const bridge=NativeModules.PAAConversationHistory as TaskNativeBridge;
  const output=await bridge.runOwnerDeviceRead(request);
  if(typeof output!=='string'||!output.trim()||output.length>600)
    throw Error('Android returned no verifiable task result');
  return output;
}


/** Only non-sensitive, owner-visible task metadata leaves the native coordinator. */
export type NativeTaskSummary={
  taskId:string;
  stepId:string;
  ordinal:number;
  origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  status:'ready'|'running'|'completed'|'cancelled'|'uncertain';
  revision:number;
  updatedAt:number;
  operation:'get_battery_level'|'get_storage_usage';
  stepStatus:'pending'|'outcome_unknown'|'verified'|'cancelled'|'uncertain';
  observation:'none'|'verified';
};
const nativeId=/^task-[0-9a-f-]{36}$/;
const nativeStepId=/^step-[0-9a-f-]{36}$/;
const validSummary=(input:unknown):input is NativeTaskSummary=>{
  if(!input||typeof input!=='object'||Array.isArray(input))return false;
  const x=input as Record<string,unknown>;
  return typeof x.taskId==='string'&&nativeId.test(x.taskId)&&
    typeof x.stepId==='string'&&nativeStepId.test(x.stepId)&&
    Number.isSafeInteger(x.ordinal)&&Number(x.ordinal)>=0&&Number(x.ordinal)<4&&
    ['chat','live-voice','assistant-role','overlay'].includes(String(x.origin))&&
    ['ready','running','completed','cancelled','uncertain'].includes(String(x.status))&&
    Number.isSafeInteger(x.revision)&&Number(x.revision)>0&&
    Number.isSafeInteger(x.updatedAt)&&Number(x.updatedAt)>0&&
    ['get_battery_level','get_storage_usage'].includes(String(x.operation))&&
    ['pending','outcome_unknown','verified','cancelled','uncertain'].includes(String(x.stepStatus))&&
    ['none','verified'].includes(String(x.observation));
};
export async function listNativeTaskTimeline():Promise<NativeTaskSummary[]>{
  if(!nativeTaskCoordinatorAvailable()||
      typeof NativeModules.PAAConversationHistory?.recentNativeTaskTimeline!=='function')
    return [];
  const response=await (NativeModules.PAAConversationHistory as TaskNativeBridge).recentNativeTaskTimeline(25);
  if(typeof response!=='string'||response.length>40000)
    throw Error('Invalid native task history');
  const rows:unknown=JSON.parse(response);
  if(!Array.isArray(rows)||rows.length>25||rows.some(x=>!validSummary(x)))
    throw Error('Native task history did not match its schema');
  return rows;
}
export async function revokeNativeTask(taskId:string):Promise<boolean>{
  if(!nativeId.test(taskId)||!nativeTaskCoordinatorAvailable()||
    typeof NativeModules.PAAConversationHistory?.revokeNativeTask!=='function')
    throw Error('Native task revocation unavailable');
  return (await (NativeModules.PAAConversationHistory as TaskNativeBridge).revokeNativeTask(taskId))===true;
}


/** Two independent read-only facts may share one bounded durable task ID. */
export function ownerReadOnlyPlanKinds(value:unknown):Array<'battery'|'storage'>|null{
  if(typeof value!=='string'||value.length<15||value.length>420||
     Array.from(value).some(x=>x.charCodeAt(0)<32))return null;
  const pieces=value.trim().split(/\s+(?:and then|then|and)\s+/i);
  if(pieces.length<2||pieces.length>4)return null;
  const kinds=pieces.map(x=>ownerDeviceFactKind(x));
  if(kinds.some(x=>x===null))return null;
  const confirmed=kinds as Array<'battery'|'storage'>;
  return new Set(confirmed).size===confirmed.length?confirmed:null;
}
export async function runOwnerReadOnlyPlan(request:string):Promise<string>{
  if(!ownerReadOnlyPlanKinds(request))throw Error('Unsupported multi-step device plan');
  const api=NativeModules.PAAConversationHistory as TaskNativeBridge|undefined;
  if(Platform.OS!=='android'||typeof api?.runOwnerReadOnlyPlan!=='function')
    throw Error('Update Nexa to run secure multi-step device plans');
  const result=await api.runOwnerReadOnlyPlan(request);
  if(typeof result!=='string'||result.length<1||result.length>1000)
    throw Error('Native task plan returned no verified outcome');
  return result;
}


/** Redact private text from a terminal Voice cursor in BOTH encrypted stores,
 * while retaining the permanent no-replay evidence and anonymous status.
 * Never changes a running task or authorizes an Android tool.
 */
export async function redactTerminalLegacyVoiceTask(taskId:string):Promise<boolean>{
  if(!nativeId.test(taskId))throw Error('Invalid Voice task identity');
  const api=NativeModules.PAAConversationHistory as TaskNativeBridge|undefined;
  if(Platform.OS!=='android'||typeof api?.redactTerminalLegacyVoiceTask!=='function')
    throw Error('Native Voice privacy controls are unavailable');
  return (await api.redactTerminalLegacyVoiceTask(taskId))===true;
}

/** Canonical plus legacy task metadata. Legacy Voice entries are NOT evidence
 * of delivery, approval, or a task with resumable native execution authority.
 */
export type UnifiedTaskSummary={
  taskId:string;
  source:'native'|'ui_action_audit'|'legacy_voice_checkpoint';
  origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  status:string;
  revision:number;
  updatedAt:number;
  stepCount:number;
  verifiedSteps:number;
  detailsRedacted?:boolean;
};
export async function listUnifiedTaskTimeline():Promise<UnifiedTaskSummary[]>{
  const native=NativeModules.PAAConversationHistory as TaskNativeBridge|undefined;
  if(Platform.OS!=='android'||typeof native?.recentUnifiedTaskTimeline!=='function')
    return [];
  const raw=await native.recentUnifiedTaskTimeline(40);
  if(typeof raw!=='string'||raw.length>50_000)
    throw Error('Task summary response is too large');
  const parsed:unknown=JSON.parse(raw);
  if(!Array.isArray(parsed)||parsed.length>40)throw Error('Invalid task index');
  const valid=(x:unknown):x is UnifiedTaskSummary=>{
    if(!x||typeof x!=='object'||Array.isArray(x))return false;
    const t=x as Record<string,unknown>;
    return typeof t.taskId==='string'&&/^task-[A-Za-z0-9_-]{8,96}$/.test(t.taskId)&&
      (t.source==='native'||t.source==='ui_action_audit'||t.source==='legacy_voice_checkpoint')&&
      ['chat','live-voice','assistant-role','overlay'].includes(String(t.origin))&&
      typeof t.status==='string'&&/^[a-z_]{1,40}$/.test(t.status)&&
      Number.isSafeInteger(t.revision)&&Number(t.revision)>=0&&
      Number.isSafeInteger(t.updatedAt)&&Number(t.updatedAt)>0&&
      Number.isSafeInteger(t.stepCount)&&Number(t.stepCount)>=0&&
      Number(t.stepCount)<=8&&Number.isSafeInteger(t.verifiedSteps)&&
      Number(t.verifiedSteps)>=0&&Number(t.verifiedSteps)<=Number(t.stepCount)&&
      (t.detailsRedacted===undefined||typeof t.detailsRedacted==='boolean');
  };
  if(parsed.some(x=>!valid(x)))throw Error('Unrecognized task index entry');
  return parsed;
}
