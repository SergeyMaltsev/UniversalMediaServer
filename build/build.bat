@echo off
rem Builds UMS for Windows: program files into build\bin and installers into build\setup.
rem Extra arguments go to build.ps1, e.g.  build.bat -Offline   (see build.ps1 for details)
call "%~dp0_run.bat" -Target all %*
