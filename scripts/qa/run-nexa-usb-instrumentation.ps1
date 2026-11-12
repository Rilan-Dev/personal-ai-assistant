# Runs Nexa Android instrumentation in bounded, independent USB sessions.
# Never clears app data, grants permissions, sends messages or changes release apps.
param(
    [string]$Serial = '',
    [string]$SourceRoot = '',
    [string]$GitRef = 'origin/Development',
    [int]$BatchSize = 12,
    [string]$OutputDirectory = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($BatchSize -lt 1 -or $BatchSize -gt 25) {
    throw 'BatchSize must be between 1 and 25'
}
$adb = (Get-Command adb -ErrorAction Stop).Source
$git = (Get-Command git -ErrorAction Stop).Source
if (-not $SourceRoot) {
    $SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
if (-not (Test-Path (Join-Path $SourceRoot '.git'))) {
    throw "Git checkout not found: $SourceRoot"
}
$revision = (& $git -C $SourceRoot rev-parse --verify $GitRef 2>&1)
if ($LASTEXITCODE -ne 0 -or -not $revision) {
    throw "Git ref not available. Fetch Development first: $GitRef"
}
$testPath = 'apps/mobile/android/app/src/androidTest/java/dev/rilandev/personalaiassistant'
$devices = @(& $adb devices -l | ForEach-Object {
    if ($_ -match '^(\S+)\s+device\b') { $Matches[1] }
})
if (-not $Serial) {
    if ($devices.Count -ne 1) {
        throw "Expected exactly one authorized USB device; found $($devices.Count)"
    }
    $Serial = $devices[0]
}
if ($devices -notcontains $Serial) { throw "ADB serial is not authorized: $Serial" }
$package = 'dev.rilandev.personalaiassistant.physicalqa'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$installed = @(& $adb -s $Serial shell pm path $package)
if (-not ($installed -match '^package:')) {
    throw 'Isolated physical QA app is not installed for the current Android user'
}
# Use the fetched revision, not the stale or dirty Windows working tree.
$paths = @(& $git -C $SourceRoot ls-tree -r --name-only $GitRef -- $testPath)
if ($LASTEXITCODE -ne 0) { throw "Cannot enumerate test classes at $GitRef" }
$tests = @($paths | ForEach-Object {
    if ($_ -match '/([A-Za-z0-9_]+AndroidTest)\.kt$') { $Matches[1] }
} | Sort-Object -Unique)
if ($tests.Count -eq 0) { throw "No test classes found at $GitRef" }
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $env:TEMP (
        'nexa-usb-qa-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
# Check the actual installed application's embedded BuildConfig revision before
# any test batch. A stale APK/test pair must never be certified as a new ref.
$identityClass = 'dev.rilandev.personalaiassistant.NexaBuildIdentityAndroidTest'
$identityLog = Join-Path $OutputDirectory 'source-identity.log'
$identityLines = @(& $adb -s $Serial shell am instrument -w -r -e class $identityClass -e nexaExpectedRevision $revision $runner 2>&1)
$identityOutput = ($identityLines | Out-String)
Set-Content -Path $identityLog -Value $identityOutput -Encoding UTF8
$identityPassed = $identityOutput -match 'OK\s*\(1 test\)' -and
    $identityOutput -match 'INSTRUMENTATION_CODE:\s*-1' -and
    $identityOutput -notmatch 'FAILURES!!!|Process crashed|INSTRUMENTATION_CODE:\s*0'
if (-not $identityPassed) {
    throw "Installed APK/test revision did not match $revision. See $identityLog"
}
Write-Host "Installed Android source identity verified: $revision"
$passed = 0
$batches = 0
Write-Host "Source ref: $GitRef ($revision)"
Write-Host "USB device: $Serial; test classes: $($tests.Count); batch size: $BatchSize"
for ($first = 0; $first -lt $tests.Count; $first += $BatchSize) {
    $last = [Math]::Min($first + $BatchSize - 1, $tests.Count - 1)
    $selected = @($tests[$first..$last])
    $classes = ($selected | ForEach-Object {
        "dev.rilandev.personalaiassistant.$_"
    }) -join ','
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
