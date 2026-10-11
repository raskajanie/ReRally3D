# Android build, fully offline: javac -> dx -> ApkPack -> apksigner. No Android SDK needed (tools\ has android.jar, dx, apksigner).
# Result: Rally3D-Android.apk next to this script.
# NOTE: AndroidManifest.xml / icon are taken precompiled from source\android-pkg\prebuilt (no aapt). If you edit
# source\android-pkg\AndroidManifest.xml, regenerate prebuilt with a real aapt (see README.txt).
. "$PSScriptRoot\tools\env.ps1"
Set-Location $PSScriptRoot
$s = "$PSScriptRoot\source"; $b = "$PSScriptRoot\build\android"; $t = $Tools
$androidJar = "$t\android.jar"
if (Test-Path $b) { Remove-Item $b -Recurse -Force }
New-Item -ItemType Directory -Path "$b\classes", "$b\root", "$b\pack" -Force | Out-Null
# 1. compile (Java 8 level, Android API 34 stubs)
$src = Get-ChildItem "$s\game", "$s\android" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Invoke-Native $Javac (@('-source', '8', '-target', '8', '-nowarn', '-bootclasspath', $androidJar, '-d', "$b\classes") + $src)
# 2. dex
Invoke-Native $Java @('-cp', "$t\dx.jar", 'com.android.dx.command.Main', '--dex', '--min-sdk-version=26', "--output=$b\root\classes.dex", "$b\classes")
# 3. pack (classes.dex + game resources + precompiled manifest/arsc/icon); resources.arsc stored+aligned
Invoke-Native $Javac @('-nowarn', '-d', "$b\pack", "$t\ApkPack.java")
Copy-Item "$s\res\*" "$b\root" -Recurse -Force
Invoke-Native $Java @('-cp', "$b\pack", 'ApkPack', "$b\signme.apk", "$s\android-pkg\prebuilt", "$b\root")
# 4. sign (v2+v3) with the persistent debug key
$ks = "$s\android-pkg\debug.jks"
if (-not (Test-Path $ks)) {
    Invoke-Native (Join-Path $JavaBin 'keytool.exe') @('-genkeypair', '-keystore', $ks, '-storepass', 'android', '-keypass', 'android', '-alias', 'rally', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '36500', '-dname', 'CN=Rally3D Port')
}
$out = "$PSScriptRoot\Rally3D-Android.apk"
Invoke-Native $Java @('-cp', "$t\apksigner.jar;$t\apksig.jar", 'com.android.apksigner.ApkSignerTool', 'sign', '--ks', $ks, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--ks-key-alias', 'rally', '--min-sdk-version', '26', '--out', $out, "$b\signme.apk")
Remove-Item "$out.idsig" -ErrorAction SilentlyContinue
Remove-Item $b -Recurse -Force
Write-Host 'BUILD OK: Rally3D-Android.apk'
