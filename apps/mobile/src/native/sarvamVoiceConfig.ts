import {NativeModules,Platform} from 'react-native';

export type SarvamVoiceAgentConfig={orgId:string;workspaceId:string;appId:string};
interface SecureVoiceStore{
  saveSarvamVoiceAgent(orgId:string,workspaceId:string,appId:string):Promise<boolean>;
  loadSarvamVoiceAgent():Promise<string|null>;
  clearSarvamVoiceAgent():Promise<boolean>;
  saveSarvamVoiceWorkspaceKey(key:string):Promise<boolean>;
  sarvamVoiceWorkspaceKeyPresent():Promise<boolean>;
  clearSarvamVoiceWorkspaceKey():Promise<boolean>;
  setSarvamVoiceStreamingConsent(value:boolean):Promise<boolean>;
  sarvamVoiceStreamingConsentStatus():Promise<boolean>;
}
const ID=/^[A-Za-z0-9_-]{4,128}$/;
export function validateSarvamVoiceConfig(value:unknown):SarvamVoiceAgentConfig{
  if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('INVALID_SARVAM_VOICE_CONFIG');
  const x=value as Record<string,unknown>;
  if(Object.keys(x).length!==3||typeof x.orgId!=='string'||typeof x.workspaceId!=='string'||typeof x.appId!=='string'||
    !ID.test(x.orgId)||!ID.test(x.workspaceId)||!ID.test(x.appId))
    throw new Error('INVALID_SARVAM_VOICE_CONFIG');
  return x as SarvamVoiceAgentConfig;
}
function native():SecureVoiceStore{
  const m=NativeModules.PAAAgentJournal;
  if(Platform.OS!=='android'||!m?.saveSarvamVoiceAgent||!m?.loadSarvamVoiceAgent||!m?.clearSarvamVoiceAgent)
    throw new Error('SARVAM_NATIVE_VAULT_UNAVAILABLE');
  return m as SecureVoiceStore;
}
export const sarvamVoiceConfigAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAAgentJournal?.saveSarvamVoiceAgent==='function';
export async function saveSarvamVoiceConfig(config:SarvamVoiceAgentConfig):Promise<void>{
  const c=validateSarvamVoiceConfig(config);
  if(!await native().saveSarvamVoiceAgent(c.orgId,c.workspaceId,c.appId))throw new Error('SARVAM_SAVE_FAILED');
}
export async function readSarvamVoiceConfig():Promise<SarvamVoiceAgentConfig|null>{
  const result=await native().loadSarvamVoiceAgent();
  return result?validateSarvamVoiceConfig(JSON.parse(result)):null;
}
export async function deleteSarvamVoiceConfig():Promise<void>{
  if(!await native().clearSarvamVoiceAgent())throw new Error('SARVAM_CLEAR_FAILED');
}

/** Samvaad workspace keys are exclusively for managed STS, not Chat. */
const VOICE_KEY=/^sk_samvaad_[A-Za-z0-9_-]{16,500}$/;
export const validateSamvaadWorkspaceKey=(key:string):boolean=>
  typeof key==='string' && VOICE_KEY.test(key);
export async function saveSamvaadWorkspaceKey(key:string):Promise<void>{
  if(!validateSamvaadWorkspaceKey(key))throw new Error('VOICE_WORKSPACE_KEY_FORMAT');
  if(!await native().saveSarvamVoiceWorkspaceKey(key))
    throw new Error('VOICE_WORKSPACE_KEY_SAVE');
}
export const samvaadWorkspaceKeyPresent=():Promise<boolean>=>
  native().sarvamVoiceWorkspaceKeyPresent();
export const deleteSamvaadWorkspaceKey=():Promise<boolean>=>
  native().clearSarvamVoiceWorkspaceKey();
export const samvaadStreamingConsentStatus=():Promise<boolean>=>
  native().sarvamVoiceStreamingConsentStatus();
export async function setSamvaadStreamingConsent(value:boolean):Promise<void>{
  if(!await native().setSarvamVoiceStreamingConsent(value))
    throw new Error('SARVAM_CONSENT_SAVE');
}
