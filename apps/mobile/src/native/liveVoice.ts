import {NativeModules,PermissionsAndroid,Platform} from 'react-native';

export type LiveVoiceStatus={
  state:'stopped'|'connecting'|'listening'|'error';
  message:string;
  microphoneActive:boolean;
};
interface NativeSession {
  startVoiceSession():Promise<boolean>;
  stopVoiceSession():Promise<boolean>;
  getVoiceSessionStatus():Promise<LiveVoiceStatus>;
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

export async function startOwnerLiveVoice():Promise<boolean> {
  if(!liveVoiceAvailable())return false;
  const mic=PermissionsAndroid.PERMISSIONS.RECORD_AUDIO;
  const granted=await PermissionsAndroid.check(mic) ||
    await PermissionsAndroid.request(mic,{
      title:'Live voice microphone access',
      message:'Starting voice streams microphone audio to your OpenAI realtime provider, even while another app is visible. A persistent notification lets you stop it. Only start when you want a live conversation.',
      buttonPositive:'Start with microphone',
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
  return module().startVoiceSession();
}
export const stopOwnerLiveVoice=():Promise<boolean>=>module().stopVoiceSession();
