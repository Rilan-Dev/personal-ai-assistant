import {NativeModules, Platform} from 'react-native';

export type LaunchableAppMatch = {
  label: string;
  packageName: string;
  source: 'package_manager';
  launcherPage: 'unknown';
};
interface CatalogueBridge { findLaunchableApps(query: string): Promise<unknown>; }
function native(): CatalogueBridge {
  const module=NativeModules.PAANexaAppCatalogue;
  if (Platform.OS !== 'android' || typeof module?.findLaunchableApps !== 'function')
    throw new Error('Android app catalogue unavailable');
  return module as CatalogueBridge;
}
export const appCatalogueAvailable = () =>
  Platform.OS === 'android' && typeof NativeModules.PAANexaAppCatalogue?.findLaunchableApps === 'function';

/** Never returns a full inventory; requires an owner-specified search term. */
export async function findLaunchableApps(query: string): Promise<LaunchableAppMatch[]> {
  const term=query.trim();
  if (term.length < 2 || term.length > 60) throw new Error('Search term must contain 2–60 characters');
  const result=await native().findLaunchableApps(term);
  if (!Array.isArray(result) || result.length > 12) throw new Error('Invalid Android app catalogue result');
  return result.map((x: unknown) => {
    if (!x || typeof x !== 'object') throw new Error('Invalid app result');
    const item=x as Record<string, unknown>;
    if (typeof item.label !== 'string' || item.label.length > 80 ||
        typeof item.packageName !== 'string' || item.packageName.length > 150 ||
        !/^[a-zA-Z0-9_.-]+$/.test(item.packageName) || item.source !== 'package_manager' ||
        item.launcherPage !== 'unknown') throw new Error('Invalid app catalogue metadata');
    return {label:item.label, packageName:item.packageName,
      source:'package_manager' as const, launcherPage:'unknown' as const};
  });
}
