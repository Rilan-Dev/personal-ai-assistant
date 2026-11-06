import type {ToolCall, ToolOutcome} from "../../agent-contracts/src/index.ts";
import {parseNativeOwnerIntent} from "../../agent-contracts/src/intent-policy.ts";
import {parseReviewedTextEntry} from "./text-entry-intent.ts";
import type {Planner} from "./runtime.ts";

/**
 * Trust boundary: this grammar parses the OWNER'S literal request before
 * consulting any provider. Tool IDs, arguments and order cannot come from
 * an app's screen contents or untrusted LLM replies. No implicit Send/Submit.
 * One step has one durable AgentHarness action ID and one owner review.
 */
const maxSteps=6;
const forbiddenTap=/\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|pin|otp|logout|sign out|payment|bank|security|verify)\b/i;
const recipientHint=(v:string):boolean=>
  v.length>=2&&v.length<=50&&v.trim()===v&&
  /^[\p{L}][\p{L}\p{N} '-]{1,49}$/u.test(v)&&
  !/\b(?:and|then|send|submit|pay|buy|call|delete|grant|allow|confirm|password|otp)\b/i.test(v);
const safeLabel=(v:string):boolean=>
  v.length>=2&&v.length<=45&&v.trim()===v&&
  /^[\p{L}\p{N}][\p{L}\p{N} _.-]*$/u.test(v)&&!forbiddenTap.test(v);

/** Split only at unquoted "then"/"and then"; quoted message text stays opaque. */
function clauses(raw:string):string[]|null {
  if(raw.length<18||raw.length>850||/[\x00-\x1f]/.test(raw))return null;
  const request=raw.trim().replace(/^hey\s+nexa[,!]?\s+/i,"");
  // "and tap"/"and scroll" are explicit commands. Do not split
  // Chrome's legitimate "open Chrome and search" intent.
  const separator=/\s+(?:(?:and\s+)?then|and(?=\s+(?:tap|click|press|scroll|swipe|type|enter|write|insert|check|show|open|launch|start|turn|switch)))\s+/gi;
  const pieces:string[]=[];
  let start=0,quote:string|null=null;
  for(let i=0;i<request.length;i++){
    const ch=request[i];
    const contraction=ch==="'"&&/[\p{L}\p{N}]/u.test(request[i-1]??"")&&
      /[\p{L}\p{N}]/u.test(request[i+1]??"");
    if(!contraction&&(ch==="\""||ch==="“"||ch==="”"||ch==="'")){
      if(quote===null)quote=ch==="“"?"”":ch;
      else if(ch===quote)quote=null;
      continue;
    }
    if(quote!==null)continue;
    separator.lastIndex=i;
    const match=separator.exec(request);
    if(match?.index===i){
      pieces.push(request.slice(start,i).trim());
      i+=match[0].length-1;
      start=i+1;
    }
  }
  if(quote!==null)return null;
  pieces.push(request.slice(start).trim());
  return pieces.length>=2&&pieces.length<=maxSteps&&pieces.every(x=>x.length>=3)?pieces:null;
}

function parseClause(raw:string):Pick<ToolCall,"toolId"|"input">|null {
  const original=raw.replace(/^please\s+/i,"").trim();
  const text=original.replace(/[.!?]+$/,"").trim();
  const chat=text.match(/^(?:open|show) (?:the )?(?:whatsapp )?chat with ([\p{L}][\p{L}\p{N} '-]{1,49})$/iu);
  if(chat&&recipientHint(chat[1].trim()))
    return {toolId:"screen.open_whatsapp_chat",input:{recipientHint:chat[1].trim()}};
  const native=parseNativeOwnerIntent(text);
  if(native)return native;
  const storage=text.match(/^(?:check|show|read|what is|what's) (?:my |the )?(?:phone |device )?storage(?: status| space| usage)?$/i);
  if(storage)return {toolId:"device.storage_status",input:{}};
  if(/^(?:inspect|show|list) (?:the |my )?(?:visible )?(?:screen )?controls$/i.test(text))
    return {toolId:"device.visible_controls",input:{}};
  const scroll=text.match(/^(?:scroll|swipe) (up|down|left|right)(?: on (?:the|this) screen)?$/i);
  if(scroll)return {toolId:"screen.scroll",input:{direction:scroll[1].toLowerCase()}};
  const tap=text.match(/^(?:tap|click|press) (?:on )?(?:the )?([\p{L}\p{N} _.-]{2,45})$/iu);
  if(tap&&safeLabel(tap[1].trim()))
    return {toolId:"screen.tap_label",input:{label:tap[1].trim()}};
  const compose=original.match(/^(?:prepare|compose|draft) (?:a )?whatsapp message (?:for|to) ([\p{L}][\p{L}\p{N} '-]{1,49}) (?:saying|that says) ["“]([^"”\r\n]{1,500})["”]$/iu);
  if(compose&&recipientHint(compose[1].trim())&&compose[2].trim())
    return {toolId:"screen.compose_whatsapp",input:{
      recipientHint:compose[1].trim(),exactText:compose[2]
    }};
  const entry=parseReviewedTextEntry(original);
  if(entry&&safeLabel(entry.targetLabel))
    return {toolId:"screen.fill_text",input:{
      targetLabel:entry.targetLabel,exactText:entry.exactText
    }};
  return null;
}

/** No partial plan: if ANY clause is unrecognized, the entire plan is rejected. */
export function parseOwnerMultiToolRequest(raw:unknown):readonly ToolCall[]|null {
  if(typeof raw!=="string")return null;
  const list=clauses(raw);
  if(!list)return null;
  const steps=list.map(parseClause);
  if(steps.some(s=>s===null))return null;
  const bound=steps as Array<Pick<ToolCall,"toolId"|"input">>;
  // App launch alone is not recipient proof; each chat header is checked
  // by native Android before its separately approved draft insertion.
  let launchedWhatsApp=false,verifiedHint:string|null=null;
  for(const step of bound){
    if(step.toolId==="device.open_app")
      launchedWhatsApp=step.input.app==="whatsapp";
    if(step.toolId==="screen.open_whatsapp_chat"){
      if(!launchedWhatsApp)return null;
      verifiedHint=String(step.input.recipientHint);
    }
    if(step.toolId==="screen.compose_whatsapp"){
      if(!verifiedHint||step.input.recipientHint!==verifiedHint)return null;
      verifiedHint=null;
    }
    if(["screen.tap_label","screen.scroll","screen.fill_text"].includes(step.toolId))
      verifiedHint=null;
  }
  return Object.freeze((steps as Array<Pick<ToolCall,"toolId"|"input">>)
    .map((s,i)=>Object.freeze({callId:"owner-seq-"+(i+1),toolId:s.toolId,
      input:Object.freeze({...s.input})})));
}

export type StepEvidence={safe:boolean;reason:string};
/**
 * Evidence is independently produced by Android. Intent dispatch, queued
 * clicks and plausible LLM prose are NEVER enough to advance the sequence.
 */
export function evidenceForOwnerStep(call:ToolCall,outcome:ToolOutcome,
                                     isLast=false):StepEvidence {
  if(outcome.status!=="success")return {safe:false,reason:"Android did not complete the tool"};
  const value=outcome.data;
  if(!value||typeof value!=="object"||Array.isArray(value))
    return {safe:false,reason:"Android returned no structured evidence"};
  const x=value as Record<string,unknown>;
  if(call.toolId==="device.battery_status")
    return {safe:Number.isInteger(x.levelPercent)&&Number(x.levelPercent)>=0&&
        Number(x.levelPercent)<=100&&typeof x.charging==="boolean",
      reason:"Battery reading was not verified"};
  if(call.toolId==="device.storage_status")
    return {safe:typeof x.totalGb==="number"&&Number.isFinite(x.totalGb)&&x.totalGb>0&&
        typeof x.freeGb==="number"&&Number.isFinite(x.freeGb)&&x.freeGb>=0&&
        x.freeGb<=x.totalGb,reason:"Storage reading was not verified"};
  if(call.toolId==="device.status")
    return {safe:x.platform==="android"&&typeof x.accessibilityEnabled==="boolean",
      reason:"Device bridge did not return a valid status"};
  if(call.toolId==="device.visible_controls")
    return {safe:typeof x.observation==="string"&&
       x.observation.startsWith("Visible non-sensitive controls in ")&&
       x.actionPerformed===false,reason:"Visible controls were not observed"};
  if(call.toolId==="device.set_flashlight")
    return {safe:x.verification==="observed"&&typeof x.enabled==="boolean"&&
        x.enabled===call.input.enabled,reason:"Flashlight state was not verified"};
  if(call.toolId==="screen.tap_label"||call.toolId==="screen.scroll")
    return {safe:x.verification==="observed_change"&&x.actionAccepted===true,
      reason:"The screen change was not verified; no next step executed"};
  if(call.toolId==="screen.open_whatsapp_chat")
    return {safe:x.verification==="verified_chat_header" &&
      x.recipientMatched===true&&x.messageComposerVisible===true &&
      x.submitted===false,
      reason:"The exact recipient header and message composer were not verified"};
  if(call.toolId==="screen.compose_whatsapp")
    return {safe:x.verification==="exact_text_visible"&&x.recipientMatched===true&&
      x.submitted===false&&x.sent===false,
      reason:"The approved recipient or draft text was not verified; nothing sent"};
  if(call.toolId==="screen.fill_text")
    return {safe:x.verification==="exact_text_visible"&&x.submitted===false,
      reason:"The exact reviewed text was not visible in the field"};
  if(call.toolId==="device.open_app"||call.toolId==="device.open_camera"||
      call.toolId==="device.browser_search")
    return {safe:x.intentDispatched===true&&
      (x.verification==="destination_package_observed"||
        (isLast&&x.verification==="not_observed")),
      reason:"App destination was not observed. Return to Nexa, inspect the app, then start a new task."};
  if(call.toolId==="device.open_notification_shade")
    return {safe:isLast&&x.notificationShadeAccepted===true,
      reason:"Notification shade visibility was not confirmed"};
  return {safe:false,reason:"Unsupported step evidence"};
}

export function createOwnerSequentialPlanner(fallback:Planner):Planner {
  return {async next(request,observations,signal){
    const steps=parseOwnerMultiToolRequest(request);
    if(!steps)return fallback.next(request,observations,signal);
    if(signal?.aborted)return {type:"clarify",question:"Task was stopped by the owner."};
    if(observations.length>steps.length)
      return {type:"clarify",question:"Unexpected task history. No more actions."};
    for(let i=0;i<observations.length;i++){
      const found=observations[i],expected=steps[i];
      if(found.call.callId!==expected.callId ||
          found.call.toolId!==expected.toolId ||
          JSON.stringify(found.call.input)!==JSON.stringify(expected.input))
        return {type:"clarify",question:"Task step or arguments changed. No further action."};
      const verified=evidenceForOwnerStep(expected,found.outcome,i===steps.length-1);
      if(!verified.safe)
        return {type:"clarify",question:verified.reason};
    }
    if(observations.length===steps.length) {
      const final=observations[observations.length-1];
      const x=final.outcome.status==="success"?final.outcome.data as Record<string,unknown>:null;
      const dispatched=x?.verification==="not_observed";
      return {type:"done",response:dispatched
        ? "All prior tool steps were verified. The final app launch was accepted by Android but its destination remains unverified. Nothing was sent."
        : "Finished "+steps.length+" owner-requested steps with Android evidence. No Send, Submit or payment was performed."};
    }
    return {type:"tool",call:steps[observations.length]};
  }};
}
