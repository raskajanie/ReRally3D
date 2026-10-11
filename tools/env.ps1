# Shared toolchain setup (dot-sourced by the build scripts). Uses the JDK bundled in tools\jdk, falls back to a system JDK.
$ErrorActionPreference = 'Stop'
$script:Root = Split-Path -Parent $PSScriptRoot
$script:Tools = $PSScriptRoot
$bundled = Join-Path $Tools 'jdk\bin\javac.exe'
if (Test-Path $bundled) { $script:JavaBin = Join-Path $Tools 'jdk\bin' }
else {
    $c = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $c) { throw 'No JDK found: tools\jdk is missing and javac is not in PATH. Re-extract the archive.' }
    $script:JavaBin = Split-Path -Parent $c.Source
}
$script:Javac = Join-Path $JavaBin 'javac.exe'
$script:Java  = Join-Path $JavaBin 'java.exe'
$script:Jar   = Join-Path $JavaBin 'jar.exe'
function Invoke-Native([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($exe)) failed (exit $LASTEXITCODE)" }
}
