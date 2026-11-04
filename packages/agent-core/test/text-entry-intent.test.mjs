import test from 'node:test';
import assert from 'node:assert/strict';
import {parseReviewedTextEntry as parse} from '../src/text-entry-intent.ts';

test('plain requested text and target field remain exact; never submit',()=>{
 assert.deepEqual(parse('Hey Nexa type I will call you later into the Search box'),{
  targetLabel:'Search',exactText:'I will call you later',reviewOnly:true
 });
 assert.deepEqual(parse('Fill the Notes field with "Meeting starts at 2 PM."'),{
  targetLabel:'Notes',exactText:'Meeting starts at 2 PM.',reviewOnly:true
 });
});
test('quoted multiline text is held for review and never executed by the parser',()=>{
 const r=parse('Enter "First line\nSecond line" into the Description field');
 assert.equal(r.exactText,'First line\nSecond line');
});
test('dangerous target fields are rejected even when requested by a user',()=>{
 for(const name of ['Password','OTP','CVV','Card number','Payment amount',
  'Bank Account Number','Recipient','Email address','Send','Submit','PIN']) {
   assert.equal(parse('type hello into the '+name+' field'),null,name);
 }
});
test('no target, chained instructions and oversized payloads fail closed',()=>{
 for(const s of ['type hello','open Chrome and type hello into Search',
   'type hello into Search and submit', 'type hello into Search and then send',
   'type '+ 'z'.repeat(501)+' into Search','type hello into X',
   'type hello into the password box']) assert.equal(parse(s),null,s.slice(0,40));
});
