/** Read-only ACA report routing. Pure and separately testable.
 * Unsupported/custom filters go to the existing agent rather than silently
 * broadening the mail query. No email operation is executed here.
 */
export type AcaMailScope = {
  unreadOnly: boolean;
  inboxOnly: boolean;
  window: "all" | "24h";
};
export function parseAcaMailSummaryIntent(text:string):AcaMailScope|null {
  if (typeof text!=="string" || text.length>200 ||
      !/\b(?:e-?mails?|inbox|mailbox)\b/i.test(text))return null;
  if (/\b(?:send|draft|reply|forward|delete|archive|mark|move|schedule|write|compose|whatsapp)\b/i.test(text))return null;
  if (!/\b(?:summari[sz]e|summary|summery|recap|report|check|read|list|show|count|how many|what.s new)\b/i.test(text))return null;
  const normalized=text.replace(/\bfrom\s+(?:the\s+)?(?:last|past)\s+24\s*(?:hours?|hrs?)\b/gi,"last 24 hours");
  const lastDay=/\b(?:24\s*(?:hours?|hrs?)|last\s+day)\b/i.test(normalized);
  const unreadOnly=/\b(?:unread|what.s new)\b/i.test(normalized);
  if(!unreadOnly && !lastDay)return null;
  if (/\b(?:from|before|after|between|specific|sender|subject|yesterday|today|week|month)\b/i.test(normalized))return null;
  return {unreadOnly,inboxOnly:/\binbox\b/i.test(normalized),
    window:unreadOnly&&!lastDay?"all":"24h"};
}
