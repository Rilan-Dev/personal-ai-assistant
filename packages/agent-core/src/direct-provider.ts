import type { Planner } from "./runtime.ts";
import { androidIntentPlanner } from "./android-planner.ts";
import type { PlannerDecision } from "../../agent-contracts/src/index.ts";
import { parseNativeOwnerIntent, requestAllowsNativeTool } from "../../agent-contracts/src/intent-policy.ts";

/**
 * BYOK direct-to-provider mode. The API key is never bundled into the APK
 * or sent to an agent backend. All providers use fixed HTTPS endpoints.
 * The phone independently validates and authorizes proposed device tools.
 */
export const PROVIDERS = Object.freeze({
  openai: { label: "OpenAI", endpoint: "https://api.openai.com/v1/chat/completions", defaultModel: "gpt-4.1-mini" },
  gemini: { label: "Google Gemini", endpoint: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", defaultModel: "gemini-3.8-flash" },
  groq: { label: "Groq", endpoint: "https://api.groq.com/openai/v1/chat/completions", defaultModel: "openai/gpt-oss-120b" },
  openrouter: { label: "OpenRouter", endpoint: "https://openrouter.ai/api/v1/chat/completions", defaultModel: "openai/gpt-4.1-mini" },
  sarvam: { label: "Sarvam AI", endpoint: "https://api.sarvam.ai/v1/chat/completions", defaultModel: "sarvam-105b" }
});
export type DirectProviderId = keyof typeof PROVIDERS;
export type DirectProviderSettings = {
  version: 1; provider: DirectProviderId; model: string; apiKey: string
};
export const DIRECT_TOOL_IDS = Object.freeze([
  "device.status", "device.battery_status", "device.set_flashlight",
  "device.open_camera", "device.open_app"
]);

export function validateProviderSettings(value: unknown): DirectProviderSettings {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid provider configuration");
  const obj = value as Record<string, unknown>;
  if (Object.keys(obj).length !== 4 || obj.version !== 1 ||
      typeof obj.provider !== "string" || !Object.prototype.hasOwnProperty.call(PROVIDERS, obj.provider) ||
      typeof obj.model !== "string" || !/^[A-Za-z0-9._:/-]{2,96}$/.test(obj.model) ||
      typeof obj.apiKey !== "string" || obj.apiKey.length < 12 || obj.apiKey.length > 512 ||
      obj.apiKey.trim() !== obj.apiKey) throw new Error("Provider, model, or API key is invalid");
  return obj as DirectProviderSettings;
}

const INSTRUCTIONS = [
  "You are a helpful personal AI assistant in an Android app.",
  "Respond with exactly ONE valid JSON object. No Markdown, code fences or extra text.",
  'For general questions: {"type":"done","response":"helpful plain-text answer"}.',
  'If clarification is needed: {"type":"clarify","question":"brief question"}.',
  'For an explicitly requested, supported SINGLE device action only: {"type":"tool","toolId":"device.battery_status","input":{}}.',
  "Allowed native tool IDs and exact inputs:",
  "- device.status {} (check device bridge status)",
  "- device.battery_status {} (check battery level/charging)",
  "- device.open_camera {} (launch camera; NO capture)",
  '- device.open_app {"app":"whatsapp"} OR {"app":"whatsapp-business"} (launch ONLY, no messages)',
  '- device.set_flashlight {"enabled":true} OR {"enabled":false} (one-time approval + CAMERA grant)',
  "NEVER claim that you can send messages, read contacts, listen in the background, or control other apps.",
  "NEVER invent tool observations or claim an action succeeded before the phone confirms it.",
  "A multi-step, ambiguous or unsupported phone-control request must be a clarify answer, never a partial tool action.",
  "Only select a device tool when requested explicitly. The phone rejects unapproved/invalid calls.",
  "Keep output under 1200 characters; use simple plain text."
].join("\n");

type Fetcher = typeof fetch;
export type DirectProviderOptions = {
  settings: DirectProviderSettings;
  availableToolIds: readonly string[];
  fetchImpl?: Fetcher;
  timeoutMs?: number;
};

function parseDecision(value: unknown, ids: Set<string>, request: string): PlannerDecision {
  if (value === null || typeof value !== "object" || Array.isArray(value))
    throw new Error("INVALID_RESPONSE");
  const d = value as Record<string, unknown>;
  if (d.type === "done" && typeof d.response === "string" && d.response.trim().length > 0 &&
      d.response.length <= 1500 && Object.keys(d).length === 2) {
    // A model must never falsely claim it executed a native action,
    // sent a message or read private data merely by answering in prose.
    const exactDeviceRead = parseNativeOwnerIntent(request) !== null;
    if (exactDeviceRead || /^(?:please\s+)?(?:open|launch|start|turn|switch|send|text|call|dial|read|delete|toggle)\b/i.test(request.trim()))
      return {type:"clarify",question:"No device action was executed. Use a single supported command and confirm permissions."};
    return {type:"done",response:d.response.trim()};
  }
  if (d.type === "clarify" && typeof d.question === "string" && d.question.trim().length > 0 &&
      d.question.length <= 1500 && Object.keys(d).length === 2)
    return {type:"clarify",question:d.question.trim()};
  if (d.type !== "tool" || typeof d.toolId !== "string" || !ids.has(d.toolId) ||
      !d.input || typeof d.input !== "object" || Array.isArray(d.input) || Object.keys(d).length !== 3)
    return {type:"clarify",question:"The provider proposed an unsupported or invalid action. Nothing was executed."};
  const input = d.input as Record<string,unknown>;
  const valid = d.toolId === "device.open_app"
    ? Object.keys(input).length === 1 && (input.app === "whatsapp" || input.app === "whatsapp-business")
    : d.toolId === "device.set_flashlight"
      ? Object.keys(input).length === 1 && typeof input.enabled === "boolean"
      : Object.keys(input).length === 0;
  if (!valid || !requestAllowsNativeTool(request,d.toolId,input))
    return {type:"clarify",question:"The provider suggested an action outside your exact request. Nothing was executed."};
  return {type:"tool",call:{callId:"direct-1",toolId:d.toolId,input}};
}

function providerError(status: number): Error {
  if (status === 401 || status === 403) return new Error("AUTH_FAILED");
  if (status === 429) return new Error("RATE_LIMIT");
  if (status === 404) return new Error("MODEL_NOT_FOUND");
  if (status >= 500) return new Error("PROVIDER_UNAVAILABLE");
  return new Error("PROVIDER_HTTP_" + status);
}

export function createDirectProviderPlanner(options: DirectProviderOptions): Planner {
  const cfg = validateProviderSettings(options.settings);
  const provider = PROVIDERS[cfg.provider];
  const ids = [...new Set(options.availableToolIds)].filter(id => DIRECT_TOOL_IDS.includes(id));
  const allowed = new Set(ids);
  const fetcher = options.fetchImpl ?? fetch;
  return {
    async next(request, observations, signal) {
      // Once Android has acted, do not let the LLM invent the outcome.
      if (observations.length > 0) return androidIntentPlanner.next(request, observations, signal);
      if (signal?.aborted) throw new Error("CANCELLED");
      // A supported, explicit user request should not fail because a model
      // demands a magic wording or guesses an unrelated tool. The proposal
      // still goes through the full phone-side AgentHarness grant, approval,
      // journal and native argument validation before any side effect.
      const intended = parseNativeOwnerIntent(request);
      if (intended && allowed.has(intended.toolId))
        return {type:"tool",call:{callId:"direct-1",...intended}};
      if (request.length < 1 || request.length > 2000) throw new Error("INVALID_REQUEST");
      const controller = new AbortController();
      const onAbort = () => controller.abort();
      signal?.addEventListener("abort",onAbort,{once:true});
      const timeout = setTimeout(() => controller.abort(),Math.min(Math.max(options.timeoutMs ?? 20000,1000),30000));
      try {
        const response = await fetcher(provider.endpoint,{
          method:"POST",
          redirect:"error",
          headers:cfg.provider === "sarvam"
            ? {"content-type":"application/json","api-subscription-key":cfg.apiKey}
            : {"content-type":"application/json",Authorization:"Bearer "+cfg.apiKey},
          body:JSON.stringify({
            model:cfg.model,
            // Sarvam V1 supports JSON mode, useful for explicit bounded
            // tool proposals. The native Android policy still validates
            // the output and never trusts LLM-generated calls directly.
            ...(cfg.provider==="sarvam"
              ? {response_format:{type:"json_object"},max_tokens:2048}
              : {}),
            messages:[
              {role:"system",content:INSTRUCTIONS + "\nAvailable tools on this phone: " + ids.join(", ")},
              {role:"user",content:request}
            ]
          }),
          signal:controller.signal
        });
        if (!response.ok) throw providerError(response.status);
        const body = await response.text();
        if (body.length > 24000) throw new Error("INVALID_RESPONSE");
        let outer: {choices?:Array<{message?:{content?:unknown}}>};
        try { outer = JSON.parse(body) as typeof outer; }
        catch { throw new Error("INVALID_RESPONSE"); }
        const content = outer?.choices?.[0]?.message?.content;
        if (typeof content !== "string" || content.length > 8000) throw new Error("INVALID_RESPONSE");
        let json: unknown;
        const trimmed = content.trim();
        // Some OpenAI-compatible providers wrap structured JSON in one
        // Markdown code fence. Accept only one complete fence, not prose.
        const fenced = trimmed.match(/^```(?:json)?\s*\n([\s\S]*?)\n```$/i);
        const text = fenced ? fenced[1] : trimmed;
        try { json = JSON.parse(text); }
        catch { throw new Error("INVALID_RESPONSE"); }
        return parseDecision(json,allowed,request);
      } catch(err) {
        if (signal?.aborted) throw new Error("CANCELLED");
        if (err instanceof Error && /^(AUTH_FAILED|RATE_LIMIT|MODEL_NOT_FOUND|PROVIDER_UNAVAILABLE|INVALID_RESPONSE|PROVIDER_HTTP_\d+)$/.test(err.message)) throw err;
        throw new Error("NETWORK_OR_TIMEOUT");
      } finally {
        clearTimeout(timeout);
        signal?.removeEventListener("abort",onAbort);
      }
    }
  };
}

/** Explicit owner-triggered connection check; executes no Android tool. */
export async function testDirectProvider(
  settings: DirectProviderSettings, fetchImpl?: Fetcher
): Promise<boolean> {
  const planner = createDirectProviderPlanner({settings,availableToolIds:[],fetchImpl});
  const result = await planner.next("Respond with a JSON done response containing the word Connected.",[]);
  if (result.type !== "done") throw new Error("INVALID_RESPONSE");
  return true;
}
