# Build the native WASAPI audio engine (VoiceChangerNative.dll).
# Requires MinGW-w64 g++ and JAVA_HOME pointing at a JDK with include\jni.h.
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk "include\jni.h"))) {
    throw "Set JAVA_HOME to a JDK that contains include\jni.h"
}

$candidates = @()
if ($env:MINGW_HOME) { $candidates += (Join-Path $env:MINGW_HOME "bin\g++.exe") }
$candidates += "C:\Users\tang4\.cache\msys2\msys64\mingw64\bin\g++.exe"
$candidates += "C:\msys64\mingw64\bin\g++.exe"
$candidates += "C:\mingw64\bin\g++.exe"
$gxx = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $gxx) {
    throw "g++ not found. Install MinGW-w64 and set MINGW_HOME."
}
Write-Host "compiler: $gxx"
$env:PATH = (Split-Path -Parent $gxx) + ";" + $env:PATH

$outDir = Join-Path $root "build\native"
New-Item -ItemType Directory -Force $outDir | Out-Null
$out = Join-Path $outDir "VoiceChangerNative.dll"

& $gxx -std=c++17 -O2 -shared -static -static-libgcc -static-libstdc++ `
    -DUNICODE -D_UNICODE -DWIN32_LEAN_AND_MEAN `
    -I "$jdk\include" -I "$jdk\include\win32" `
    (Join-Path $root "native\src\VoiceChangerNative.cpp") `
    (Join-Path $root "native\src\WasapiEngine.cpp") `
    (Join-Path $root "native\src\Guids.cpp") `
    -o $out -lole32 -luuid -loleaut32 -lpropsys -lavrt
if ($LASTEXITCODE -ne 0) {
    throw "g++ failed with exit code $LASTEXITCODE"
}

$distRoot = Join-Path $root "dist"
if (Test-Path $distRoot) {
    $distNative = Join-Path $distRoot "native"
    New-Item -ItemType Directory -Force $distNative | Out-Null
    Copy-Item $out $distNative -Force
    Write-Host "copied to dist\native"
}
Get-Item $out | Select-Object FullName, Length, LastWriteTime
