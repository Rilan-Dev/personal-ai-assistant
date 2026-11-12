import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const app=readFileSync('apps/mobile/App.tsx','utf8');

test('Tools presents owner-controlled App launching grant before saved task backlog',()=>{
  const controls=app.indexOf('<Text style={styles.cardHeading}>Device controls</Text>');
  const grant=app.indexOf('<Text style={styles.settingTitle}>App launching</Text>');
  const proposals=app.indexOf('<LocalTaskProposals prefill={taskReviewPrefill}/>');
  assert.ok(controls>0 && grant>controls && proposals>grant,
    'Owner must not scroll through old task proposals to reach app-launch control');
  assert.equal(app.match(/<LocalTaskProposals prefill={taskReviewPrefill}\/>/g)?.length,1);
});
test('App launch grant remains explicit and does not bypass Android or action approval',()=>{
  assert.match(app,/accessibilityRole="switch" accessibilityState=\{\{checked:capabilities\.includes\('apps\.launch'\)\}\}/);
  assert.match(app,/onPress=\{\(\)=>toggle\('apps\.launch'\)\}/);
  assert.match(app,/no automatic message sending/);
  assert.match(app,/reapprove each side effect/i);
});
