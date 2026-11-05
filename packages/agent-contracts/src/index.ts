/** Cross-platform public tool interface. Never give the model unrestricted native method access. */
export type Capability =
  | "apps.launch" | "device.observe" | "device.interact"
  | "contacts.read" | "location.read" | "notifications.read"
  | "messaging.compose" | "messaging.send" | "calls.place"
  | "hardware.flashlight";

export type RiskLevel = "low" | "medium" | "high";
export type ToolInput = Record<string, unknown>;
export type ToolCall = { callId: string; toolId: string; input: ToolInput };
export type ToolMetadata = {
  id: string;
  description: string;
  capabilities: readonly Capability[];
  risk: RiskLevel;
  hasExternalSideEffect: boolean;
};
export type ToolOutcome =
  | { status: "success"; data: unknown }
  | { status: "blocked"; reason: string }
  | { status: "error"; message: string };

export type PlannerDecision =
  | { type: "tool"; call: ToolCall }
  | { type: "done"; response: string }
  | { type: "clarify"; question: string };
export type AgentTraceEvent =
  | { type: "planning"; iteration: number }
  | { type: "executing"; call: ToolCall }
  | { type: "tool_result"; callId: string; outcome: ToolOutcome }
  | { type: "approval_required"; call: ToolCall }
  | { type: "completed"; message: string }
  | { type: "stopped"; reason: string };
export { requestAllowsNativeTool } from "./intent-policy.ts";

export {iconSemanticFromLabel, parseScreenIconSnapshot} from './screen-icons.ts';
export type {ScreenIconSnapshot, ScreenIcon, IconSemantic, IconRect, IconEvidence} from './screen-icons.ts';
// Experimental DSH-style planning contract: deliberately not executable.
export { bindTaskPlan, TaskPlanRejected, NEXA_TASK_PLAN_VERSION,
  NEXA_TASK_V2_ENABLED, NEXA_TASK_PLAN_MAX_STEPS } from "./task-plan.ts";
export type { TaskOrigin, TaskEvidenceKind, TrustedTaskTool, TaskStepProposal,
  TaskPlanProposal, TaskPlanAdmission, BoundTaskStep, BoundTaskPlan,
  TaskPlanRejectCode } from "./task-plan.ts";
