<#
.SYNOPSIS
    Builds UMS for Windows x64 from this source checkout: program files and/or installer.

.DESCRIPTION
    Runs "mvn -P windows package" in the repository root and collects the results:
      bin    -> build\bin\    program folder, same layout the installer puts into Program Files
                              (UMS.exe, ums.jar, jre17\, bin\ with FFmpeg and other tools, web\, renderers\ ...)
      setup  -> build\setup\  UMS-Windows-<version>-x86_64.exe (no 32-bit installer, disabled in pom.xml)
      all    -> both, from one Maven run
    For "bin" the NSIS step is skipped (-Dnsis.disabled=true) once target\UMS.exe exists, which saves several minutes.

    Build tools come from ..\..\setup\build-tools.ps1 (JDK 17, Maven, caches in setup\tools; nothing is
    installed, the environment is changed for this session only). The first build also downloads the
    Windows binaries (FFmpeg, JRE, MediaInfo ...) into target\bin; -Offline reuses them.

    Nothing is installed or started. The working tree stays as it was: the build rewrites the tracked
    react-client\.yarn\install-state.gz, which is restored afterwards if it was unchanged before.

.PARAMETER Target
    all (default), bin or setup.

.PARAMETER Offline
    Skip downloading the Windows binaries (needs a previous online build; target\ must not be cleaned).

.PARAMETER Tests
    Run the unit tests (skipped by default).

.PARAMETER CustomVersion
    Release number of the custom edition (e.g. 15.9.0-custom.1, set by release.ps1): shown as the version in the
    web UI and used in the installer name UMS-Windows-<CustomVersion>-x86_64.exe.

.EXAMPLE
    .\build.ps1
    .\build.ps1 -Target bin -Offline
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'bin', 'setup')]
    [string]$Target = 'all',
    [switch]$Offline,
    [switch]$Tests,
    [string]$CustomVersion = ''
)

$ErrorActionPreference = 'Stop'

$BuildDir  = $PSScriptRoot
$SourceDir = [IO.Path]::GetFullPath((Join-Path $BuildDir '..'))
$SetupDir  = [IO.Path]::GetFullPath((Join-Path $SourceDir '..\setup'))
$TargetDir = Join-Path $SourceDir 'target'
$ExtRes    = Join-Path $SourceDir 'src\main\external-resources'
$YarnState = 'react-client/.yarn/install-state.gz'

$toolsScript = Join-Path $SetupDir 'build-tools.ps1'
if (-not (Test-Path -LiteralPath $toolsScript)) { throw "Build tools script not found: $toolsScript" }
. $toolsScript

function Invoke-Git {
    # Windows PowerShell 5.1 turns redirected native stderr into errors, so do not stop on it
    $ErrorActionPreference = 'Continue'
    $out = & git -C $SourceDir @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed: $out" }
    return $out
}

