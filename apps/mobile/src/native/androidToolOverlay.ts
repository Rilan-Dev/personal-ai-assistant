import {NativeModules, Platform} from 'react-native';
import type {MessageRequest,MessageAppId} from '@paa/agent-core';

export type ToolOverlayStatus = {granted:boolean; visible:boolean};
export type ReviewedVoiceDraft = {contactHint:string; message:string; mode:'review_only'};
type OverlayNative = {
  getStatus(): Promise<ToolOverlayStatus>;
  requestPermission(): Promise<'settings_opened'>;
  show(): Promise<boolean>;
  hide(): Promise<boolean>;
  consumePendingTab(): Promise<string>;
  consumePendingVoiceDraft(): Promise<ReviewedVoiceDraft | null>;
  consumePendingMessageRequest(): Promise<unknown>;
  consumePendingTextEntry(): Promise<unknown>;
  completeChatRequest(id:string,response:string):Promise<boolean>;
  canAutoApproveNavigation(command:string):Promise<boolean>;
};
function bridge(): OverlayNative {
  const value = NativeModules.PAANexaToolOverlay;
  if (Platform.OS !== 'android' || !value?.getStatus ||
      !value?.requestPermission || !value?.show || !value?.hide ||
      !value?.consumePendingTab || !value?.consumePendingVoiceDraft)
    throw new Error('The updated Android tool-overlay module is not available');
  return value as OverlayNative;
}
export const NEXA_SHARED_OVERLAY_CHAT_EVENT = 'NexaOverlayChatRequest';

/** Delivers a result only to the one outstanding owner-written overlay turn. */
export async function completeSharedOverlayChat(id:string,response:string):Promise<boolean>{
  if(!/^[0-9a-f-]{36}$/.test(id)||typeof response!=='string'||response.length>8000)
    throw Error('INVALID_OVERLAY_CHAT_RESULT');
  const value=bridge();
  if(typeof value.completeChatRequest!=='function')
    throw Error('Overlay Chat bridge not present in this APK');
  return value.completeChatRequest(id,response);
}

/** Rechecks saved owner-selected navigation trust in native Android.
 * OS Accessibility and owner Pause are consulted at the time of the call.
 * This is not a request for Android runtime/system permission.
 */
export async function canAutoApproveTrustedNavigation(
  command:'open'|'scroll_screen:up'|'scroll_screen:down'|'scroll_screen:left'|'scroll_screen:right'
):Promise<boolean>{
  const value=bridge();
  if(typeof value.canAutoApproveNavigation!=='function')return false;
  return value.canAutoApproveNavigation(command);
}

export const toolOverlayAvailable = () => Platform.OS === 'android' &&
  typeof NativeModules.PAANexaToolOverlay?.getStatus === 'function';
export const getToolOverlayStatus = () => bridge().getStatus();
export const requestToolOverlayPermission = () => bridge().requestPermission();
export const showToolOverlay = () => bridge().show();
export const hideToolOverlay = () => bridge().hide();
export const consumeToolOverlayNavigation = (): Promise<string> =>
  bridge().consumePendingTab();

/** A spoken type request only opens a review UI, not another application's keyboard. */
export async function consumeSpokenTextEntry(): Promise<{targetLabel:string,exactText:string,reviewOnly:true}|null> {
  const api=bridge();
  if(typeof api.consumePendingTextEntry!=='function')
    throw Error('Updated Android voice text input handoff required');
  const raw=await api.consumePendingTextEntry();
  if(raw===null)return null;
  if(!raw||typeof raw!=='object')throw Error('Invalid Android spoken text-entry request');
  const data=raw as Record<string,unknown>;
  if(data.reviewOnly!==true ||
    typeof data.targetLabel!=='string'||data.targetLabel.length>60 ||
    typeof data.exactText!=='string'||data.exactText.length>500||
    !data.exactText.trim()) throw Error('Invalid text-entry request');
  return {targetLabel:data.targetLabel,exactText:data.exactText,reviewOnly:true};
}

/** Single-use spoken request. A voice transcript grants no app or recipient authority. */
export async function consumeSpokenMessageRequest():Promise<MessageRequest|null> {
  const bridgeValue=bridge();
  if(typeof bridgeValue.consumePendingMessageRequest!=='function')
    throw Error('Updated Android voice message handoff required');
  const raw=await bridgeValue.consumePendingMessageRequest();
  if(raw===null)return null;
  if(!raw||typeof raw!=='object')throw Error('Invalid spoken message request');
  const data=raw as Record<string,unknown>;
  const app:MessageAppId|null=data.explicitApp==null?null:
    (['whatsapp','whatsapp-business','sms','imo','botim'].includes(String(data.explicitApp))
      ? data.explicitApp as MessageAppId : null);
  if(data.reviewOnly!==true ||
    typeof data.recipientHint!=='string'||data.recipientHint.length>50||
    typeof data.text!=='string'||!data.text.trim()||data.text.length>500||
    (data.explicitApp!==null && data.explicitApp!==undefined && app===null))
    throw Error('Invalid spoken message routing metadata');
  return {recipientHint:data.recipientHint,text:data.text,explicitApp:app,reviewOnly:true};
}

/** Single-use, memory-only handoff. Contact remains an unverified hint. */
export async function consumeReviewedVoiceDraft(): Promise<ReviewedVoiceDraft|null> {
  const pending = await bridge().consumePendingVoiceDraft();
  if (pending === null) return null;
  if (pending?.mode !== 'review_only' ||
      typeof pending.contactHint !== 'string' ||
      typeof pending.message !== 'string' ||
      pending.contactHint.length > 50 ||
      !pending.message.trim() || pending.message.length > 500)
    throw new Error('Android returned an invalid voice draft');
  return pending;
}
