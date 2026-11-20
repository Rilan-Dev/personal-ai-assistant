import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { getAcaApiOrigin, resolveAcaApiOrigin } from '../../apps/mobile/src/native/acaConfig.ts';

test('accepts HTTPS ACA origins in development and production', () => {
  assert.equal(resolveAcaApiOrigin('https://aca.example.test', true), 'https://aca.example.test');
  assert.equal(resolveAcaApiOrigin('https://aca.example.test/', false), 'https://aca.example.test');
  assert.equal(resolveAcaApiOrigin('https://aca.example.test:8443', false), 'https://aca.example.test:8443');
});

test('allows HTTP only on loopback during development', () => {
  assert.equal(resolveAcaApiOrigin('http://localhost:3000', true), 'http://localhost:3000');
  assert.equal(resolveAcaApiOrigin('http://127.0.0.1:3000', true), 'http://127.0.0.1:3000');
  assert.throws(() => resolveAcaApiOrigin('http://aca.example.test', true), /HTTPS|loopback/i);
  assert.throws(() => resolveAcaApiOrigin('http://localhost:3000', false), /HTTPS|loopback/i);
});

test('fails closed when no origin is configured', () => {
  assert.equal(resolveAcaApiOrigin(undefined, true), 'https://aca-unconfigured.invalid');
  assert.throws(() => resolveAcaApiOrigin(undefined, false), /explicit ACA API origin/i);
});

test('rejects malformed, credentialed, path, query, and fragment URLs', () => {
  for (const value of [
    'not a URL',
    'https://user:password@aca.example.test',
    'https://aca.example.test/api',
    'https://aca.example.test?tenant=one',
    'https://aca.example.test#fragment',
    ' https://aca.example.test ',
  ]) {
    assert.throws(() => resolveAcaApiOrigin(value, true), undefined, value);
  }
});

test('reads the Expo compile-time origin with the runtime development mode', () => {
  const originalDev = globalThis.__DEV__;
  const originalOrigin = process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
  try {
    globalThis.__DEV__ = true;
    process.env.EXPO_PUBLIC_ACA_API_ORIGIN = 'https://dev.aca.example.test';
    assert.equal(getAcaApiOrigin(), 'https://dev.aca.example.test');
    delete process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
    assert.equal(getAcaApiOrigin(), 'https://aca-unconfigured.invalid');
  } finally {
    if (originalDev === undefined) delete globalThis.__DEV__;
    else globalThis.__DEV__ = originalDev;
    if (originalOrigin === undefined) delete process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
    else process.env.EXPO_PUBLIC_ACA_API_ORIGIN = originalOrigin;
  }
});

test('keeps an embedded production-mode bundle bootable when Expo omitted the optional debug origin', () => {
  const originalDev = globalThis.__DEV__;
  const originalOrigin = process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
  try {
    globalThis.__DEV__ = false;
    delete process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
    assert.equal(getAcaApiOrigin(), 'https://aca-unconfigured.invalid');
  } finally {
    if (originalDev === undefined) delete globalThis.__DEV__;
    else globalThis.__DEV__ = originalDev;
    if (originalOrigin === undefined) delete process.env.EXPO_PUBLIC_ACA_API_ORIGIN;
    else process.env.EXPO_PUBLIC_ACA_API_ORIGIN = originalOrigin;
  }
});

test('Android JS bundle cache key includes the configured ACA origin', async () => {
  const gradle = await readFile(new URL('../../apps/mobile/android/app/build.gradle', import.meta.url), 'utf8');
  assert.match(gradle, /task\.name\s*==~\s*\/createBundle\.\*JsAndAssets\//);
  assert.match(gradle, /inputs\.property\(["']expoPublicAcaApiOrigin["']\s*,\s*acaApiOrigin\)/);
});
