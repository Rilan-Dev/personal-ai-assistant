import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync} from "node:fs";

test("ALL GitHub Actions workflows require explicit owner-dispatched runs",()=>{
  const dir=".github/workflows";
  const files=readdirSync(dir).filter(file=>/\.ya?ml$/.test(file));
  assert.ok(files.length>=5);
  for(const file of files) {
    const yaml=readFileSync(dir+"/"+file,"utf8");
    const header=yaml.split(/^jobs:/m)[0];
    assert.match(header,/^on:\s*\n\s+workflow_dispatch:\s*$/m,file);
    assert.doesNotMatch(header,/^\s+(push|pull_request|schedule|workflow_run|repository_dispatch|create|delete):/m,file);
    assert.doesNotMatch(header,/^\s+branches:/m,file);
  }
});
test("Debug signing secret is gated to manual main-branch runs",()=>{
  const file=readFileSync(".github/workflows/android-debug.yml","utf8");
  assert.match(file,/github\.event_name == 'workflow_dispatch'/);
  assert.match(file,/github\.ref == 'refs\/heads\/main'/);
  assert.doesNotMatch(file,/github\.event_name == 'push'/);
});
