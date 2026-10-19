#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env.local'

if (Test-Path -LiteralPath $envFile -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }

        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }

        $key = $trimmed.Substring(0, $separator).Trim()
        if ($key -ne 'JAVA_HOME') { continue }

        $value = $trimmed.Substring($separator + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($value) { $env:JAVA_HOME = $value }
    }
}

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set. Copy .env.example to .env.local and set JAVA_HOME to a JDK 17 root, or set JAVA_HOME in this terminal."
}

$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}

$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$versionOutput = (& $javaExe -version 2>&1 | Out-String)
$ErrorActionPreference = $previousErrorAction
if ($versionOutput -notmatch 'version "17\.') {
    throw "This project requires JDK 17. Current JAVA_HOME reports:`n$versionOutput"
}

$mvnw = Join-Path $repoRoot 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mvnw -PathType Leaf)) {
    throw "Maven Wrapper not found: $mvnw"
}

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @('--batch-mode', '--no-transfer-progress', 'clean', 'verify')
}

Write-Host "[build] JAVA_HOME=$env:JAVA_HOME"
Push-Location $repoRoot
try {
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $mvnw @MavenArgs
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
}
finally {
    Pop-Location
}

exit $exitCode
