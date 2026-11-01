import {NativeModules, Platform} from 'react-native';
import type {OwnerSelectedContact} from './ownerPrivacy';

/** Does not tap Send or imply delivery. The user taps Send in WhatsApp. */
export type WhatsAppDraftResult = {
  intentDispatched: true;
  messageSent: false;
  verification: 'not_observed';
};
interface WhatsAppBridge {
  openReviewedDraft(phone: string, message: string): Promise<WhatsAppDraftResult>;
}
export const whatsAppDraftAvailable = (): boolean =>
  Platform.OS === 'android' &&
  typeof NativeModules.PAANexaWhatsAppCompose?.openReviewedDraft === 'function';

export async function openWhatsAppReviewedDraft(
  contact: OwnerSelectedContact,
  message: string
): Promise<WhatsAppDraftResult> {
  if (!whatsAppDraftAvailable()) throw new Error('Updated WhatsApp composer is not installed');
  if (contact?.source !== 'android-contact-picker' ||
      typeof contact.phone !== 'string' ||
      typeof message !== 'string' || !message.trim() || message.length > 500)
    throw new Error('Select one contact and enter a message of up to 500 characters');
  const native = NativeModules.PAANexaWhatsAppCompose as WhatsAppBridge;
  const result = await native.openReviewedDraft(contact.phone, message);
  if (!result?.intentDispatched || result.messageSent !== false ||
      result.verification !== 'not_observed')
    throw new Error('Android did not confirm the composer launch request');
  return result;
}
