import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync, readdirSync, statSync} from 'node:fs';
import path from 'node:path';

const gradle=readFileSync('apps/mobile/android/app/build.gradle','utf8');
const runner=readFileSync('scripts/qa/start-physical-live-debug.ps1','utf8');
const cli=readFileSync('nexa.ps1','utf8');
const identity=readFileSync(
  'apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant/NexaBuildIdentityAndroidTest.kt',
  'utf8');

function yamlFiles(directory) {
  return readdirSync(directory).flatMap(name=>{
    const full=path.join(directory,name);
    if(statSync(full).isDirectory())return name==='results'?[]:yamlFiles(full);
    return full.endsWith('.yaml')?[full]:[];
  });
}

test('one isolated .debug app supports Metro Fast Refresh and source identity checks',()=>{
  assert.ok(gradle.includes('paaLiveDebug'), 'Gradle needs the explicit live-debug property');
  assert.ok(/liveDebugBuild[\s\S]*?debuggableVariants = \["debug"\]/.test(gradle),
    'live debug builds must use Metro/Fast Refresh');
  assert.ok(/PAA_USE_EMBEDDED_JS_BUNDLE[\s\S]*?useEmbeddedPhysicalQaBundle/.test(gradle),
    'live debug config must flow into the runtime bundle setting');
  assert.ok(/liveDebugBuild[\s\S]*?applicationIdSuffix '\.debug'/.test(gradle),
    'live debug must resolve to the single isolated .debug package');
  assert.ok(/liveDebugBuild[\s\S]*?installDebugAlongsideRelease[\s\S]*?GradleException/.test(gradle),
    'live debug must refuse to overwrite the release package');
  assert.ok(runner.includes('personalaiassistant.debug'), 'physical live runner must target .debug');
  assert.ok(cli.includes("PackageId = 'dev.rilandev.personalaiassistant.debug'"),
    'Nexa CLI must default to the single .debug package');
  assert.ok(identity.includes('dev.rilandev.personalaiassistant.debug'),
    'source identity checks must permit the isolated .debug package');
});

test('all Nexa Maestro flows target the same isolated debug app',()=>{
  const manifests=yamlFiles('.maestro').map(file=>[file,readFileSync(file,'utf8')]);
  assert.ok(!manifests.some(([file])=>file.split(/[\\/]/).includes('results')),
    'captured Maestro result bundles are evidence artifacts, not executable source flows');
  for(const [file,source] of manifests) {
    if(!source.startsWith('appId: dev.rilandev.personalaiassistant'))continue;
    assert.match(source,/^appId: dev\.rilandev\.personalaiassistant\.debug\s*$/m,
      `${file} must target the single .debug package`);
    assert.doesNotMatch(source,/^appId: dev\.rilandev\.personalaiassistant(?:\.physicalqa2?|\s*$)/m,
      `${file} must not target an old QA or base package`);
  }
});
