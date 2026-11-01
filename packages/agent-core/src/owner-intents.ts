/**
 * Local owner-command router. Never calls tools. It only prefills a review
 * UI for one explicitly quoted WhatsApp draft, and does not resolve contacts.
 */
export type ReviewedWhatsAppIntent = {
  contactHint: string;
  message: string;
  mode: 'review_only';
};

export function parseReviewedWhatsAppIntent(command: string): ReviewedWhatsAppIntent | null {
  if (typeof command !== 'string' || command.length > 800) return null;
  const normalized = command.trim().replace(/^hey\s+nexa[,!]?\s+/i, '');
  const match = normalized.match(
    /^(?:please\s+)?(?:send|prepare)\s+(?:a\s+)?whatsapp\s+(?:message\s+)?to\s+(?:my\s+)?([a-z][a-z\s-]{0,49}?)\s*(?:saying\s+|:\s*|,\s*)?["“]([^"”\r\n]{1,500})["”]$/i
  );
  if (!match) return null;
  const contactHint = match[1].trim().replace(/\s+/g,' ');
  const message = match[2];
  if (!contactHint || message.trim().length === 0 ||
      /\b(?:and|then)\s+(?:call|open|delete|pay|transfer)\b/i.test(contactHint)) return null;
  return {contactHint, message, mode: 'review_only'};
}
