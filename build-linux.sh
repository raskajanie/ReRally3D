#!/usr/bin/env bash
# Linux / macOS build (same steps as compilepc.ps1 / compileandroid.ps1, no PowerShell, no Wine).
# Needs JDK 21 (javac, jar) in PATH. Usage: ./build-linux.sh [apk|pc|all]   (default: all)
# Results: Rally3D-Android.apk and Rally3D-Java21-GPU.jar next to this script.
# The PC jar built here only has the Windows LWJGL natives (source/lib); to RUN it on Linux/macOS add
# lwjgl*-natives-linux / -macos jars (3.4.2) to source/lib first (see README, Build).
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$PWD; S=$ROOT/source; T=$ROOT/tools; WHAT=${1:-all}
command -v javac >/dev/null || { echo "javac not found: install JDK 21" >&2; exit 1; }
B=$(mktemp -d); trap 'rm -rf "$B"' EXIT

build_apk() {
  # classes (Java 8 level, Android API 34 stubs) -> dex -> pack with the precompiled manifest -> sign v2+v3
  mkdir -p "$B/classes" "$B/root" "$B/pack"
  javac -source 8 -target 8 -nowarn -bootclasspath "$T/android.jar" -d "$B/classes" $(find "$S/game" "$S/android" -name '*.java')
  java -cp "$T/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$B/root/classes.dex" "$B/classes"
  javac -nowarn -d "$B/pack" "$T/ApkPack.java"
  cp -r "$S"/res/* "$B/root/"
  java -cp "$B/pack" ApkPack "$B/signme.apk" "$S/android-pkg/prebuilt" "$B/root"
  java -cp "$T/apksigner.jar:$T/apksig.jar" com.android.apksigner.ApkSignerTool sign \
    --ks "$S/android-pkg/debug.jks" --ks-pass pass:android --key-pass pass:android --ks-key-alias rally \
    --min-sdk-version 26 --out "$ROOT/Rally3D-Android.apk" "$B/signme.apk"
  rm -f "$ROOT/Rally3D-Android.apk.idsig"
  echo "BUILD OK: Rally3D-Android.apk"
}

build_pc() {
  mkdir -p "$B/pc"
  local CP; CP=$(ls "$S"/lib/*.jar | grep -v natives | tr '\n' ':')
  javac --release 21 -nowarn -cp "$CP" -d "$B/pc" $(find "$S/game" "$S/pc" -name '*.java')
  cp -r "$S"/res/* "$B/pc/"
  local cp_line="Class-Path:"; for j in "$S"/lib/*.jar; do cp_line="$cp_line source/lib/$(basename "$j")"; done
  # manifest lines are limited to 72 bytes: wrap with a leading space
  { echo "Manifest-Version: 1.0"; echo "Main-Class: rally3d.desktop.Main"; echo "$cp_line" | fold -w 70 | sed '2,$s/^/ /'; } > "$B/MANIFEST.MF"
  jar cfm "$ROOT/Rally3D-Java21-GPU.jar" "$B/MANIFEST.MF" -C "$B/pc" .
  echo "BUILD OK: Rally3D-Java21-GPU.jar"
}

case "$WHAT" in
  apk) build_apk ;;
  pc) build_pc ;;
  all) build_pc; build_apk ;;
  *) echo "usage: $0 [apk|pc|all]" >&2; exit 1 ;;
esac
