/**
 * Device-side intent gate. The model can plan, but its output never supplies
 * proof that the owner asked to launch an app or activate hardware.
 *
 * Recognize polite, natural *single action* requests while rejecting mixed,
 * negated and quoted commands. Unknown intents do not become device actions.
 */
export function normalizeNativeRequest(request: string): string | null {
  if (typeof request !== "string" || request.length < 1 || request.length > 350) return null;
  let text = request.toLowerCase().trim().replace(/[.!?]+$/, "").trim();
  text = text.replace(/^hey\s+(?:nexa|assistant)[,!]?\s+/, "").trim();
  // Clauses and negation are never auto-reduced to one side effect.
  if (/[\n;"“”‘’]|(?:\b(?:and|then|also|after|before|while|or|but|without|instead|send|text|message|call|delete|don't|do not|never|not|no)\b)/.test(text))
    return null;
  // Normalize pragmatic owner instructions without treating arbitrary
  // sentences as permission. The prompt must still resolve to ONE exact
  // supported action and the native executor validates it independently.
  const courtesy = /^(?:would you mind (?:please )?|(?:can|could|would|will) you (?:please )?|please )/;
  const hadCourtesy = courtesy.test(text);
  text = text.replace(/^would you mind (?:please )?/, "")
    .replace(/^(?:can|could|would|will) you (?:please )?/, "")
    .replace(/^please /, "").trim()
    .replace(/^i (?:want|need) you to /, "")
    .replace(/^i would like you to /, "")
    .replace(/^i'd like you to /, "").trim()
    .replace(/\s+please$/, "").replace(/\s+for me$/, "").trim();
  // Speech recognition sometimes adds the standalone "I" after "can you
  // please". Never treat an unprompted "I open WhatsApp" statement as an
  // action; only correct that token following an explicit request prefix.
  if (hadCourtesy) text = text.replace(/^i (?=(?:open|launch|start)\b)/, "");
  if (text.startsWith("opening ")) text = "open " + text.slice(8);
  if (text.startsWith("launching ")) text = "launch " + text.slice(10);
  return text;
}

export function parseFlashlightIntent(normalized: string): boolean | null {
  const prefix = normalized.match(/^(?:turn|switch) (on|off) (?:my |the )?(?:phone )?(?:flashlight|torch)$/);
  if (prefix) return prefix[1] === "on";
  const suffix = normalized.match(/^(?:turn|switch) (?:my |the )?(?:phone )?(?:flashlight|torch) (on|off)$/);
  return suffix ? suffix[1] === "on" : null;
}

export function parseChromeSearchIntent(request: string): string | null {
  if (typeof request !== "string" || request.length < 12 || request.length > 250 ||
      /[\n\r;\"“”‘’]/.test(request)) return null;
  let text = request.toLowerCase().trim().replace(/[.!?]+$/, "").trim();
  text = text.replace(/^hey\s+(?:nexa|assistant)[,!]?\s+/, "");
  text = text.replace(/^(?:(?:can|could|would|will) you (?:please )?|please )/, "").trim();
  const m = text.match(/^(?:open|launch|start) (?:my |the )?chrome(?: browser)? and (?:search(?: for)?|look up) ([a-z0-9][a-z0-9 -]{1,89})$/);
  if (!m) return null;
  const query=m[1].trim();
  if (query.length < 3 || /\b(?:send|text|message|call|dial|pay|buy|purchase|delete|remove|grant|install|uninstall|password|otp|stop|cancel|without|never|instead)\b/.test(query)) return null;
  return query;
}

export type NativeOwnerIntent = {
  toolId: "device.status" | "device.battery_status" | "device.set_flashlight" |
    "device.open_camera" | "device.open_app" | "device.browser_search";
  input: Record<string, unknown>;
};

export function parseNativeOwnerIntent(request: string): NativeOwnerIntent | null {
  const chromeQuery=parseChromeSearchIntent(request);
  if(chromeQuery) return {toolId:"device.browser_search",input:{query:chromeQuery}};
  const text = normalizeNativeRequest(request);
  if (text === null) return null;
  const desired = parseFlashlightIntent(text);
  if (desired !== null) return {toolId:"device.set_flashlight",input:{enabled:desired}};
  if (/^(?:check|show|what is|what's) (?:my |the )?(?:agent |device )?status$/.test(text))
    return {toolId:"device.status",input:{}};
  if (/^(?:(?:check|show|what is|what's) (?:my |the )?(?:device |phone )?(?:battery|charge)(?: level| status| percentage| percent)?|(?:what is|what's) (?:my |the )?(?:device |phone )?(?:battery|charge) percentage|how much (?:battery|charge) (?:do i have|is left)|how (?:much|many) percent (?:battery|charge) (?:do i have|is left))$/.test(text))
    return {toolId:"device.battery_status",input:{}};
  if (/^(?:open|launch|start|turn on) (?:my |the )?camera(?: app)?$/.test(text))
    return {toolId:"device.open_camera",input:{}};
  const app = text.match(/^(?:open|launch|start) (?:my |the )?(whatsapp business|whatsapp)(?: app)?$/);
  if (app) return {toolId:"device.open_app",input:{app:app[1]==="whatsapp business"?"whatsapp-business":"whatsapp"}};
  return null;
}

export function requestAllowsNativeTool(
  request: string, toolId: string, input: Record<string, unknown>
): boolean {
  const intent = parseNativeOwnerIntent(request);
  if (!intent || intent.toolId !== toolId || !input || typeof input !== "object" || Array.isArray(input))
    return false;
  const expected = intent.input;
  return Object.keys(input).length === Object.keys(expected).length &&
    Object.entries(expected).every(([k,v])=>Object.prototype.hasOwnProperty.call(input,k) && input[k]===v);
}
