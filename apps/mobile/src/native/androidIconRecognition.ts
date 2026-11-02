import {NativeModules, Platform} from 'react-native';
import {parseScreenIconSnapshot} from '@paa/agent-contracts';
import type {ScreenIconSnapshot} from '@paa/agent-contracts';

interface NativeIconBridge {
  getStatus(): Promise<{enabled:boolean; armed:boolean; iconScanArmed:boolean}>;
  armIconScan(): Promise<boolean>;
  takeIconScan(): Promise<unknown>;
  clearIconScan(): Promise<boolean>;
  openAccessibilitySettings(): Promise<boolean>;
}
function bridge(): NativeIconBridge {
  const module = NativeModules.PAAScreenAssist;
  if (Platform.OS !== 'android' || !module?.armIconScan || !module?.takeIconScan ||
      !module?.clearIconScan) throw new Error('Android icon scanning unavailable');
  return module as NativeIconBridge;
}
export const iconRecognitionAvailable = (): boolean =>
  Platform.OS === 'android' && typeof NativeModules.PAAScreenAssist?.armIconScan === 'function';
export const getIconRecognitionStatus = () => bridge().getStatus();
/** Arm for 20 seconds; user switches to the app to be inspected. */
export const armIconRecognition = () => bridge().armIconScan();
/** Consumes the snapshot once; returns only whitelisted accessible icon metadata. */
export async function takeIconRecognition(): Promise<ScreenIconSnapshot | null> {
  return parseScreenIconSnapshot(await bridge().takeIconScan());
}
export const clearIconRecognition = () => bridge().clearIconScan();
export const openIconRecognitionSettings = () => bridge().openAccessibilitySettings();
