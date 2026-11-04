import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

/**
 * Maestro's HTML aggregate may say PASSED while all individual flows show
 * STOPPED. Only individual flow results are sufficient for acceptance.
 */
export function evaluateMaestroReport(html, expectedCount) {
  if (!Number.isInteger(expectedCount) || expectedCount < 1 || expectedCount > 100) {
    throw new Error('Expected flow count must be a positive integer <= 100');
  }
  const statuses = [...String(html).matchAll(/Status:\s*(PASSED|FAILED|STOPPED|CANCELLED|CANCELED|ERROR)\b/gi)]
    .map(match => match[1].toUpperCase());
  const counts = Object.fromEntries([...new Set(statuses)].map(status => [
    status, statuses.filter(item => item === status).length
  ]));
  return {
    expectedCount,
    actualCount: statuses.length,
    statuses,
    counts,
    passed: statuses.length === expectedCount && statuses.every(s => s === 'PASSED')
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , htmlPath, expectedArg] = process.argv;
  const expected = Number(expectedArg);
  try {
    const report = readFileSync(htmlPath, 'utf8');
    const result = evaluateMaestroReport(report, expected);
    const summary = `Maestro flow evidence: ${result.actualCount}/${result.expectedCount} individually recorded; ${JSON.stringify(result.counts)}`;
    console.log(summary);
    if (process.env.GITHUB_STEP_SUMMARY) {
      const { appendFileSync } = await import('node:fs');
      appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary + '\n');
    }
    if (!result.passed) {
      console.error('Maestro acceptance NO-GO: missing, failed or stopped flow evidence.');
      process.exitCode = 1;
    }
  } catch (error) {
    console.error('Maestro acceptance NO-GO: report absent or invalid (' + error.constructor.name + ')');
    process.exitCode = 1;
  }
}
