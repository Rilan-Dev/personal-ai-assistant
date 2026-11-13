import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// Windows checkouts may use CRLF for committed YAML; the policy is about
// the flow structure, not Git's working-tree line ending.
const normalized=(file)=>readFileSync(file,'utf8').replace(/\r\n/g,'\n');
const workflow=normalized('.github/workflows/maestro-cloud-manual.yml');
const flow=normalized('.maestro/cloud-qa/16-owner-permission-recovery.yaml');
const app=readFileSync('apps/mobile/App.tsx','utf8');

test('manual Maestro suite selects one isolated, signed-release grant-recovery flow',()=>{
  assert.match(workflow,/workflow_dispatch:/);
  assert.match(workflow,/          - grant-recovery\n/);
  assert.match(workflow,/grant-recovery\) target="\.maestro\/cloud-qa\/16-owner-permission-recovery\.yaml"/);
  assert.match(workflow,/cancel-in-progress: false/);
  assert.match(flow,/^appId: dev\.rilandev\.personalaiassistant$/m);
  assert.match(flow,/clearState: true/);
  assert.doesNotMatch(flow,/dev\.rilandev\.personalaiassistant\.physicalqa/);
});
test('grant-recovery flow never grants the permission or invokes an external action',()=>{
  assert.match(flow,/visible: "Task blocked: Permission required: apps\.launch"/);
  assert.match(flow,/assertVisible: "Review device permissions in Tools"/);
  assert.match(flow,/tapOn: "Review device permissions in Tools"/);
  assert.match(flow,/assertVisible: "App launching"/);
  assert.doesNotMatch(flow,/tapOn: "App launching"/);
  assert.doesNotMatch(flow,/Approve once|Resume saved task|adb|shell|openApp|launchBrowser/);
  assert.match(app,/Review device permissions in Tools/);
  const grant=app.indexOf('<Text style={styles.settingTitle}>App launching</Text>');
  const memory=app.indexOf('<Text style={styles.cardHeading}>Private recipient-app memory</Text>');
  assert.ok(grant>0&&memory>grant);
});
