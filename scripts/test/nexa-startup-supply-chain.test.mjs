import assert from 'node:assert/strict';
import { test } from 'node:test';
import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const read = (relative) => readFileSync(path.join(root, relative), 'utf8');

test('Nexa start entrypoints never execute an unreviewed init bootstrap', () => {
  for (const relative of ['init', 'apps/mobile/init', 'services/agent-runtime/init']) {
    assert.equal(existsSync(path.join(root, relative)), false,
      `Unexpected executable bootstrap: ${relative}`);
  }
  for (const relative of [
    'package.json',
    'apps/mobile/package.json',
    'services/agent-runtime/package.json',
  ]) {
    const manifest = JSON.parse(read(relative));
    for (const [name, command] of Object.entries(manifest.scripts ?? {})) {
      assert.doesNotMatch(command, /\bnode(?:\.exe)?\s+(?:\.\/)?init(?:\s|$|&&)/i,
        `Unexpected bootstrap in ${relative} ${name}`);
    }
  }
});

test('Metro config contains only the bounded Expo configuration', () => {
  const source = read('apps/mobile/metro.config.js');
  assert.ok(source.length < 4096, 'Unexpected oversized Metro startup configuration');
  assert.match(source, /getDefaultConfig\(__dirname\)/);
  assert.doesNotMatch(source, /\b(?:eval|Function|spin|splitLane)\s*\(/);
});

test('Git ignores private environment files in every workspace', () => {
  const sensitive = [
    '.env',
    '.env.production',
    '.env.local',
    'apps/mobile/.env',
    'apps/mobile/.env.development',
    'services/agent-runtime/.env',
    'services/agent-runtime/.env.production',
    'config.bat',
  ];
  for (const candidate of sensitive) {
    const result = spawnSync('git',
      ['check-ignore', '--no-index', '-q', '--', candidate],
      { cwd: root, encoding: 'utf8' });
    assert.equal(result.status, 0,
      `Secret path must be ignored: ${candidate}; stderr=${result.stderr?.trim()}`);
  }
  for (const template of ['.env.example', 'apps/mobile/.env.example']) {
    const result = spawnSync('git',
      ['check-ignore', '--no-index', '-q', '--', template],
      { cwd: root, encoding: 'utf8' });
    assert.equal(result.status, 1, `Safe template should remain trackable: ${template}`);
  }
});