function Copy-Tree([string]$from, [string]$to, [string[]]$excludeDirs = @()) {
    $rcArgs = @($from, $to, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:1', '/W:1')
    if ($excludeDirs) { $rcArgs += '/XD'; $rcArgs += $excludeDirs }
    & robocopy.exe @rcArgs | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy $from -> $to failed with exit code $LASTEXITCODE" }
}

$pom = [xml](Get-Content -LiteralPath (Join-Path $SourceDir 'pom.xml') -Raw)
$version = $pom.project.version
$jre = 'jre' + $pom.project.properties.'project.jre-version'
# Installers from a branch other than main get its name: UMS-Windows-<version>-custom-x86_64.exe
$branch = Invoke-Git rev-parse --abbrev-ref HEAD
$suffix = if ($branch -and $branch -ne 'main' -and $branch -ne 'HEAD') { "-$branch" } else { '' }
Write-Host "UMS $version (branch $branch), target: $Target"

$yarnStateClean = -not (Invoke-Git status --porcelain -- $YarnState)

Use-BuildTools

# --- Maven --------------------------------------------------------------------

$mvnArgs = @('-B', '-P', 'windows', 'package', "-Dmaven.repo.local=$BuildMavenRepoLocal")
# In a git worktree the git-commit-id plugin reads the shared .git and reports the commit of the other worktree
# (main). The web UI caches translations per git.commit.id, so pass this checkout's commit ourselves.
$commitId = Invoke-Git rev-parse HEAD
# Uncommitted changes (e.g. a changed translation) need a new id too, or the browser keeps the old strings
if (Invoke-Git status --porcelain) { $commitId += '-dirty-' + (Get-Date -Format 'yyyyMMddHHmmss') }
$commitTime = Invoke-Git log -1 --format=%cd --date=format:%Y-%m-%d
$mvnArgs += @('-Dmaven.gitcommitid.skip=true', "-Dgit.commit.id=$commitId", "-Dgit.commit.time=$commitTime")
# Local build time for the web UI status bar (project.build.time, custom:common)
$mvnArgs += "-Dbuild.time=$(Get-Date -Format 'yyyy-MM-dd HH:mm')"
# Release number for the About page and the status bar (project.custom.version, custom:release)
if ($CustomVersion) { $mvnArgs += "-Dcustom.version=$CustomVersion" }
if (-not $Tests) { $mvnArgs += '-DskipTests' }
if ($Offline) { $mvnArgs += '-Doffline' }
# UMS.exe (the launcher) is made by the NSIS step too: skip NSIS only once target\UMS.exe exists (not in a fresh clone)
if ($Target -eq 'bin' -and (Test-Path -LiteralPath (Join-Path $TargetDir 'UMS.exe'))) { $mvnArgs += '-Dnsis.disabled=true' }

foreach ($stale in 'UMS-x86_64.exe', 'UMS-x86.exe') {
    Remove-Item -LiteralPath (Join-Path $TargetDir $stale) -ErrorAction SilentlyContinue
}
Push-Location $SourceDir
try {
    & mvn.cmd @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    if ($yarnStateClean -and (Invoke-Git status --porcelain -- $YarnState)) {
        Invoke-Git checkout -- $YarnState | Out-Null
    }
}

# --- bin: same files as the "Program Files" section of setup-x86_64.nsi -----------

if ($Target -in 'all', 'bin') {
    $out = Join-Path $BuildDir 'bin'
    if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Recurse -Force }
    New-Item -ItemType Directory -Force $out | Out-Null

    Copy-Tree (Join-Path $ExtRes 'web') (Join-Path $out 'web')
    Copy-Tree (Join-Path $ExtRes 'renderers') (Join-Path $out 'renderers')
    foreach ($file in @(
        (Join-Path $TargetDir 'UMS.exe'),
        (Join-Path $TargetDir 'ums.jar'),
        (Join-Path $ExtRes 'UMS.bat'),
        (Join-Path $SourceDir 'CHANGELOG.md'),
        (Join-Path $SourceDir 'README.md'),
        (Join-Path $SourceDir 'LICENSE.txt'),
        (Join-Path $ExtRes 'logback.xml'),
        (Join-Path $ExtRes 'logback.headless.xml'),
        (Join-Path $ExtRes 'icon.ico'),
        (Join-Path $ExtRes 'DummyInput.ass'),
        (Join-Path $ExtRes 'DummyInput.jpg')
    )) {
        if (-not (Test-Path -LiteralPath $file)) { throw "Build output missing: $file" }
        Copy-Item -LiteralPath $file -Destination $out
    }

    $winBin = Join-Path $TargetDir 'bin\windows'
    Copy-Tree (Join-Path $winBin "x86_64\$jre") (Join-Path $out $jre)
    Copy-Tree $winBin (Join-Path $out 'bin') @((Join-Path $winBin 'x86'), (Join-Path $winBin 'x86_64'), (Join-Path $winBin 'winxp'))
    Copy-Tree (Join-Path $winBin 'x86_64') (Join-Path $out 'bin') @((Join-Path $winBin "x86_64\$jre"))

    $count = (Get-ChildItem -LiteralPath $out -Recurse -File | Measure-Object).Count
    Write-Host "Program files: $out ($count files)" -ForegroundColor Green
}

# --- setup: the NSIS installers ---------------------------------------------------

if ($Target -in 'all', 'setup') {
    $out = Join-Path $BuildDir 'setup'
    New-Item -ItemType Directory -Force $out | Out-Null
    # Only the 64-bit installer: the 32-bit one is disabled in pom.xml (custom:build-scripts)
    $built = Join-Path $TargetDir 'UMS-x86_64.exe'
    if (-not (Test-Path -LiteralPath $built)) { throw "Installer missing: $built" }
    $name = if ($CustomVersion) { "UMS-Windows-$CustomVersion-x86_64.exe" } else { "UMS-Windows-$version$suffix-x86_64.exe" }
    $dest = Join-Path $out $name
    Copy-Item -LiteralPath $built -Destination $dest -Force
    Write-Host "Installer: $dest" -ForegroundColor Green
}
