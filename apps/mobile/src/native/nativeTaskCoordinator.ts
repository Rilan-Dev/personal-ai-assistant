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
type TaskNativeBridge={runOwnerDeviceRead(request:string):Promise<string>};
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
