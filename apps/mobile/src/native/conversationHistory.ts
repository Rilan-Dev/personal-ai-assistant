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
export type NexaAgentTaskEntry={
  taskId:string;sessionId:string;origin:'chat'|'live-voice'|'assistant-role'|'overlay';
  status:'proposed'|'cancelled';policyVersion:string;goal:string;
  createdAt:number;updatedAt:number;
};
type Bridge={
  appendEntry(session:string,channel:string,provider:string,role:string,text:string):Promise<boolean>;
  recentEntries(limit:number):Promise<string>;
  recentTasks(limit:number):Promise<string>;
  recentAgentTasks(limit:number):Promise<string>;
  saveOwnerTaskRequest(goal:string):Promise<string>;
  cancelOwnerTaskRequest(taskId:string):Promise<boolean>;
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
/** Explicit owner-created requests only. This does not activate a planner. */
export const ownerTaskPreviewAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAConversationHistory?.saveOwnerTaskRequest==='function';
export async function saveOwnerTaskRequest(goal:string):Promise<string>{
  if(typeof goal!=='string'||!goal.trim()||goal.length>500)
    throw new Error('Task goal must contain 1 to 500 characters');
  return bridge().saveOwnerTaskRequest(goal);
}
export const cancelOwnerTaskRequest=(taskId:string):Promise<boolean>=>
  bridge().cancelOwnerTaskRequest(taskId);
export async function recentAgentTasks(limit=40):Promise<NexaAgentTaskEntry[]>{
  const raw=await bridge().recentAgentTasks(limit);
  return JSON.parse(raw) as NexaAgentTaskEntry[];
}
export async function recentAndroidProcessExits():Promise<NexaProcessExit[]>{
  const raw=await bridge().recentAndroidProcessExits();
  return JSON.parse(raw) as NexaProcessExit[];
}
export const shareAndroidProcessExits=()=>bridge().shareAndroidProcessExits();
export const copyConversationHistory=()=>bridge().copyAllHistory();
export const shareConversationHistory=()=>bridge().shareAllHistory();
