import type { Planner } from "./runtime.ts";
import type { ToolCall, ToolOutcome, PlannerDecision } from "../../agent-contracts/src/index.ts";
import { requestAllowsNativeTool } from "../../agent-contracts/src/intent-policy.ts";

/**
 * Mobile-side LangGraph planning adapter. The backend returns suggestions
 * only. AgentHarness still validates tool grants, input and approvals locally.
 * Token must be entered by the owner at runtime; never embed it in JS bundles.
 */
export type RemotePlannerConfig = {
  baseUrl: string;
  token: string;
  availableToolIds: readonly string[];
  timeoutMs?: number;
};

export function validateAgentEndpoint(raw: string): string {
  const address = new URL(raw.trim());
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(address.hostname);
  if (address.protocol !== "https:" && !(address.protocol === "http:" && loopback)) {
    throw new Error("A HTTPS URL is required; HTTP is allowed only for loopback development.");
  }
  if (address.username || address.password || address.search || address.hash) {
    throw new Error("Agent URL cannot contain credentials, search parameters or a fragment.");
  }
  if (address.pathname !== "/" && address.pathname !== "") {
    throw new Error("Enter only the base origin, without API path.");
  }
  return address.origin;
}

function minimalOutcome(outcome: ToolOutcome): ToolOutcome {
  if (outcome.status !== "success") {
    return outcome.status === "blocked"
      ? { status: "blocked", reason: "Device policy rejected this action" }
      : { status: "error", message: "Device tool failed" };
  }
  const data = outcome.data;
  if (data !== null && typeof data === "object" && !Array.isArray(data)) {
    const value = data as Record<string, unknown>;
    return { status: "success", data: {
      ...(typeof value.intentDispatched === "boolean" ? {intentDispatched: value.intentDispatched} : {}),
      ...(value.verification === "not_observed" ? {verification: value.verification} : {}),
      ...(typeof value.platform === "string" ? {platform: value.platform.slice(0, 25)} : {}),
      ...(Number.isInteger(value.levelPercent) && Number(value.levelPercent) >= 0 && Number(value.levelPercent) <= 100
          ? {levelPercent: Number(value.levelPercent)} : {}),
      ...(typeof value.charging === "boolean" ? {charging: value.charging} : {}),
      ...(typeof value.enabled === "boolean" && value.verification === "observed"
        ? {enabled: value.enabled, verification: "observed"} : {})
    }};
  }
  return { status: "success", data: {} };
}

function ensureDecision(value: unknown, allowed: Set<string>): PlannerDecision {
  if (!value || typeof value !== "object") throw new Error("Invalid planner response");
  const d = value as Record<string, unknown>;
  if (d.type === "done" && typeof d.response === "string" && d.response.length <= 1500)
    return { type: "done", response: d.response };
  if (d.type === "clarify" && typeof d.question === "string" && d.question.length <= 1500)
    return { type: "clarify", question: d.question };
  if (d.type === "tool" && d.call && typeof d.call === "object") {
    const c = d.call as Record<string, unknown>;
    if (typeof c.callId === "string" && c.callId.length <= 80 &&
        typeof c.toolId === "string" && allowed.has(c.toolId) &&
        c.input !== null && typeof c.input === "object" && !Array.isArray(c.input)) {
      return { type: "tool", call: { callId: c.callId, toolId: c.toolId, input: c.input as Record<string, unknown> } };
    }
  }
  throw new Error("Invalid planner response");
}

export function createRemotePlanner(config: RemotePlannerConfig): Planner {
  const origin = validateAgentEndpoint(config.baseUrl);
  if (config.token.length < 32) throw new Error("Agent token must contain at least 32 characters.");
  const ids: string[] = [...new Set(config.availableToolIds)].filter(id =>
    id === "device.status" || id === "device.battery_status" || id === "device.set_flashlight" || id === "device.open_camera" || id === "device.open_app"
  );
  if (!ids.length) throw new Error("No supported device tools available for remote planning.");
  const allowed = new Set(ids);

  return {
    async next(request, observations: readonly {call: ToolCall; outcome: ToolOutcome}[], signal) {
      if (signal?.aborted) throw new Error("Cancelled");
      // Never forward raw tool responses, screen content, or contact information.
      const safe = observations.map(o => ({
        call: { callId: o.call.callId, toolId: o.call.toolId, input: {} },
        outcome: minimalOutcome(o.outcome)
      })).filter(o => allowed.has(o.call.toolId));
      const controller = new AbortController();
      const listener = () => controller.abort();
      signal?.addEventListener("abort", listener, { once: true });
      const timer = setTimeout(() => controller.abort(), Math.min(config.timeoutMs ?? 12000, 30000));
      try {
        const response = await fetch(origin + "/v1/plan", {
          method: "POST",
          headers: { "content-type": "application/json", "Authorization": "Bearer " + config.token },
          body: JSON.stringify({ request: request.slice(0, 2000), observations: safe.slice(0, 16), availableToolIds: ids }),
          signal: controller.signal
        });
        if (!response.ok) throw new Error("Agent planning service unavailable");
        const body = await response.text();
        if (body.length > 16000) throw new Error("Invalid planner response");
        const decision = ensureDecision((JSON.parse(body) as {decision?: unknown}).decision, allowed);
        if (decision.type === "tool" &&
            !requestAllowsNativeTool(request, decision.call.toolId, decision.call.input)) {
          return { type: "clarify", question: "The remote planner proposed an action outside this request. Nothing was executed." };
        }
        return decision;
      } catch {
        throw new Error("Remote planner request failed");
      } finally {
        clearTimeout(timer);
        signal?.removeEventListener("abort", listener);
      }
    }
  };
}
