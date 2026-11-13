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
$signerList = @($signers | Select-Object -Unique | Select-Object -First 1)
if ($signerList.Count -ne 1) {
    throw 'Android SDK apksigner.bat was not found'
}
$signerExecutable = $signerList[0]

$basePaths = @(& $adb -s $Serial shell pm path $package |
    Where-Object {
        $_.StartsWith('package:') -and $_.Trim().EndsWith('/base.apk')
    })
if ($LASTEXITCODE -ne 0 -or $basePaths.Count -ne 1) {
    throw 'Exactly one installed Nexa main-package base.apk is required'
}
$remotePath = $basePaths[0].Trim().Substring(8)
if (-not $remotePath.StartsWith('/data/app/') -or
    -not $remotePath.EndsWith('/base.apk')) {
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
    $verification = @(& $signerExecutable verify --print-certs $apk 2>&1)
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
        # A prior development install may be signed with the owner's EXISTING
        # local debug keystore. Inspect only its public certificate fingerprint;
        # never export, copy or upload the private key. Matching the legacy
        # identity does NOT authorize upgrading to the different release key.
        $legacyKeystore = Join-Path $SourceRoot 'apps\mobile\android\app\debug.keystore'
        if (Test-Path -LiteralPath $legacyKeystore -PathType Leaf) {
            $keytoolCommand = Get-Command keytool -ErrorAction SilentlyContinue
            $keytoolPath = if ($keytoolCommand) { $keytoolCommand.Source }
                else { Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe' }
            if (Test-Path -LiteralPath $keytoolPath -PathType Leaf) {
                # The optional local legacy-debug classification is NEVER
                # needed to decide the already-proven release signer mismatch.
                # Some Windows keytool/JBR launches wait indefinitely in SSH
                # sessions. Bound this subprocess to 10 seconds and preserve
                # the mandatory signer-mismatch rejection even on timeout.
                # Redirect only the certificate listing to temporary files,
                # deleted by the outer finally; do not export a private key.
                $legacyOutput = Join-Path $temporary 'legacy-cert-public.txt'
                $legacyErrors = Join-Path $temporary 'legacy-cert-diagnostics.txt'
                $arguments = '-list -v -keystore "' + $legacyKeystore +
                    '" -storepass android -alias androiddebugkey -noprompt'
                $keytoolProcess = $null
                try {
                    $keytoolProcess = Start-Process -FilePath $keytoolPath
                        -ArgumentList $arguments -NoNewWindow -PassThru
                        -RedirectStandardOutput $legacyOutput
                        -RedirectStandardError $legacyErrors
                    if (-not $keytoolProcess.WaitForExit(10000)) {
                        try { $keytoolProcess.Kill() } catch {}
                        # A stuck Java child may outlive the diagnostic; the
                        # release verdict must never wait for optional output.
                        Write-Output 'LEGACY_DEBUG_KEYTOOL_TIMEOUT=true'
                    } elseif ($keytoolProcess.ExitCode -eq 0) {
                        $listing = @(Get-Content -LiteralPath $legacyOutput
                            -ErrorAction SilentlyContinue)
                        $certLines = @($listing | Where-Object {
                            $_.ToString().TrimStart().StartsWith('SHA256:')
                        })
                        if ($certLines.Count -eq 1) {
                            $legacyDigest = ($certLines[0].ToString().Split(':',2)[1] -replace ':','').Trim().ToLowerInvariant()
                            if ($legacyDigest -match '^[0-9a-f]{64}
            }
        }
        throw 'SIGNER_MISMATCH: in-place release upgrade NOT certified; do not uninstall or clear user data'
    }
    Write-Output 'SIGNER_MATCH: installed Nexa main package matches the pinned permanent release identity'
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}
 -and
                                $legacyDigest -eq $digests[0]) {
                                Write-Output 'LEGACY_DEBUG_SIGNER_MATCH=true'
                                Write-Output 'Legacy owner debug keystore can be inspected for non-production in-place upgrades. Production key migration needs a separately verified Android signing lineage.'
                            }
                        }
                    }
                } catch {
                    # This is an optional diagnostic only. A mismatch always
                    # stays a hard NO-GO whether keytool runs or fails.
                    Write-Output 'LEGACY_DEBUG_KEYTOOL_UNAVAILABLE=true'
                } finally {
                    if ($null -ne $keytoolProcess) {
                        if (-not $keytoolProcess.HasExited) {
                            try { $keytoolProcess.Kill() } catch {}
                        }
                        $keytoolProcess.Dispose()
                    }
                }
            }
        }
        throw 'SIGNER_MISMATCH: in-place release upgrade NOT certified; do not uninstall or clear user data'
    }
    Write-Output 'SIGNER_MATCH: installed Nexa main package matches the pinned permanent release identity'
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}
