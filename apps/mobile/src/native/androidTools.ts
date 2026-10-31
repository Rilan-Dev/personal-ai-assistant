import { NativeModules, Platform } from "react-native";
import type { Tool } from "@paa/agent-core";

type NativeStatus = {
  platform: string;
  adapter: string;
  cameraIntentSupported: boolean;
  accessibilityEnabled: boolean;
  voiceWakeWordSupported: boolean;
};
type LaunchOutcome = {
  intentDispatched: boolean;
  verification: "not_observed";
  action?: string;
  packageId?: string;
};
interface DeviceControlBridge {
  getStatus(): Promise<NativeStatus>;
  getBatteryStatus(): Promise<{ levelPercent: number; charging: boolean; source: string }>;
  openSupportedApp(app: string): Promise<LaunchOutcome>;
  openCamera(): Promise<LaunchOutcome>;
}

export const nativeDeviceAvailable = (): boolean =>
  Platform.OS === "android" &&
  typeof NativeModules.PAADeviceControl?.getStatus === "function" &&
  typeof NativeModules.PAADeviceControl?.openSupportedApp === "function" &&
  typeof NativeModules.PAADeviceControl?.openCamera === "function";

function getBridge(): DeviceControlBridge {
  if (!nativeDeviceAvailable()) throw new Error("Android native module is not available. Rebuild the native application.");
  return NativeModules.PAADeviceControl as DeviceControlBridge;
}
const plainObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const emptyInput = (value: unknown): value is Record<string, unknown> =>
  plainObject(value) && Object.keys(value).length === 0;
const appInput = (value: unknown): value is Record<string, unknown> =>
  plainObject(value) &&
  Object.keys(value).length === 1 &&
  (value.app === "whatsapp" || value.app === "whatsapp-business");

/** A Kotlin bridge is the only permitted path to native device operations. */
export function createAndroidTools(): Tool[] {
  return [
    {
      id: "demo.approval_test",
      description: "Exercise one-time approval safely without touching the device",
      capabilities: [],
      risk: "high",
      hasExternalSideEffect: false,
      validateInput: emptyInput,
      execute: async () => ({ status: "success", data: { training: true, noDeviceAction: true } })
    },
    {
      id: "device.status",
      description: "Read non-sensitive bridge diagnostic status",
      capabilities: [],
      risk: "low",
      hasExternalSideEffect: false,
      validateInput: emptyInput,
      execute: async () => ({ status: "success", data: await getBridge().getStatus() })
    },
    {
      id: "device.battery_status",
      description: "Read the device's current battery charge and charging status; no history",
      capabilities: [],
      risk: "low",
      hasExternalSideEffect: false,
      validateInput: emptyInput,
      execute: async () => ({ status: "success", data: await getBridge().getBatteryStatus() })
    },
    {
      id: "device.open_camera",
      description: "Open camera via system Android Intent; does not capture an image",
      capabilities: ["apps.launch"],
      risk: "low",
      hasExternalSideEffect: false,
      validateInput: emptyInput,
      execute: async () => ({ status: "success", data: await getBridge().openCamera() })
    },
    {
      id: "device.open_app",
      description: "Launch an allowlisted installed Android application",
      capabilities: ["apps.launch"],
      risk: "low",
      hasExternalSideEffect: false,
      validateInput: appInput,
      execute: async ({app}) => ({ status: "success", data: await getBridge().openSupportedApp(String(app)) })
    }
  ];
}
