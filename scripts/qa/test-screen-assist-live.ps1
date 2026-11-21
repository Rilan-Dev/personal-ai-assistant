[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[A-Za-z0-9._:-]{1,80}$')]
  [string]$Serial
)

$ErrorActionPreference = 'Stop'
$bridge = Join-Path $PSScriptRoot '..\..\nexa.ps1'
$debugPackage = 'dev.rilandev.personalaiassistant.debug'
$settingsPackage = 'com.android.settings'
$armed = $false

try {
  $status = & $bridge status -Serial $Serial -Format json | ConvertFrom-Json
  if ($status.debugBuild -ne $true -or $status.bridgeAvailable -ne $true -or
      $status.accessibilityEnabled -ne $true) {
    throw 'Nexa debug bridge or Screen Assist is not ready on the selected device.'
  }

  $arm = & $bridge armread -Serial $Serial -Format json -Confirm | ConvertFrom-Json
  if ($arm.armed -ne $true) { throw 'Nexa did not arm the one-shot capture.' }
  $armed = $true

  $open = & $bridge invoke -Serial $Serial -ToolId device.open_app `
    -ArgsJson '{"app":"Settings"}' -Format json -Confirm | ConvertFrom-Json
  if ($open.verification -ne 'destination_package_observed' -or
      $open.packageId -ne $settingsPackage) {
    throw 'Nexa did not verify Android Settings as the opened app.'
  }

  Start-Sleep -Seconds 2
  $back = & $bridge key -Serial $Serial -Name back -Format json -Confirm | ConvertFrom-Json
  if ($back.screenObservationAvailable -ne $true -or
      $back.screenObservation.packageName -ne $debugPackage) {
    throw 'Nexa did not verify return to its debug app before review.'
  }

  $review = & $bridge reviewread -Serial $Serial -Format json -Confirm | ConvertFrom-Json
  $armed = $false
  if ($review.captured -ne $true -or $review.packageName -ne $settingsPackage) {
    throw 'The one-shot capture was not reviewed from Android Settings.'
  }

  $consumed = & $bridge reviewread -Serial $Serial -Format json -Confirm | ConvertFrom-Json
  if ($consumed.captured -ne $false -or $null -ne $consumed.packageName) {
    throw 'The one-shot capture was not consumed after review.'
  }

  [pscustomobject]@{
    device = $Serial
    captured = $true
    packageName = $review.packageName
    captureConsumed = $true
    capturedTextReturned = $false
  } | ConvertTo-Json -Compress
} finally {
  if ($armed) {
    try { & $bridge reviewread -Serial $Serial -Format json -Confirm | Out-Null }
    catch { }
  }
}
