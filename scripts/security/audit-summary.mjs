/** Read npm audit JSON and create a nonblocking security report. */
import { readFileSync, writeFileSync } from "node:fs";
const file = process.argv[2] ?? "npm-audit.json";
const output = process.argv[3] ?? "dependency-audit-summary.md";
let audit;
try { audit = JSON.parse(readFileSync(file,"utf8")); }
catch { console.error("Invalid npm audit report"); process.exit(1); }
const v = audit.metadata?.vulnerabilities;
if (!v || typeof v !== "object") {
  console.error("Report missing vulnerability metadata");
  process.exit(1);
}
const order = { critical:0, high:1, moderate:2, low:3, info:4 };
const rows = Object.entries(audit.vulnerabilities ?? {})
  .filter(([name,record]) => typeof name === "string" && record && typeof record === "object")
  .map(([name,record]) => ({
    name, severity:record.severity, direct:Boolean(record.isDirect),
    via:(record.via ?? []).filter(x=>typeof x==="string").slice(0,4).join(", ")
  }))
  .sort((a,b)=>(order[a.severity] ?? 5)-(order[b.severity] ?? 5)||a.name.localeCompare(b.name));
const summary = [
  "## npm dependency audit (informational, not a release approval)",
  "",
  "**Critical:** "+(v.critical??0)+" | **High:** "+(v.high??0)+" | **Moderate:** "+(v.moderate??0)+" | **Low:** "+(v.low??0),
  "",
  "| Dependency | Severity | Direct | Via |",
  "|---|---|---|---|",
  ...rows.map(x=>"| "+x.name+" | "+x.severity+" | "+(x.direct?"Yes":"No")+" | "+x.via+" |"),
  "",
  "Do not force major transitive package overrides without Expo APK, Web and harness compatibility tests.",
  "Open advisories require risk assessment before distribution.",
  ""
].join("\n");
writeFileSync(output,summary);
console.log(summary);
