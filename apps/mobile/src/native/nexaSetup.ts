import {NativeModules, PermissionsAndroid, Platform} from 'react-native';

/** Actual OS state; app tool opt-ins are separate and session-scoped. */
export type NexaSetupStatus = {
  microphone: boolean;
  notifications: boolean;
  camera: boolean;
  approximateLocation: boolean;
  screenAssist: boolean;
  launcherNavigation: boolean;
  assistantRoleAvailable: boolean;
  assistantSelected: boolean;
  overlayGranted: boolean;
  overlayVisible: boolean;
  setupAcknowledged: boolean;
  versionName: string;
  versionCode: number;
  sourceRevision: string;
  sdkLevel: number;
};

interface SetupBridge {
  getSetupStatus(): Promise<NexaSetupStatus>;
  acknowledgeSetup(): Promise<boolean>;
  openAppSettings(): Promise<boolean>;
}
function bridge(): SetupBridge {
  const value = NativeModules.PAANexaSetup;
  if (Platform.OS !== 'android' || !value?.getSetupStatus ||
      !value?.acknowledgeSetup || !value?.openAppSettings)
    throw new Error('An updated native Nexa APK is required for permission setup.');
  return value as SetupBridge;
}
export const nexaSetupAvailable = () =>
  Platform.OS === 'android' && typeof NativeModules.PAANexaSetup?.getSetupStatus === 'function';

export async function readNexaSetupStatus(): Promise<NexaSetupStatus> {
  const data = await bridge().getSetupStatus();
  if (!data || typeof data.versionName !== 'string' ||
      typeof data.setupAcknowledged !== 'boolean' ||
      typeof data.microphone !== 'boolean' ||
      typeof data.screenAssist !== 'boolean')
    throw new Error('The installed Android build did not return valid setup status.');
  return data;
}
export const acknowledgeNexaSetup = () => bridge().acknowledgeSetup();
export const openNexaAppSettings = () => bridge().openAppSettings();

/** Only asks for permission; does not obtain coordinates or start tracking. */
export async function requestApproximateLocationSetup(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  const permission = PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION;
  return await PermissionsAndroid.check(permission) ||
    await PermissionsAndroid.request(permission, {
      title: 'Approximate location, only when you ask',
      message: 'Grant optional approximate location to read a location once from Tools. Nexa does not track you or upload locations automatically.',
      buttonPositive: 'Allow approximate location',
      buttonNegative: 'Not now'
    }) === PermissionsAndroid.RESULTS.GRANTED;
}
