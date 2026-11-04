import test from 'node:test';
import assert from 'node:assert/strict';
import {
  bindTaskPlan, TaskPlanRejected, NEXA_TASK_V2_ENABLED,
  NEXA_TASK_PLAN_MAX_STEPS, NEXA_TASK_PLAN_VERSION
} from '../../agent-contracts/src/task-plan.ts';

const NOW = 2_000_000_000_000;
const read = Object.freeze({
  id: 'device.battery_status', risk: 'low', evidence: 'native_reading',
  hasExternalSideEffect: false, requiresApproval: false, enabled: true
});
const navigate = Object.freeze({
  id: 'device.open_app', risk: 'medium', evidence: 'foreground_package',
  hasExternalSideEffect: true, requiresApproval: true, enabled: true
});
const send = Object.freeze({
  id: 'messaging.send', risk: 'high', evidence: 'owner_review',
  hasExternalSideEffect: true, requiresApproval: true, enabled: true
});
const ctx = () => ({
  ownerRequestId: 'owner-request-12345678', origin: 'chat',
  policyVersion: 'nexa-20261105', nowMs: NOW,
  tools: [read, navigate, send]
});
const step = (stepId = 'step-0001', toolId = read.id, input = {}) => ({
  stepId, toolId, input, dependsOn: []
});
const plan = () => ({
  version: NEXA_TASK_PLAN_VERSION,
  taskId: 'task-12345678', ownerRequestId: 'owner-request-12345678',
  policyVersion: 'nexa-20261105', createdAtMs: NOW - 1_000,
  expiresAtMs: NOW + 120_000, steps: [step()]
});
const blocked = (value, overrides, code) => {
  assert.throws(
    () => bindTaskPlan(value, {...ctx(), ...overrides}),
    error => error instanceof TaskPlanRejected && error.code === code
  );
};

test('new DSH-inspired coordinator remains disabled by default', () => {
  assert.equal(NEXA_TASK_V2_ENABLED, false);
  assert.equal(NEXA_TASK_PLAN_MAX_STEPS, 8);
});

test('only trusted metadata assigns risk/evidence/approval, and output cannot execute', () => {
  const result = bindTaskPlan(plan(), ctx());
  assert.equal(result.executable, false);
  assert.equal(result.origin, 'chat');
  assert.deepEqual(result.steps[0].input, {});
  assert.equal(result.steps[0].risk, 'low');
  assert.equal(result.steps[0].expectedEvidence, 'native_reading');
  assert.equal(result.steps[0].requiresApproval, false);
  assert.equal(Object.isFrozen(result.steps), true);
  assert.equal(Object.isFrozen(result.steps[0].input), true);
});

test('one request can propose dependent verified steps but approval derives from registry', () => {
  const value = plan();
  value.steps = [
    step('step-0001'),
    {...step('step-0002', 'device.open_app', {app: 'whatsapp'}), dependsOn: ['step-0001']}
  ];
  const result = bindTaskPlan(value, {...ctx(), allowMediumRisk: true, origin: 'live-voice'});
  assert.deepEqual(result.steps[1].dependsOn, ['step-0001']);
  assert.equal(result.steps[1].requiresApproval, true);
  assert.equal(result.steps[1].expectedEvidence, 'foreground_package');
  assert.equal(result.origin, 'live-voice');
  assert.equal(result.executable, false);
});

test('reject external planner attempts to forge the owner or change policy', () => {
  const a = plan(); a.ownerRequestId = 'different-owner-1234';
  blocked(a, {}, 'scope_mismatch');
  const b = plan(); b.policyVersion = 'old';
  blocked(b, {}, 'scope_mismatch');
});

test('reject any plan or step field claiming execution authority', () => {
  const a = {...plan(), approved: true};
  blocked(a, {}, 'malformed');
  const b = plan(); b.steps = [{...step(), requiresApproval: false, risk: 'low'}];
  blocked(b, {}, 'malformed');
  const c = plan(); c.steps = [{...step(), result: 'verified'}];
  blocked(c, {}, 'malformed');
});

