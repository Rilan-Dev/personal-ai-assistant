import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateCloudCli} from './validate-cloud-cli.mjs';
const flow=(s,i)=>'['+s+'] Nexa test '+i+' (1m 3s)';
test('accept only 11 individually passing named flows with cloud confirmation',()=>{
  const report=Array.from({length:11},(_,i)=>flow('Passed',i)).join('\n')+'\n11/11 Flows Passed';
  const r=evaluateCloudCli(report,11);
  assert.equal(r.passed,true);
  assert.equal(r.observed,11);
});
test('reject a single failed flow even if other ten pass',()=>{
  const report=Array.from({length:11},(_,i)=>flow(i===4?'Failed':'Passed',i)).join('\n')+'\n1/11 Flow Failed';
  assert.equal(evaluateCloudCli(report,11).passed,false);
});
test('reject aggregate-only HTML evidence and missing per-flow log',()=>{
  assert.equal(evaluateCloudCli('Status: PASSED\n11/11 Flows Passed',11).passed,false);
  assert.equal(evaluateCloudCli('',11).passed,false);
});
test('reject repeated flow events and uncorroborated output',()=>{
  const duplicate=Array.from({length:11},()=>flow('Passed',1)).join('\n')+'\n11/11 Flows Passed';
  assert.equal(evaluateCloudCli(duplicate,11).passed,false);
  const noSummary=Array.from({length:11},(_,i)=>flow('Passed',i)).join('\n');
  assert.equal(evaluateCloudCli(noSummary,11).passed,false);
});
