import type { Tool, Planner } from "./runtime.ts";
import type { ToolCall } from "../../agent-contracts/src/index.ts";

/** Demo-only tools; no data from a device is accessed. */
export function createDemoTools(): Tool[] {
  return [
    { id: "demo.ping", description: "Return local demo diagnostics", capabilities: [], risk: "low", hasExternalSideEffect: false,
      validateInput: (v): v is Record<string, unknown> => !!v && typeof v === "object" && !Array.isArray(v),
      execute: async () => ({ status: "success", data: { message: "Demo engine online; Android tools not connected" } }) },
    { id: "demo.open_app", description: "Simulate app launch, not the real device", capabilities: ["apps.launch"], risk: "low", hasExternalSideEffect: false,
      validateInput: (v): v is Record<string, unknown> => !!v && typeof v === "object" && typeof (v as any).app === "string",
      execute: async input => ({ status: "success", data: { simulated: true, app: input.app } }) },
    { id: "demo.compose_message", description: "Prepare but never send a sample message", capabilities: ["messaging.compose"], risk: "medium", hasExternalSideEffect: false,
      validateInput: (v): v is Record<string, unknown> => !!v && typeof v === "object" && typeof (v as any).recipient === "string" && typeof (v as any).message === "string",
      execute: async input => ({ status: "success", data: { simulated: true, recipient: input.recipient, draft: input.message, sent: false } }) }
  ];
}
export const demoPlanner: Planner = {
  async next(request, observations) {
    if (observations.length) {
      const last = observations.at(-1)!;
      return { type: "done", response: `Demo only: ${JSON.stringify(last.outcome)}. No real device action occurred.` };
    }
    const instruction = request.toLowerCase().trim();
    let call: ToolCall = { callId: "demo-1", toolId: "demo.ping", input: {} };
    if (instruction.includes("camera")) call = { callId: "demo-1", toolId: "demo.open_app", input: { app: "camera" } };
    else if (instruction.includes("whatsapp")) call = { callId: "demo-1", toolId: "demo.open_app", input: { app: "whatsapp" } };
    else if (instruction.includes("birthday")) call = { callId: "demo-1", toolId: "demo.compose_message", input: { recipient: "ROM", message: "Happy Birthday!" } };
    return { type: "tool", call };
  }
};
