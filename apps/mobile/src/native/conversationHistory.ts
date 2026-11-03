import {NativeModules,Platform} from 'react-native';

export type NexaConversationEntry={
  id:number; sessionId:string; channel:'voice'|'chat'|'tool';
  provider:string; role:'user'|'assistant'|'tool'; text:string; createdAt:number;
};
export type NexaProcessExit={
  process:string;timestamp:number;reason:string;reasonCode:number;
  pid:number;importance:number;rssKb:number;
};
export type NexaTaskHistoryEntry={
  taskId:string;sessionId:string;status:string;stepIndex:number;
  goal:string;steps:string[];lastEvidence:string;updatedAt:number;
};
type Bridge={
  appendEntry(session:string,channel:string,provider:string,role:string,text:string):Promise<boolean>;
  recentEntries(limit:number):Promise<string>;
  recentTasks(limit:number):Promise<string>;
  recentAndroidProcessExits():Promise<string>;
  shareAndroidProcessExits():Promise<boolean>;
  copyAllHistory():Promise<boolean>;
  shareAllHistory():Promise<boolean>;
};
const bridge=():Bridge=>{
  if(Platform.OS!=='android'||!NativeModules.PAAConversationHistory)
    throw new Error('Android local history unavailable');
  return NativeModules.PAAConversationHistory as Bridge;
};
export const historyAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAConversationHistory?.recentEntries==='function';
export const appendConversation=(session:string,channel:'chat'|'voice'|'tool',
  provider:string,role:'user'|'assistant'|'tool',text:string):Promise<boolean>=>
    bridge().appendEntry(session,channel,provider,role,text);
export async function recentConversations(limit=100):Promise<NexaConversationEntry[]>{
  const raw=await bridge().recentEntries(limit);
  return JSON.parse(raw) as NexaConversationEntry[];
}
export async function recentVoiceTasks(limit=40):Promise<NexaTaskHistoryEntry[]>{
  const raw=await bridge().recentTasks(limit);
  return JSON.parse(raw) as NexaTaskHistoryEntry[];
}
export async function recentAndroidProcessExits():Promise<NexaProcessExit[]>{
  const raw=await bridge().recentAndroidProcessExits();
  return JSON.parse(raw) as NexaProcessExit[];
}
export const shareAndroidProcessExits=()=>bridge().shareAndroidProcessExits();
export const copyConversationHistory=()=>bridge().copyAllHistory();
export const shareConversationHistory=()=>bridge().shareAllHistory();
