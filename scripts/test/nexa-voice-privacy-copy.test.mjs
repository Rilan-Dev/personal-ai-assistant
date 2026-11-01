import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const app=readFileSync('apps/mobile/App.tsx','utf8');

test('Voice UI distinguishes background opt-in wake detection from live conversation',()=>{
  assert.match(app,/separate opt-in offline Hey Nexa listener/);
  assert.match(app,/explicit Voice action or an assistant invocation mode/);
  assert.match(app,/green microphone indicator/);
  assert.match(app,/Stop notification/);
  assert.match(app,/cannot hide/);
  assert.doesNotMatch(app,/No wake-word or unattended recording\./);
  assert.doesNotMatch(app,/No wake word or unattended recording\./);
});
