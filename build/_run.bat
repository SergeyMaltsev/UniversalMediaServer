@echo off
rem Shared launcher for build*.bat: runs build.ps1 with pwsh, or Windows PowerShell if pwsh is missing.
setlocal
set "PS=pwsh.exe"
where pwsh.exe >nul 2>&1 || set "PS=powershell.exe"

"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
set "RC=%ERRORLEVEL%"

rem Keep the window open when started by double-click from Explorer
echo %CMDCMDLINE% | find /i "/c" >nul && pause
exit /b %RC%
