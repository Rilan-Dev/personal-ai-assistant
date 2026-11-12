# Requires a pre-installed Nexa debug app + matching instrumentation APK.
# Operates ONLY on the synthetic QA Activity, not an owner's WhatsApp or
# other third-party app. Never clears data, changes OS permissions, or sends.
# Usage: powershell -NoProfile -File scripts/maestro-physical-qa.ps1 -Serial <authorized-ADB-serial>
[CmdletBinding()]
param(
    [string] $Serial = $env:ANDROID_SERIAL
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Serial) -or
    $Serial -notmatch '^[a-zA-Z0-9.:_-]{4,120}$') {
    throw 'Provide an authorized ADB serial with -Serial. No device selected.'
}

$sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$adb = if (Test-Path -LiteralPath $sdkAdb) {
    $sdkAdb
} else {
    (Get-Command adb -ErrorAction Stop).Source
}
$targetPackage = 'dev.rilandev.personalaiassistant.debug'
$runner = "$targetPackage.test/androidx.test.runner.AndroidJUnitRunner"
$ready = @(& $adb -s $Serial get-state 2>&1)
if ($LASTEXITCODE -ne 0 -or ($ready -join '').Trim() -ne 'device') {
    throw 'The explicitly selected physical device is not available.'
}

$installed = @(& $adb -s $Serial shell pm path $targetPackage 2>&1)
if ($LASTEXITCODE -ne 0 -or -not (($installed -join "`n") -match 'package:')) {
    throw 'Matching debug app is not installed. No test actions performed.'
}
$runners = @(& $adb -s $Serial shell pm list instrumentation 2>&1)
if ($LASTEXITCODE -ne 0 -or -not (($runners -join "`n").Contains($runner))) {
    throw 'Matching instrumentation runner is not installed. No test actions performed.'
}

# Run all three non-communicating synthetic cases in ONE instrumentation
# process. Separate invocations can race Android's single UiAutomationService
# registration (already registered), even when every adb process exited zero.
# No external account, message recipient, or device permission is accessed.
$cases = @(
    'duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader',
    'twoIdenticalChatRowsRemainAmbiguousAndAreNeverTapped',
    'uncertainSpeechVariantCanBeFoundButNotWrongAlias'
)
$prefix = 'dev.rilandev.personalaiassistant.NexaWhatsAppChatUiAndroidTest'
$selection = (@($cases | ForEach-Object { "${prefix}#$_" })) -join ','
$output = @(& $adb -s $Serial shell am instrument -w -r -e class $selection $runner 2>&1)
$exit = $LASTEXITCODE
$report = ($output | ForEach-Object { [string]$_ }) -join "`n"
$summary = [regex]::Match($report, 'OK \([0-9]+ tests?\)')
$reportedCount = if ($summary.Success) {
    [int]($summary.Value -replace '[^0-9]', '')
} else { 0 }
$passed = $exit -eq 0 -and
    $summary.Success -and $reportedCount -eq $cases.Count -and
    $report -match 'INSTRUMENTATION_CODE:\s*-1' -and
    $report -notmatch 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|shortMsg=|INSTRUMENTATION_STATUS_CODE:\s*-2|INSTRUMENTATION_CODE:\s*0'
$failed = 0
if ($passed) {
    Write-Output "PASS: all $($cases.Count) synthetic physical-device cases in one session."
} else {
    $failed++
    Write-Output "FAIL: physical QA did not establish all $($cases.Count) successful test outcomes (adb exit $exit, verified $reportedCount)."
}
if($failed -gt 0) {
    Write-Error 'Synthetic physical-device QA is not certified. No other device action attempted.'
    exit 1
}
exit 0
