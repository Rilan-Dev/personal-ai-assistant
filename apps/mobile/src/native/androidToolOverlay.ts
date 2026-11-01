import {NativeModules, Platform} from 'react-native';

export type ToolOverlayStatus = {granted:boolean; visible:boolean};
type OverlayNative = {
  getStatus(): Promise<ToolOverlayStatus>;
  requestPermission(): Promise<'settings_opened'>;
  show(): Promise<boolean>;
  hide(): Promise<boolean>;
  consumePendingTab(): Promise<string>;
};
function bridge(): OverlayNative {
  const value = NativeModules.PAANexaToolOverlay;
  if (Platform.OS !== 'android' || !value?.getStatus ||
      !value?.requestPermission || !value?.show || !value?.hide ||
      !value?.consumePendingTab)
    throw new Error('The updated Android tool-overlay module is not available');
  return value as OverlayNative;
}
export const toolOverlayAvailable = () => Platform.OS === 'android' &&
  typeof NativeModules.PAANexaToolOverlay?.getStatus === 'function';
export const getToolOverlayStatus = () => bridge().getStatus();
export const requestToolOverlayPermission = () => bridge().requestPermission();
export const showToolOverlay = () => bridge().show();
export const hideToolOverlay = () => bridge().hide();
export const consumeToolOverlayNavigation = (): Promise<string> =>
  bridge().consumePendingTab();
