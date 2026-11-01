import {NativeModules, Platform} from 'react-native';

export type WakeStatus = {
  modelReady: boolean;
  installing: boolean;
  listening: boolean;
  enabled: boolean;
  restartRequired: boolean;
  state: string;
  message: string;
  permissionGranted: boolean;
};
type WakeBridge = {
  getStatus(): Promise<WakeStatus>;
  installModel(): Promise<'install_started'|'already_installed'>;
  startListening(): Promise<'start_requested'>;
  stopListening(): Promise<boolean>;
  resumeIfEnabled(): Promise<string>;
};
function module(): WakeBridge {
  const value = NativeModules.PAANexaWakeWord;
  if (Platform.OS !== 'android' || !value?.getStatus || !value?.installModel ||
      !value?.startListening || !value?.stopListening)
    throw new Error('Updated Nexa native offline wake-word service required');
  return value as WakeBridge;
}
export const wakeWordAvailable = () => Platform.OS === 'android' &&
  typeof NativeModules.PAANexaWakeWord?.getStatus === 'function';
export const readWakeStatus = () => module().getStatus();
export const installWakeModel = () => module().installModel();
export const startWakeListening = () => module().startListening();
export const stopWakeListening = () => module().stopListening();
export const resumeWakeIfEnabled = () => module().resumeIfEnabled();
