import {NativeModules, Platform} from 'react-native';

export type KnownApp = 'github'|'whatsapp'|'whatsapp-business';
export type AppAvailability = {
  app: KnownApp;
  packageId: string;
  installed: boolean;
  homeScreenPage: 'unknown';
  verification: 'package_manager_only';
};
type AppBridge = {
  checkSupportedApp(app: KnownApp): Promise<AppAvailability>;
  openSupportedApp(app: KnownApp): Promise<{
    intentDispatched: boolean;
    packageId: string;
    verification: 'not_observed';
  }>;
};
const native = (): AppBridge => {
  const m = NativeModules.PAADeviceControl;
  if (Platform.OS !== 'android' || !m?.checkSupportedApp || !m?.openSupportedApp)
    throw new Error('Updated native Android app discovery not available.');
  return m as AppBridge;
};
export const appDiscoveryAvailable = (): boolean =>
  Platform.OS === 'android' &&
  typeof NativeModules.PAADeviceControl?.checkSupportedApp === 'function';
/** Checks only allowlisted package IDs, without querying all installed apps. */
export const checkKnownApp = (app: KnownApp): Promise<AppAvailability> =>
  native().checkSupportedApp(app);
/** A requested launch Intent is not confirmation that the destination UI opened. */
export const launchKnownApp = (app: KnownApp) => native().openSupportedApp(app);
