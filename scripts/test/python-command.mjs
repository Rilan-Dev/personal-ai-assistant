import {spawnSync} from "node:child_process";

export function python3Command() {
  const candidates = process.platform === "win32"
    ? spawnSync("where.exe", ["python3"], {encoding: "utf8"}).stdout
        .trim().split(/\r?\n/).filter(Boolean)
    : ["python3"];
  const working = candidates.find(candidate =>
    spawnSync(candidate, ["--version"], {encoding: "utf8"}).status === 0);
  if (!working) throw new Error("Python 3 is required for Android acceptance tests");
  return working;
}
