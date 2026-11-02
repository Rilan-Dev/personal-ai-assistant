import type {Tool,Planner} from './runtime.ts';
import type {ToolInput} from '../../agent-contracts/src/index.ts';
import type {ScreenIconSnapshot} from '../../agent-contracts/src/screen-icons.ts';
import {inspectGuidedScreenPlan} from './screen-confidence.ts';
import type {createOwnerScreenGrant} from './screen-grounding.ts';

type Grant=ReturnType<typeof createOwnerScreenGrant>;
type LocalAnswer={status:string;answer:string;elementIds:string[]};
type Resolver=(snapshot:ScreenIconSnapshot,question:string,now:number)=>LocalAnswer;
const exact=(input:unknown,key:string):input is ToolInput=>
 !!input&&typeof input==='object'&&!Array.isArray(input)&&
 Object.keys(input).length===1&&
 typeof (input as Record<string,unknown>)[key]==='string'&&
 (input as Record<string,string>)[key].length<=100;
const permittedQuestion=(question:string):boolean=>
 /^(?:where is|where's|find|locate|show me) (?:the |my )?(?:search|menu|more options|back|settings|close|call|send|delete|camera|share|attach|microphone|video call)(?: icon| button| control)?\??$/i.test(question.trim())||
 /^(?:what icons are on (?:my |the )?screen|list (?:visible )?icons)\??$/i.test(question.trim());
const navigationTemplates=new Set([
 'find search in menu','open search from menu','open settings from menu',
 'find settings in menu','open menu','find search','open settings'
]);
/**
 * Register only in an owner-visible app. Calls are blocked until an explicit,
 * in-memory metadata release exists; there is no general screen permission
 * bypass, screenshot transfer, native click, approval token or tap coordinate.
 */
export function createReadOnlyScreenTools(
 grant:Grant,resolve:Resolver,clock:()=>number=Date.now
):Tool[]{
 const observe:Tool={
  id:'screen.observe',description:'Read a short-lived, owner-released icon snapshot; no Android action.',
  capabilities:[],risk:'low',hasExternalSideEffect:false,
  validateInput:(input:unknown):input is ToolInput=>
   exact(input,'question')&&permittedQuestion(input.question as string),
  execute:async(input)=>{
   if(!observe.validateInput(input))return {status:'blocked',reason:'Unsupported read-only screen question'};
   const now=clock(),snap=grant.read(now);
   if(!snap)return {status:'blocked',reason:'Owner screen release missing or expired'};
   const response=resolve(snap,input.question as string,now);
   return {status:'success',data:{answer:response.answer,status:response.status,snapshotId:snap.snapshotId,actionEligible:false}};
  }
 };
 const suggest:Tool={
  id:'screen.suggest_navigation',description:'Suggest an owner-confirmed non-executable menu path.',
  capabilities:[],risk:'low',hasExternalSideEffect:false,
  validateInput:(input:unknown):input is ToolInput=>
   exact(input,'request')&&navigationTemplates.has((input.request as string).toLowerCase().trim()),
  execute:async(input)=>{
   if(!suggest.validateInput(input))return {status:'blocked',reason:'Unsupported navigation suggestion'};
   const now=clock(),snap=grant.read(now);
   if(!snap)return {status:'blocked',reason:'Owner screen release missing or expired'};
   const proposal=inspectGuidedScreenPlan(input.request as string,snap,now);
   return {status:'success',data:{...proposal,snapshotId:snap.snapshotId,actionEligible:false,answer:proposal.message}};
  }
 };
 return [observe,suggest];
}
/** Deterministic owner-facing tool routing: the LLM cannot invent actions. */
export function createScreenAwarePlanner(
 base:Planner,grant:Grant,resolve:Resolver,clock:()=>number=Date.now
):Planner{
 return {next:async(request,observations,signal)=>{
  const normalized=request.trim().toLowerCase().replace(/[?.!]+$/,'');
  const plan=navigationTemplates.has(normalized);
  const locate=permittedQuestion(request);
  if(!plan&&!locate)return base.next(request,observations,signal);
  if(observations.length){
   const last=observations[observations.length-1];
   if(!['screen.observe','screen.suggest_navigation'].includes(last.call.toolId))
    return base.next(request,observations,signal);
   // Explicit owner release is rechecked BEFORE returning an observation.
   // Do not replay another question's result into this request or continue
   // after revocation/backgrounding/expiry.
   const expectedTool=plan?'screen.suggest_navigation':'screen.observe';
   const expectedInput=plan?{request:normalized}:{question:request};
   const current=grant.read(clock());
   const data=last.outcome.status==='success'?last.outcome.data:null;
   if(observations.length!==1 || !current ||
      last.call.toolId!==expectedTool ||
      JSON.stringify(last.call.input)!==JSON.stringify(expectedInput) ||
      !data || typeof data!=='object' || !('snapshotId' in data) ||
      data.snapshotId!==current.snapshotId)
    return {type:'clarify',question:'Screen consent expired or observation context changed. Scan and release the current screen again.'};
   const message=data&&typeof data==='object'&&'answer' in data&&typeof data.answer==='string'
    ?data.answer:'Screen observation was unavailable. No action was taken.';
   return {type:'done',response:message};
  }
  if(!grant.read(clock()))return {type:'clarify',
   question:'Open Nexa Tools, scan icons and explicitly release the short-lived icon metadata to the assistant.'};
  return {type:'tool',call:{
   callId:plan?'screen-guide-1':'screen-observe-1',
   toolId:plan?'screen.suggest_navigation':'screen.observe',
   input:plan?{request:normalized}:{question:request}
  }};
 }};
}
