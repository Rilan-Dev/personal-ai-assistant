import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import ts from 'typescript';

const source = readFileSync(new URL('../../apps/mobile/src/native/acaEmail.ts',import.meta.url),'utf8');
const compiled = ts.transpileModule(source,{compilerOptions:{
  module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,esModuleInterop:true
}}).outputText;
const validToken='nexa_m1_'+'A'.repeat(43);
const apiOrigin='https://dev.aca.example.test';
const id='00000000-0000-0000-0000-000000000001';
const orgId='00000000-0000-0000-0000-000000000002';

function harness(overrides={}) {
  let saved=null,clears=0;
  const calls=[];
  const vault={
    async saveAcaSession(data){if(overrides.failSave)return false; saved=data;return true},
    async loadAcaSession(){return saved},
    async clearAcaSession(){saved=null;clears++;return true}
  };
  const response=(status,body)=>({
    ok:status>=200&&status<300,status,async text(){return JSON.stringify(body)}
  });
  async function fakeFetch(url,opts={}) {
    const parsed=new URL(url);
    calls.push({origin:parsed.origin,path:parsed.pathname,query:parsed.searchParams,method:opts.method??'GET',
      headers:opts.headers??{},body:opts.body??null});
    if(overrides.handler) return overrides.handler(parsed,opts,response);
    if(parsed.pathname==='/api/mobile/v1/session' && opts.method==='POST')
      return response(201,{accessToken:validToken,expiresAt:'2099-01-01T00:00:00.000Z'});
    if(parsed.pathname==='/api/mobile/v1/targets')
      return response(200,{organizations:[{id:orgId,name:'Test Org',role:'owner',
        agents:[{id,name:'Test Agent'}]}]});
    if(parsed.pathname==='/api/mobile/v1/session' && opts.method==='DELETE')
      return response(200,{revoked:true});
    return response(404,{error:'not_found'});
  }
  const moduleExports={};
  const sandbox={
    exports:moduleExports,
    require(name){
      if(name==='react-native')
        return {NativeModules:{PAAAgentJournal:vault},Platform:{OS:'android'}};
      if(name==='./acaConfig')
        return {getAcaApiOrigin:()=>overrides.origin??apiOrigin};
      if(name==='./acaMailIntent')
        return {parseAcaMailSummaryIntent:()=>null};
      throw new Error('Unexpected import '+name);
    },
    fetch:fakeFetch,URL,URLSearchParams,AbortController,console
  };
  runInNewContext(compiled,sandbox,{filename:'acaEmail.ts'});
  return {client:moduleExports,calls,vault,get saved(){return saved},get clears(){return clears},response};
}
test('mobile identity client uses encrypted session storage and avoids a separate mail API',async()=>{
  const h=harness();
  const {client}=h;
  const session=await client.connectAca('OWNER@example.invalid','never-log-this');
  assert.equal(session.origin,apiOrigin);
  assert.equal(session.token,validToken);
  assert.equal(JSON.parse(h.saved).token,validToken);
  assert.equal(h.calls[0].method,'POST');
  assert.equal(h.calls[0].path,'/api/mobile/v1/session');
  assert.equal(h.calls[0].origin,apiOrigin);
  assert.equal(h.calls[0].headers.Cookie,undefined);
  const targets=await client.acaTargets(session);
  assert.equal(targets.length,1);
  assert.equal(targets[0].agents.length,1);
  const selected={...session,organizationId:orgId,agentId:id};
  await client.storeAcaSession(selected);
  assert.equal(selected.organizationId,orgId);
  assert.equal(selected.agentId,id);
  assert.equal(h.calls.some(x=>x.path.includes('/mail/summary')),false);
  await client.disconnectAca(selected);
  assert.equal(h.saved,null);
  assert.equal(h.clears,1);
  assert.deepEqual(h.calls.map(x=>x.method),['POST','GET','DELETE']);
});

test('canonical ACA conversation locator persists in the secure session and is target-scoped',async()=>{
  const h=harness();
  const session=await h.client.connectAca('OWNER@example.invalid','never-log-this');
  const selected={...session,organizationId:orgId,agentId:id};
  const chatConversation={organizationId:orgId,agentId:id,
    conversationId:'00000000-0000-0000-0000-000000000009'};
  await h.client.storeAcaSession({...selected,chatConversation});
  assert.equal(JSON.stringify(await h.client.loadAcaSession()),JSON.stringify({...selected,chatConversation}));
  await assert.rejects(h.client.storeAcaSession({...selected,chatConversation:{...chatConversation,agentId:orgId}}),
    /Invalid ACA session/);
});
test('malformed and expired device sessions cannot fetch authorized ACA targets',async()=>{
  const {client,calls}=harness();
  const session={version:1,origin:apiOrigin,
    token:validToken,expiresAt:'2020-01-01T00:00:00.000Z',organizationId:'',agentId:''};
  await assert.rejects(client.acaTargets(session),/session expired/i);
  await assert.rejects(client.acaTargets({...session,organizationId:orgId,agentId:id,token:'bad'}),/session expired/i);
  assert.equal(calls.length,0);
});
test('403 account/organization denial surfaces without leaking business data',async()=>{
  const h=harness({handler:(url,opts,response)=>{
    if(url.pathname==='/api/mobile/v1/targets')return response(403,{error:'forbidden',privateBody:'DO-NOT-SHOW'});
    return response(500,{error:'unconfigured'});
  }});
  const session={version:1,origin:apiOrigin,
    token:validToken,expiresAt:'2099-01-01T00:00:00.000Z',organizationId:orgId,agentId:id};
  await assert.rejects(h.client.acaTargets(session),e=>typeof e?.message==='string' && /access denied/.test(e.message) && !e.message.includes('DO-NOT-SHOW'));
});
test('bad credentials are rejected locally without contacting ACA',async()=>{
  const h=harness();
  await assert.rejects(h.client.connectAca('bad-address','abc'),/email and password/);
  await assert.rejects(h.client.connectAca('owner@example.invalid',''),/email and password/);
  assert.equal(h.calls.length,0);
});
test('the ACA identity client has no alternate mail-summary endpoint',()=>{
  assert.doesNotMatch(source,/acaMailSummary/);
  assert.doesNotMatch(source,/\/api\/mobile\/v1\/mail\/summary/);
});

