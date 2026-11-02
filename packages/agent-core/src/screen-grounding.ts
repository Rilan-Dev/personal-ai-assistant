import type {ScreenIconSnapshot, IconRect} from '../../agent-contracts/src/screen-icons.ts';
type VisualIconPreview={snapshotId:string;packageName:string;capturedAtWallMs:number;expiresInMs:number;crops:{iconId:string;boundsPx:IconRect}[]};
import type {IconVisionHint} from './icon-vision.ts';

export type GroundingStatus='geometry_matched'|'source_mismatch'|'expired'|'ambiguous'|'conflicting'|'unknown'|'unmatched';
export type GroundingResult={
 status:GroundingStatus; evidenceStrength:'limited'|'none'; actionEligible:false;
 matchedIconId?:string; description:string
};
type ReadOnlyPlanStep={semantic:'menu'|'search'|'settings';freshInspectionRequired:true;ownerConfirmationRequired:true};
export type ReadOnlyNavigationPlan={status:'suggested'|'unsupported'|'expired';executable:false;steps:ReadOnlyPlanStep[];message:string};
const semantics:Readonly<Record<string,string>>=Object.freeze({
 search:'search',menu:'menu',settings:'settings',back:'back',close:'close',
 send:'send',delete:'delete',call:'call',camera:'camera',share:'share',
 attach:'attach','more options':'more_options',microphone:'microphone'
});
const validTime=(created:number,ttl:number,now:number)=>
 Number.isSafeInteger(created)&&Number.isFinite(now)&&Number.isInteger(ttl)&&
 ttl>0&&ttl<=60000&&now>=created&&now<created+ttl;
const validRect=(r:IconRect)=>[r.left,r.top,r.right,r.bottom]
 .every(n=>Number.isInteger(n)&&n>=0&&n<=20000)&&r.right>r.left&&r.bottom>r.top;
const overlap=(a:IconRect,b:IconRect)=>{
 const width=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left));
 const height=Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
 return width*height/Math.max(1,(a.right-a.left)*(a.bottom-a.top));
};
const result=(status:GroundingStatus,matchedIconId?:string):GroundingResult=>({
 status,evidenceStrength:status==='geometry_matched'?'limited':'none',actionEligible:false,
 ...(matchedIconId?{matchedIconId}:{}),
 description:status==='geometry_matched'
 ?'A visual guess overlaps one observed icon. Identity is still unverified; no action is authorized.'
 :'No verified icon target. No action is authorized.'
});
/**
 * Cross-checks SAME-APP, SAME-RECTANGLE, fresh owner-armed observations.
 * The geometry association is a *hint*, not a calibrated visual confidence
 * score, authorization, or evidence that Android can click the control.
 */
export function groundVisualIcon(
 snapshot:ScreenIconSnapshot,visual:VisualIconPreview,hint:IconVisionHint,cropId:string,now:number
):GroundingResult{
 if(!validTime(snapshot.capturedAtWallMs,snapshot.expiresInMs,now)||
    !validTime(visual.capturedAtWallMs,visual.expiresInMs,now)||
    Math.abs(snapshot.capturedAtWallMs-visual.capturedAtWallMs)>30000)return result('expired');
 if(snapshot.packageName!==visual.packageName ||
    !/^[a-zA-Z][a-zA-Z0-9._]{2,149}$/.test(snapshot.packageName))return result('source_mismatch');
 const crop=visual.crops.filter(x=>x.iconId===cropId);
 if(crop.length!==1||!validRect(crop[0].boundsPx))return result('unmatched');
 const semantic=semantics[hint.label?.trim().toLowerCase()]||'unknown';
 if(semantic==='unknown'||hint.verified!==false)return result('unknown');
 const matches=snapshot.icons.filter(i=>validRect(i.boundsPx)&&
    overlap(crop[0].boundsPx,i.boundsPx)>=0.85&&
    overlap(i.boundsPx,crop[0].boundsPx)>=0.85);
 if(matches.length>1)return result('ambiguous');
 if(matches.length===0)return result('unmatched');
 const match=matches[0];
 if(match.semantic!=='unknown'&&match.semantic!==semantic)return result('conflicting');
 return result('geometry_matched',match.id);
}
/** Owner release exposes ONLY an existing whitelisted icon snapshot, for at most 20s. */
export function createOwnerScreenGrant(){
 let released:ScreenIconSnapshot|null=null;
 let until=0;
 return {
  release(snapshot:ScreenIconSnapshot,now:number):boolean{
   released=null;until=0;
   if(!validTime(snapshot.capturedAtWallMs,snapshot.expiresInMs,now)||
      snapshot.icons.length>64||snapshot.version!==1)return false;
   const remaining=Math.min(20000,snapshot.capturedAtWallMs+snapshot.expiresInMs-now);
   if(remaining<=0)return false;
   released={...snapshot,icons:snapshot.icons.map(i=>({...i,boundsPx:{...i.boundsPx}})),
    ...(snapshot.windowBoundsPx?{windowBoundsPx:{...snapshot.windowBoundsPx}}:{})};
   until=now+remaining;return true;
  },
  read(now:number):ScreenIconSnapshot|null{
   if(!released||now>=until||!validTime(released.capturedAtWallMs,released.expiresInMs,now)){
    released=null;return null;
   }
   return released;
  },
  revoke():void{released=null;until=0;}
 };
}
/** Fixed suggestion only; never a native action or model-generated tap sequence. */
export function proposeNavigationSteps(
 request:string,snapshot:ScreenIconSnapshot,now:number
):ReadOnlyNavigationPlan{
 const none=(status:ReadOnlyNavigationPlan['status'],message:string):ReadOnlyNavigationPlan=>
  ({status,executable:false,steps:[],message});
 if(!validTime(snapshot.capturedAtWallMs,snapshot.expiresInMs,now))
  return none('expired','Screen observation expired; request a new one.');
 const templates:Record<string,Array<'menu'|'search'|'settings'>>=Object.freeze({
  'find search in menu':['menu','search'],
  'open search from menu':['menu','search'],
  'open settings from menu':['menu','settings'],
  'find settings in menu':['menu','settings'],
  'open menu':['menu'],'find search':['search'],'open settings':['settings']
 });
 const selected=templates[request.toLowerCase().trim().replace(/[.?!]+$/,'')];
 if(!selected)return none('unsupported','Only bounded, non-destructive navigation suggestions are available.');
 return {status:'suggested',executable:false,
  steps:selected.map(semantic=>({semantic,freshInspectionRequired:true,ownerConfirmationRequired:true})),
  message:'Suggestion only. Open floating Nexa Tools to inspect and approve each step separately.'};
}
