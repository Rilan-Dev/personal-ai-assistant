# Read-only Android release-signing lineage preflight for Nexa's main app ID.
# Compares the existing on-phone APK signature with the repository's pinned
# permanent release certificate. Never installs/uninstalls/clears or grants
# permissions. The temporary pulled APK is deleted even when verification fails.
param(
    [string]$Serial = '',
    [string]$SourceRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$package = 'dev.rilandev.personalaiassistant'

if (-not $SourceRoot) {
    $SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$fingerprintFile = Join-Path $SourceRoot 'scripts\signing\nexa-release-cert.sha256'
if (-not (Test-Path -LiteralPath $fingerprintFile -PathType Leaf)) {
    throw "Pinned release fingerprint file not found"
}
$expected = [IO.File]::ReadAllText($fingerprintFile).Trim().ToLowerInvariant()
if ($expected -notmatch '^[0-9a-f]{64}$') {
    throw 'Pinned release certificate is not a valid SHA-256 digest'
}

$adb = (Get-Command adb -ErrorAction Stop).Source
if (-not $Serial) {
    $devices = @(& $adb devices -l | ForEach-Object {
        if ($_ -match '^(\S+)\s+device\b') { $Matches[1] }
    })
    if ($devices.Count -ne 1) {
        throw 'Select exactly one authorized device using -Serial'
    }
    $Serial = $devices[0]
}
$state = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') {
    throw 'Selected ADB device is unavailable or unauthorized'
}

$sdkLocations = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
) | Where-Object { $_ -and (Test-Path $_ -PathType Container) }
$signers = @()
foreach ($sdk in $sdkLocations) {
    $root = Join-Path $sdk 'build-tools'
    if (Test-Path $root -PathType Container) {
        $signers += @(Get-ChildItem -LiteralPath $root -Filter 'apksigner.bat' -Recurse -File |
            Sort-Object FullName -Descending | Select-Object -ExpandProperty FullName)
    }
}
$signer = @($signers | Select-Object -Unique | Select-Object -First 1)
if ($signer.Count -ne 1) {
    throw 'Android SDK apksigner.bat was not found'
}

$basePaths = @(& $adb -s $Serial shell pm path $package |
    Where-Object {
        $_.StartsWith('package:') -and $_.Trim().EndsWith('/base.apk')
    })
if ($LASTEXITCODE -ne 0 -or $basePaths.Count -ne 1) {
    throw 'Exactly one installed Nexa main-package base.apk is required'
}
$remotePath = $basePaths[0].Trim().Substring(8)
if ($remotePath -notmatch '^/data/app/[^' + [char]0 + ']+/base\.apk$') {
    throw 'Unexpected installed APK path'
}

$temporary = Join-Path $env:TEMP ('nexa-signature-audit-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary -ErrorAction Stop | Out-Null
try {
    $apk = Join-Path $temporary 'installed.apk'
    # ADB's ordinary transfer progress and newer apksigner Java warnings
    # appear on stderr even when both commands exit successfully.
    $ErrorActionPreference = 'Continue'
    & $adb -s $Serial pull $remotePath $apk 2>&1 | Out-Null
    $pullExit = $LASTEXITCODE
    if ($pullExit -ne 0 -or -not (Test-Path $apk -PathType Leaf)) {
        throw 'Could not inspect installed main-package APK'
    }
    $verification = @(& $signer[0] verify --print-certs $apk 2>&1)
    $signExit = $LASTEXITCODE
    if ($signExit -ne 0) {
        throw 'Installed APK has an invalid or unsupported signature'
    }
    $digests = @($verification | Where-Object {
        $_.ToString().Contains('certificate SHA-256 digest:')
    } | ForEach-Object {
        ($_.ToString() -split ':')[-1].Trim().ToLowerInvariant()
    } | Sort-Object -Unique)
    if ($digests.Count -ne 1 -or $digests[0] -notmatch '^[0-9a-f]{64}$') {
        throw 'Installed main package has missing or ambiguous signer certificates'
    }
    Write-Output ('INSTALLED_MAIN_CERT_SHA256=' + $digests[0])
    Write-Output ('PINNED_RELEASE_CERT_SHA256=' + $expected)
    if ($digests[0] -ne $expected) {
        throw 'SIGNER_MISMATCH: in-place release upgrade NOT certified; do not uninstall or clear user data'
    }
    Write-Output 'SIGNER_MATCH: installed Nexa main package matches the pinned permanent release identity'
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}
