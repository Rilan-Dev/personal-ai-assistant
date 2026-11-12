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

# Each synthetic scenario runs in isolation. The test fixture guarantees
# zero outbound communication; real recipient/account data must never be
# included. Only the short pass/fail result is emitted.
$cases = @(
    'duplicatedLabelNodesResolveToOneRealChatRowAndVerifyHeader',
    'twoIdenticalChatRowsRemainAmbiguousAndAreNeverTapped',
    'uncertainSpeechVariantCanBeFoundButNotWrongAlias'
)
$failed = 0
foreach ($case in $cases) {
    $className = "dev.rilandev.personalaiassistant.NexaWhatsAppChatUiAndroidTest#$case"
    $output = @(& $adb -s $Serial shell am instrument -w -r -e class $className $runner 2>&1)
    $exit = $LASTEXITCODE
    $report = ($output | ForEach-Object { [string]$_ }) -join "`n"
    $passed = $exit -eq 0 -and
        $report -match 'OK \([0-9]+ tests?\)' -and
        $report -match 'INSTRUMENTATION_CODE:\s*-1' -and
        $report -notmatch 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|shortMsg=|INSTRUMENTATION_CODE:\s*0'
    if ($passed) {
        Write-Output "PASS: synthetic Android accessibility case $case"
    } else {
        $failed++
        # Fail closed even when adb exits with zero for failed instrumentation.
        Write-Output "FAIL: synthetic Android accessibility case $case (adb exit $exit)"
    }
}
if($failed -gt 0) {
    Write-Error "$failed synthetic physical-device cases failed. No other device action attempted."
    exit 1
}
Write-Output "PASS: all $($cases.Count) isolated synthetic device cases."
exit 0
