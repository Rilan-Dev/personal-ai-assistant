import { NativeModules, Platform } from 'react-native';

/** Explicitly user-armed, single-use Android Accessibility text observation. */
interface AccessibilityBridge {
  getStatus(): Promise<{enabled:boolean; armed:boolean}>;
  openAccessibilitySettings(): Promise<boolean>;
  armOneScreen(): Promise<boolean>;
  takeCapturedText(): Promise<{text:string;packageName:string;ownerArmed:boolean}|null>;
  clear(): Promise<boolean>;
  inspectFiveHomeApps(): Promise<string>;
}
function bridge(): AccessibilityBridge {
  const module=NativeModules.PAAScreenAssist;
  if (Platform.OS!=='android' || !module?.getStatus || !module?.armOneScreen ||
      !module?.takeCapturedText || !module?.clear)
    throw new Error('Screen Assist is not available on this platform');
  return module as AccessibilityBridge;
}
export const screenAssistAvailable=():boolean =>
  Platform.OS==='android' && typeof NativeModules.PAAScreenAssist?.getStatus==='function';
export const getScreenAssistStatus=()=>bridge().getStatus();
export const openScreenAssistSettings=()=>bridge().openAccessibilitySettings();
export const armScreenReadOnce=()=>bridge().armOneScreen();
export const clearScreenRead=()=>bridge().clear();
export async function takeScreenReadOnce():Promise<{text:string;packageName:string}|null> {
  const snap=await bridge().takeCapturedText();
  if (!snap?.ownerArmed || typeof snap.text!=='string' || snap.text.length>2000 ||
      typeof snap.packageName!=='string' || snap.packageName.length>100) return null;
  return {text:snap.text,packageName:snap.packageName};
}

/** Five target-app icon counts only. No raw screen data or app navigation. */
export async function inspectFiveHomeApps():Promise<string>{
  const instance=bridge();
  if(typeof instance.inspectFiveHomeApps!=='function')
    throw Error('Update Nexa to enable the bounded five-app home inspection');
  const report=await instance.inspectFiveHomeApps();
  if(typeof report!=='string'||report.length>950||
    !report.startsWith('HOME_PILOT'))throw Error('Invalid Android launcher evidence');
  return report;
}
