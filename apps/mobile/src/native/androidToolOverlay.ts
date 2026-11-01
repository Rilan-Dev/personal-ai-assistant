import {NativeModules, Platform} from 'react-native';

export type ToolOverlayStatus = {granted:boolean; visible:boolean};
export type ReviewedVoiceDraft = {contactHint:string; message:string; mode:'review_only'};
type OverlayNative = {
  getStatus(): Promise<ToolOverlayStatus>;
  requestPermission(): Promise<'settings_opened'>;
  show(): Promise<boolean>;
  hide(): Promise<boolean>;
  consumePendingTab(): Promise<string>;
  consumePendingVoiceDraft(): Promise<ReviewedVoiceDraft | null>;
};
function bridge(): OverlayNative {
  const value = NativeModules.PAANexaToolOverlay;
  if (Platform.OS !== 'android' || !value?.getStatus ||
      !value?.requestPermission || !value?.show || !value?.hide ||
      !value?.consumePendingTab || !value?.consumePendingVoiceDraft)
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

/** Single-use, memory-only handoff. Contact remains an unverified hint. */
export async function consumeReviewedVoiceDraft(): Promise<ReviewedVoiceDraft|null> {
  const pending = await bridge().consumePendingVoiceDraft();
  if (pending === null) return null;
  if (pending?.mode !== 'review_only' ||
      typeof pending.contactHint !== 'string' ||
      typeof pending.message !== 'string' ||
      pending.contactHint.length > 50 ||
      !pending.message.trim() || pending.message.length > 500)
    throw new Error('Android returned an invalid voice draft');
  return pending;
}
