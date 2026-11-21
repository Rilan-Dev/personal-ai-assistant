#requires -Version 5.1
<#
.SYNOPSIS
  Calls Nexa's debug-only PowerShell bridge through Nexa app tools.
.DESCRIPTION
  ADB is used only for TCP port forwarding to the Nexa app's local abstract
  socket. This script does not invoke adb shell, adb input, am, uiautomator,
  arbitrary keycodes, or host-side UI automation. Nexa performs validation
  and dispatch through its registered Android tools and secure action journal.
  The Nexa app must be open at least once so its debug bridge is running.
.EXAMPLE
  .\nexa.ps1 status -Serial 099344034S003788
.EXAMPLE
  .\nexa.ps1 tools -Serial 099344034S003788
.EXAMPLE
  .\nexa.ps1 observe -Serial 099344034S003788 -Format json -Confirm
.EXAMPLE
  .\nexa.ps1 tree -Serial 099344034S003788 -Format json -Confirm
.EXAMPLE
  .\nexa.ps1 tap -Serial 099344034S003788 -SnapshotId <id> -Index 3 -Confirm
.EXAMPLE
  .\nexa.ps1 scroll -Serial 099344034S003788 -SnapshotId <id> -Direction down -Confirm
.EXAMPLE
  .\nexa.ps1 key -Serial 099344034S003788 -Name back -Confirm
.EXAMPLE
  .\nexa.ps1 invoke -Serial 099344034S003788 -ToolId device.battery_status -ArgsJson '{}'
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet('status', 'tools', 'observe', 'tree', 'tap', 'scroll', 'key', 'invoke')]
  [string]$Command,
  [string]$Serial = $env:ANDROID_SERIAL,
  [string]$PackageId = 'dev.rilandev.personalaiassistant.debug',
  [ValidateSet('text', 'json')]
  [string]$Format = 'text',
  [string]$SnapshotId,
  [Nullable[int]]$Index,
  [ValidateSet('up', 'down', 'left', 'right')]
  [string]$Direction,
  [ValidateSet('back', 'home', 'notifications')]
  [string]$Name,
  [string]$ToolId,
  [string]$ArgsJson = '{}',
  [switch]$Confirm,
  [ValidateRange(0, 65535)]
  [int]$LocalPort = 0
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $Serial -or $Serial -notmatch '^[A-Za-z0-9._:-]{1,80}$') {
  throw 'Provide an authorized ADB serial with -Serial (for example emulator-5554).'
}
if ($PackageId -ne 'dev.rilandev.personalaiassistant.debug') {
  throw 'The Nexa QA bridge accepts only dev.rilandev.personalaiassistant.debug.'
}
if ($Command -in @('observe', 'tree', 'tap', 'scroll', 'key') -and -not $Confirm) {
  throw "$Command requires -Confirm."
}
if ($Command -in @('tap', 'scroll') -and
    ($SnapshotId -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$')) {
  throw "$Command requires a current -SnapshotId returned by observe."
}
if ($Command -eq 'tap' -and $null -eq $Index) {
  throw 'tap requires an -Index from the current observation (0 through 39).'
}
if ($Command -eq 'tap' -and ($Index -lt 0 -or $Index -gt 39)) {
  throw 'tap requires an -Index from the current observation (0 through 39).'
}
if ($Command -eq 'scroll' -and -not $Direction) {
  throw 'scroll requires -Direction up, down, left, or right.'
}
if ($Command -eq 'key' -and -not $Name) {
  throw 'key requires -Name back, home, or notifications.'
}
if ($Command -eq 'invoke') {
  if (-not $ToolId -or $ToolId -notmatch '^[a-z][a-z0-9_.-]{2,80}$') {
    throw 'invoke requires a Nexa tool ID such as device.battery_status.'
  }
  try {
    $parsedArgs = ConvertFrom-Json -InputObject $ArgsJson -ErrorAction Stop
  } catch {
    throw '-ArgsJson must contain a valid JSON object.'
  }
  if ($null -eq $parsedArgs -or $parsedArgs -is [Array] -or
      $parsedArgs -is [string] -or $parsedArgs -is [ValueType]) {
    throw '-ArgsJson must contain a JSON object.'
  }
}

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCommand) {
  throw 'adb was not found. Install Android platform-tools and add adb to PATH.'
}

$script:localPort = $LocalPort
if ($script:localPort -eq 0) {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  try {
    $listener.Start()
    $script:localPort = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
  } finally {
    $listener.Stop()
  }
}

