import {NativeModules,Platform} from 'react-native';

export type NexaConversationEntry={
  id:number; sessionId:string; channel:'voice'|'chat'|'tool';
  provider:string; role:'user'|'assistant'|'tool'; text:string; createdAt:number;
};
type Bridge={
  appendEntry(session:string,channel:string,provider:string,role:string,text:string):Promise<boolean>;
  recentEntries(limit:number):Promise<string>;
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
export const copyConversationHistory=()=>bridge().copyAllHistory();
export const shareConversationHistory=()=>bridge().shareAllHistory();
