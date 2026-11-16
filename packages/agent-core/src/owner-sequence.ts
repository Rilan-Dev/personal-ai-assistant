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
// Several messaging apps expose a long accessibility description for their
// Search field (for example, "Ask Meta AI or Search"). The owner grammar
// reduces those live labels to the single safe Search-field capability; the
// native executor still has to find exactly one current editable Search node.
const isSearchFieldLabel=(value:string):boolean=>/\bsearch$/i.test(value.trim());

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
  const compose=original.match(/^(?:prepare|compose|draft) (?:a |an )?(whatsapp|imo|botim) message (?:for|to) ([\p{L}][\p{L}\p{N} '-]{1,49}) (?:saying|that says) ["“]([^"”\r\n]{1,500})["”]$/iu);
  if(compose&&recipientHint(compose[2].trim())&&compose[3].trim())
    return {toolId:"screen.compose_message",input:{
      appId:compose[1].toLowerCase(),recipientHint:compose[2].trim(),exactText:compose[3]
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
  const steps:Array<Pick<ToolCall,"toolId"|"input">|null>=[];
  // App launch alone is not recipient proof; each chat header is checked
  // by native Android before its separately approved draft insertion. Send
  // is parsed only as a final exact reference to that same reviewed draft.
  let launchedWhatsApp=false,verifiedHint:string|null=null,verifiedText:string|null=null;
  let sent=false;
  let launchedMessageApp:string|null=null;
  let searchOpened=false,searchedContact:string|null=null,selectedMessageContact:string|null=null;
  let reviewedMessage:{appId:string;recipientHint:string;exactText:string}|null=null;
  let messageSent=false;
  for(let index=0;index<list.length;index++){
    const rawClause=list[index];
    const clause=rawClause.replace(/^please\s+/i,"").replace(/[.!?]+$/i,"").trim();
    let step:Pick<ToolCall,"toolId"|"input">|null=parseClause(rawClause);
    // Preserve the existing verified WhatsApp-chat route when the owner used
    // its explicit chat-opening command; search-based flows use the generic
    // app-bound composer below.
    if(step?.toolId==="screen.compose_message"&&
        step.input.appId==="whatsapp"&&launchedWhatsApp&&verifiedHint&&
        step.input.recipientHint===verifiedHint&&verifiedText===null){
      step={toolId:"screen.compose_whatsapp",input:{
        recipientHint:String(step.input.recipientHint),exactText:String(step.input.exactText)
      }};
    }
    if(!step&&/^(?:send (?:it|the message|this message))$/i.test(clause)&&
        index===list.length-1){
      if(verifiedHint&&verifiedText&&!sent){
        step={toolId:"screen.send_whatsapp",input:{
          recipientHint:verifiedHint,exactText:verifiedText
        }};
        sent=true;
      }else if(reviewedMessage&&!messageSent){
        step={toolId:"screen.send_message",input:{
          appId:reviewedMessage.appId,
          recipientHint:reviewedMessage.recipientHint,
          exactText:reviewedMessage.exactText
        }};
      }
    }
    if(!step)return null;
    if(launchedMessageApp&&step.toolId==="screen.tap_label"&&
        isSearchFieldLabel(String(step.input.label)))
      step={toolId:"screen.tap_label",input:{label:"Search"}};
    if(launchedMessageApp&&step.toolId==="screen.fill_text"&&
        isSearchFieldLabel(String(step.input.targetLabel)))
      step={toolId:"screen.fill_text",input:{...step.input,targetLabel:"Search"}};
    steps.push(step);
    if(step.toolId==="device.open_app") {
      launchedWhatsApp=step.input.app==="whatsapp";
      launchedMessageApp=["whatsapp","imo","botim"].includes(String(step.input.app))
        ?String(step.input.app):null;
      searchOpened=false;
      searchedContact=null;
      selectedMessageContact=null;
      reviewedMessage=null;
    }
    if(step.toolId==="screen.open_whatsapp_chat"){
      if(!launchedWhatsApp)return null;
      verifiedHint=String(step.input.recipientHint);
      verifiedText=null;
    }
    if(step.toolId==="screen.compose_whatsapp"){
      if(!verifiedHint||step.input.recipientHint!==verifiedHint||verifiedText!==null)return null;
      verifiedText=String(step.input.exactText);
    }
    if(step.toolId==="screen.send_whatsapp"){
      if(!verifiedHint||!verifiedText||step.input.recipientHint!==verifiedHint||
          step.input.exactText!==verifiedText||!sent)return null;
      verifiedHint=null;
      verifiedText=null;
    }
    if(step.toolId==="screen.tap_label"){
      const label:string=String(step.input.label);
      if(launchedMessageApp&&label.toLowerCase()==="search"&&!reviewedMessage){
        searchOpened=true;
        searchedContact=null;
        selectedMessageContact=null;
      }else if(launchedMessageApp&&searchedContact&&label===searchedContact&&!reviewedMessage){
        selectedMessageContact=label;
        searchOpened=false;
        searchedContact=null;
      }else{
        searchOpened=false;
        searchedContact=null;
        selectedMessageContact=null;
      }
    }
    if(step.toolId==="screen.fill_text"){
      const target:string=String(step.input.targetLabel);
      const exactText:string=String(step.input.exactText);
      if(launchedMessageApp&&searchOpened&&target.toLowerCase()==="search"&&
          recipientHint(exactText)&&!reviewedMessage){
        searchedContact=exactText;
        searchOpened=false;
        selectedMessageContact=null;
      }else{
        searchOpened=false;
        searchedContact=null;
        selectedMessageContact=null;
      }
    }
    if(step.toolId==="screen.compose_message"){
      const appId:string=String(step.input.appId);
      const recipient:string=String(step.input.recipientHint);
      const exactText:string=String(step.input.exactText);
      if(!launchedMessageApp||launchedMessageApp!==appId||
          selectedMessageContact!==recipient||reviewedMessage||messageSent)return null;
      reviewedMessage={appId,recipientHint:recipient,exactText};
      selectedMessageContact=null;
    }
    if(step.toolId==="screen.send_message"){
      if(!reviewedMessage||messageSent||index!==list.length-1||
          JSON.stringify(step.input)!==JSON.stringify(reviewedMessage))return null;
      messageSent=true;
    }
    if(["screen.tap_label","screen.scroll","screen.fill_text"].includes(step.toolId)){
      verifiedHint=null;
      verifiedText=null;
    }
    if(reviewedMessage&&step.toolId!=="screen.compose_message"&&
        step.toolId!=="screen.send_message")return null;
    if(messageSent&&index!==list.length-1)return null;
  }
  if(sent&&steps.at(-1)?.toolId!=="screen.send_whatsapp")return null;
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
  if(call.toolId==="screen.send_whatsapp")
    return {safe:x.verification==="sent_message_visible"&&
      x.recipientMatched===true&&x.exactTextMatched===true&&x.sent===true&&
      x.deliveryReported===false,
      reason:"The exact sent message was not verified in the selected chat; delivery was not claimed"};
  if(call.toolId==="screen.compose_message")
    return {safe:x.verification==="exact_text_visible"&&x.appId===call.input.appId&&
      x.recipientMatched===true&&x.submitted===false&&x.sent===false,
      reason:"The exact reviewed app, recipient and draft text were not verified; nothing sent"};
  if(call.toolId==="screen.send_message")
    return {safe:x.verification==="sent_message_visible"&&x.appId===call.input.appId&&
      x.recipientMatched===true&&x.exactTextMatched===true&&x.sent===true&&
      x.deliveryReported===false,
      reason:"The exact message was not verified in the selected app; delivery was not claimed"};
  if(call.toolId==="screen.fill_text")
    return {safe:x.verification==="exact_text_visible"&&x.submitted===false,
      reason:"The exact reviewed text was not visible in the field"};
  if(call.toolId==="device.open_app"||call.toolId==="device.open_camera"||
      call.toolId==="device.browser_search") {
    const expectedPackage=call.toolId==="device.open_app"?({
      whatsapp:"com.whatsapp",imo:"com.imo.android.imoim",botim:"im.thebot.messenger"
    } as Record<string,string>)[String(call.input.app)]:undefined;
    return {safe:x.intentDispatched===true&&
      (x.verification==="destination_package_observed"&&
        (!expectedPackage||x.packageId===expectedPackage)||
        isLast&&x.verification==="not_observed"),
      reason:"App destination was not observed. Return to Nexa, inspect the app, then start a new task."};
  }
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
        : final.call.toolId==="screen.send_whatsapp"
        ? "WhatsApp shows the exact requested message in the selected conversation. Delivery status was not verified."
        : final.call.toolId==="screen.send_message"
        ? `${String(final.call.input.appId).toUpperCase()} shows the exact requested message in the selected conversation. Delivery status was not verified.`
        : "Finished "+steps.length+" owner-requested steps with Android evidence. No Send, Submit or payment was performed."};
    }
    return {type:"tool",call:steps[observations.length]};
  }};
}
