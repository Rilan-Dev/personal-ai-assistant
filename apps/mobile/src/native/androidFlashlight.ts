import { NativeModules, PermissionsAndroid, Platform } from "react-native";

type VerifiedTorchState = {
  enabled: boolean;
  verification: "observed";
  source: "android-camera2";
};
interface TorchBridge {
  hasFlashlight(): Promise<boolean>;
  setFlashlight(enabled: boolean): Promise<VerifiedTorchState>;
}

export function nativeFlashlightAvailable(): boolean {
  return Platform.OS === "android" &&
    typeof NativeModules.PAAFlashlightControl?.hasFlashlight === "function" &&
    typeof NativeModules.PAAFlashlightControl?.setFlashlight === "function";
}
function getTorch(): TorchBridge {
  if (!nativeFlashlightAvailable()) throw new Error("Native flashlight unavailable");
  return NativeModules.PAAFlashlightControl as TorchBridge;
}
export const hasHardwareFlashlight = (): Promise<boolean> => getTorch().hasFlashlight();

/** Explicit UI-only permission flow. Never call this from the model executor. */
export async function requestCameraForFlashlight(): Promise<boolean> {
  if (!nativeFlashlightAvailable()) return false;
  const permission = PermissionsAndroid.PERMISSIONS.CAMERA;
  if (await PermissionsAndroid.check(permission)) return true;
  const response = await PermissionsAndroid.request(permission, {
    title: "Flashlight hardware permission",
    message: "Camera permission is required by Android to control your flashlight. This assistant does not capture camera images or video. Flashlight changes also require in-app permission and one-time approval.",
    buttonPositive: "Allow",
    buttonNegative: "Not now"
  });
  return response === PermissionsAndroid.RESULTS.GRANTED;
}
export async function setVerifiedFlashlight(enabled: boolean): Promise<VerifiedTorchState> {
  if (!nativeFlashlightAvailable()) throw new Error("Native flashlight unavailable");
  if (!await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.CAMERA)) {
    throw new Error("Camera permission required");
  }
  const result = await getTorch().setFlashlight(enabled);
  if (result?.verification !== "observed" || result.enabled !== enabled ||
      result.source !== "android-camera2") {
    throw new Error("Flashlight state was not verified");
  }
  return result;
}
