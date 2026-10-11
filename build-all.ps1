# One click: builds the PC jar AND the Android APK. Right-click -> Run with PowerShell (or double-click build-all.bat).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$ok = $true
foreach ($step in 'compilepc.ps1', 'compileandroid.ps1') {
    Write-Host "=== $step ===" -ForegroundColor Cyan
    try { & "$PSScriptRoot\$step" } catch { $ok = $false; Write-Host "FAILED: $step - $($_.Exception.Message)" -ForegroundColor Red }
}
Write-Host ''
if ($ok) { Write-Host 'ALL DONE: Rally3D-Java21-GPU.jar + Rally3D-Android.apk' -ForegroundColor Green }
else { Write-Host 'Finished with errors (see above).' -ForegroundColor Red }
if ($Host.Name -eq 'ConsoleHost') { Read-Host 'Press Enter to close' | Out-Null }
