import type {ScreenIconSnapshot,IconSemantic,IconRect} from '../../agent-contracts/src/screen-icons.ts';
import type {IconVisionHint} from './icon-vision.ts';
import {groundVisualIcon,proposeNavigationSteps} from './screen-grounding.ts';

type Preview={snapshotId:string;packageName:string;windowId?:number;capturedAtWallMs:number;
 expiresInMs:number;expiresAtWallMs?:number;crops:{iconId:string;boundsPx:IconRect}[]};

/** Heuristic evidence index, NOT a calibrated probability of model correctness. */
export type IconEvidenceAssessment={
 status:'blocked'|'tentative'|'corroborated';
 score:number;
 modelConfidenceProbability:null;
 executable:false;
 matchedIconId?:string;
 reasons:string[];
};
const semantics:Readonly<Record<string,IconSemantic>>=Object.freeze({
 search:'search',menu:'menu',settings:'settings',back:'back',close:'close',
 call:'call',send:'send',delete:'delete',camera:'camera',share:'share',
 attach:'attach',microphone:'microphone','more options':'more_options',
 'video call':'video_call'
});
const expiry=(s:{capturedAtWallMs:number;expiresInMs:number;expiresAtWallMs?:number})=>
 s.expiresAtWallMs??s.capturedAtWallMs+s.expiresInMs;
/** Read-only estimate of how much independent metadata supports a vision hint. */
export function scoreIconEvidence(
 snapshot:ScreenIconSnapshot,view:Preview,hint:IconVisionHint,cropId:string,now:number
):IconEvidenceAssessment{
 const grounded=groundVisualIcon(snapshot,view,hint,cropId,now);
 if(grounded.status!=='geometry_matched'||!grounded.matchedIconId)
  return {status:'blocked',score:0,modelConfidenceProbability:null,executable:false,
   reasons:['Grounding refused: '+grounded.status]};
 const icon=snapshot.icons.find(i=>i.id===grounded.matchedIconId);
 const guessed=semantics[hint.label.trim().toLowerCase()];
 if(!icon||!guessed||!icon.clickable&&icon.semantic==='unknown')
  return {status:'blocked',score:0,modelConfidenceProbability:null,executable:false,
   reasons:['Missing or unsupported accessibility evidence']};
 const corroborated=icon.semantic===guessed;
 const reasons=[corroborated
  ?'Accessibility label independently agrees with the visual description'
  :'Only geometry agrees; accessibility label is unknown'];
 let score=corroborated?55:31;
 if(snapshot.windowId!==undefined&&snapshot.windowId===view.windowId){
  score+=15;reasons.push('Same Android window ID');
 }else{reasons.push('No trustworthy shared Android window ID');}
 if(Math.abs(snapshot.capturedAtWallMs-view.capturedAtWallMs)<=10000){
  score+=7;reasons.push('Crops and metadata captured close together');
 }
 if(Math.min(expiry(snapshot),expiry(view))-now>=5000){
  score+=5;reasons.push('Both observations retain at least five seconds of lifetime');
 }
 score=Math.min(score,corroborated?85:55);
 return {status:corroborated?'corroborated':'tentative',
  score,modelConfidenceProbability:null,executable:false,
  matchedIconId:icon.id,reasons};
}

export type GuidedScreenPreview={
 status:'ready_to_review'|'needs_new_scan'|'ambiguous'|'expired'|'unsupported';
 executable:false;nextSemantic?:string;
 steps:readonly {semantic:string;freshInspectionRequired:true;ownerConfirmationRequired:true}[];
 message:string
};
/** No auto-execution: first-step evidence is advisory and must be refreshed before tapping. */
export function inspectGuidedScreenPlan(
 request:string,snapshot:ScreenIconSnapshot,now:number
):GuidedScreenPreview{
 const plan=proposeNavigationSteps(request,snapshot,now);
 if(plan.status!=='suggested'){
  return {status:plan.status,executable:false,steps:[],message:plan.message};
 }
 const next=plan.steps[0]?.semantic;
 const target=next==='menu'?['menu','more_options']:[next];
 const matches=snapshot.icons.filter(i=>i.clickable&&target.includes(i.semantic));
 const steps=plan.steps.map(s=>({...s}));
 if(matches.length===0)return {status:'needs_new_scan',executable:false,
  steps,nextSemantic:next,
  message:'The first navigation control was not identified. Inspect the current app again.'};
 if(matches.length>1)return {status:'ambiguous',executable:false,steps,nextSemantic:next,
  message:'Multiple matching first-step controls. Review the live screen manually.'};
 return {status:'ready_to_review',executable:false,steps,nextSemantic:next,
  message:'First step is visible. Review it in the Nexa floating Tools panel; every step requires a new inspection and owner confirmation.'};
}
