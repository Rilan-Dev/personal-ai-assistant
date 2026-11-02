import {NativeModules, Platform} from 'react-native';
import type {IconRect} from '@paa/agent-contracts';

export type VisualIconCrop = {
  iconId: string;
  boundsPx: IconRect;
  dataUri: string; // local, temporary PNG thumbnail, NOT a full screenshot
};
export type VisualIconPreview = {
  snapshotId: string;
  packageName: string;
  windowId?: number;
  capturedAtWallMs: number;
  expiresInMs: number;
  expiresAtWallMs?: number;
  crops: VisualIconCrop[];
};
interface NativeVisualBridge {
  armVisualScan(): Promise<boolean>;
  takeVisualScan(): Promise<unknown>;
  clearVisualScan(): Promise<boolean>;
}
const bridge=(): NativeVisualBridge=>{
  const m=NativeModules.PAAScreenAssist;
  if(Platform.OS!=='android'||typeof m?.armVisualScan!=='function' ||
      typeof m?.takeVisualScan!=='function'||typeof m?.clearVisualScan!=='function')
    throw Error('Visual icon capture unavailable');
  return m as NativeVisualBridge;
};
export const visualIconCaptureAvailable=()=>
  Platform.OS==='android' && typeof NativeModules.PAAScreenAssist?.armVisualScan==='function';
export const armVisualIcons=()=>bridge().armVisualScan();
export const discardVisualIcons=()=>bridge().clearVisualScan();
/** Validate native data before it can appear in React Native. No provider transfer. */
export async function takeVisualIcons(): Promise<VisualIconPreview|null> {
  const v=await bridge().takeVisualScan();
  if(!v || typeof v!=='object')return null;
  const src=v as Record<string,unknown>;
  if(typeof src.snapshotId!=='string'||src.snapshotId.length>80 ||
    typeof src.packageName!=='string'||src.packageName.length>150 ||
    (src.windowId!==undefined && (!Number.isInteger(src.windowId)||
       (src.windowId as number)<0||(src.windowId as number)>2147483647)) ||
    !Number.isSafeInteger(src.capturedAtWallMs) ||
    !Number.isInteger(src.expiresInMs)||(src.expiresInMs as number)<=0||
    (src.expiresInMs as number)>60000 ||
    !Array.isArray(src.crops)||src.crops.length>6) return null;
  const crops:VisualIconCrop[]=[];
  for(const crop of src.crops){
    if(!crop || typeof crop!=='object')return null;
    const item=crop as Record<string,unknown>;
    const b=item.boundsPx as Record<string,unknown>|undefined;
    if(typeof item.iconId!=='string'||item.iconId.length>40 ||
      typeof item.dataUri!=='string'||item.dataUri.length>66000||
      !/^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(item.dataUri) ||
      !b || !['left','top','right','bottom'].every(k=>
         Number.isInteger(b[k]) && (b[k] as number)>=0 && (b[k] as number)<=20000) ||
      (b.right as number)<=(b.left as number)||(b.bottom as number)<=(b.top as number))
      return null;
    crops.push({iconId:item.iconId,dataUri:item.dataUri,boundsPx:{
      left:b.left as number,top:b.top as number,right:b.right as number,bottom:b.bottom as number}});
  }
  return {snapshotId:src.snapshotId,packageName:src.packageName,
    ...(src.windowId!==undefined?{windowId:src.windowId as number}:{}),
    capturedAtWallMs:src.capturedAtWallMs as number,expiresInMs:src.expiresInMs as number,
    expiresAtWallMs:Math.min((src.capturedAtWallMs as number)+60000,Date.now()+(src.expiresInMs as number)),crops};
}
