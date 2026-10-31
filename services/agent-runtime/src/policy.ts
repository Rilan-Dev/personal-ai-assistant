import { z } from "zod";
import type { PlannerDecision, ToolCall, ToolOutcome } from "../../../packages/agent-contracts/src/index.ts";
import { requestAllowsNativeTool } from "../../../packages/agent-contracts/src/intent-policy.ts";

/**
 * Trust boundary: the planner receives only these server-owned schemas.
 * The phone independently validates each call again before any native action.
 * Plugins must be explicitly registered here in a later, reviewed phase.
 */
export const ToolIdSchema = z.enum(["device.status", "device.battery_status", "device.open_camera", "device.open_app"]);
export type AllowedToolId = z.infer<typeof ToolIdSchema>;

const EmptyInput = z.object({}).strict();
const AppInput = z.object({ app: z.enum(["whatsapp", "whatsapp-business"]) }).strict();

export const toolCatalog = Object.freeze({
  "device.status": {
    description: "Return non-sensitive Android bridge diagnostics. Does not grant permissions.",
    input: EmptyInput,
    risk: "low"
  },
  "device.battery_status": {
    description: "Read the device battery percentage and charging state, without history.",
    input: EmptyInput,
    risk: "low"
  },
  "device.open_camera": {
    description: "Request opening the camera app. Does NOT capture or record anything.",
    input: EmptyInput,
    risk: "low"
  },
  "device.open_app": {
    description: "Request opening an installed allowlisted WhatsApp application.",
    input: AppInput,
    risk: "low"
  }
} as const);

const ToolOutcomeSchema = z.discriminatedUnion("status", [
  z.object({ status: z.literal("success"), data: z.unknown() }),
  z.object({ status: z.literal("error"), message: z.string().max(500) }),
  z.object({ status: z.literal("blocked"), reason: z.string().max(500) })
]);

const ToolCallSchema = z.object({
  callId: z.string().min(1).max(80),
  toolId: ToolIdSchema,
  input: z.record(z.string(), z.unknown())
}).strict();

export const PlanningRequestSchema = z.object({
  request: z.string().trim().min(1).max(2000),
  availableToolIds: z.array(ToolIdSchema).max(4),
  observations: z.array(z.object({
    call: ToolCallSchema,
    outcome: ToolOutcomeSchema
  }).strict()).max(16)
}).strict();

export type PlanningRequest = z.infer<typeof PlanningRequestSchema>;

export const ModelDecisionSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("tool"), toolId: ToolIdSchema, input: z.record(z.string(), z.unknown()) }).strict(),
  z.object({ type: z.literal("done"), response: z.string().min(1).max(1500) }).strict(),
  z.object({ type: z.literal("clarify"), question: z.string().min(1).max(1500) }).strict()
]);
export type ModelDecision = z.infer<typeof ModelDecisionSchema>;

export function selectCatalog(ids: readonly AllowedToolId[]) {
  const unique = [...new Set(ids)];
  return unique.map(id => ({
    id,
    description: toolCatalog[id].description,
    parameters: z.toJSONSchema(toolCatalog[id].input)
  }));
}

function minimalOutcome(outcome: ToolOutcome): ToolOutcome {
  if (outcome.status === "success") {
    const value = outcome.data;
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      return { status: "success", data: { result: "non-sensitive data withheld" } };
    }
    const d = value as Record<string, unknown>;
    return { status: "success", data: {
      ...(typeof d.intentDispatched === "boolean" ? { intentDispatched: d.intentDispatched } : {}),
      ...(d.verification === "not_observed" ? { verification: "not_observed" } : {}),
      ...(typeof d.platform === "string" ? { platform: d.platform.slice(0, 25) } : {}),
      ...(Number.isInteger(d.levelPercent) && Number(d.levelPercent) >= 0 && Number(d.levelPercent) <= 100
        ? { levelPercent: Number(d.levelPercent) } : {}),
      ...(typeof d.charging === "boolean" ? { charging: d.charging } : {})
    }};
  }
  return outcome.status === "blocked"
    ? { status: "blocked", reason: "Tool blocked by device policy" }
    : { status: "error", message: "Tool execution failed" };
}

export function redactObservations(input: PlanningRequest): PlanningRequest {
  return {
    request: input.request,
    availableToolIds: input.availableToolIds,
    observations: input.observations.map(obs => ({
      call: { callId: obs.call.callId, toolId: obs.call.toolId, input: {} },
      outcome: minimalOutcome(obs.outcome as ToolOutcome)
    }))
  };
}

/** Reject invalid/unauthorized arguments even when an LLM returns correct JSON. */
export function enforceDecision(raw: unknown, input: PlanningRequest): PlannerDecision {
  const result = ModelDecisionSchema.safeParse(raw);
  if (!result.success) {
    return { type: "clarify", question: "The model returned an invalid plan. Nothing was executed." };
  }
  const decision = result.data;
  if (decision.type === "done") {
    const last = input.observations.at(-1);
    if (last?.call.toolId === "device.battery_status" && last.outcome.status === "success") {
      const d = last.outcome.data;
      if (d !== null && typeof d === "object" && !Array.isArray(d)) {
        const data = d as Record<string, unknown>;
        if (typeof data.levelPercent === "number" && Number.isInteger(data.levelPercent) &&
            data.levelPercent >= 0 && data.levelPercent <= 100 &&
            typeof data.charging === "boolean") {
          return { type: "done", response: "Android reports battery at " + data.levelPercent +
            "%; " + (data.charging ? "charging." : "not currently charging.") };
        }
      }
      return { type: "clarify", question: "No validated battery status is available. No action was taken." };
    }
    if (last?.outcome.status === "success" && typeof last.outcome.data === "object" && last.outcome.data !== null &&
        (last.outcome.data as {verification?:unknown}).verification === "not_observed") {
      return { type: "done", response: "Android accepted the requested launch Intent, but the destination screen has not been verified. No further device action was performed." };
    }
    return { type: "done", response: decision.response };
  }
  if (decision.type === "clarify") return decision;
  const id = decision.toolId;
  if (!input.availableToolIds.includes(id)) {
    return { type: "clarify", question: "That tool is not enabled on this device. Nothing was executed." };
  }
  const validated = toolCatalog[id].input.safeParse(decision.input);
  if (!validated.success) {
    return { type: "clarify", question: "The proposed tool arguments were invalid or not allowlisted. Nothing was executed." };
  }
  if (input.observations.length >= 16) {
    return { type: "clarify", question: "Action budget exhausted. No additional action was taken." };
  }
  if (!requestAllowsNativeTool(input.request, id, validated.data)) {
    return { type: "clarify", question: "This request is not a supported single-step device action. Nothing was executed." };
  }
  const call: ToolCall = {
    callId: "step-" + (input.observations.length + 1),
    toolId: id,
    input: validated.data
  };
  return { type: "tool", call };
}
