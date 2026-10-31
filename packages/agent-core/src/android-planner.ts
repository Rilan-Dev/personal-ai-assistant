import type { Planner } from "./runtime.ts";
import type { ToolCall } from "../../agent-contracts/src/index.ts";

/**
 * Deterministic first native-command planner. Deliberately NOT an LLM:
 * unrecognized or multi-action commands must not produce partial side effects.
 * Replace with a schema-constrained planner after policy/replay tests are green.
 */
export const androidIntentPlanner: Planner = {
  async next(request, observations) {
    if (observations.length) {
      const last = observations[observations.length - 1];
      if (last.outcome.status !== "success") {
        return { type: "clarify", question: "Android could not perform the requested action. No further actions were taken." };
      }
      const result = last.outcome.data as Record<string, unknown>;
      if (result?.intentDispatched === true) {
        return {
          type: "done",
          response: "Android accepted the launch request. This version cannot independently verify the destination screen."
        };
      }
      if (last.call.toolId === "demo.approval_test") {
        return { type: "done", response: "The one-time approval was consumed. This was a safe training action; no device operation occurred." };
      }
      if (last.call.toolId === "device.battery_status") {
        const level = result?.levelPercent;
        if (typeof level === "number" && Number.isInteger(level) && level >= 0 && level <= 100 &&
            typeof result?.charging === "boolean") {
          return { type: "done",
            response: "Android reports battery at " + level + "%; " +
              (result.charging ? "charging." : "not currently charging.") };
        }
        return { type: "clarify", question: "Battery status was unavailable or invalid; no other actions were taken." };
      }
      if (last.call.toolId === "device.status") {
        return { type: "done", response: "Android's native device bridge responded. This does not establish microphone, accessibility or tool permissions." };
      }
      return { type: "clarify", question: "The action returned an unexpected result; no additional action was taken." };
    }
    const input = request.toLowerCase().trim()
      .replace(/^hey\s+(nexa|assistant)[,!]?\s+/, "")
      .replace(/[.!?]+$/, "").trim();
    let call: ToolCall | null = null;
    if (input === "test approval") {
      call = { callId: "native-1", toolId: "demo.approval_test", input: {} };
    } else if (/^(?:check|show|what is|what's) (?:my |the )?(?:device |phone )?battery(?: level| status)?$/.test(input)) {
      call = { callId: "native-1", toolId: "device.battery_status", input: {} };
    } else if (/^(check|show|what is|what's) (my |the )?(agent |device )?status$/.test(input)) {
      call = { callId: "native-1", toolId: "device.status", input: {} };
    } else {
      const match = input.match(/^(?:please )?(?:open|launch|start) (?:my |the )?(camera|whatsapp business|whatsapp)(?: please)?$/);
      if (match) {
        const target = match[1];
        call = target === "camera"
          ? { callId: "native-1", toolId: "device.open_camera", input: {} }
          : { callId: "native-1", toolId: "device.open_app", input: { app: target === "whatsapp business" ? "whatsapp-business" : "whatsapp" } };
      }
    }
    if (call) return { type: "tool", call };
    return {
      type: "clarify",
      question: "Native controls currently support: Open camera, Open WhatsApp, Open WhatsApp Business, Check device status, Check battery level, and Test approval. Multi-step actions and messaging are not enabled yet."
    };
  }
};
