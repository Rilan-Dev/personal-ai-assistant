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
};
function bridge(): OverlayNative {
  const value = NativeModules.PAANexaToolOverlay;
  if (Platform.OS !== 'android' || !value?.getStatus ||
      !value?.requestPermission || !value?.show || !value?.hide ||
      !value?.consumePendingTab || !value?.consumePendingVoiceDraft)
    throw new Error('The updated Android tool-overlay module is not available');
  return value as OverlayNative;
}
export const toolOverlayAvailable = () => Platform.OS === 'android' &&
  typeof NativeModules.PAANexaToolOverlay?.getStatus === 'function';
export const getToolOverlayStatus = () => bridge().getStatus();
export const requestToolOverlayPermission = () => bridge().requestPermission();
export const showToolOverlay = () => bridge().show();
export const hideToolOverlay = () => bridge().hide();
export const consumeToolOverlayNavigation = (): Promise<string> =>
  bridge().consumePendingTab();

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
