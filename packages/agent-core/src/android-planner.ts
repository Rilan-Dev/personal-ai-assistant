import type { Planner } from "./runtime.ts";
import type { ToolCall } from "../../agent-contracts/src/index.ts";
import { parseNativeOwnerIntent } from "../../agent-contracts/src/intent-policy.ts";

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
      if (last.call.toolId === "device.open_notification_shade") {
        if (result?.notificationShadeAccepted === true) {
          return { type: "done", response:
            "Android accepted the request to open the notification shade. I did not read notification contents." };
        }
        return { type: "clarify", question:
          "Android did not confirm the notification shade request. No notification contents were read." };
      }
      if (result?.intentDispatched === true) {
        return {
          type: "done",
          response: "Android accepted the launch request. This version cannot independently verify the destination screen."
        };
      }
      if (last.call.toolId === "demo.approval_test") {
        return { type: "done", response: "The one-time approval was consumed. This was a safe training action; no device operation occurred." };
      }
      if (last.call.toolId === "device.set_flashlight") {
        if (result?.verification === "observed" &&
            typeof result?.enabled === "boolean") {
          return { type: "done", response:
            "Android confirmed flashlight " + (result.enabled ? "ON" : "OFF") +
            ". Other applications can change its state afterward." };
        }
        return { type: "clarify", question: "The flashlight state was not confirmed; no further action taken." };
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
    const ownerIntent = parseNativeOwnerIntent(request);
    const call: ToolCall | null = request.trim().toLowerCase() === "test approval"
      ? {callId:"native-1",toolId:"demo.approval_test",input:{}}
      : ownerIntent ? {callId:"native-1",...ownerIntent} : null;
    if (call) return { type: "tool", call };
    return {
      type: "clarify",
      question: "Native controls currently support: Open Chrome and search a public query, open the notification shade, open camera or WhatsApp, check device status or battery, turn flashlight on/off, and test approval. Each device action requires its own approval. Multi-step actions and messaging are not enabled yet."
    };
  }
};
