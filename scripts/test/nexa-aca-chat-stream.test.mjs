import test from 'node:test';
import assert from 'node:assert/strict';
import { streamAcaChatTurn, AcaChatRequestError } from '../../apps/mobile/src/native/acaChat.ts';

const apiOrigin='https://dev.aca.example.test';
const token='nexa_m1_'+'A'.repeat(43);
const organizationId='00000000-0000-0000-0000-000000000002';
const agentId='00000000-0000-0000-0000-000000000004';
const conversationId='00000000-0000-0000-0000-000000000006';
const validSession={version:1,origin:apiOrigin,token,expiresAt:'2099-01-01T00:00:00.000Z',organizationId,agentId};
const originalFetch=globalThis.fetch;
const originalOrigin=process.env.EXPO_PUBLIC_ACA_API_ORIGIN;

function jsonEvent(name,payload){return `event: ${name}\ndata: ${JSON.stringify(payload)}\n\n`;}
function responseFromChunks(chunks,status=200,contentType='text/event-stream; charset=utf-8'){
  const encoder=new TextEncoder();
  const body=new ReadableStream({start(controller){for(const chunk of chunks)controller.enqueue(chunk instanceof Uint8Array?chunk:encoder.encode(chunk));controller.close();}});
  return new Response(body,{status,headers:{'content-type':contentType}});
}

test.beforeEach(()=>{process.env.EXPO_PUBLIC_ACA_API_ORIGIN=apiOrigin;});
test.afterEach(()=>{
  globalThis.fetch=originalFetch;
  if(originalOrigin===undefined)delete process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
  else process.env.EXPO_PUBLIC_ACA_API_ORIGIN=originalOrigin;
});

test('parses split SSE frames and emits the typed ACA event sequence',async()=>{
  const source=[
    jsonEvent('conversation',{conversationId}),
    jsonEvent('status',{phase:'tools'}),
    jsonEvent('progress',{phase:'reading_email'}),
    jsonEvent('delta',{text:'A short synthetic summary.'}),
    jsonEvent('done',{conversationId,provider:'sarvam',model:'test-model',source:'local_mail_database',complete:true,matchedCount:3,includedCount:3,hasMore:false,lastSyncAt:null}),
  ].join('');
  const encoded=new TextEncoder().encode(source);
  const chunks=[];
  for(let i=0;i<encoded.length;i+=11)chunks.push(encoded.slice(i,i+11));
  globalThis.fetch=async()=>responseFromChunks(chunks);
  const events=[];
  await streamAcaChatTurn(validSession,{organizationId,agentId,conversationId,message:'Summarize recent emails',signal:new AbortController().signal},event=>events.push(event));
  assert.deepEqual(events.map(event=>event.type),['conversation','status','progress','delta','done']);
  assert.equal(events[0].conversationId,conversationId);
  assert.equal(events[3].text,'A short synthetic summary.');
  assert.equal(events[4].source,'local_mail_database');
  assert.equal(events[4].matchedCount,3);
});

test('sends only authorized scope and message with the configured bearer origin',async()=>{
  let captured;
  globalThis.fetch=async(url,init)=>{
    captured={url:String(url),init,body:JSON.parse(init.body)};
    return responseFromChunks([jsonEvent('conversation',{conversationId}),jsonEvent('done',{conversationId})]);
  };
  await streamAcaChatTurn(validSession,{organizationId,agentId,conversationId,message:'Check email',signal:new AbortController().signal},()=>{});
  assert.equal(captured.url,apiOrigin+'/api/mobile/v1/chat/stream');
  assert.equal(captured.init.method,'POST');
  assert.equal(captured.init.headers.Authorization,'Bearer '+token);
  assert.equal(captured.init.headers.Accept,'text/event-stream');
  assert.equal(captured.init.credentials,'omit');
  assert.equal(captured.init.redirect,'error');
  assert.equal(captured.body.organizationId,organizationId);
  assert.equal(captured.body.agentId,agentId);
  assert.equal(captured.body.conversationId,conversationId);
  assert.equal(captured.body.message,'Check email');
  assert.deepEqual(Object.keys(captured.body).sort(),['agentId','conversationId','message','organizationId']);
});

test('rejects malformed JSON, unknown event types, and oversized event frames',async()=>{
  for(const payload of [
    {chunks:['event: delta\ndata: {broken}\n\n'],pattern:/malformed/i},
    {chunks:[jsonEvent('provider_override',{provider:'attacker'})],pattern:/unsupported.*event/i},
    {chunks:[jsonEvent('delta',{text:'x'.repeat(40_000)})],pattern:/too large/i},
  ]){
    globalThis.fetch=async()=>responseFromChunks(payload.chunks);
    await assert.rejects(streamAcaChatTurn(validSession,{organizationId,agentId,message:'Check email',signal:new AbortController().signal},()=>{}),payload.pattern);
  }
});

test('maps HTTP authorization and feature-gate failures without reflecting response bodies',async()=>{
  for(const status of [401,403,404]){
    globalThis.fetch=async()=>new Response(JSON.stringify({error:'private',mailBody:'DO-NOT-REFLECT'}),{status,headers:{'content-type':'application/json'}});
    await assert.rejects(streamAcaChatTurn(validSession,{organizationId,agentId,message:'Check email',signal:new AbortController().signal},()=>{}),error=>
      error instanceof AcaChatRequestError && error.httpStatus===status && !error.message.includes('DO-NOT-REFLECT'));
  }
});

test('an active abort stops event delivery and closes the reader',async()=>{
  const controller=new AbortController();
  globalThis.fetch=async()=>responseFromChunks([
    jsonEvent('delta',{text:'first'}),
    jsonEvent('done',{conversationId}),
  ]);
  const events=[];
  await streamAcaChatTurn(validSession,{organizationId,agentId,message:'Check email',signal:controller.signal},event=>{
    events.push(event);
    controller.abort();
  });
  assert.deepEqual(events.map(event=>event.type),['delta']);
});

test('rejects a stale session origin or mismatched selected agent before networking',async()=>{
  let calls=0;
  globalThis.fetch=async()=>{calls++;return responseFromChunks([]);};
  await assert.rejects(streamAcaChatTurn({...validSession,origin:'https://other.example.test'},
    {organizationId,agentId,message:'Check email',signal:new AbortController().signal},()=>{}),/session|origin/i);
  await assert.rejects(streamAcaChatTurn(validSession,
    {organizationId,agentId:'00000000-0000-0000-0000-000000000007',message:'Check email',signal:new AbortController().signal},()=>{}),/selected.*agent|scope/i);
  assert.equal(calls,0);
});
