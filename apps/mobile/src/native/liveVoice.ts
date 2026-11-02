import {NativeModules,PermissionsAndroid,Platform} from 'react-native';

export type SarvamVoiceAuthorization = { ready:boolean; code:string; message:string };

export type LiveVoiceStatus={
  state:'stopped'|'connecting'|'listening'|'error';
  /** Returned by the running Android service, not inferred from UI labels. */
  provider?:'sarvam-api'|'openai'|null;
  message:string;
  microphoneActive:boolean;
  microphoneMuted:boolean;
  pendingAppApproval?:string|null;
  lastToolResult?:string;
};
interface NativeSession {
  startVoiceSession():Promise<boolean>;
  stopVoiceSession():Promise<boolean>;
  getVoiceSessionStatus():Promise<LiveVoiceStatus>;
  setVoiceMicrophoneMuted(muted:boolean):Promise<boolean>;
  decideVoiceAppAction(approve:boolean):Promise<boolean>;
  checkSarvamVoiceAuthorization():Promise<SarvamVoiceAuthorization>;
}
function module():NativeSession {
  const m=NativeModules.PAARealtimeVoice;
  if(Platform.OS!=='android'||!m?.startVoiceSession||!m?.stopVoiceSession||!m?.getVoiceSessionStatus)
    throw new Error('Realtime native voice unavailable');
  return m as NativeSession;
}
export const liveVoiceAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAARealtimeVoice?.startVoiceSession==='function';
export const getLiveVoiceStatus=()=>module().getVoiceSessionStatus();
export const checkSarvamVoiceAuthorization=()=>module().checkSarvamVoiceAuthorization();

export async function prepareOwnerLiveVoicePermissions(provider:'sarvam'|'openai'|'device'='device'):Promise<boolean> {
  if(!liveVoiceAvailable())return false;
  const mic=PermissionsAndroid.PERMISSIONS.RECORD_AUDIO;
  const granted=await PermissionsAndroid.check(mic) ||
    await PermissionsAndroid.request(mic,{
      title:'Live voice microphone access',
      message:provider==='device'
        ? 'Hey Nexa wake detection uses the microphone locally. After an authorized wake, Nexa may start your separately consented live AI provider. Android shows a notification with Stop.'
        : 'When you start live voice, microphone audio streams to '+(provider==='sarvam'
          ? 'Sarvam Model API (Saaras realtime recognition)'
          : 'your configured OpenAI realtime account')+'. Android shows a notification with Stop. Voice streaming requires your permission.',
      buttonPositive:'Allow microphone',
      buttonNegative:'Cancel'
    })===PermissionsAndroid.RESULTS.GRANTED;
  if(!granted)return false;
  // Notification visibility is required by platform policy; Android 13+
  // may allow FGS without POST_NOTIFICATIONS, but users should still consent.
  if(typeof Platform.Version==="number" && Platform.Version>=33){
    const notifications=PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS;
    const approved=await PermissionsAndroid.check(notifications) ||
      await PermissionsAndroid.request(notifications)===PermissionsAndroid.RESULTS.GRANTED;
    if(!approved)throw new Error('Notification permission is required for owner-visible voice');
  }
  return true;
}

export async function startOwnerLiveVoice(provider:'sarvam'|'openai'):Promise<boolean> {
  if(!await prepareOwnerLiveVoicePermissions(provider))return false;
  return module().startVoiceSession();
}
export const stopOwnerLiveVoice=():Promise<boolean>=>module().stopVoiceSession();
export const decideOwnerLiveAppAction=(approve:boolean):Promise<boolean>=>{
  if(typeof approve!=='boolean')throw new Error('INVALID_APP_APPROVAL');
  return module().decideVoiceAppAction(approve);
};
export const setOwnerLiveMicMuted=(muted:boolean):Promise<boolean>=>{
  if(typeof muted!=='boolean')throw new Error('INVALID_MIC_MUTE');
  return module().setVoiceMicrophoneMuted(muted);
};
