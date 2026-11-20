import { NativeModules, Platform } from "react-native";

type NativePowerShellBridge = {
  debugBuild: boolean;
  start():Promise<boolean>;
  stop():Promise<boolean>;
  reply(requestId:string,responseJson:string):Promise<boolean>;
};

export type PowerShellBridgeRequestEvent = {
  requestId:string;
  envelopeJson:string;
};

export const POWER_SHELL_BRIDGE_EVENT = "NexaPowerShellRequest";

function bridge():NativePowerShellBridge {
  const value=NativeModules.PAAPowerShellBridge;
  if(Platform.OS!=="android" || typeof value?.start!=="function" ||
     typeof value?.reply!=="function")
    throw new Error("Debug PowerShell bridge is unavailable; rebuild the Android debug app.");
  return value as NativePowerShellBridge;
}

export function powerShellBridgeDebugBuild():boolean {
  return Platform.OS==="android" && NativeModules.PAAPowerShellBridge?.debugBuild===true;
}

export async function startPowerShellBridge():Promise<boolean> {
  if(!powerShellBridgeDebugBuild())return false;
  return bridge().start();
}

export async function stopPowerShellBridge():Promise<boolean> {
  if(!powerShellBridgeDebugBuild())return false;
  return bridge().stop();
}

export async function replyPowerShellBridge(requestId:string,responseJson:string):Promise<boolean> {
  return bridge().reply(requestId,responseJson);
}
