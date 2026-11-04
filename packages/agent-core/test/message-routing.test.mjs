import test from 'node:test';
import assert from 'node:assert/strict';
import {parseMessageRequest, decideMessageApp, recipientMemoryKey} from '../src/message-routing.ts';

const apps=[
 {id:'whatsapp',label:'WhatsApp',packageName:'com.whatsapp'},
 {id:'sms',label:'Messages',packageName:'com.google.android.apps.messaging'},
 {id:'imo',label:'imo',packageName:'com.imo.android.imoim'}
];

test('parses unquoted user message verbatim and never assigns an unstated app',()=>{
 assert.deepEqual(parseMessageRequest('Hey Nexa Send Message to my Mom That I am in the meeting i will call you later'),{
  recipientHint:'Mom',text:'I am in the meeting i will call you later',explicitApp:null,reviewOnly:true
 });
});
test('preserves literal message punctuation and quotes, with an explicit app',()=>{
 const found=parseMessageRequest('Send a WhatsApp message to my Mom saying "In a meeting; please call later!"');
 assert.deepEqual(found,{recipientHint:'Mom',text:'In a meeting; please call later!',explicitApp:'whatsapp',reviewOnly:true});
});
test('first time with several installed apps must request human app selection',()=>{
 const input=parseMessageRequest('Send message to Mom that I will call later');
 assert.equal(decideMessageApp(input,apps,null).kind,'choose');
 assert.equal(decideMessageApp(input,apps,'sms').app.id,'sms');
 assert.equal(decideMessageApp(input,apps,'botim').kind,'choose');
});
test('explicit channel overrides prior memory, and unsupported app is never substituted',()=>{
 const input=parseMessageRequest('Send imo message to Mom saying Hello');
 assert.equal(decideMessageApp(input,apps,'whatsapp').app.id,'imo');
 const requested=parseMessageRequest('Send botim message to Mom saying Hello');
 assert.deepEqual(decideMessageApp(requested,apps,'whatsapp'),{kind:'unavailable',app:null,reason:'not_installed'});
});
test('invalid and ambiguous requests have zero side effects',()=>{
 for(const raw of [
  'Send a message to Mom', 'Open WhatsApp and send message to Mom that hello',
  'Send message to Mom and then call Dad that hello',
  'Send message to Mom saying ', 'Send message to Mom saying '+ 'a'.repeat(501),
  'Forward SMS from Dad to Mom that hello'
 ])assert.equal(parseMessageRequest(raw),null,raw);
});
test('memory scopes only owner-defined aliases, never raw phone numbers',()=>{
 assert.equal(recipientMemoryKey('  My   Mom  '),'my mom');
 assert.equal(recipientMemoryKey('+971501234567'),null);
 assert.equal(recipientMemoryKey(''),null);
});
