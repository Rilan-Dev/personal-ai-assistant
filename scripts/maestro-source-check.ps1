param(
    [string]$ExpectedSha = 'aaa248d7fd4ca4924444dc027af9c7a62a9be348'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$upstream = Join-Path $repo 'third_party\maestro-upstream'

function Fail([string]$message) {
    [Console]::Error.WriteLine('MAESTRO_PIN_FAILED: ' + $message)
    exit 1
}

if (-not (Test-Path -LiteralPath (Join-Path $upstream '.git'))) {
    Fail 'Submodule is absent; run git submodule update --init.'
}

$actual = (& git -C $upstream rev-parse HEAD 2>$null)
if ($LASTEXITCODE -ne 0 -or $actual -ne $ExpectedSha) {
    Fail ('Expected '+$ExpectedSha+' but found '+$actual+'. Pin changes require review.')
}

$dirty = & git -C $upstream status --porcelain
if ($LASTEXITCODE -ne 0 -or $dirty) {
    Fail 'Upstream working tree is dirty. Never edit original Maestro source.'
}

$files = @(
    'maestro\Point.kt',
    'maestro\Bounds.kt',
    'maestro\TreeNode.kt',
    'maestro\SwipeDirection.kt',
    'maestro\ScrollDirection.kt'
)
$sourceRoot = Join-Path $upstream 'maestro-client\src\main\java'
foreach($file in $files) {
    if (-not (Test-Path -LiteralPath (Join-Path $sourceRoot $file) -PathType Leaf)) {
        Fail ('Required upstream source missing: ' + $file)
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $upstream 'LICENSE') -PathType Leaf)) {
    Fail 'Upstream license missing.'
}
Write-Output ('MAESTRO_PIN_OK '+$actual+'; '+$files.Count+' portable sources; unchanged upstream')
