param(
  [Parameter(Mandatory = $true)]
  [string] $DeviceId,

  [string] $MaestroCli,
  [int] $AccessibilityBindTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$flows = @(
  (Join-Path $repoRoot '.maestro\physical-qa\00-device-preflight.yaml'),
  (Join-Path $repoRoot '.maestro\physical-qa\02-ready-check.yaml'),
  (Join-Path $repoRoot '.maestro\physical-qa\01-enable-accessibility-services.yaml')
)
$packageName = 'dev.rilandev.personalaiassistant.physicalqa2'
$qaScreenService = "$packageName/dev.rilandev.personalaiassistant.ScreenAssistService"
$qaLauncherService = "$packageName/dev.rilandev.personalaiassistant.NexaLauncherNavigationService"

if (-not $MaestroCli) {
  $maestroCommand = Get-Command 'maestro.bat' -ErrorAction SilentlyContinue
  if (-not $maestroCommand) { $maestroCommand = Get-Command 'maestro' -ErrorAction SilentlyContinue }
  if ($maestroCommand) {
    $MaestroCli = $maestroCommand.Source
  } else {
    $installedCli = Join-Path $env:LOCALAPPDATA 'Programs\Maestro\maestro\bin\maestro.bat'
    if (Test-Path $installedCli) { $MaestroCli = $installedCli }
  }
}
if (-not $MaestroCli -or -not (Test-Path $MaestroCli)) {
  throw 'Maestro CLI was not found. Pass -MaestroCli with its full path.'
}
foreach ($flow in $flows) {
  if (-not (Test-Path $flow)) { throw "Preflight flow is missing: $flow" }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { throw 'adb is not on PATH.' }
$deviceState = & $adb.Source -s $DeviceId get-state 2>&1
if ($LASTEXITCODE -ne 0 -or ($deviceState | Out-String).Trim() -ne 'device') {
  throw "Physical Android device $DeviceId is not connected and ready."
}
$maestroPackages = (& $adb.Source -s $DeviceId shell pm list packages 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or
    $maestroPackages -notmatch '(?m)^package:dev\.mobile\.maestro\s*$' -or
    $maestroPackages -notmatch '(?m)^package:dev\.mobile\.maestro\.test\s*$') {
  throw 'Install the Maestro 2.12.1 app/server APK pair before running this physical-device preflight.'
}
foreach ($driverPackage in @('dev.mobile.maestro', 'dev.mobile.maestro.test')) {
  & $adb.Source -s $DeviceId shell pm enable $driverPackage | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Could not enable Maestro runner package $driverPackage." }
}

$testOutput = Join-Path $repoRoot '.qa-results\physical-qa-preflight'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$junitReports = [System.Collections.Generic.List[string]]::new()

# App configuration and the Tools readiness check run before Android Settings.
# Maestro UiAutomation restores the prior enabled-service list when another
# session starts, so Accessibility setup must be the final Maestro session.
$driverCleanupExitCode = 0
try {
  foreach ($flow in $flows) {
    foreach ($driverPackage in @('dev.mobile.maestro', 'dev.mobile.maestro.test')) {
      & $adb.Source -s $DeviceId shell pm enable $driverPackage | Out-Null
      if ($LASTEXITCODE -ne 0) { throw "Could not enable Maestro runner package $driverPackage." }
    }
    $stage = [System.IO.Path]::GetFileNameWithoutExtension($flow)
    $junit = Join-Path $testOutput "$stage-$runId.xml"
    $runArtifacts = Join-Path (Join-Path $testOutput $runId) $stage
    $junitReports.Add($junit)
    # Keep the known-good local Android driver pair installed. Maestro 2.12.1's
    # reinstall path uninstalls the pair and can leave this device driver dead.
    & $MaestroCli test "--device=$DeviceId" --no-reinstall-driver --format=JUNIT "--output=$junit" "--test-output-dir=$runArtifacts" $flow
    $maestroExitCode = $LASTEXITCODE
    if ($maestroExitCode -ne 0) { throw "Maestro preflight stage '$stage' failed with exit code $maestroExitCode. See $junit" }

    # Each short stage releases UiAutomation so Accessibility services can bind
    # before the next stage starts its own Maestro session.
    & $adb.Source -s $DeviceId shell am force-stop dev.mobile.maestro
    if ($LASTEXITCODE -ne 0) { throw 'Could not release Maestro driver between stages.' }
    & $adb.Source -s $DeviceId shell am force-stop dev.mobile.maestro.test
    if ($LASTEXITCODE -ne 0) { throw 'Could not release Maestro test driver between stages.' }
  }
} finally {
  foreach ($driverPackage in @('dev.mobile.maestro', 'dev.mobile.maestro.test')) {
    & $adb.Source -s $DeviceId shell pm enable $driverPackage | Out-Null
    if ($LASTEXITCODE -ne 0) { $driverCleanupExitCode = $LASTEXITCODE }
  }
  & $adb.Source -s $DeviceId shell am force-stop dev.mobile.maestro
  if ($LASTEXITCODE -ne 0) { $driverCleanupExitCode = $LASTEXITCODE }
  & $adb.Source -s $DeviceId shell am force-stop dev.mobile.maestro.test
  if ($LASTEXITCODE -ne 0) { $driverCleanupExitCode = $LASTEXITCODE }
}
if ($driverCleanupExitCode -ne 0) { throw 'Could not restore/stop the Maestro driver packages.' }

$requiredRuntimePermissions = @(
  'android.permission.RECORD_AUDIO',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.CAMERA'
)
$packageDump = (& $adb.Source -s $DeviceId shell dumpsys package $packageName 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Could not query QA2 runtime permissions.' }
$runtimePermissionLines = $packageDump -split "`r?`n"
foreach ($permission in $requiredRuntimePermissions) {
  $permissionPattern = '^\s*' + [regex]::Escape($permission) + ':\s+granted=true(?:,|$)'
  if (-not ($runtimePermissionLines | Where-Object { $_ -match $permissionPattern } | Select-Object -First 1)) {
    throw "Required runtime permission is not granted: $permission."
  }
}

$overlayState = (& $adb.Source -s $DeviceId shell appops get $packageName SYSTEM_ALERT_WINDOW 2>&1 | Out-String).Trim()
$overlayPermissionGranted = $packageDump -match 'android\.permission\.SYSTEM_ALERT_WINDOW:\s+granted=true'
$overlayExplicitlyDenied = $overlayState -match 'SYSTEM_ALERT_WINDOW:\s+(deny|ignore)'
if ($LASTEXITCODE -ne 0 -or -not $overlayPermissionGranted -or $overlayExplicitlyDenied) {
  throw "Overlay permission is not granted. PackageManager granted=$overlayPermissionGranted; app-op=[$overlayState]"
}

$deadline = (Get-Date).AddSeconds($AccessibilityBindTimeoutSeconds)
$accessibilityDump = ''
do {
  $accessibilityDump = (& $adb.Source -s $DeviceId shell dumpsys accessibility 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0) { throw 'Could not query Android Accessibility service state.' }
  $enabledQaScreen = $accessibilityDump.Contains($qaScreenService)
  $enabledQaLauncher = $accessibilityDump.Contains($qaLauncherService)
  $boundQaScreen = $accessibilityDump.Contains('Service[label=Nexa Screen Assist QA2')
  $boundQaLauncher = $accessibilityDump.Contains('Service[label=Nexa Launcher Navigation QA2')
  if ($enabledQaScreen -and $enabledQaLauncher -and $boundQaScreen -and $boundQaLauncher) { break }
  if ((Get-Date) -ge $deadline) { break }
  Start-Sleep -Seconds 2
} while ($true)

if (-not $enabledQaScreen -or -not $enabledQaLauncher -or -not $boundQaScreen -or -not $boundQaLauncher) {
  $enabledStatus = "ScreenAssist=$enabledQaScreen Launcher=$enabledQaLauncher"
  $boundStatus = "ScreenAssist=$boundQaScreen Launcher=$boundQaLauncher"
  throw "QA2 Accessibility is not enabled and bound. Enabled [$enabledStatus]; bound [$boundStatus]."
}
if ($accessibilityDump -notmatch 'Crashed services:\s*\{\s*\}') {
  throw 'Android reports a crashed Accessibility service.'
}

& $adb.Source -s $DeviceId shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'QA2 permissions are ready, but Nexa could not be returned to the foreground.' }

Write-Output "PASS: Maestro permission setup completed on $DeviceId."
Write-Output 'PASS: Microphone, notifications, coarse location, and camera runtime permissions are granted.'
Write-Output 'PASS: Overlay permission is granted.'
Write-Output 'PASS: QA2 Screen Assist and Launcher Navigation are both enabled and bound with no crash.'
Write-Output 'PASS: Nexa QA2 returned to the foreground, ready for the next test.'
foreach ($report in $junitReports) { Write-Output "Maestro report: $report" }
