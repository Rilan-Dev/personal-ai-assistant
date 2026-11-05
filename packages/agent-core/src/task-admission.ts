import type {PlannerDecision, ToolMetadata} from "../../agent-contracts/src/index.ts";
import {bindTaskPlan, TaskPlanRejected, NEXA_TASK_PLAN_VERSION,
  NEXA_TASK_PLAN_MAX_LIFETIME_MS} from "../../agent-contracts/src/task-plan.ts";
import type {BoundTaskPlan, TaskOrigin, TaskEvidenceKind,
  TrustedTaskTool} from "../../agent-contracts/src/task-plan.ts";
import type {Planner} from "./runtime.ts";

/**
 * The common boundary for decisions from local rules, direct BYOK models and
 * remote LangGraph. This is proposal normalization, NOT device execution.
 * All authority (tool metadata, request ID and policy) is supplied locally.
 */
const evidenceByTool: Readonly<Record<string, TaskEvidenceKind>> = Object.freeze({
  "device.status": "native_reading",
  "device.battery_status": "native_reading",
  "device.set_flashlight": "owner_review",
  "device.open_camera": "foreground_package",
  "device.open_app": "foreground_package",
  "device.browser_search": "foreground_package",
  "screen.read": "screen_observation",
  "screen.observe": "screen_observation"
});

export type TrustedPlannerTaskContext = Readonly<{
  taskId: string;
  ownerRequestId: string;
  origin: TaskOrigin;
  policyVersion: string;
  tools: readonly ToolMetadata[];
  nowMs: number;
  allowMediumRisk?: boolean;
}>;

function registeredTools(meta: readonly ToolMetadata[]): TrustedTaskTool[] {
  return meta.filter(tool => Object.hasOwn(evidenceByTool, tool.id)).map(tool => ({
    id: tool.id,
    risk: tool.risk,
    evidence: evidenceByTool[tool.id]!,
    hasExternalSideEffect: tool.hasExternalSideEffect,
    requiresApproval: tool.risk !== "low" || tool.hasExternalSideEffect,
    enabled: true
  }));
}

/**
 * Convert ONE planner-selected tool action into an immutable task proposal.
 * Neither the model's call ID nor JSON permissions can become authority.
 * The task identifier must have been minted by Android, not by a provider.
 */
export function normalizePlannerTaskDecision(
  decision: PlannerDecision,
  ctx: TrustedPlannerTaskContext
): BoundTaskPlan {
  if(decision.type!=="tool") throw new TaskPlanRejected("malformed");
  return bindTaskPlan({
    version:NEXA_TASK_PLAN_VERSION,
    taskId:ctx.taskId,
    ownerRequestId:ctx.ownerRequestId,
    policyVersion:ctx.policyVersion,
    createdAtMs:ctx.nowMs,
    expiresAtMs:ctx.nowMs+NEXA_TASK_PLAN_MAX_LIFETIME_MS,
    steps:[{
      stepId:"step-0001",
      toolId:decision.call.toolId,
      input:decision.call.input,
      dependsOn:[]
    }]
  }, {
    ownerRequestId:ctx.ownerRequestId,
    origin:ctx.origin,
    policyVersion:ctx.policyVersion,
    nowMs:ctx.nowMs,
    tools:registeredTools(ctx.tools),
    allowMediumRisk:ctx.allowMediumRisk===true
  });
}

export type TaskAdmissionPlannerOptions=Readonly<{
  /** Caller must keep this false until the native coordinator is ready. */
  enabled: boolean;
  context: (decision:PlannerDecision) => TrustedPlannerTaskContext | Promise<TrustedPlannerTaskContext>;
  /** Must durably persist a review-only plan before reporting it as saved. */
  persistProposal?: (plan:BoundTaskPlan) => Promise<void>;
}>;

/**
 * Plug this adapter around ANY Planner: local, direct provider or remote API.
 * When enabled, selected tool actions are NEVER forwarded to AgentHarness.
 * Instead, the bound non-executable plan is optionally persisted for review.
 * With no durable storage, or any failed validation, fail closed.
 */
export function createTaskAdmissionPlanner(
  planner:Planner, options:TaskAdmissionPlannerOptions
): Planner {
  if(!options.enabled) return planner;
  return {
    async next(request, observations, signal) {
      if(signal?.aborted) return {type:"clarify", question:"Task was cancelled."};
      const decision=await planner.next(request, observations, signal);
      if(signal?.aborted) return {type:"clarify",question:"Task was cancelled."};
      if(decision.type!=="tool") return decision;
      try {
        const context=await options.context(decision);
        if(signal?.aborted)return {type:"clarify",question:"Task was cancelled."};
        const plan=normalizePlannerTaskDecision(decision,context);
        if(!options.persistProposal)
          return {type:"clarify",question:"Task execution is disabled until the native coordinator is available. Nothing was executed."};
        await options.persistProposal(plan);
        return {type:"clarify",question:"Task proposal saved for your review. No device action was executed."};
      } catch {
        return {type:"clarify",question:"Task proposal was not admitted or stored. No device action was executed."};
      }
    }
  };
}
