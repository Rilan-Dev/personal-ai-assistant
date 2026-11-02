import test from 'node:test';
import assert from 'node:assert/strict';
import {parsePairedIconObservation} from '../src/paired-icon-capture.ts';

const bounds={left:24,top:45,right:72,bottom:89};
const pair=()=>({
 snapshotId:'visual-001',packageName:'com.example.app',windowId:7,
 capturedAtWallMs:1000,expiresInMs:45000,
 crops:[{iconId:'icon-0',boundsPx:bounds}],
 observation:{
  version:1,snapshotId:'visual-001',packageName:'com.example.app',windowId:7,
  capturedAtWallMs:1000,expiresInMs:45000,
  windowBoundsPx:{left:0,top:0,right:400,bottom:800},
  icons:[{id:'icon-0',label:'Unknown icon',semantic:'unknown',clickable:true,
    evidence:'accessibility',boundsPx:bounds}]
 }
});
test('an exact same-event paired unknown icon remains read-only and parses',()=>{
 const observed=parsePairedIconObservation(pair());
 assert.ok(observed);
 assert.equal(observed.snapshotId,'visual-001');
 assert.equal(observed.icons[0].semantic,'unknown');
 assert.equal(observed.windowId,7);
});
test('no metadata, mismatched window, stale captured timestamp or package fail closed',()=>{
 for(const update of [
  {observation:undefined},
  {windowId:8},
  {packageName:'com.other.app'},
  {capturedAtWallMs:5000},
  {observation:{...pair().observation,windowId:9}},
  {observation:{...pair().observation,expiresInMs:35000}}
 ])assert.equal(parsePairedIconObservation({...pair(),...update}),null);
});
test('a crop must identify exactly one unknown clickable icon with identical geometry',()=>{
 const base=pair();
 for(const observation of [
  {...base.observation,icons:[]},
  {...base.observation,icons:[{...base.observation.icons[0],semantic:'search',label:'Search'}]},
  {...base.observation,icons:[{...base.observation.icons[0],clickable:false}]},
  {...base.observation,icons:[{...base.observation.icons[0],boundsPx:{...bounds,left:35}}]},
  {...base.observation,icons:[...base.observation.icons,{...base.observation.icons[0],id:'icon-1'}]}
 ])assert.equal(parsePairedIconObservation({...base,observation}),null);
});
test('all crop IDs must be unique and within the observation; unknown extras are allowed',()=>{
 const p=pair();
 assert.equal(parsePairedIconObservation({...p,crops:[p.crops[0],p.crops[0]]}),null);
 assert.equal(parsePairedIconObservation({...p,crops:[{...p.crops[0],iconId:'missing'}]}),null);
 assert.ok(parsePairedIconObservation({...p,crops:[]}));
});
