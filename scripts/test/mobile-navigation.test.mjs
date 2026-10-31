import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const app=readFileSync('apps/mobile/App.tsx','utf8');

test('mobile navigation has four real independent tab screens',()=>{
  assert.match(app,/useState<'chat'\|'voice'\|'tools'\|'settings'>\('chat'\)/);
  for(const id of ['chat','voice','tools','settings']) {
    assert.match(app,new RegExp("tab === '"+id+"'"));
    assert.match(app,new RegExp("id:'"+id+"'"));
  }
  assert.match(app,/accessibilityRole="tab"/);
  assert.match(app,/accessibilityState=\{\{selected:tab===item.id\}\}/);
  assert.match(app,/onPress=\{\(\)=>navigate\(item.id\)\}/);
  assert.match(app,/bottomNav:/);
});

test('settings controls and credential entry are separate from conversation',()=>{
  assert.match(app,/\{tab === 'settings' && <ScrollView/);
  assert.match(app,/AI Configuration & API Key/);
  assert.match(app,/secureTextEntry editable=/);
  assert.match(app,/saveProviderToVault\(config\)/);
  assert.match(app,/clearProviderFromVault\(\)/);
  assert.match(app,/\{tab === 'chat' && <>/);
  assert.match(app,/styles\.composerRow/);
});

test('voice and device tools expose explicit action controls',()=>{
  assert.match(app,/\{tab === 'voice' && <ScrollView/);
  assert.match(app,/onPress=\{listening\?cancelListening:listenForCommand\}/);
  assert.match(app,/\{tab === 'tools' && <ScrollView/);
  assert.match(app,/onPress=\{grantFlashlightAccess\}/);
  assert.match(app,/onPress=\{installLaunchTile\}/);
  assert.match(app,/pendingApproval && <View/);
});

test('user and agent text use consistent readable system font',()=>{
  assert.match(app,/fontFamily:'sans-serif'/);
  assert.match(app,/fontSize:14,lineHeight:22/);
  assert.match(app,/borderRadius:22/);
});

test('Android back button navigates from settings/tools/voice to chat',()=>{
  assert.match(app,/BackHandler\.addEventListener\('hardwareBackPress'/);
  assert.match(app,/if\(tab!=='chat'\)\{navigate\('chat'\);return true;\}/);
  assert.match(app,/Keyboard\.dismiss\(\)/);
});
