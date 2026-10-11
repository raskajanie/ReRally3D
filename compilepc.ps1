# PC build. Result: Rally3D-Java21-GPU.jar next to this script. Uses the bundled JDK (tools\jdk).
. "$PSScriptRoot\tools\env.ps1"
Set-Location $PSScriptRoot
$s = "$PSScriptRoot\source"; $b = "$PSScriptRoot\build\pc"
if (Test-Path $b) { Remove-Item $b -Recurse -Force }
New-Item -ItemType Directory -Path $b -Force | Out-Null
$libs = Get-ChildItem "$s\lib" -Filter *.jar
$cp = ($libs | Where-Object { $_.Name -notmatch 'natives' } | ForEach-Object { $_.FullName }) -join ';'
$src = Get-ChildItem "$s\game", "$s\pc" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Invoke-Native $Javac (@('--release', '21', '-nowarn', '-cp', $cp, '-d', $b) + $src)
Copy-Item "$s\res\*" $b -Recurse -Force
$rt = 'Class-Path: ' + (($libs | ForEach-Object { 'source/lib/' + $_.Name }) -join ' ')
$lines = @('Manifest-Version: 1.0', 'Main-Class: rally3d.desktop.Main')
while ($rt.Length -gt 70) { $i = $rt.LastIndexOf(' ', 70); $lines += $rt.Substring(0, $i); $rt = ' ' + $rt.Substring($i) }
$lines += $rt
[IO.File]::WriteAllLines("$b\MANIFEST.MF", $lines, [Text.UTF8Encoding]::new($false))
Invoke-Native $Jar @('cfm', "$PSScriptRoot\Rally3D-Java21-GPU.jar", "$b\MANIFEST.MF", '-C', $b, '.')
Remove-Item $b -Recurse -Force
Write-Host 'BUILD OK: Rally3D-Java21-GPU.jar'
