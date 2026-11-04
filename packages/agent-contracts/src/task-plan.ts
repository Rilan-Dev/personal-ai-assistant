/**
 * DSH-inspired, Android-safe proposed-task admission contract.
 *
 * This is NOT an execution API. All data from an LLM, a provider, or a remote
 * planner is untrusted. The trusted Android-side registry supplies risk,
 * evidence and approval requirements; the proposal can supply none of them.
 *
 * No action is dispatched by this module. Until a separately reviewed native
 * coordinator exists, NEXA_TASK_V2_ENABLED remains false.
 */
import type {RiskLevel, ToolInput} from './index.ts';

export const NEXA_TASK_PLAN_VERSION = 1 as const;
export const NEXA_TASK_V2_ENABLED = false as const;
export const NEXA_TASK_PLAN_MAX_STEPS = 8 as const;
export const NEXA_TASK_PLAN_MAX_LIFETIME_MS = 5 * 60_000;

export type TaskOrigin = 'chat' | 'live-voice' | 'assistant-role' | 'overlay';
export type TaskEvidenceKind = 'native_reading' | 'foreground_package' |
  'screen_observation' | 'owner_review';

/** Registered by trusted code, not reported by a language model. */
export type TrustedTaskTool = {
  id: string;
  risk: RiskLevel;
  evidence: TaskEvidenceKind;
  hasExternalSideEffect: boolean;
  requiresApproval: boolean;
  enabled: boolean;
};

export type TaskStepProposal = {
  stepId: string;
  toolId: string;
  input: ToolInput;
  dependsOn: string[];
};
export type TaskPlanProposal = {
  version: typeof NEXA_TASK_PLAN_VERSION;
  taskId: string;
  ownerRequestId: string;
  policyVersion: string;
  createdAtMs: number;
  expiresAtMs: number;
  steps: TaskStepProposal[];
};
export type TaskPlanAdmission = {
  ownerRequestId: string;
  origin: TaskOrigin;
  policyVersion: string;
  nowMs: number;
  tools: readonly TrustedTaskTool[];
  /** Medium-risk tools still require explicit, policy-controlled rollout. */
  allowMediumRisk?: boolean;
};
export type BoundTaskStep = Readonly<{
  stepId: string;
  toolId: string;
  input: Readonly<ToolInput>;
  dependsOn: readonly string[];
  risk: RiskLevel;
  expectedEvidence: TaskEvidenceKind;
  requiresApproval: boolean;
}>;
export type BoundTaskPlan = Readonly<{
  version: typeof NEXA_TASK_PLAN_VERSION;
  taskId: string;
  ownerRequestId: string;
  origin: TaskOrigin;
  policyVersion: string;
  expiresAtMs: number;
  steps: readonly BoundTaskStep[];
  /** Admission is never the authorization to execute on Android. */
  executable: false;
}>;

export type TaskPlanRejectCode =
  | 'malformed' | 'oversized' | 'stale' | 'scope_mismatch' | 'dependency'
  | 'unknown_tool' | 'disabled_tool' | 'unsafe_tool';

export class TaskPlanRejected extends Error {
  readonly code: TaskPlanRejectCode;
  constructor(code: TaskPlanRejectCode) {
    super('Task plan refused: ' + code);
    this.name = 'TaskPlanRejected';
    this.code = code;
  }
}

function reject(code: TaskPlanRejectCode): never {
  throw new TaskPlanRejected(code);
}
function record(x: unknown): x is Record<string, unknown> {
  if (x === null || typeof x !== 'object' || Array.isArray(x)) return false;
  const proto = Object.getPrototypeOf(x);
  return proto === Object.prototype || proto === null;
}
function fields(x: unknown, required: readonly string[]): x is Record<string, unknown> {
  return record(x) && Object.keys(x).length === required.length &&
    required.every(k => Object.hasOwn(x, k));
}
const id = (x: unknown) => typeof x === 'string' &&
  /^[A-Za-z0-9][A-Za-z0-9_-]{7,99}$/.test(x);
const stepId = (x: unknown) => typeof x === 'string' &&
  /^step-[A-Za-z0-9_-]{3,72}$/.test(x);
const toolId = (x: unknown) => typeof x === 'string' &&
  /^[a-z][a-z0-9_.-]{2,79}$/.test(x);
const policyId = (x: unknown) => typeof x === 'string' &&
  /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$/.test(x);
const integer = (x: unknown): x is number => typeof x === 'number' &&
  Number.isSafeInteger(x);

/** Only bounded JSON data; no prototypes, functions or dangerous property names. */
function jsonData(x: unknown, depth = 0): boolean {
  if (depth > 5) return false;
  if (x === null || typeof x === 'string' && x.length <= 500 ||
      typeof x === 'boolean' || typeof x === 'number' && Number.isFinite(x)) return true;
  if (Array.isArray(x)) return x.length <= 16 && x.every(v => jsonData(v, depth + 1));
  if (!record(x) || Object.keys(x).length > 24) return false;
  return Object.entries(x).every(([k, v]) =>
    k.length > 0 && k.length <= 80 &&
    !['__proto__', 'constructor', 'prototype'].includes(k) &&
    jsonData(v, depth + 1));
}
function freezeJson<T>(x: T): T {
  if (x && typeof x === 'object') {
    for (const value of Object.values(x)) freezeJson(value);
    Object.freeze(x);
  }
  return x;
}

