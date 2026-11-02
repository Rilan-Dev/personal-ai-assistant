import test from 'node:test';
import assert from 'node:assert/strict';
import {iconSemanticFromLabel, parseScreenIconSnapshot} from '../../agent-contracts/src/screen-icons.ts';

const valid = () => ({version:1,snapshotId:'sample-abc',packageName:'com.example.app',
  capturedAtWallMs:1730000000000,expiresInMs:10000,
  icons:[{id:'i1',label:'Video call',semantic:'video_call',evidence:'accessibility',
    clickable:true,boundsPx:{left:10,top:20,right:50,bottom:60}}]});

test('only bounded, known icon labels have semantics', () => {
  assert.equal(iconSemanticFromLabel(' VIDEO CALL BUTTON '), 'video_call');
  assert.equal(iconSemanticFromLabel('Send to Alice'), 'unknown');
  assert.equal(iconSemanticFromLabel('Password: 1234'), 'unknown');
  assert.equal(iconSemanticFromLabel({text:'Call'}), 'unknown');
});
test('parses a validated observation while stripping arbitrary extra fields', () => {
  const input=valid(); input.icons[0].privateMessage='do not expose';
  const output=parseScreenIconSnapshot({...input,screenshot:'hidden screenshot'});
  assert.ok(output);
  assert.equal(output.icons[0].semantic,'video_call');
  assert.equal(JSON.stringify(output).includes('privateMessage'),false);
  assert.equal(JSON.stringify(output).includes('screenshot'),false);
});
test('rejects stale/oversized or forged metadata and invalid bounds', () => {
  assert.equal(parseScreenIconSnapshot({...valid(),expiresInMs:60001}),null);
  assert.equal(parseScreenIconSnapshot({...valid(),icons:Array(65).fill(valid().icons[0])}),null);
  const fake=valid();fake.icons[0].semantic='delete';
  assert.equal(parseScreenIconSnapshot(fake),null);
  const bounds=valid();bounds.icons[0].boundsPx.right=2;
  assert.equal(parseScreenIconSnapshot(bounds),null);
});
test('rejects duplicate IDs and unexpected packages', () => {
  const duplicate=valid();duplicate.icons.push({...duplicate.icons[0]});
  assert.equal(parseScreenIconSnapshot(duplicate),null);
  assert.equal(parseScreenIconSnapshot({...valid(),packageName:'com.app\nsecret'}),null);
});
