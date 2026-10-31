import type {
  Capability, ToolCall, ToolMetadata, ToolOutcome, PlannerDecision, AgentTraceEvent, ToolInput
} from "../../agent-contracts/src/index.ts";

export interface ToolExecutionContext {
  readonly signal?: AbortSignal;
  readonly sessionId: string;
}
export interface Tool extends ToolMetadata {
  validateInput: (input: unknown) => input is ToolInput;
  execute: (input: ToolInput, context: ToolExecutionContext) => Promise<ToolOutcome>;
}
export interface Planner {
  next: (request: string, observations: readonly { call: ToolCall; outcome: ToolOutcome }[], signal?: AbortSignal) => Promise<PlannerDecision>;
}
export interface RunOptions {
  request: string;
  sessionId: string;
  grantedCapabilities: ReadonlySet<Capability>;
  approvedFingerprints?: ReadonlySet<string>;
  signal?: AbortSignal;
  onEvent?: (event: AgentTraceEvent) => void;
  maxSteps?: number;
}
export type AgentRunResult =
  | { status: "completed"; response: string; trace: readonly AgentTraceEvent[] }
  | { status: "awaiting_approval"; call: ToolCall; fingerprint: string; trace: readonly AgentTraceEvent[] }
  | { status: "blocked" | "cancelled" | "failed"; reason: string; trace: readonly AgentTraceEvent[] };

export function approvalFingerprint(call: ToolCall, sessionId: string): string {
  // Stable, exact call/args binding. Approval is also scoped to the current session by the caller.
  return JSON.stringify([sessionId, call.callId, call.toolId, sorted(call.input)]);
}
function sorted(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sorted);
  if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    return Object.fromEntries(Object.keys(obj).sort().map(k => [k, sorted(obj[k])]));
  }
  return value;
}
export class ToolRegistry {
  private tools = new Map<string, Tool>();
  register(tool: Tool): void {
    if (!/^[a-z][a-z0-9_.-]+$/.test(tool.id)) throw new Error("Invalid tool id");
    if (this.tools.has(tool.id)) throw new Error(`Tool already registered: ${tool.id}`);
    this.tools.set(tool.id, tool);
  }
  get(id: string): Tool | undefined { return this.tools.get(id); }
  describe(): readonly ToolMetadata[] {
    return [...this.tools.values()].map(({ id, description, risk, capabilities, hasExternalSideEffect }) => ({
      id, description, risk, capabilities: [...capabilities], hasExternalSideEffect
    }));
  }
}

/** Deterministic executor: the model proposes; the policy gate executes. */
export class AgentHarness {
  private readonly tools: ToolRegistry;
  private readonly planner: Planner;
  constructor(tools: ToolRegistry, planner: Planner) { this.tools = tools; this.planner = planner; }

  async run(options: RunOptions): Promise<AgentRunResult> {
    const trace: AgentTraceEvent[] = [];
    const emit = (ev: AgentTraceEvent) => { trace.push(ev); options.onEvent?.(ev); };
    const observations: { call: ToolCall; outcome: ToolOutcome }[] = [];
    const maxSteps = Math.min(Math.max(options.maxSteps ?? 8, 1), 32);
    const processedCalls = new Set<string>();
    const failed = (status: "blocked" | "cancelled" | "failed", reason: string): AgentRunResult => {
      emit({ type: "stopped", reason }); return { status, reason, trace };
    };
    for (let iteration = 0; iteration < maxSteps; iteration++) {
      if (options.signal?.aborted) return failed("cancelled", "Cancelled by user");
      emit({ type: "planning", iteration });
      let decision: PlannerDecision;
      try { decision = await this.planner.next(options.request, observations, options.signal); }
      catch { return failed("failed", "Planning failed"); }
      if (options.signal?.aborted) return failed("cancelled", "Cancelled by user");
      if (decision.type === "done" || decision.type === "clarify") {
        const response = decision.type === "done" ? decision.response : decision.question;
        emit({ type: "completed", message: response });
        return { status: "completed", response, trace };
      }
      const call = decision.call;
      if (processedCalls.has(call.callId)) return failed("blocked", "Duplicate action ID refused");
      processedCalls.add(call.callId);
      const tool = this.tools.get(call.toolId);
      if (!tool) return failed("blocked", `Unknown tool: ${call.toolId}`);
      if (!tool.validateInput(call.input)) return failed("blocked", `Invalid input for ${call.toolId}`);
      const missing = tool.capabilities.filter(cap => !options.grantedCapabilities.has(cap));
      if (missing.length) return failed("blocked", `Permission required: ${missing.join(", ")}`);
      // Side effects always require approval, regardless of model-supplied risk classification.
      const requiresApproval = tool.hasExternalSideEffect || tool.risk === "high";
      const fingerprint = approvalFingerprint(call, options.sessionId);
      if (requiresApproval && !options.approvedFingerprints?.has(fingerprint)) {
        emit({ type: "approval_required", call });
        return { status: "awaiting_approval", call, fingerprint, trace };
      }
      emit({ type: "executing", call });
      let outcome: ToolOutcome;
      try { outcome = await tool.execute(call.input, { signal: options.signal, sessionId: options.sessionId }); }
      catch { outcome = { status: "error", message: "Tool execution failed" }; }
      if (options.signal?.aborted) return failed("cancelled", "Cancelled by user");
      emit({ type: "tool_result", callId: call.callId, outcome });
      observations.push({ call, outcome });
      if (outcome.status === "blocked") return failed("blocked", outcome.reason);
      if (outcome.status === "error") return failed("failed", outcome.message);
    }
    return failed("failed", "Action budget exhausted");
  }
}
