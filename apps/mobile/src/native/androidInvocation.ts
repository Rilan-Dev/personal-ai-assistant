import { NativeModules, Platform } from "react-native";

/** Owner-controlled Android Quick Settings tile. Never starts the microphone. */
export type TileInstallResult =
  | "added" | "already_added" | "declined"
  | "manual_add_required" | "unavailable";

export function tileInstallationAvailable(): boolean {
  return Platform.OS === "android" &&
    typeof NativeModules.PAADeviceControl?.requestAssistantTile === "function";
}
export async function requestAssistantQuickTile(): Promise<TileInstallResult> {
  if (!tileInstallationAvailable()) throw new Error("Android tile service unavailable");
  const result: unknown = await NativeModules.PAADeviceControl.requestAssistantTile();
  if (result !== "added" && result !== "already_added" &&
      result !== "declined" && result !== "manual_add_required" &&
      result !== "unavailable") {
    throw new Error("Unrecognized tile response");
  }
  return result;
}
