import { NativeModules, PermissionsAndroid, Platform } from "react-native";

/**
 * Android SpeechRecognizer is system-provided, not an always-on recording loop.
 * The on-device engine is required; there is no silent network fallback.
 * A transcript becomes editable text, never an automatically executed command.
 */
export type AndroidVoiceCapabilities = {
  onDeviceRecognition: boolean;
  micGranted: boolean;
  offlineOnly: boolean;
  wakeWord: boolean;
  androidApi: number;
};

interface NativeVoiceBridge {
  getCapabilities(): Promise<AndroidVoiceCapabilities>;
  listenOnce(languageTag: string): Promise<string>;
  cancelListening(): Promise<boolean>;
  speakOffline(text: string, languageTag: string): Promise<boolean>;
  stopSpeaking(): Promise<boolean>;
}

export function voiceBridgeAvailable(): boolean {
  const native = NativeModules.PAAVoiceControl;
  return Platform.OS === "android" &&
    typeof native?.getCapabilities === "function" &&
    typeof native?.listenOnce === "function" &&
    typeof native?.cancelListening === "function" &&
    typeof native?.speakOffline === "function" &&
    typeof native?.stopSpeaking === "function";
}

function bridge(): NativeVoiceBridge {
  if (!voiceBridgeAvailable()) throw new Error("Native Android voice module unavailable");
  return NativeModules.PAAVoiceControl as NativeVoiceBridge;
}

export const getVoiceCapabilities = (): Promise<AndroidVoiceCapabilities> =>
  bridge().getCapabilities();

export async function requestMicrophoneForTap(): Promise<boolean> {
  if (!voiceBridgeAvailable()) return false;
  const permission = PermissionsAndroid.PERMISSIONS.RECORD_AUDIO;
  if (await PermissionsAndroid.check(permission)) return true;
  const answer = await PermissionsAndroid.request(permission, {
    title: "Microphone access for one voice command",
    message: "Allow microphone use when you tap Listen. Audio goes to the Android on-device speech service, not our agent backend. Listening stops after one command or when cancelled.",
    buttonPositive: "Allow",
    buttonNegative: "Not now"
  });
  return answer === PermissionsAndroid.RESULTS.GRANTED;
}

export async function transcribeOnDevice(languageTag = ""): Promise<string> {
  const result = await bridge().listenOnce(languageTag);
  const text = result.trim();
  if (text.length === 0 || text.length > 2000) throw new Error("Empty or oversized transcript");
  return text;
}
export const cancelOnDeviceTranscription = (): Promise<boolean> =>
  bridge().cancelListening();

export async function speakOffline(text: string, languageTag = ""): Promise<boolean> {
  if (typeof text !== "string" || !text.trim() || text.length > 1000) {
    throw new Error("Text must contain 1–1000 characters");
  }
  return bridge().speakOffline(text, languageTag);
}
export const stopOfflineSpeech = (): Promise<boolean> => bridge().stopSpeaking();
