import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateMaestroReport } from './validate-report.mjs';

const report = (statuses, aggregate = 'PASSED') =>
  '<html><h1>Test Result: ' + aggregate + '</h1>' +
  statuses.map(status => '<p>Status: ' + status + '<br>Duration: 1s</p>').join('') +
  '</html>';

test('all individually passed Maestro flow results are accepted', () => {
  const result = evaluateMaestroReport(report(['PASSED', 'PASSED', 'PASSED']), 3);
  assert.equal(result.passed, true);
  assert.deepEqual(result.counts, { PASSED: 3 });
});

test('aggregate PASSED cannot conceal STOPPED or FAILED flows', () => {
  const result = evaluateMaestroReport(report(['STOPPED', 'PASSED', 'FAILED']), 3);
  assert.equal(result.passed, false);
  assert.equal(result.counts.STOPPED, 1);
  assert.equal(result.counts.FAILED, 1);
});

test('a missing or truncated report never becomes accepted', () => {
  assert.equal(evaluateMaestroReport(report(['PASSED']), 11).passed, false);
  assert.equal(evaluateMaestroReport('<html>Test Result: PASSED</html>', 1).passed, false);
});

test('invalid expected flow counts are refused', () => {
  assert.throws(() => evaluateMaestroReport(report(['PASSED']), 0));
  assert.throws(() => evaluateMaestroReport(report(['PASSED']), 1.5));
});
