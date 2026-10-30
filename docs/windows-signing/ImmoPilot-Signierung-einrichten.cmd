@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Initialize-ImmoPilotSigning.ps1" -CopyForGitHub
if errorlevel 1 echo Einrichtung nicht abgeschlossen. Vorhandene App unveraendert lassen.
pause
