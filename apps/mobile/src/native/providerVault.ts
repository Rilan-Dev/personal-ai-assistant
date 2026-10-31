import { NativeModules, Platform } from "react-native";
import { validateProviderSettings } from "@paa/agent-core";
import type { DirectProviderSettings } from "@paa/agent-core";

/** The native implementation uses Android Keystore AES-GCM, not AsyncStorage. */
interface NativeProviderVault {
  saveProviderSettings(value: string): Promise<boolean>;
  loadProviderSettings(): Promise<string | null>;
  clearProviderSettings(): Promise<boolean>;
}
function vault(): NativeProviderVault {
  const module = NativeModules.PAAAgentJournal;
  if (Platform.OS !== "android" ||
      typeof module?.saveProviderSettings !== "function" ||
      typeof module?.loadProviderSettings !== "function" ||
      typeof module?.clearProviderSettings !== "function") {
    throw new Error("Android Keystore provider settings unavailable");
  }
  return module as NativeProviderVault;
}
export function providerVaultAvailable(): boolean {
  const module = NativeModules.PAAAgentJournal;
  return Platform.OS === "android" &&
    typeof module?.saveProviderSettings === "function" &&
    typeof module?.loadProviderSettings === "function" &&
    typeof module?.clearProviderSettings === "function";
}
export async function loadProviderFromVault(): Promise<DirectProviderSettings | null> {
  const value = await vault().loadProviderSettings();
  return value === null ? null : validateProviderSettings(JSON.parse(value));
}
export async function saveProviderToVault(config: DirectProviderSettings): Promise<void> {
  const safe = validateProviderSettings(config);
  if (!await vault().saveProviderSettings(JSON.stringify(safe))) throw new Error("Secure settings write failed");
}
export async function clearProviderFromVault(): Promise<void> {
  if (!await vault().clearProviderSettings()) throw new Error("Secure settings clear failed");
}
