export type AcaBusinessIntent =
  | { kind: "business_read" }
  | { kind: "business_write" }
  | { kind: "mixed_request" }
  | { kind: "unsupported_business" };

const BUSINESS_REFERENCE = /\b(?:aca|business|work|company|customer|client|vendor|supplier|lead|deal|pipeline|invoice|quote|order|knowledge\s*base|agent\s+configuration|provider\s+configuration|organization|org\s+data|e-?mails?|mailbox|inbox|calendar|schedule|meetings?|events?|whatsapp\s+business)\b/i;
const PERSONAL_MESSAGING = /\b(?:mom|mum|mother|dad|father|wife|husband|son|daughter|brother|sister|friend|my\s+contact)\b/i;
const SCREEN_ACTION = /\b(?:open|launch|tap|click|press|touch|scroll|swipe|pull\s+down|navigate|go\s+to|type\s+(?:in|into)|enter\s+(?:in|into))\b.{0,100}\b(?:app|screen|page|button|field|input|chrome|whatsapp|messages|settings|calendar|notification|phone|android)\b|\b(?:tap|click|touch|scroll|swipe|pull\s+down)\b/i;
const WRITE_ACTION = /\b(?:send|reply|forward|draft|compose|delete|archive|move|mark\s+(?:as\s+)?read|schedule|reschedule|cancel|create|update|change|configure|set|grant|revoke|connect|disconnect|approve|book|invite|assign|close|remove|add)\b/i;
const READ_ACTION = /\b(?:summari[sz]e|summary|recap|report|check|read|list|show|count|how\s+many|what(?:'s|\s+is|\s+are|\s+were)?|which|who|when|where|find|search|filter|retrieve|look\s+up|review|scan|availability|upcoming|recent|latest|unread|overdue|due|status|compare|explain|tell\s+me)\b/i;
const SEND_SUMMARY_TO_OWNER = /\bsend\s+(?:me|us)\s+(?:(?:a|an|the)\s+)?(?:\w+\s+){0,3}(?:summary|report|recap|overview|count|list)\b/i;

/**
 * Routes explicit ACA/business-data work to ACA's canonical agent chat.
 * Personal messages and device/screen-control work remain in Nexa's existing
 * native path. Mixed requests fail closed so neither side runs only halfway.
 */
export function classifyAcaBusinessIntent(message: string): AcaBusinessIntent | null {
  if (typeof message !== "string" || !message.trim() || message.length > 2000) return null;
  const text = message.trim();
  const hasBusinessReference = BUSINESS_REFERENCE.test(text);
  const explicitBusinessContext = /\baca\b|\bwhatsapp\s+business\b/i.test(text);
  const hasScreenAction = SCREEN_ACTION.test(text);

  // Plain personal WhatsApp/SMS requests are handled by Nexa's reviewed
  // device messaging flow, even though they contain the word "message".
  if (!explicitBusinessContext && PERSONAL_MESSAGING.test(text) &&
      !/\b(?:e-?mail|inbox|calendar|schedule|invoice|customer|client|business|work)\b/i.test(text)) {
    return null;
  }

  if (!hasBusinessReference) return null;
  if (hasScreenAction) {
    if (READ_ACTION.test(text) || WRITE_ACTION.test(text)) return { kind: "mixed_request" };
    return null;
  }
  if (WRITE_ACTION.test(text) && !SEND_SUMMARY_TO_OWNER.test(text)) return { kind: "business_write" };
  if (READ_ACTION.test(text)) return { kind: "business_read" };
  return { kind: "unsupported_business" };
}
