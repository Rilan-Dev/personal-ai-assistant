import {parseScreenIconSnapshot} from '../../agent-contracts/src/screen-icons.ts';
import type {ScreenIconSnapshot,IconRect} from '../../agent-contracts/src/screen-icons.ts';

type CropIdentity={iconId:string;boundsPx:IconRect};
type PairedCapture={
 snapshotId:string;packageName:string;windowId:number;
 capturedAtWallMs:number;expiresInMs:number;
 crops:CropIdentity[];observation:unknown
};
const sameRect=(a:IconRect,b:IconRect)=>a.left===b.left&&a.right===b.right&&
 a.top===b.top&&a.bottom===b.bottom;

/**
 * Fail-closed association of a screenshot CROP to the accessibility nodes
 * from the SAME capture event. No image data, icon model guesses, or
 * action tokens are accepted by this function.
 */
export function parsePairedIconObservation(value:unknown):ScreenIconSnapshot|null{
 if(!value||typeof value!=='object'||Array.isArray(value))return null;
 const pair=value as Partial<PairedCapture>;
 if(typeof pair.snapshotId!=='string'||typeof pair.packageName!=='string'||
  !Number.isInteger(pair.windowId)||!Number.isSafeInteger(pair.capturedAtWallMs)||
  !Number.isInteger(pair.expiresInMs)||!Array.isArray(pair.crops)||
  pair.crops.length>6)return null;
 const snapshot=parseScreenIconSnapshot(pair.observation);
 if(!snapshot ||snapshot.snapshotId!==pair.snapshotId ||
    snapshot.packageName!==pair.packageName ||snapshot.windowId!==pair.windowId ||
    snapshot.capturedAtWallMs!==pair.capturedAtWallMs ||
    snapshot.expiresInMs!==pair.expiresInMs)return null;
 const seen=new Set<string>();
 for(const crop of pair.crops){
  if(!crop||typeof crop.iconId!=='string'||seen.has(crop.iconId)||
     !crop.boundsPx)return null;
  seen.add(crop.iconId);
  const exactly=snapshot.icons.filter(i=>sameRect(i.boundsPx,crop.boundsPx));
  if(exactly.length!==1||exactly[0].id!==crop.iconId||
     exactly[0].semantic!=='unknown'||!exactly[0].clickable)return null;
 }
 return snapshot;
}
