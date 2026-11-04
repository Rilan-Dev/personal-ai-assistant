import {NativeModules, Platform} from 'react-native';
import type {InstalledMessageApp, MessageAppId} from '@paa/agent-core';

type DispatchOutcome = {
  intentDispatched:true;
  messageSent:false;
  verification:'not_observed';
};
type MessagingBridge = {
  installedChannels():Promise<unknown>;
  openSmsDraft(phone:string,message:string):Promise<DispatchOutcome>;
  stageManualComposer(appId:string,recipient:string,message:string):Promise<DispatchOutcome>;
  discardManualComposer():Promise<boolean>;
  stageReviewedTextField(label:string,text:string):Promise<boolean>;
  cancelReviewedTextField():Promise<boolean>;
};
type MemoryBridge = {
  rememberMessageApp(recipient:string,appId:string):Promise<boolean>;
  rememberedMessageApp(recipient:string):Promise<string|null>;
  forgetMessageApp(recipient:string):Promise<boolean>;
};

function messaging():MessagingBridge {
  const n=NativeModules.PAANexaMessaging;
  if(Platform.OS!=='android' || !n?.installedChannels ||
    !n?.openSmsDraft || !n?.stageManualComposer) throw Error('Updated messaging APK is required');
  return n as MessagingBridge;
}
function memory():MemoryBridge {
  const n=NativeModules.PAAAgentJournal;
  if(Platform.OS!=='android' || !n?.rememberMessageApp ||
    !n?.rememberedMessageApp || !n?.forgetMessageApp)
    throw Error('Encrypted messaging preference storage is unavailable');
  return n as MemoryBridge;
}
const knownIds:MessageAppId[]=['whatsapp','whatsapp-business','sms','imo','botim'];
const safeApp=(value:unknown):value is MessageAppId=>
  typeof value==='string' && knownIds.includes(value as MessageAppId);

export const messagingAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAANexaMessaging?.installedChannels==='function' &&
  typeof NativeModules.PAAAgentJournal?.rememberedMessageApp==='function';

export async function installedMessagingApps():Promise<InstalledMessageApp[]> {
  const response=await messaging().installedChannels();
  if(!Array.isArray(response)||response.length>5)throw Error('Unexpected messaging-app inventory');
  const items:InstalledMessageApp[]=response.map((raw:unknown)=>{
    if(!raw||typeof raw!=='object')throw Error('Invalid installed app');
    const x=raw as Record<string,unknown>;
    if(!safeApp(x.id)||typeof x.label!=='string'||x.label.length>50||
      typeof x.packageName!=='string'||! /^[a-zA-Z][a-zA-Z0-9_.]{2,150}$/.test(x.packageName))
      throw Error('Invalid installed app metadata');
    return {id:x.id,label:x.label,packageName:x.packageName};
  });
  if(new Set(items.map(x=>x.id)).size!==items.length)
    throw Error('Duplicate installed messaging app');
  return items;
}
export async function rememberedMessageApp(alias:string):Promise<MessageAppId|null> {
  const value=await memory().rememberedMessageApp(alias);
  if(value==null)return null;
  if(!safeApp(value))throw Error('Invalid encrypted messaging preference');
  return value;
}
export const rememberMessageApp=async(alias:string,id:MessageAppId)=>{
  if(!safeApp(id)||!await memory().rememberMessageApp(alias,id))
    throw Error('Unable to securely remember the messaging app');
};
export const forgetMessageApp=async(alias:string)=>{
  if(!await memory().forgetMessageApp(alias))throw Error('Unable to delete saved preference');
};
function validated(outcome:DispatchOutcome):DispatchOutcome {
  if(outcome?.intentDispatched!==true||outcome.messageSent!==false||
    outcome.verification!=='not_observed')
    throw Error('Android did not confirm the reviewed handoff');
  return outcome;
}
/** User-selected contact from Android picker is checked in the screen layer. */
export const openReviewedSmsDraft=async(phone:string,message:string)=>
  validated(await messaging().openSmsDraft(phone,message));
/** App opens, then owner navigates to chat and confirms one text-field fill. */
export const openManualMessageComposer=async(appId:'imo'|'botim'|'whatsapp-business',hint:string,text:string)=>
  validated(await messaging().stageManualComposer(appId,hint,text));
export const discardManualMessage=()=>messaging().discardManualComposer();
export const reviewedTextInputAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAANexaMessaging?.stageReviewedTextField==='function';
export async function stageReviewedTextInput(label:string,text:string):Promise<void> {
  if(typeof messaging().stageReviewedTextField!=='function' ||
    !await messaging().stageReviewedTextField(label,text))
    throw new Error('Android could not safely stage a reviewed input field');
}
export async function cancelReviewedTextInput():Promise<void> {
  if(typeof messaging().cancelReviewedTextField!=='function' ||
    !await messaging().cancelReviewedTextField())
    throw new Error('Android text-entry review could not be discarded');
}
