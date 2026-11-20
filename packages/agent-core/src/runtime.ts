import type {
  Capability, ToolCall, ToolMetadata, ToolOutcome, PlannerDecision, AgentTraceEvent, ToolInput
} from "../../agent-contracts/src/index.ts";
import type { DurableActionJournal, SessionCheckpoint, SessionStatus, ObservedAction } from "./journal.ts";

export interface ToolExecutionContext {
  readonly signal?: AbortSignal;
  readonly sessionId: string;
  /** Android verifies the exact claimed owner-review step before dispatch. */
  readonly callId?: string;
  readonly approvalFingerprint?: string;
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
  /** @deprecated Bare fingerprints cannot authorize execution. */
  approvedFingerprints?: ReadonlySet<string>;
  journal?: DurableActionJournal;
  /** Resume only after explicit owner choice; never automatically on boot. */
  resume?: boolean;
  signal?: AbortSignal;
  onEvent?: (event: AgentTraceEvent) => void;
  /** Receives only normalized, allowlisted planner failure codes. */
  onPlanningError?: (code: string) => void;
  /** Receives only allowlisted native tool error codes; exception text is never exposed. */
  onToolError?: (code: string) => void;
  /** Project observations for persistence without changing the in-memory tool results. */
  checkpointObservations?: (observations: readonly ObservedAction[]) => readonly ObservedAction[];
  maxSteps?: number;
}
export type AgentRunResult =
  | { status: "completed"; response: string; trace: readonly AgentTraceEvent[] }
  | { status: "awaiting_approval"; call: ToolCall; fingerprint: string; trace: readonly AgentTraceEvent[] }
  | { status: "blocked" | "cancelled" | "failed"; reason: string; trace: readonly AgentTraceEvent[] };

