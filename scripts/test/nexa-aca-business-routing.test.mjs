import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const { classifyAcaBusinessIntent } = await import('../../apps/mobile/src/native/acaBusinessIntent.ts');
const app = readFileSync(new URL('../../apps/mobile/App.tsx', import.meta.url), 'utf8');

test('routes ACA and work-data reads to the canonical business chat', () => {
  for (const request of [
    'Summarize my recent emails',
    'Find emails from Acme about the renewal',
    'Send me a short summary of customer emails',
    'What meetings do I have tomorrow?',
    'Check calendar availability Friday afternoon',
    'Show recent WhatsApp Business customer conversations',
    'Search the company knowledge base for the refund process',
    'What provider is configured for the sales agent in ACA?',
    'Review the customer pipeline and latest invoices',
  ]) assert.deepEqual(classifyAcaBusinessIntent(request), { kind: 'business_read' }, request);
});

test('blocks ACA business writes before any Nexa planner can execute them', () => {
  for (const request of [
    'Send an email to the supplier',
    'Schedule a meeting with the customer',
    'Reply to this WhatsApp Business customer',
    'Change the sales agent provider configuration in ACA',
    'Create an invoice for the customer',
  ]) assert.deepEqual(classifyAcaBusinessIntent(request), { kind: 'business_write' }, request);
});

test('does not misroute Android and personal messaging work into ACA', () => {
  for (const request of [
    'Scroll down on the screen',
    'Open Chrome',
    'Open WhatsApp',
    'Open Calendar',
    'Tap the customer tab',
    'Send Mom a WhatsApp message that I am in a meeting',
    'Text my brother that I will call later',
    'What is my phone battery level?',
  ]) assert.equal(classifyAcaBusinessIntent(request), null, request);
});

test('stops mixed business and direct screen-control requests for clarification', () => {
  assert.deepEqual(classifyAcaBusinessIntent('Open Chrome and summarize my ACA emails'), { kind: 'mixed_request' });
  assert.deepEqual(classifyAcaBusinessIntent('Tap the calendar app and schedule a customer meeting'), { kind: 'mixed_request' });
  assert.deepEqual(classifyAcaBusinessIntent('Open Chrome and search my ACA inbox'), { kind: 'mixed_request' });
  assert.deepEqual(classifyAcaBusinessIntent('Please help me with ACA'), { kind: 'unsupported_business' });
});

test('ACA business routing uses the configured stream and not local provider or native harness', () => {
  assert.match(app, /classifyAcaBusinessIntent\(request\)/);
  assert.match(app, /streamAcaChatTurn\(/);
  assert.match(app, /createScreenAwarePlanner\(planner,screenGrant,resolveScreenQuestion\)/);
  assert.match(app, /new AgentHarness\(engine\.registry/);
  assert.doesNotMatch(app, /summarizeAcaMailWithProvider/);
});
