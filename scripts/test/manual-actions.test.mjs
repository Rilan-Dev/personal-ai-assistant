import test from "node:test";
import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {mkdtempSync,writeFileSync,chmodSync,readFileSync,existsSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {delimiter,join} from "node:path";

const script="scripts/ci/manual-actions.sh";
const sha="e68d6fa7296cfd72ff702dac11293713e0b33112";
const bash=process.platform==="win32"
  ? join(process.env.ProgramFiles||"C:\\Program Files","Git","bin","bash.exe")
  : "/bin/bash";

function harness(scenario,args){
  const root=mkdtempSync(join(tmpdir(),"paa-gh-run-"));
  const bin=join(root,"gh");
  const log=join(root,"calls");
  // GitHub CLI mock: no network, no Actions or credentials.
  const mock=[
    "#!/usr/bin/env bash",
    "set -eu",
    'printf "%s\n" "$*" >> "$MOCK_LOG"',
    'if [[ "$1 $2" == "auth status" ]]; then [[ "$MOCK_SCENARIO" != "no_auth" ]]; exit $?; fi',
    'if [[ "$1" == "api" ]]; then echo "$MOCK_SHA"; exit 0; fi',
    'if [[ "$1 $2" == "run list" ]]; then',
    '  workflow=""; expression=""',
    '  while [[ $# -gt 0 ]]; do',
    '    case "$1" in',
    '      --workflow) workflow="$2"; shift 2 ;;',
    '      --jq) expression="$2"; shift 2 ;;',
    '      *) shift ;;',
    '    esac',
    '  done',
    '  case "$MOCK_SCENARIO" in',
    '    existing_success)',
    '      [[ "$expression" == *\x27conclusion == "success"\x27* ]] && echo 1 || echo 0 ;;',
    '    existing_active)',
    '      [[ "$expression" == *\x27status != "completed"\x27* ]] && echo 1 || echo 0 ;;',
    '    existing_failure)',
    '      [[ "$expression" == *\x27conclusion != "success"\x27* ]] && echo 1 || echo 0 ;;',
    '    release_ready|emulator_ready)',
    '      if [[ "$workflow" == "android-debug.yml" ||',
    '            ( "$MOCK_SCENARIO" == "release_ready" && "$workflow" == "android-emulator.yml" ) ]]; then',
    '        [[ "$expression" == *\x27conclusion == "success"\x27* ]] && echo 1 || echo 0',
    '      else echo 0; fi ;;',
    '    *) echo 0 ;;',
    '  esac',
    '  exit 0',
    'fi',
    'if [[ "$1 $2" == "workflow run" ]]; then echo "MOCK_DISPATCH_SUCCESS"; exit 0; fi',
    'exit 22',
    ''
  ].join("\n").replaceAll("\\x27","'");
  writeFileSync(bin,mock);
  chmodSync(bin,0o755);
  const result=spawnSync(bash,[script,...args],{
    // Git Bash startup is much slower on Windows than native POSIX bash, and
    // each preflight intentionally makes several isolated gh mock calls.
    encoding:"utf8",timeout:60000,
    env:{...process.env,MOCK_SCENARIO:scenario,MOCK_SHA:sha,MOCK_LOG:log,
      PATH:root+delimiter+process.env.PATH}
  });
  const calls=existsSync(log)?readFileSync(log,"utf8"):"";
  rmSync(root,{recursive:true,force:true});
  return {result,calls};
}

test("dispatcher requires explicit allowed workflow argument and stays on Development",()=>{
  const source=readFileSync(script,"utf8");
  assert.match(source,/target_ref="Development"/);
  assert.match(source,/gh workflow run "\$workflow" --repo "\$REPO" --ref "\$target_ref"/);
  assert.match(source,/workflow" == "android-main-promotion\.yml"[\s\S]*target_ref="main"/);
  assert.doesNotMatch(source,/git push|curl.*token/);
  const {result,calls}=harness("clean",["arbitrary.yml"]);
  assert.equal(result.status,2);
  assert.doesNotMatch(calls,/workflow run/);
});

test("manual debug dispatch targets current Development SHA and requested workflow",()=>{
  const {result,calls}=harness("clean",["android-debug.yml"]);
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/MOCK_DISPATCH_SUCCESS/);
  assert.match(calls,/api repos\/Rilan-Dev\/personal-ai-assistant\/commits\/Development/);
  assert.match(calls,new RegExp("--commit "+sha));
  assert.match(calls,/workflow run android-debug.yml --repo Rilan-Dev\/personal-ai-assistant --ref Development/);
});

test("dry run never dispatches a job",()=>{
  const {result,calls}=harness("clean",["android-debug.yml","--dry-run"]);
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/DRY RUN/);
  assert.doesNotMatch(calls,/workflow run/);
});

test("existing successful or active runs prevent duplicate Actions charges",()=>{
  for (const scenario of ["existing_success","existing_active"]){
    const {result,calls}=harness(scenario,["android-debug.yml"]);
    assert.equal(result.status,0,result.stderr);
    assert.match(result.stdout,/SKIPPED/);
    assert.doesNotMatch(calls,/workflow run/);
  }
});

test("failed same-SHA jobs require explicit retry",()=>{
  const failure=harness("existing_failure",["android-debug.yml"]);
  assert.equal(failure.result.status,3);
  assert.doesNotMatch(failure.calls,/workflow run/);
  const retry=harness("existing_failure",["android-debug.yml","--retry-failed"]);
  assert.equal(retry.result.status,0,retry.result.stderr);
  assert.match(retry.calls,/workflow run android-debug.yml/);
});

test("release is refused until debug and emulator passed on same Development SHA",()=>{
  const failure=harness("emulator_ready",["android-release-test.yml"]);
  assert.equal(failure.result.status,3);
  assert.match(failure.result.stderr,/emulator acceptance/);
  assert.doesNotMatch(failure.calls,/workflow run/);
  const okay=harness("release_ready",["android-release-test.yml"]);
  assert.equal(okay.result.status,0,okay.result.stderr);
  assert.match(okay.calls,/workflow run android-release-test.yml/);
});

test("emulator is refused before debug compile passes",()=>{
  const failure=harness("clean",["android-emulator.yml"]);
  assert.equal(failure.result.status,3);
  assert.match(failure.result.stderr,/debug compilation/);
  assert.doesNotMatch(failure.calls,/workflow run/);
  const okay=harness("emulator_ready",["android-emulator.yml"]);
  assert.equal(okay.result.status,0,okay.result.stderr);
  assert.match(okay.calls,/workflow run android-emulator.yml/);
});

test("auth failure never dispatches anything",()=>{
  const {result,calls}=harness("no_auth",["android-debug.yml"]);
  assert.equal(result.status,2);
  assert.match(result.stderr,/authentication/);
  assert.doesNotMatch(calls,/workflow run/);
});


test("one gated main promotion pipeline can be manually dispatched without individual workflow prerequisites",()=>{
  const okay=harness("clean",["android-main-promotion.yml"]);
  assert.equal(okay.result.status,0,okay.result.stderr);
  assert.match(okay.calls,/api repos\/Rilan-Dev\/personal-ai-assistant\/commits\/main/);
  assert.match(okay.calls,/workflow run android-main-promotion.yml --repo Rilan-Dev\/personal-ai-assistant --ref main/);
  const dry=harness("clean",["android-main-promotion.yml","--dry-run"]);
  assert.equal(dry.result.status,0,dry.result.stderr);
  assert.doesNotMatch(dry.calls,/workflow run/);
});
