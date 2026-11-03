# Read-only evidence collection for an owner-approved Nexa Android test.
# No adb permission grants, automation taps, shell settings writes, or uploads.
param(
  [string]$Serial = "",
  [ValidateSet("Before","After")][string]$Phase = "Before",
  [string]$OutputDirectory = ".\\nexa-screen-assist-evidence"
)
$ErrorActionPreference = "Stop"
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = if ($adbCommand) { $adbCommand.Source } else {
  Join-Path $env:LOCALAPPDATA "Android\\Sdk\\platform-tools\\adb.exe"
}
if (!(Test-Path $adbPath)) {
  throw "adb not found. Install Android platform-tools or add adb to PATH."
}
$devices = @(& $adbPath devices -l)
$ready = @($devices | Where-Object { $_ -match '^(\\S+)\\s+device\\s+' })
if (-not $Serial) {
  if ($ready.Count -ne 1) {
    throw "Expected exactly one authorized device. Run adb devices -l, authorize USB debugging on the phone, or supply -Serial."
  }
  $Serial = [regex]::Match($ready[0], '^\\S+').Value
}
$target = @("-s", $Serial)
function Invoke-DeviceRead([string[]]$Arguments) {
  $result = @(& $adbPath @target @Arguments 2>&1)
  if ($LASTEXITCODE -ne 0) {
    throw ("adb failed: " + ($Arguments -join " ") + " - " + ($result -join " "))
  }
  return $result
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$prefix = Join-Path $OutputDirectory ("Nexa-" + $Phase + "-" + $stamp)
$package = "dev.rilandev.personalaiassistant"
$manifest = Invoke-DeviceRead @("shell","dumpsys","package",$package)
$manifest | Select-String -Pattern "versionName=|versionCode=|ScreenAssistService|enabled=" |
  Out-File -FilePath ($prefix + "-package.txt") -Encoding utf8
$enabled = Invoke-DeviceRead @("shell","settings","get","secure","enabled_accessibility_services")
$enabled | Out-File -FilePath ($prefix + "-owner-enabled-services.txt") -Encoding utf8
$accessibility = Invoke-DeviceRead @("shell","dumpsys","accessibility")
$accessibility | Select-String -Pattern "ScreenAssistService|Nexa Screen Assist|Bound Services|Enabled Services|mBoundServices|mEnabledServices" -Context 2,2 |
  Out-File -FilePath ($prefix + "-accessibility-binding.txt") -Encoding utf8
$activities = Invoke-DeviceRead @("shell","dumpsys","activity","activities")
$activities | Select-String -Pattern $package,"com.android.systemui" -Context 1,1 |
  Out-File -FilePath ($prefix + "-activities.txt") -Encoding utf8
$crash = Invoke-DeviceRead @("logcat","-b","crash","-d")
$crash | Out-File -FilePath ($prefix + "-crashes.txt") -Encoding utf8
Write-Host ("Evidence saved locally: " + (Resolve-Path $OutputDirectory).Path)
Write-Host ("Device: " + $Serial + " | Phase: " + $Phase)
Write-Host "Manual Android consent is required. This script never enables Accessibility or runs UI actions."
Write-Host "Review and redact phone numbers, contact names, emails, and other private log data before sharing."