function New-RequestId {
  [Guid]::NewGuid().ToString()
}
function Send-NexaRequest {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter()][object]$Body
  )
  $headers = @{ 'X-Nexa-Request-Id' = (New-RequestId) }
  $uri = "$script:baseUri$Path"
  if ($Method -eq 'GET') {
    return Invoke-RestMethod -Method Get -Uri $uri -Headers $headers -TimeoutSec 20
  }
  $jsonBody = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
  return Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json' -Body $jsonBody -TimeoutSec 35
}

$forwardCreated = $false
$script:socketName = 'nexa-qa-control-' + ($PackageId.Split('.')[-1])
try {
  $forwardOutput = & $adbCommand.Source -s $Serial forward --no-rebind "tcp:$script:localPort" "localabstract:$script:socketName" 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw ('ADB port forwarding failed for {0}: {1}' -f $Serial, ($forwardOutput | Out-String))
  }
  $forwardCreated = $true
  $script:baseUri = "http://127.0.0.1:$script:localPort/v1"

  try {
    $status = Send-NexaRequest -Method Get -Path '/status'
  }
  catch {
    $failure = $_.Exception
    $details = [System.Collections.Generic.List[string]]::new()
    while ($failure) {
      if ($failure.Message) { $details.Add($failure.Message) }
      $failure = $failure.InnerException
    }
    $detailText = $details -join ' -> '
    throw "Nexa app control bridge is unavailable on $Serial. Open the selected compatible debug Nexa app on the phone; if it was already running, bring it forward once and retry. This script never falls back to ADB shell or raw input. Details: $detailText"
  }

  # The native bridge returns a stable { ok, data } envelope. PowerShell's
  # Invoke-RestMethod preserves that envelope, so unwrap it before validating
  # the app-tool protocol metadata.
  if ($status.PSObject.Properties.Name -contains 'ok') {
    if ($status.ok -ne $true) {
      throw "Nexa status request failed: $($status.error)"
    }
    if ($status.PSObject.Properties.Name -contains 'data') {
      $status = $status.data
    }
  }

  if (($status.PSObject.Properties.Name -notcontains 'protocol') -or
      $status.protocol -ne 'nexa-qa-v1' -or
      $status.executor -ne 'NexaAppTools' -or
      $status.debugBuild -ne $true -or
      $status.rawAdbInput -ne $false) {
    throw 'The connected endpoint is not the expected debug-only Nexa app-tools bridge; request refused.'
  }

  switch ($Command) {
    'status' { $result = $status }
    'tools' { $result = Send-NexaRequest -Method Get -Path '/tools' }
    'observe' {
      $result = Send-NexaRequest -Method Post -Path '/observe' -Body @{
        format = $Format; confirmed = $true
      }
    }
    'tree' {
      $result = Send-NexaRequest -Method Post -Path '/invoke' -Body @{
        toolId = 'screen.accessibility_tree'; input = @{}; confirmed = $true
      }
    }
    'tap' {
      $result = Send-NexaRequest -Method Post -Path '/tap' -Body @{
        snapshotId = $SnapshotId; index = $Index; confirmed = $true
      }
    }
    'scroll' {
      $result = Send-NexaRequest -Method Post -Path '/scroll' -Body @{
        snapshotId = $SnapshotId; direction = $Direction; confirmed = $true
      }
    }
    'key' {
      $result = Send-NexaRequest -Method Post -Path '/key' -Body @{
        name = $Name; confirmed = $true
      }
    }
    'invoke' {
      $result = Send-NexaRequest -Method Post -Path '/invoke' -Body @{
        toolId = $ToolId; input = $parsedArgs; confirmed = [bool]$Confirm
      }
    }
  }

  if ($result -and $result.PSObject.Properties.Name -contains 'ok' -and $result.ok -eq $false) {
    throw "Nexa refused '$Command': $($result.error)"
  }
  if ($result -and $result.PSObject.Properties.Name -contains 'ok' -and
      $result.ok -eq $true -and $result.PSObject.Properties.Name -contains 'data') {
    $result = $result.data
  }

  if ($Format -eq 'json' -or $Command -eq 'tools') {
    ConvertTo-Json -InputObject $result -Depth 24
  } else {
    $result
  }
}
finally {
  if ($forwardCreated) {
    & $adbCommand.Source -s $Serial forward --remove "tcp:$script:localPort" *> $null
  }
}

