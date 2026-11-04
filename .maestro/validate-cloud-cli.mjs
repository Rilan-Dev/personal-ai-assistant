import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

/** Maestro HTML-DETAILED may contain only one overall Status, even with
 * several individually reported flows. The cloud CLI emits one [Passed] or
 * [Failed] event per flow. Treat only those per-flow events as evidence.
 */
export function evaluateCloudCli(log, expectedCount) {
  if (!Number.isInteger(expectedCount) || expectedCount < 1 || expectedCount > 100)
    throw new Error('Expected flow count must be 1-100');
  const clean = String(log).replace(/\x1b\[[0-9;]*m/g,'');
  const matches = [...clean.matchAll(/^\s*\[(Passed|Failed|Stopped|Cancelled|Canceled|Error)\]\s+(.+?)\s*$/gim)];
  const flows = matches.map(([,status,detail])=>({
    status: status.toUpperCase(),
    detail: detail.trim()
  }));
  const completed = flows.length === expectedCount;
  const allPassed = flows.every(x=>x.status==='PASSED');
  const hasSummary = /\b\d+\s*\/\s*\d+\s+Flow(?:s)?\s+(?:Passed|Failed)\b/i.test(clean)
    || /\bAll Flows Passed\b/i.test(clean);
  // One status for each flow, no duplicates, and the cloud summary must
  // corroborate its output. A missing or shortened log fails closed.
  const distinct = new Set(flows.map(x=>x.detail.replace(/\s+\(\d+m(?:\s+\d+s)?\).*$/,'').trim())).size === flows.length;
  return {expectedCount, observed:flows.length, passed:completed && allPassed && distinct && hasSummary,flows,distinct,hasSummary};
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [, , path, count] = process.argv;
    const result = evaluateCloudCli(readFileSync(path,'utf8'),Number(count));
    const counts=Object.fromEntries([...new Set(result.flows.map(x=>x.status))].map(k=>[k,result.flows.filter(x=>x.status===k).length]));
    const summary='Maestro per-flow CLI evidence: '+result.observed+'/'+result.expectedCount+
      '; statuses='+JSON.stringify(counts)+'; summary='+result.hasSummary+
      '; distinct='+result.distinct;
    console.log(summary);
    if (process.env.GITHUB_STEP_SUMMARY) {
      const {appendFileSync}=await import('node:fs');
      appendFileSync(process.env.GITHUB_STEP_SUMMARY,summary+'\n');
    }
    if (!result.passed) {
      console.error('Maestro acceptance NO-GO: missing, failed or duplicate individual flow evidence.');
      process.exitCode=1;
    }
  } catch(e) {
    console.error('Maestro acceptance NO-GO: missing or invalid CLI transcript ('+e.constructor.name+').');
    process.exitCode=1;
  }
}
