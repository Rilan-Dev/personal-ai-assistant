import test from 'node:test';
import assert from 'node:assert/strict';
import {parseReviewedWhatsAppIntent} from '../src/owner-intents.ts';

test('explicit owner WhatsApp request prefills only a review, never sends',()=>{
  const parsed = parseReviewedWhatsAppIntent(
    'Hey Nexa, send a WhatsApp message to my mom "I will call you later"');
  assert.deepEqual(parsed, {
    contactHint:'mom', message:'I will call you later', mode:'review_only'
  });
  assert.equal(parseReviewedWhatsAppIntent(
    'Prepare WhatsApp to ROM: “Happy birthday!”')?.mode,'review_only');
});
test('never interprets unquoted, ambiguous or chained instructions as messages',()=>{
  const bad = [
    'Send WhatsApp message to mom',
    'Open WhatsApp and send a message to Mom',
    'WhatsApp Mom I will call later',
    'Send a WhatsApp message to mom "Hello" and buy something',
    'Send WhatsApp to mom "Hello" and call someone',
    'Send WhatsApp to mom: ""',
    'Send WhatsApp to mom: "'+'x'.repeat(501)+'"',
    'Hey Nexa, send WhatsApp to mom and then transfer funds: "yes"',
    'Send WhatsApp to mom: "line 1\nline2"'
  ];
  for (const input of bad)
    assert.equal(parseReviewedWhatsAppIntent(input),null,input);
});
