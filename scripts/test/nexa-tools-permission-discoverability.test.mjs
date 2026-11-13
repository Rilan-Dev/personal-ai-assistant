import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const app=readFileSync('apps/mobile/App.tsx','utf8');

test('Tools presents owner-controlled App launching grant before saved task backlog',()=>{
  const tools=app.indexOf("{tab === 'tools' && <ScrollView");
  const controls=app.indexOf('<Text style={styles.cardHeading}>Device controls</Text>');
  const grant=app.indexOf('<Text style={styles.settingTitle}>App launching</Text>');
  const memory=app.indexOf('<Text style={styles.cardHeading}>Private recipient-app memory</Text>');
  const proposals=app.indexOf('<LocalTaskProposals prefill={taskReviewPrefill}/>');
  assert.ok(tools>0 && controls>tools && grant>controls &&
    memory>grant && proposals>memory,
    'App launching must be above memory and saved tasks on the actual Tools screen');
  assert.equal(app.match(/<LocalTaskProposals prefill={taskReviewPrefill}\/>/g)?.length,1);
});
test('App launch grant remains explicit and does not bypass Android or action approval',()=>{
  assert.match(app,/accessibilityRole="switch" accessibilityState=\{\{checked:capabilities\.includes\('apps\.launch'\)\}\}/);
  assert.match(app,/onPress=\{\(\)=>toggle\('apps\.launch'\)\}/);
  assert.match(app,/no automatic message sending/);
  assert.match(app,/reapprove each side effect/i);
});

test('a blocked Chat device action links to Tools without changing any grant',()=>{
  assert.match(app, /message\.speaker==='assistant'[\s\S]*?\^Task blocked: Permission required:/);
  assert.match(app,/accessibilityLabel="Review device action permissions in Tools"/);
  assert.match(app,/Review device permissions in Tools/);
  assert.match(app,/onPress=\{\(\)=>navigate\('tools'\)\}/);
  assert.doesNotMatch(app,/Review device permissions in Tools[\s\S]{0,200}toggle\('apps\.launch'\)/);
});