test('expired or corrupted Keystore sessions are purged, not silently reused',async()=>{
  const h=harness();
  const session={version:1,origin:apiOrigin,
    token:validToken,expiresAt:'2020-01-01T00:00:00.000Z',
    organizationId:orgId,agentId:id};
  await h.client.storeAcaSession(session);
  assert.equal(await h.client.loadAcaSession(),null);
  assert.equal(h.saved,null);
  assert.equal(h.clears,1);
  await h.vault.saveAcaSession('{ corrupted JSON');
  assert.equal(await h.client.loadAcaSession(),null);
  assert.equal(h.saved,null);
  assert.equal(h.clears,2);
});
test('saved account selection must be in a currently authorized organization-agent pair',()=>{
  const h=harness();
  const session={version:1,origin:apiOrigin,
    token:validToken,expiresAt:'2099-01-01T00:00:00.000Z',
    organizationId:orgId,agentId:id};
  const organizations=[{id:orgId,name:'One',role:'owner',agents:[{id,name:'Active'}]}];
  assert.equal(h.client.authorizedAcaSelection(session,organizations),true);
  assert.equal(h.client.authorizedAcaSelection({...session,agentId:'00000000-0000-0000-0000-000000000003'},organizations),false);
  assert.equal(h.client.authorizedAcaSelection({...session,organizationId:'00000000-0000-0000-0000-000000000004'},organizations),false);
  assert.equal(h.client.authorizedAcaSelection({...session,agentId:''},organizations),false);
  assert.equal(h.client.validAcaSession({...session,agentId:'not-a-valid-uuid'}),false);
});
test('server revoke failure is surfaced to caller while local device token is cleared',async()=>{
  const h=harness({handler:(url,opts,response)=>{
    if(url.pathname==='/api/mobile/v1/session'&&opts.method==='DELETE')
      return response(503,{error:'temporary_outage'});
    return response(404,{error:'unconfigured'});
  }});
  const session={version:1,origin:apiOrigin,
    token:validToken,expiresAt:'2099-01-01T00:00:00.000Z',
    organizationId:orgId,agentId:id};
  await h.client.storeAcaSession(session);
  const revoked=await h.client.disconnectAca(session);
  assert.equal(revoked.serverRevoked,false);
  assert.equal(h.saved,null);
  assert.equal(h.clears,1);
});
test('401 and 403 return typed status without reflecting private server bodies',async()=>{
  for(const code of [401,403]){
    const h=harness({handler:()=>h.response(code,{error:'forbidden',secret:'not-for-client'})});
    const session={version:1,origin:apiOrigin,
      token:validToken,expiresAt:'2099-01-01T00:00:00.000Z',
      organizationId:orgId,agentId:id};
    await assert.rejects(h.client.acaTargets(session),
      error=>error?.httpStatus===code && !error.message.includes('not-for-client'));
  }
});

test('failed Android secure storage revokes freshly issued remote device session',async()=>{
  const h=harness({failSave:true});
  await assert.rejects(h.client.connectAca('owner@example.invalid','test-only-password'),
    /Cannot securely store ACA login on this phone/);
  assert.equal(h.saved,null);
  assert.deepEqual(h.calls.map(call=>call.method),['POST','DELETE']);
  const revoke=h.calls[1];
  assert.equal(revoke.path,'/api/mobile/v1/session');
  assert.equal(revoke.headers.Authorization,'Bearer '+validToken);
});

test('sessions saved for a different ACA origin are rejected before any request',async()=>{
  const h=harness();
  const session={version:1,origin:'https://other.aca.example.test',
    token:validToken,expiresAt:'2099-01-01T00:00:00.000Z',organizationId:orgId,agentId:id};
  await assert.rejects(h.client.storeAcaSession(session),/Invalid ACA session/);
  await assert.rejects(h.client.acaTargets(session),/session expired/i);
  assert.equal(h.calls.length,0);
});
