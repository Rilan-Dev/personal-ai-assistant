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
type TaskNativeBridge={
  runOwnerDeviceRead(request:string):Promise<string>;
  runOwnerReadOnlyPlan(request:string):Promise<string>;
  recentNativeTaskTimeline(limit:number):Promise<string>;
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
  origin:'chat'|'live-voice';
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
    (x.origin==='chat'||x.origin==='live-voice')&&
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
