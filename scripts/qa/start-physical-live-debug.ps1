[CmdletBinding()]
param(
    [string]$Serial = '099344034S003788'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$packageName = 'dev.rilandev.personalaiassistant.physicalqa2'
$adbCommand = Get-Command adb -ErrorAction Stop
$npmCommand = Get-Command npm.cmd -ErrorAction Stop

$deviceList = @(& $adbCommand.Source devices)
if ($LASTEXITCODE -ne 0) {
    throw 'adb devices failed.'
}
$attached = $deviceList | Where-Object {
    $_ -match ('^' + [regex]::Escape($Serial) + '\s+device(?:\s|$)')
}
if (-not $attached) {
    throw "Physical Android device $Serial is not attached and authorized. No other device will be selected."
}

$installedPath = @(& $adbCommand.Source -s $Serial shell pm path $packageName)
if ($LASTEXITCODE -ne 0 -or -not ($installedPath -match '^package:')) {
    throw "The isolated QA app $packageName is not installed on $Serial. Build/install the live QA2 variant once first."
}

& $adbCommand.Source -s $Serial reverse tcp:8081 tcp:8081
if ($LASTEXITCODE -ne 0) {
    throw "Could not establish USB reverse port 8081 for $Serial."
}

Write-Host "Metro live mode for $packageName on physical device $Serial."
Write-Host 'JavaScript/TypeScript edits use Fast Refresh; native Android changes require a rebuild and one reinstall.'
Write-Host 'Keep this terminal running while testing. Press Ctrl+C to stop Metro.'

Push-Location $projectRoot
try {
    & $npmCommand.Source run start:mobile:live
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
