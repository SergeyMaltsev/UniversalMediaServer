@echo off
rem Builds the UMS Windows x86_64 installer into build\setup.
rem Extra arguments go to build.ps1, e.g.  build-setup.bat -Offline
call "%~dp0_run.bat" -Target setup %*
