import assert from 'node:assert/strict';
import {existsSync, readFileSync} from 'node:fs';
import test from 'node:test';

const startupManifests = [
  'package.json',
  'apps/mobile/package.json',
  'services/agent-runtime/package.json',
];

test('application startup scripts do not execute an unreviewed init loader', () => {
  const scripts = startupManifests.map((path) => readFileSync(path, 'utf8')).join('\n');
  assert.doesNotMatch(scripts, /\bnode\s+init\b/i);
});

test('Metro config stays declarative and free of injected runtime code', () => {
  const source = readFileSync('apps/mobile/metro.config.js', 'utf8');
  assert.ok(source.length < 1000, 'Metro config must remain a small configuration module');
  assert.match(source, /module\.exports\s*=\s*config\s*;?/);
  assert.doesNotMatch(source, /constructor\s*\(|debugger|while\s*\(\s*true\s*\)/i);
});

test('unreviewed init loader files are absent', () => {
  for (const path of ['init', 'apps/mobile/init', 'services/agent-runtime/init']) {
    assert.equal(existsSync(path), false, `${path} must not be present`);
  }
});
