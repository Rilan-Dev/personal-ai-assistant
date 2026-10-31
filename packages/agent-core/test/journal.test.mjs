import test from "node:test";
import assert from "node:assert/strict";
import { AgentHarness, ToolRegistry, approvalFingerprint } from "../src/runtime.ts";
import { InMemoryActionJournal } from "../src/journal.ts";
const call = {callId:"task-1",toolId:"test.send",input:{recipient:"ROM",message:"Happy Birthday"}};
const planner={next:async(_,obs)=>obs.length?{type:"done",response:"Action submitted"}:{type:"tool",call}};
const registry=(execute,high=true)=>{
 const r=new ToolRegistry();
 r.register({id:"test.send", description:"Controlled test action",risk:high?"high":"low",
   capabilities:[],hasExternalSideEffect:high,validateInput:v=>!!v && v.recipient==="ROM",
   execute});
 return r;
};
const options=(journal,extra={})=>({
 request:"Approve test action",sessionId:"session-1",grantedCapabilities:new Set(),
 journal,...extra
});
test("one-time human approval is required, stored, consumed, and cannot be reused",async()=>{
 const journal=new InMemoryActionJournal();let count=0;
 const h=new AgentHarness(registry(async()=>{count++;return {status:"success",data:{ok:true}}}),planner);
 const first=await h.run(options(journal));
 assert.equal(first.status,"awaiting_approval");
 assert.equal(journal.state("session-1",call.callId),"pending");
 assert.equal(count,0);
 assert.equal(await journal.approveAction("session-1",call.callId,first.fingerprint),true);
 assert.equal(await journal.approveAction("session-1",call.callId,first.fingerprint),false);
 const next=await h.run(options(journal,{resume:true}));
 assert.equal(next.status,"completed");
 assert.equal(count,1);
 assert.equal(journal.state("session-1",call.callId),"settled");
 assert.equal((await h.run(options(journal,{resume:true}))).status,"blocked");
 assert.equal(count,1);
 assert.equal(await journal.claimAction("session-1",call.callId,first.fingerprint,true),false);
});
test("fingerprint mismatch or altered input cannot receive approval",async()=>{
 const journal=new InMemoryActionJournal();
 const first=await new AgentHarness(registry(async()=>({status:"success",data:{}})),planner).run(options(journal));
 assert.equal(first.status,"awaiting_approval");
 const forged=approvalFingerprint({...call,input:{recipient:"Other",message:"Happy Birthday"}},"session-1");
 assert.equal(await journal.approveAction("session-1",call.callId,forged),false);
 assert.equal(await journal.approveAction("session-other",call.callId,first.fingerprint),false);
 assert.equal(await journal.claimAction("session-1",call.callId,first.fingerprint,true),false);
});
test("a claimed action from before process death must never be repeated",async()=>{
 const journal=new InMemoryActionJournal();
 await journal.saveSession({version:1,sessionId:"session-1",request:"Approve test action",
   status:"running",observations:[],updatedAt:1});
 await journal.claimAction("session-1",call.callId,approvalFingerprint(call,"session-1"),false);
 let count=0;
 const h=new AgentHarness(registry(async()=>{count++;return {status:"success",data:{}}},false),planner);
 const result=await h.run(options(journal,{resume:true}));
 assert.equal(result.status,"blocked");
 assert.equal(count,0);
 assert.equal(journal.state("session-1",call.callId),"claimed");
});
test("same session ID cannot begin another task and journal survives new harness instance",async()=>{
 const journal=new InMemoryActionJournal();
 const h=new AgentHarness(registry(async()=>({status:"success",data:{ok:true}}),false),planner);
 assert.equal((await h.run(options(journal))).status,"completed");
 assert.equal((await new AgentHarness(registry(async()=>({status:"success",data:{}}),false),planner).run(options(journal))).status,"blocked");
 const snapshot=await journal.loadSession("session-1");
 assert.equal(snapshot.status,"completed");
 assert.equal(snapshot.observations.length,1);
});
test("cancellation mid execution marks action as uncertain, never retries",async()=>{
 const journal=new InMemoryActionJournal(), ctl=new AbortController();let count=0;
 const h=new AgentHarness(registry(async()=>{
   count++;ctl.abort();return {status:"success",data:{queued:true}};
 },false),planner);
 const result=await h.run(options(journal,{signal:ctl.signal}));
 assert.equal(result.status,"cancelled");
 assert.equal(count,1);
 assert.equal(journal.state("session-1",call.callId),"uncertain");
 assert.equal((await h.run(options(journal,{resume:true}))).status,"blocked");
 assert.equal(count,1);
});
test("failure to persist prior to dispatch blocks the action",async()=>{
 const journal=new InMemoryActionJournal();let executed=0;
 journal.saveSession=async()=>{throw Error("hidden secure error")};
 const h=new AgentHarness(registry(async()=>{executed++;return {status:"success",data:{}}},false),planner);
 const result=await h.run(options(journal));
 assert.equal(result.status,"blocked");
 assert.equal(executed,0);
 assert.doesNotMatch(JSON.stringify(result),/hidden secure error/);
});
test("reservation failed before tool dispatch blocks execution",async()=>{
 const journal=new InMemoryActionJournal();let n=0;
 journal.claimAction=async()=>{throw Error("disk failure")};
 const result=await new AgentHarness(registry(async()=>{n++;return {status:"success",data:{}}},false),planner).run(options(journal));
 assert.equal(result.status,"blocked");
 assert.equal(n,0);
});
test("recovery from observed successful action plans next step without replaying",async()=>{
 const journal=new InMemoryActionJournal();let n=0;
 await journal.saveSession({version:1,sessionId:"session-1",request:"Approve test action",
  status:"running",observations:[{call,outcome:{status:"success",data:{ok:true}}}],updatedAt:1});
 await journal.claimAction("session-1",call.callId,approvalFingerprint(call,"session-1"),false);
 await journal.finishAction("session-1",call.callId,false);
 const result=await new AgentHarness(registry(async()=>{n++;return {status:"success",data:{}}},false),planner).run(options(journal,{resume:true}));
 assert.equal(result.status,"completed");assert.equal(n,0);
});
test("concurrent attempts cannot both reserve the same action",async()=>{
 const journal=new InMemoryActionJournal();
 const fingerprint=approvalFingerprint(call,"session-1");
 const results=await Promise.all(Array.from({length:25},()=>journal.claimAction("session-1",call.callId,fingerprint,false)));
 assert.equal(results.filter(Boolean).length,1);
});
test("approved action claim fails when request fingerprint is swapped",async()=>{
 const journal=new InMemoryActionJournal();const fp=approvalFingerprint(call,"session-1");
 assert.equal(await journal.prepareApproval("session-1",call.callId,fp),true);
 assert.equal(await journal.approveAction("session-1",call.callId,fp),true);
 assert.equal(await journal.claimAction("session-1",call.callId,approvalFingerprint({...call,input:{recipient:"OTHER"}},"session-1"),true),false);
 assert.equal(await journal.claimAction("session-1",call.callId,fp,true),true);
 assert.equal(await journal.claimAction("session-1",call.callId,fp,true),false);
});
test("an unknown or already finalized session cannot be resumed",async()=>{
 const journal=new InMemoryActionJournal();
 const h=new AgentHarness(registry(async()=>({status:"success",data:{ok:true}}),false),planner);
 assert.equal((await h.run(options(journal,{resume:true}))).status,"blocked");
 await journal.saveSession({version:1,sessionId:"session-1",request:"another request",
   status:"running",observations:[],updatedAt:1});
 assert.equal((await h.run(options(journal,{resume:true}))).status,"blocked");
 assert.equal((await h.run(options(journal))).status,"blocked");
});
