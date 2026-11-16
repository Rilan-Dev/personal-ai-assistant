import type { Tool, ToolExecutionContext } from "@paa/agent-core";

export interface ReviewedMessageNativeBridge {
  performReviewedMessageDraft(appId:string,recipientHint:string,exactText:string,
    sessionId:string,callId:string,fingerprint:string):Promise<{
      appId:string;verification:"exact_text_visible";recipientMatched:true;
      submitted:false;sent:false
    }>;
  performReviewedMessageSend(appId:string,recipientHint:string,exactText:string,
    sessionId:string,callId:string,fingerprint:string):Promise<{
      appId:string;verification:"sent_message_visible";recipientMatched:true;
      exactTextMatched:true;sent:true;deliveryReported:false
    }>;
}

const plainObject=(value:unknown):value is Record<string,unknown>=>
  value!==null&&typeof value==="object"&&!Array.isArray(value);
const messageApp=(value:unknown):value is "whatsapp"|"imo"|"botim"=>
  value==="whatsapp"||value==="imo"||value==="botim";
const recipient=(value:unknown):value is string=>
  typeof value==="string"&&/^[\p{L}][\p{L}\p{N} '-]{1,49}$/u.test(value)&&
  !/\b(?:and|then|send|submit|pay|buy|call|delete|grant|allow|confirm|password|otp)\b/i.test(value);
const exactText=(value:unknown):value is string=>
  typeof value==="string"&&value.length>=1&&value.length<=500&&
  value.trim().length>0&&!/[\x00-\x08\x0b-\x1f]/.test(value);
function mandate(context:ToolExecutionContext):[string,string,string]{
  if(typeof context.sessionId!=="string"||!context.sessionId||
      typeof context.callId!=="string"||!context.callId||
      typeof context.approvalFingerprint!=="string"||
      context.approvalFingerprint.length<10)
    throw Error("Native message action requires a consumed owner-approved step");
  return [context.sessionId,context.callId,context.approvalFingerprint];
}
const appInput=(input:unknown):input is Record<string,unknown>=>
  plainObject(input)&&Object.keys(input).length===3&&
  messageApp(input.appId)&&recipient(input.recipientHint)&&exactText(input.exactText);

/** The same production tool definitions are used by Chat and deterministic QA. */
export function createReviewedMessageTools(
  getBridge:()=>ReviewedMessageNativeBridge
):Tool[]{
  return [
    {
      id:"screen.compose_message",
      description:"Fill the exact owner-reviewed message in the uniquely verified chat for one allowlisted app (WhatsApp, IMO or BOTIM). Does not submit or send.",
      capabilities:["device.interact"],risk:"medium",hasExternalSideEffect:true,
      validateInput:appInput,
      execute:async({appId,recipientHint,exactText},context)=>{
        const data=await getBridge().performReviewedMessageDraft(
          String(appId),String(recipientHint),String(exactText),...mandate(context));
        if(data.appId!==appId||data.verification!=="exact_text_visible"||
            data.recipientMatched!==true||data.submitted!==false||data.sent!==false)
          throw Error("The reviewed draft was not verified in the requested app");
        return {status:"success",data};
      }
    },
    {
      id:"screen.send_message",
      description:"Send one exact reviewed draft in WhatsApp, IMO or BOTIM after its separate high-risk owner approval. Native code checks the same app, exact recipient, draft, unique Send button and one-shot receipt; delivery is never claimed.",
      capabilities:["device.interact"],risk:"high",hasExternalSideEffect:true,
      validateInput:appInput,
      execute:async({appId,recipientHint,exactText},context)=>{
        const data=await getBridge().performReviewedMessageSend(
          String(appId),String(recipientHint),String(exactText),...mandate(context));
        if(data.appId!==appId||data.verification!=="sent_message_visible"||
            data.recipientMatched!==true||data.exactTextMatched!==true||
            data.sent!==true||data.deliveryReported!==false)
          throw Object.assign(Error("Message Send was not verified in the requested app"),
            {code:"MESSAGE_SEND_UNCERTAIN"});
        return {status:"success",data};
      }
    }
  ];
}
