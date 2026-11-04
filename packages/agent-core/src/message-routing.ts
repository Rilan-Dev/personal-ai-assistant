/**
 * A deterministic, on-device message-intent parser.
 * It NEVER resolves a contact, chooses an unstated channel, or sends.
 * Untrusted AI text is not an authorization source.
 */
export type MessageAppId = 'whatsapp' | 'whatsapp-business' | 'sms' | 'imo' | 'botim';
export type MessageRequest = {
  recipientHint: string;
  text: string;
  explicitApp: MessageAppId | null;
  reviewOnly: true;
};
export type InstalledMessageApp = {id: MessageAppId; label: string; packageName: string};
export type MessageAppDecision = {kind: 'choose' | 'selected' | 'unavailable'; app: InstalledMessageApp | null; reason: 'explicit' | 'remembered' | 'only_installed' | 'first_use' | 'not_installed'};

/**
 * Reject conversational clauses masquerading as an intent; quoted text
 * after "that" / "saying" is message DATA and may naturally contain "and".
 */
export function parseMessageRequest(raw: string): MessageRequest | null {
  if (typeof raw !== 'string' || raw.length < 14 || raw.length > 850) return null;
  let request = raw.trim().replace(/^hey\s+nexa[,!]?\s+/i, '').trim();
  if (/[\u0000-\u0008\u000b-\u001f]/.test(request)) return null;
  const verb = request.match(/^(?:please\s+)?(?:send|prepare)\s+(?:a\s+)?(?:(whatsapp business|whatsapp|sms|imo|botim)\s+)?(?:text\s+)?(?:message|text|sms)\s+to\s+(?:my\s+)?/i);
  if (!verb) return null;
  const prefixApp = verb[1]?.toLowerCase() ?? null;
  request = request.slice(verb[0].length);
  // An explicit delimiter is mandatory; otherwise a contact name and body
  // cannot be distinguished safely.
  const split = request.match(/^([\p{L}][\p{L}\p{N} .'-]{0,48}?)\s+(?:that\s+|saying\s+)([\s\S]+)$/iu) ??
    request.match(/^([\p{L}][\p{L}\p{N} .'-]{0,48}?):\s*([\s\S]+)$/iu);
  if (!split) return null;
  const hint = split[1].trim().replace(/\s+/g,' ');
  let text = split[2].trim();
  if ((text.startsWith('"') && text.endsWith('"')) ||
      (text.startsWith('“') && text.endsWith('”'))) text = text.slice(1, -1);
  if (!hint || hint.length > 50 || !text.trim() || text.length > 500) return null;
  // The channel can also appear immediately before the recipient.
  const trailing = hint.match(/^(.*)\s+(?:via|on|using)\s+(whatsapp business|whatsapp|sms|imo|botim)$/i);
  const recipientHint = trailing ? trailing[1].trim() : hint;
  const channel = prefixApp || trailing?.[2]?.toLowerCase() || null;
  if (!recipientHint || recipientHint.length > 50 ||
      /\b(?:and then|also send|and send|then open|without consent)\b/i.test(recipientHint))
    return null;
  const explicitApp: MessageAppId | null = channel === 'whatsapp business' ? 'whatsapp-business'
    : channel === 'whatsapp' || channel === 'sms' || channel === 'imo' || channel === 'botim'
      ? channel : null;
  return {recipientHint, text, explicitApp, reviewOnly:true};
}

/** A remembered selection is a local hint, never contact-identity proof. */
export function decideMessageApp(request: MessageRequest, installed: InstalledMessageApp[],
  remembered: MessageAppId | null): MessageAppDecision {
  const preferred=request.explicitApp ?? remembered;
  const found=preferred?installed.find(a=>a.id===preferred):undefined;
  if(preferred && found)return {kind:'selected',app:found,reason:request.explicitApp?'explicit':'remembered'};
  if(request.explicitApp)return {kind:'unavailable',app:null,reason:'not_installed'};
  // No guess or substitution: auto-select only when exactly one supported app
  // is installed and the owner has no explicit or remembered preference.
  if(!preferred && installed.length===1)
    return {kind:'selected',app:installed[0],reason:'only_installed'};
  return {kind:'choose',app:null,reason:remembered?'not_installed':'first_use'};
}

export function recipientMemoryKey(recipient: string): string | null {
  if(typeof recipient!=='string')return null;
  const key=recipient.toLowerCase().replace(/\s+/g,' ').trim();
  return /^[a-z][a-z0-9 '-]{1,49}$/.test(key) ? key : null;
}
