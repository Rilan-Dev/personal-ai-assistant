/** User's explicit text-entry intent, never an LLM command or a submit approval. */
export type ReviewedTextEntry = {
  targetLabel: string;
  exactText: string;
  reviewOnly: true;
};

const unsafeField = /\b(password|passcode|pin|otp|one[ -]?time|verification|security|secret|recovery|payment|card|cvv|cvc|iban|bank|account number|recipient|to address|email address|phone number|amount|transfer|purchase|checkout|submit|send)\b/i;

/** Requires a named text box; ambiguity or unrelated commands fail closed. */
export function parseReviewedTextEntry(raw: string): ReviewedTextEntry | null {
  if (typeof raw !== 'string' || raw.length < 14 || raw.length > 820 ||
      /[\u0000-\u0008\u000b-\u001f]/.test(raw)) return null;
  const cleaned = raw.trim().replace(/^hey\s+nexa[,!]?\s+/i, '')
    .replace(/^please\s+/i, '');
  const m = cleaned.match(/^(?:type|enter|write|insert)\s+([\s\S]+)\s+(?:into|in)\s+(?:the\s+)?(.+)$/i)
    ?? cleaned.match(/^fill\s+(?:the\s+)?(.+?)\s+with\s+([\s\S]+)$/i);
  if (!m) return null;
  const fillFirst = /^fill\b/i.test(cleaned);
  let text = (fillFirst ? m[2] : m[1]).trim();
  let label = (fillFirst ? m[1] : m[2]).trim()
    .replace(/\s+(?:input\s+)?(?:field|box)$/i, '').trim();
  if ((text.startsWith('"') && text.endsWith('"')) ||
      (text.startsWith('“') && text.endsWith('”')) ||
      (text.startsWith("'") && text.endsWith("'")))
    text=text.slice(1,-1);
  if (text.length < 1 || text.length > 500 || !text.trim() ||
      label.length < 2 || label.length > 60 ||
      !/^[\p{L}\p{N}][\p{L}\p{N} _.-]{1,59}$/u.test(label) ||
      unsafeField.test(label) ||
      /\b(?:and then|also open|and send|and submit|and pay|without approval)\b/i.test(cleaned))
    return null;
  return {targetLabel:label,exactText:text,reviewOnly:true};
}