const SAFE_NATIVE_TOOL_ERROR_CODES = new Set([
  "ACCESSIBILITY_DISABLED",
  "INVALID_SCROLL",
  "OWNER_ACTIONS_PAUSED",
  "REVIEWED_STEP_NOT_CLAIMED",
  "SCROLL_ACTION_NOT_ACCEPTED",
  "SCROLL_CONTEXT_CHANGED",
  "SCROLL_DISPATCH_REJECTED",
  "SCROLL_JOURNAL_DENIED",
  "SCROLL_WINDOW_UNAVAILABLE",
  "SCROLL_CONTAINER_UNAVAILABLE",
  "SCROLL_TARGET_AMBIGUOUS",
  "SCROLL_OUTCOME_UNCERTAIN",
  "SCROLL_TARGET_UNAVAILABLE",
  "STALE_SCREEN_SNAPSHOT",
  "UI_TAP_TARGET_UNAVAILABLE",
  "UI_TAP_TARGET_AMBIGUOUS",
  "UI_TAP_HIERARCHY_UNVERIFIED",
  "UI_TAP_DISPATCH_REJECTED",
  "UI_TAP_DENIED",
  "UI_TAP_UNCERTAIN",
  "UI_EVIDENCE_NOT_COMMITTED",
  "UI_OUTCOME_UNCERTAIN",
  "KEY_ACTION_REFUSED",
  "KEY_ACTION_AUDIT_UNCERTAIN",
  "KEY_ACTION_UNCERTAIN",
  "TEXT_FIELD_UNVERIFIED",
  "TEXT_OUTCOME_UNCERTAIN",
  "TEXT_PARENT_EVIDENCE_FAILED",
  "TEXT_REVIEW_INVALID",
  "APP_NOT_UNIQUE_OR_INSTALLED",
  "APP_LAUNCH_OUTCOME_UNKNOWN",
  "APP_LAUNCH_REFUSED",
  "INVALID_SEARCH",
  "CHROME_NOT_VERIFIED",
  "NOTIFICATION_SHADE_FAILED",
  "NO_CAMERA",
  "CAMERA_DENIED",
  "CAMERA_OUTCOME_UNKNOWN",
  "SCREEN_OBSERVATION_UNAVAILABLE",
  "CHAT_STEP_NOT_CLAIMED",
  "CHAT_ROW_UNVERIFIED",
  "CHAT_HEADER_NOT_VERIFIED",
  "CHAT_EVIDENCE_NOT_COMMITTED",
  "CHAT_VERIFICATION_UNCERTAIN",
  "CHAT_NAVIGATION_UNCERTAIN",
  "MESSAGE_STEP_NOT_CLAIMED",
  "MESSAGE_RECIPIENT_UNVERIFIED",
  "MESSAGE_COMPOSER_UNVERIFIED",
  "MESSAGE_DRAFT_UNCERTAIN",
  "MESSAGE_EVIDENCE_NOT_COMMITTED",
  "MESSAGE_SEND_NOT_CLAIMED",
  "MESSAGE_APP_NOT_SUPPORTED",
  "MESSAGE_SEND_UNCERTAIN"
]);

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
    let observations: ObservedAction[] = [];
    let resumePendingCall: ToolCall | undefined;
    const maxSteps = Math.min(Math.max(options.maxSteps ?? 8, 1), 32);
    const processedCalls = new Set<string>();
    const journal = options.journal;
    let checkpoint: SessionCheckpoint = {
      version: 1, sessionId: options.sessionId, request: options.request,
      observations: [], status: "running", updatedAt: Date.now()
    };
    const save = async (status: SessionStatus, pendingCall?: ToolCall) => {
      if (!journal) return;
      const checkpointObservations=options.checkpointObservations?.(observations)??observations;
      checkpoint = { ...checkpoint, observations: [...checkpointObservations], status,
        updatedAt: Date.now(), pendingCall };
      await journal.saveSession(checkpoint);
    };
    const failed = async (status: "blocked" | "cancelled" | "failed", reason: string): Promise<AgentRunResult> => {
      emit({ type: "stopped", reason });
      try { await save(status); } catch { /* Never retry an already claimed action. */ }
      return { status, reason, trace };
    };
    try {
      if (journal && options.resume) {
        const previous = await journal.loadSession(options.sessionId);
        if (!previous || previous.version !== 1 || previous.request !== options.request)
          return { status: "blocked", reason: "No matching resumable session", trace };
        if (!["running", "awaiting_approval"].includes(previous.status))
          return { status: "blocked", reason: "Session already finalized", trace };
        if (!Array.isArray(previous.observations) || previous.observations.length > 32)
          return { status: "blocked", reason: "Invalid session history", trace };
        if (previous.status === "awaiting_approval") {
          if (!previous.pendingCall)
            return { status: "blocked", reason: "Saved approval action unavailable", trace };
          // Resume the exact call shown to and approved by the owner. Asking
          // the model to plan again could produce a different action under
          // the same approval, so the journal remains the only authority.
          resumePendingCall = previous.pendingCall;
        }
        checkpoint = previous;
        observations = [...previous.observations];
        observations.forEach(o => processedCalls.add(o.call.callId));
      } else if (journal) {
        if (await journal.loadSession(options.sessionId))
          return { status: "blocked", reason: "Session ID already used", trace };
        await save("running");
      }
    } catch {
      return { status: "blocked", reason: "Secure journal unavailable; no actions executed", trace };
    }
    for (let iteration = observations.length; iteration < maxSteps; iteration++) {
      if (options.signal?.aborted) return failed("cancelled", "Cancelled by user");
      emit({ type: "planning", iteration });
      let decision: PlannerDecision;
      try {
        if (resumePendingCall) {
          decision = { type: "tool", call: resumePendingCall };
          resumePendingCall = undefined;
        } else decision = await this.planner.next(options.request, observations, options.signal);
      }
      catch (error) {
        const raw=error instanceof Error?error.message:"";
        const code=/^(?:AUTH_FAILED|RATE_LIMIT|MODEL_NOT_FOUND|PROVIDER_UNAVAILABLE|INVALID_RESPONSE|NETWORK_OR_TIMEOUT|PROVIDER_HTTP_\d{3})$/.test(raw)
          ?raw:null;
        if(code&&options.onPlanningError) {
          try { options.onPlanningError(code); } catch { /* Diagnostics never change task outcome. */ }
        }
        return failed("failed", "Planning failed");
      }
      if (options.signal?.aborted) return failed("cancelled", "Cancelled by user");
      if (decision.type === "done" || decision.type === "clarify") {
        const response = decision.type === "done" ? decision.response : decision.question;
        try { await save("completed"); }
        catch { return failed("failed", "Could not persist completed session"); }
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
      // Side effects always require durable one-time approval, regardless of model claims.
      const requiresApproval = tool.hasExternalSideEffect || tool.risk === "high";
      const fingerprint = approvalFingerprint(call, options.sessionId);
      if (requiresApproval && !journal) {
        emit({ type: "approval_required", call });
        return { status: "awaiting_approval", call, fingerprint, trace };
      }
      if (journal) {
        try {
          if (requiresApproval) {
            if (!await journal.prepareApproval(options.sessionId, call.callId, fingerprint))
              return failed("blocked", "Action already claimed or approval changed");
            await save("awaiting_approval", call);
          }
          // Atomically journal BEFORE dispatch. False means unapproved or already attempted.
          if (!await journal.claimAction(options.sessionId, call.callId, fingerprint, requiresApproval)) {
            if (requiresApproval) {
              emit({ type: "approval_required", call });
              return { status: "awaiting_approval", call, fingerprint, trace };
            }
            return failed("blocked", "Action already reserved or outcome uncertain; replay forbidden");
          }
          await save("running");
        } catch { return failed("blocked", "Could not reserve action securely; no replay allowed"); }
      }
      emit({ type: "executing", call });
      let outcome: ToolOutcome;
      try { outcome = await tool.execute(call.input, { signal: options.signal, sessionId: options.sessionId,
        callId: call.callId, approvalFingerprint: fingerprint }); }
      catch (error) {
        const code = error && typeof error === "object" && "code" in error &&
          typeof error.code === "string" && SAFE_NATIVE_TOOL_ERROR_CODES.has(error.code)
          ? error.code : null;
        if (code && options.onToolError) {
          try { options.onToolError(code); } catch { /* Diagnostics never change task outcome. */ }
        }
        outcome = { status: "error", message: "Tool execution failed" };
      }
      if (journal) {
        try { await journal.finishAction(options.sessionId, call.callId,
          options.signal?.aborted === true || outcome.status === "error"); }
        catch { return failed("failed", "Action outcome uncertain; replay forbidden"); }
      }
      if (options.signal?.aborted) return failed("cancelled", "Cancelled; action outcome may be uncertain");
      emit({ type: "tool_result", callId: call.callId, outcome });
      observations.push({ call, outcome });
      try { await save("running"); }
      catch { return failed("failed", "Could not persist observation; replay forbidden"); }
      if (outcome.status === "blocked") return failed("blocked", outcome.reason);
      if (outcome.status === "error") return failed("failed", outcome.message);
    }
    return failed("failed", "Action budget exhausted");
  }
}
