@echo off
REM Build, install, and launch Photo Helper. Boots an emulator if none is attached.
REM Usage: run.cmd [-Clean] [-NoLaunch] [-Avd <name>] [-Task <gradleTask>]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-app.ps1" %*
