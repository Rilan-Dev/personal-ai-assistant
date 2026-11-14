import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const repoRoot=join(dirname(fileURLToPath(import.meta.url)),'..','..');
const gradle=readFileSync(join(repoRoot,'apps/mobile/android/app/build.gradle'),'utf8');

test('Windows native staging is conditional; Linux keeps Android Gradle Plugin defaults',()=>{
  const begin=gradle.lastIndexOf('    externalNativeBuild {');
  const end=gradle.indexOf('\n    androidResources {',begin);
  assert.ok(begin >= 0 && end > begin, 'app-level CMake staging block exists');
  const staging=gradle.slice(begin,end);
  assert.match(staging,/if\s*\(System\.getProperty\('os\.name'\)\.toLowerCase\(java\.util\.Locale\.ROOT\)\.contains\('windows'\)\)\s*\{\s*buildStagingDirectory\s+'D:\/paa-cxx'\s*\}/,
    'D: staging must only be selected on Windows');
  assert.equal((gradle.match(/buildStagingDirectory\s+'D:\/paa-cxx'/g)||[]).length,1,
    'there must be no unguarded duplicate Windows-only staging path');
});