/**
 * Validate a planner's proposal against an owner-minted request identifier and
 * a separate trusted tool registry. Reject extra fields rather than accepting
 * model-provided approvals, evidence, risk labels or tool permissions.
 *
 * The result is immutable and non-executable. A future coordinator MUST still
 * check actual Android permissions and issue a durable one-shot action claim.
 */
export function bindTaskPlan(value: unknown, ctx: TaskPlanAdmission): BoundTaskPlan {
  if (!fields(value, [
    'version', 'taskId', 'ownerRequestId', 'policyVersion',
    'createdAtMs', 'expiresAtMs', 'steps'
  ]) || value.version !== NEXA_TASK_PLAN_VERSION || !id(value.taskId) ||
    !id(value.ownerRequestId) || !policyId(value.policyVersion) ||
    !integer(value.createdAtMs) || !integer(value.expiresAtMs) ||
    !Array.isArray(value.steps) || value.steps.length < 1 ||
    value.steps.length > NEXA_TASK_PLAN_MAX_STEPS) reject('malformed');

  if (!integer(ctx.nowMs) || !id(ctx.ownerRequestId) || !policyId(ctx.policyVersion) ||
      !['chat', 'live-voice', 'assistant-role', 'overlay'].includes(ctx.origin) ||
      !Array.isArray(ctx.tools)) reject('malformed');
  if (value.ownerRequestId !== ctx.ownerRequestId ||
      value.policyVersion !== ctx.policyVersion) reject('scope_mismatch');
  if (value.createdAtMs < ctx.nowMs - NEXA_TASK_PLAN_MAX_LIFETIME_MS ||
      value.createdAtMs > ctx.nowMs + 30_000 ||
      value.expiresAtMs <= ctx.nowMs ||
      value.expiresAtMs <= value.createdAtMs ||
      value.expiresAtMs - value.createdAtMs > NEXA_TASK_PLAN_MAX_LIFETIME_MS)
    reject('stale');

  let serialized: string;
  try { serialized = JSON.stringify(value); } catch { return reject('malformed'); }
  if (serialized.length > 12_000) reject('oversized');

  const toolRules = new Map<string, TrustedTaskTool>();
  for (const rule of ctx.tools) {
    if (!rule || !toolId(rule.id) || toolRules.has(rule.id) ||
        !['low', 'medium', 'high'].includes(rule.risk) ||
        !['native_reading', 'foreground_package', 'screen_observation', 'owner_review'].includes(rule.evidence) ||
        typeof rule.enabled !== 'boolean' ||
        typeof rule.hasExternalSideEffect !== 'boolean' ||
        typeof rule.requiresApproval !== 'boolean') reject('malformed');
    toolRules.set(rule.id, rule);
  }

  const seen = new Set<string>();
  const bound: BoundTaskStep[] = [];
  for (const raw of value.steps as unknown[]) {
    if (!fields(raw, ['stepId', 'toolId', 'input', 'dependsOn']) ||
        !stepId(raw.stepId) || !toolId(raw.toolId) ||
        !record(raw.input) || !jsonData(raw.input) ||
        !Array.isArray(raw.dependsOn) ||
        raw.dependsOn.length > NEXA_TASK_PLAN_MAX_STEPS ||
        raw.dependsOn.some(dep => !stepId(dep))) reject('malformed');

    const rawInput = JSON.stringify(raw.input);
    if (rawInput.length > 2048) reject('oversized');
    if (seen.has(raw.stepId)) reject('dependency');
    const dependencyIds = raw.dependsOn as string[];
    if (new Set(dependencyIds).size !== dependencyIds.length ||
        dependencyIds.some(dep => !seen.has(dep))) reject('dependency');

    const rule = toolRules.get(raw.toolId as string);
    if (!rule) reject('unknown_tool');
    if (!rule.enabled) reject('disabled_tool');
    // An LLM may not grant itself a sensitive capability, even if it lies
    // about the step's risk in its JSON proposal.
    if (rule.risk === 'high' || rule.risk === 'medium' && ctx.allowMediumRisk !== true)
      reject('unsafe_tool');

    seen.add(raw.stepId as string);
    bound.push(Object.freeze({
      stepId: raw.stepId as string,
      toolId: raw.toolId as string,
      input: freezeJson(JSON.parse(rawInput) as ToolInput),
      dependsOn: Object.freeze([...dependencyIds]),
      risk: rule.risk,
      expectedEvidence: rule.evidence,
      requiresApproval: rule.requiresApproval || rule.hasExternalSideEffect ||
        rule.risk === 'medium'
    }));
  }

  return Object.freeze({
    version: NEXA_TASK_PLAN_VERSION,
    taskId: value.taskId as string,
    ownerRequestId: ctx.ownerRequestId,
    origin: ctx.origin,
    policyVersion: ctx.policyVersion,
    expiresAtMs: value.expiresAtMs,
    steps: Object.freeze(bound),
    executable: false
  });
}
