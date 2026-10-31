import {NativeModules,Platform,PermissionsAndroid} from 'react-native';

interface SpeechBridge{
  listenOnce():Promise<string>;
  cancelListening():Promise<boolean>;
  speak(text:string,language:string):Promise<boolean>;
  stopSpeaking():Promise<boolean>;
}
const bridge=():SpeechBridge=>{
  const native=NativeModules.PAASarvamSpeech;
  if(Platform.OS!=='android'||!native?.listenOnce||!native?.speak)
    throw new Error('SARVAM_NATIVE_SPEECH_UNAVAILABLE');
  return native as SpeechBridge;
};
export const sarvamSpeechAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAASarvamSpeech?.listenOnce==='function';
export async function dictateSarvamOnce():Promise<string>{
  const result=await bridge().listenOnce();
  if(typeof result!=='string'||!result.trim()||result.length>2000)
    throw new Error('EMPTY_SARVAM_STT');
  return result.trim();
}
export const cancelSarvamDictation=()=>bridge().cancelListening();
export async function speakSarvamOnce(text:string,language:string):Promise<boolean>{
  if(!text.trim()||text.length>1000)throw new Error('INVALID_TEXT');
  return bridge().speak(text,language);
}
export const stopSarvamSpeech=()=>bridge().stopSpeaking();


export async function requestMicForSarvamDictation():Promise<boolean>{
  if(!sarvamSpeechAvailable())return false;
  const permission=PermissionsAndroid.PERMISSIONS.RECORD_AUDIO;
  if(await PermissionsAndroid.check(permission))return true;
  return (await PermissionsAndroid.request(permission,{
    title:'Sarvam speech recognition',
    message:'Record one spoken command and send audio to Sarvam AI for speech recognition. The recognized text will appear for your review in Chat. Continuous live conversations have no on-screen transcripts.',
    buttonPositive:'Allow once',buttonNegative:'Cancel'
  }))===PermissionsAndroid.RESULTS.GRANTED;
}
