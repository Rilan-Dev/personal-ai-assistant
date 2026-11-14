import type {DurableActionJournal} from "./journal.ts";
import type {ToolCall} from "../../agent-contracts/src/index.ts";
import {requestAllowsNativeTool} from "../../agent-contracts/src/intent-policy.ts";
import {parseOwnerMultiToolRequest} from "./owner-sequence.ts";

/** Safe navigation is the ONLY Android tool class eligible for an
 * independently user-selected "always allow" setting. It cannot grant
 * Accessibility, microphone, contacts, calls or message-send authority.
 */
export type TrustedNavigationOperation =
  | "open"
  | "scroll_screen:up"|"scroll_screen:down"
  | "scroll_screen:left"|"scroll_screen:right";
type Decision = (command:TrustedNavigationOperation)=>Promise<boolean>;

const directScroll=/^(?:please\s+)?(?:scroll|swipe) (up|down|left|right)(?: on (?:the|this) (?:current )?(?:home )?screen)?[.!?]*$/i;
function exactOwnerStep(request:string,call:ToolCall):TrustedNavigationOperation|null {
  const args=call.input;
  if(!args || typeof args!=="object" || Array.isArray(args))return null;
  const expected=requestAllowsNativeTool(request,call.toolId,args)
    ? true : directScroll.test(request.trim()) &&
        call.toolId==="screen.scroll" &&
        Object.keys(args).length===1 &&
        args.direction===request.trim().match(directScroll)?.[1]?.toLowerCase();
  const plan=parseOwnerMultiToolRequest(request);
  const inPlan=Array.isArray(plan) && plan.some(step=>
    step.callId===call.callId && step.toolId===call.toolId &&
    JSON.stringify(step.input)===JSON.stringify(args));
  if(!expected && !inPlan)return null;
  if(call.toolId==="device.open_app" &&
      typeof args.app==="string" && Object.keys(args).length===1)
    return "open";
  if(call.toolId==="screen.scroll" && typeof args.direction==="string" &&
      Object.keys(args).length===1 &&
      ["up","down","left","right"].includes(args.direction))
    return `scroll_screen:${args.direction}` as TrustedNavigationOperation;
  return null;
}
function extractCall(sessionId:string,callId:string,fingerprint:string):ToolCall|null {
  try {
    const decoded:unknown=JSON.parse(fingerprint);
    if(!Array.isArray(decoded)||decoded.length!==4||
       decoded[0]!==sessionId||decoded[1]!==callId||
       typeof decoded[2]!=="string"||
       !decoded[3]||typeof decoded[3]!=="object"||Array.isArray(decoded[3]))return null;
    return {callId,toolId:decoded[2],input:decoded[3] as Record<string,unknown>};
  }catch{return null;}
}
/** Wrap existing encrypted journal. Always prepare and claim the same
 * fingerprint. An OS grant / owner Pause denial keeps the existing review
 * flow; a failure never becomes a grant or causes a replay.
 */
export function withTrustedOwnerNavigation(
  journal:DurableActionJournal,ownerRequest:string,mayTrust:Decision,
  active:boolean
):DurableActionJournal {
  return {
    ...journal,
    async prepareApproval(sessionId,callId,fingerprint){
      const prepared=await journal.prepareApproval(sessionId,callId,fingerprint);
      if(!prepared)return false;
      if(!active)return true;
      const call=extractCall(sessionId,callId,fingerprint);
      const cmd=call&&exactOwnerStep(ownerRequest,call);
      if(cmd){
        const allowed=await mayTrust(cmd).catch(()=>false);
        if(allowed) {
          // This recorded, single-use approval is for THIS fingerprint only.
          // Native current-app, owner Pause and OS checks still apply before
          // the final Android Intent/Accessibility dispatch.
          try {await journal.approveAction(sessionId,callId,fingerprint);}
          catch {/* Continue with visible owner review, not a silent success. */}
        }
      }
      return true;
    }
  };
}
