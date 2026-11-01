import {NativeModules, Platform} from 'react-native';

/** Android may offer Nexa as a default assistant. This does not grant a hotword. */
export type NexaAssistantRoleStatus = {
  available: boolean;
  selected: boolean;
  wakeWordReady: boolean;
};
interface AssistantBridge {
  getAssistantRoleStatus(): Promise<NexaAssistantRoleStatus>;
  requestAssistantRole(): Promise<'unavailable'|'already_selected'|'selection_opened'>;
}
function bridge(): AssistantBridge {
  const module = NativeModules.PAANexaAssistantRole;
  if (Platform.OS !== 'android' ||
      typeof module?.getAssistantRoleStatus !== 'function' ||
      typeof module?.requestAssistantRole !== 'function')
    throw new Error('Install the updated native Android build for assistant selection.');
  return module as AssistantBridge;
}
export const assistantRoleAvailable = (): boolean =>
  Platform.OS === 'android' &&
  typeof NativeModules.PAANexaAssistantRole?.getAssistantRoleStatus === 'function';
export const getNexaAssistantRoleStatus = (): Promise<NexaAssistantRoleStatus> =>
  bridge().getAssistantRoleStatus();
export const requestNexaAssistantRole = (): Promise<'unavailable'|'already_selected'|'selection_opened'> =>
  bridge().requestAssistantRole();