test('reject unknown, disabled, high-risk and non-opted-in medium tools', () => {
  const a = plan(); a.steps = [step('step-0001', 'mobile.unknown')];
  blocked(a, {}, 'unknown_tool');
  const b = plan(); b.steps = [step('step-0001', 'messaging.send')];
  blocked(b, {allowMediumRisk:true}, 'unsafe_tool');
  const c = plan(); c.steps = [step('step-0001', 'device.open_app')];
  blocked(c, {}, 'unsafe_tool');
  blocked(plan(), {tools: [{...read, enabled: false}]}, 'disabled_tool');
});

test('reject cycles, duplicate IDs and future step dependencies', () => {
  const a = plan();
  a.steps = [{...step(), dependsOn:['step-0001']}];
  blocked(a, {}, 'dependency');
  const b = plan(); b.steps = [step(), step()];
  blocked(b, {}, 'dependency');
  const c = plan(); c.steps = [
    {...step(), dependsOn:['step-0002']},
    {...step('step-0002'), dependsOn:['step-0001']}
  ];
  blocked(c, {}, 'dependency');
  const d = plan(); d.steps = [
    step(),
    {...step('step-0002'), dependsOn:['step-0001','step-0001']}
  ];
  blocked(d, {}, 'dependency');
});

test('reject stale, future, expired, or too long plans', () => {
  const a = plan(); a.expiresAtMs = NOW - 1;
  blocked(a, {}, 'stale');
  const b = plan(); b.createdAtMs = NOW - 400_000;
  blocked(b, {}, 'stale');
  const c = plan(); c.expiresAtMs = NOW + 500_000;
  blocked(c, {}, 'stale');
  const d = plan(); d.createdAtMs = NOW + 60_000;
  blocked(d, {}, 'stale');
});

test('reject step budget and overlarge inputs', () => {
  const a = plan(); a.steps = Array.from({length: 9}, (_, i) =>
    step('step-' + String(i).padStart(4, '0')));
  blocked(a, {}, 'malformed');
  const b = plan(); b.steps = [step('step-0001', read.id, {query: 'q'.repeat(501)})];
  blocked(b, {}, 'malformed');
  const c = plan(); c.steps = [step('step-0001', read.id,
    {values: Array.from({length:17}, () => 1)})];
  blocked(c, {}, 'malformed');
});

test('reject input prototype-pollution keys and non-JSON contents', () => {
  const a = plan();
  a.steps = [step('step-0001', read.id,
    JSON.parse('{"__proto__":{"allow":true}}'))];
  blocked(a, {}, 'malformed');
  const b = plan(); b.steps = [step('step-0001', read.id, {foo: () => true})];
  blocked(b, {}, 'malformed');
  const c = plan(); c.steps = [step('step-0001', read.id,
    {nested: {constructor: {approved: true}}})];
  blocked(c, {}, 'malformed');
});

test('copy and freeze nested input so a planner cannot mutate the admitted plan', () => {
  const a = plan();
  const input = {context: {safe: ['one', 'two']}};
  a.steps = [step('step-0001', read.id, input)];
  const admitted = bindTaskPlan(a, ctx());
  input.context.safe.push('third');
  assert.deepEqual(admitted.steps[0].input, {context: {safe: ['one', 'two']}});
  assert.equal(Object.isFrozen(admitted.steps[0].input.context), true);
  assert.equal(Object.isFrozen(admitted.steps[0].input.context.safe), true);
});

test('reject duplicated or malformed trusted registries instead of guessing', () => {
  blocked(plan(), {tools: [read, read]}, 'malformed');
  blocked(plan(), {tools: [{...read, evidence:'sent'}]}, 'malformed');
  blocked(plan(), {origin:'external-provider'}, 'malformed');
});
