@echo off
cd /d "%~dp0"
set "JAR=build\VoiceChanger.jar"

if not exist "%JAR%" (
  echo VoiceChanger.jar not found. Building...
  powershell -ExecutionPolicy Bypass -File build.ps1
  if not exist "%JAR%" (
    echo Build failed. Check the output above.
    pause
    exit /b 1
  )
)

if exist "runtime\bin\javaw.exe" (
  start "" "runtime\bin\javaw.exe" -jar "%JAR%"
) else (
  start "" javaw -jar "%JAR%"
)
