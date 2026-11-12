# Runs every native Nexa test class in fresh, bounded Android instrumentation
# sessions. The Infinix may disconnect UiAutomation during a single ~220-test
# session; grouping preserves ALL assertions while avoiding a stale connection.
# Does not clear app data, grant permissions, send messages, or change releases.
param(
    [string]$Serial = '',
    [string]$SourceRoot = '',
    [int]$BatchSize = 12,
    [string]$OutputDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($BatchSize -lt 1 -or $BatchSize -gt 25) {
    throw 'BatchSize must be between 1 and 25'
}
$adb = (Get-Command adb -ErrorAction Stop).Source
if (-not $SourceRoot) {
    $SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$testDirectory = Join-Path $SourceRoot 'apps\mobile\android\app\src\androidTest\java\dev\rilandev\personalaiassistant'
if (-not (Test-Path $testDirectory -PathType Container)) {
    throw "Android test source not found: $testDirectory"
}
$devices = @(& $adb devices -l | Where-Object { $_ -match '^(\S+)\s+device\b' } |
    ForEach-Object { $Matches[1] })
if (-not $Serial) {
    if ($devices.Count -ne 1) { throw "Expected exactly one authorized USB device; found $($devices.Count)" }
    $Serial = $devices[0]
}
if ($devices -notcontains $Serial) { throw "ADB serial is not authorized: $Serial" }
$package = 'dev.rilandev.personalaiassistant.physicalqa'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$installed = @(& $adb -s $Serial shell pm path $package)
if (-not ($installed -match '^package:')) {
    throw "Isolated physical QA app is not installed for the current Android user"
}
$tests = @(Get-ChildItem -Path $testDirectory -Filter '*AndroidTest.kt' -File |
    Sort-Object Name | ForEach-Object { $_.BaseName })
if ($tests.Count -eq 0) { throw 'No Android instrumentation test classes found' }

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $env:TEMP ('nexa-usb-qa-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$passed = 0
$batches = 0
Write-Host "USB device: $Serial; test classes: $($tests.Count); batch size: $BatchSize"
for ($first = 0; $first -lt $tests.Count; $first += $BatchSize) {
    $last = [Math]::Min($first + $BatchSize - 1, $tests.Count - 1)
    $selected = @($tests[$first..$last])
    $classes = ($selected | ForEach-Object { "dev.rilandev.personalaiassistant.$_" }) -join ','
    $batches++
    $logFile = Join-Path $OutputDirectory ('batch-{0:D2}.log' -f $batches)
    $lines = @(& $adb -s $Serial shell am instrument -w -r -e class $classes $runner 2>&1)
    $output = ($lines | Out-String)
    Set-Content -Path $logFile -Value $output -Encoding UTF8
    $match = [regex]::Match($output, 'OK\s*\((\d+) tests?\)')
    $complete = $output -match 'INSTRUMENTATION_CODE:\s*-1'
    $failed = $output -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_CODE:\s*0'
    if (-not $match.Success -or -not $complete -or $failed) {
        Write-Error "Android instrumentation batch $batches failed; inspect $logFile"
        exit 1
    }
    $count = [int]$match.Groups[1].Value
    $passed += $count
    Write-Host "Batch $batches passed: $count tests ($($selected.Count) classes)"
}
Write-Host "PASS: $passed Android instrumentation tests across $batches isolated batches"
Write-Host "Local evidence: $OutputDirectory"
