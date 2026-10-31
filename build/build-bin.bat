@echo off
rem Builds the UMS program files (no installer) into build\bin.
rem Extra arguments go to build.ps1, e.g.  build-bin.bat -Offline
call "%~dp0_run.bat" -Target bin %*
