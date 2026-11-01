import {NativeModules, Platform} from 'react-native';

export type LauncherNavigationStatus = {
  enabled: boolean;
  state: 'idle'|'searching'|'found'|'not_found'|'wrong_screen'|'gesture_blocked'|
    'timeout'|'service_stopped'|'interrupted'|'cancelled'|'launch_failed'|'expired';
  swipes: number;
};
interface LauncherNative {
  getNavigationStatus(): Promise<LauncherNavigationStatus>;
  findGitHubIcon(): Promise<boolean>;
  cancelNavigation(): Promise<boolean>;
}
function native(): LauncherNative {
  const module = NativeModules.PAANexaLauncher;
  if (Platform.OS !== 'android' || !module?.getNavigationStatus ||
      !module?.findGitHubIcon || !module?.cancelNavigation)
    throw new Error('Nexa Launcher Navigation native module unavailable.');
  return module as LauncherNative;
}
export const launcherNavigationAvailable = (): boolean =>
  Platform.OS === 'android' &&
  typeof NativeModules.PAANexaLauncher?.getNavigationStatus === 'function';
export const getLauncherNavigationStatus = () => native().getNavigationStatus();
export const findGitHubOnLauncher = () => native().findGitHubIcon();
export const cancelLauncherNavigation = () => native().cancelNavigation();
