<#
.SYNOPSIS
    Builds and publishes a Windows x64 release of Universal Media Server - Custom edition on the fork (GitHub).

.DESCRIPTION
    Version: the upstream version from pom.xml (15.9.0-SNAPSHOT -> 15.9.0) plus the next custom number from the
    fork's tags: v15.9.0-custom.1, v15.9.0-custom.2, ... (a new upstream version starts again at .1).

    Steps:
      1. Checks: branch custom, no local changes, pushed (= origin/custom), custom tags valid, gh logged in.
      2. build.ps1 -Target all -CustomVersion <version>: program files and the installer, the version shown in the
         web UI is the release number.
      3. Assets in build\release\<version>\: the installer UMS-Windows-<version>-x86_64.exe and the portable
         UMS-Windows-<version>-x86_64-portable.zip (program files + an empty portable\ folder: settings stay
         next to the program).
      4. Release notes: what the edition is (README-Custom.md) and the commits since the previous release.
      5. Asks (unless -Yes), then creates the annotated tag, pushes it to origin and runs gh release create.

.PARAMETER Yes
    Publish without asking.

.PARAMETER Offline
    Passed to build.ps1 (reuse the downloaded Windows binaries).

.PARAMETER DryRun
    Build and prepare the assets and notes, but do not tag or publish.
#>
[CmdletBinding()]
param(
    [switch]$Yes,
    [switch]$Offline,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$BuildDir  = $PSScriptRoot
$SourceDir = [IO.Path]::GetFullPath((Join-Path $BuildDir '..'))

function Invoke-Git {
    $ErrorActionPreference = 'Continue'
    $out = & git -C $SourceDir @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed: $out" }
    return $out
}

# --- 1. Checks ------------------------------------------------------------------------

if ((Invoke-Git rev-parse --abbrev-ref HEAD) -ne 'custom') { throw 'Not on the custom branch' }
if (Invoke-Git status --porcelain) { throw 'Local changes: commit them first' }
Invoke-Git fetch --quiet --tags origin | Out-Null
$head = Invoke-Git rev-parse HEAD
if ($head -ne (Invoke-Git rev-parse origin/custom)) { throw 'custom is not pushed (HEAD differs from origin/custom)' }
$tagCheck = Join-Path $BuildDir 'check-custom-tags.ps1'
if (Test-Path -LiteralPath $tagCheck) {
    & pwsh -NoProfile -File $tagCheck | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Custom tag check failed' }
}
$repo = (Invoke-Git remote get-url origin) -replace '^https://github.com/', '' -replace '\.git$', ''
& gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'gh is not logged in (gh auth login)' }

$pom = [xml](Get-Content -LiteralPath (Join-Path $SourceDir 'pom.xml') -Raw)
$base = $pom.project.version -replace '-SNAPSHOT$', ''
$numbers = @(Invoke-Git tag --list "v$base-custom.*" | ForEach-Object {
    if ($_ -match "^v$([regex]::Escape($base))-custom\.(\d+)$") { [int]$Matches[1] }
})
$next = if ($numbers.Count) { ($numbers | Measure-Object -Maximum).Maximum + 1 } else { 1 }
$version = "$base-custom.$next"
$tag = "v$version"
$previous = Invoke-Git tag --list 'v*-custom.*' --sort=-creatordate | Select-Object -First 1
Write-Host "Release $version (tag $tag) of $repo, commit $($head.Substring(0, 9))" -ForegroundColor Cyan

# --- 2. Build -------------------------------------------------------------------------

$buildArgs = @{ Target = 'all'; CustomVersion = $version }
if ($Offline) { $buildArgs.Offline = $true }
& (Join-Path $BuildDir 'build.ps1') @buildArgs

# --- 3. Assets ------------------------------------------------------------------------

$out = Join-Path $BuildDir "release\$version"
if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null
$installer = Join-Path $out "UMS-Windows-$version-x86_64.exe"
Copy-Item -LiteralPath (Join-Path $BuildDir "setup\UMS-Windows-$version-x86_64.exe") -Destination $installer

$stage = Join-Path $out "UMS-$version"
Copy-Item -LiteralPath (Join-Path $BuildDir 'bin') -Destination $stage -Recurse
New-Item -ItemType Directory -Force (Join-Path $stage 'portable') | Out-Null
Set-Content -LiteralPath (Join-Path $stage 'portable\README.txt') -Encoding ascii -Value @(
    'Portable mode: UMS keeps its settings, database and logs in this folder.',
    'Start UMS.exe from the folder above (the working folder must be that folder).'
)
$zip = Join-Path $out "UMS-Windows-$version-x86_64-portable.zip"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Optimal, $true)
Remove-Item -LiteralPath $stage -Recurse -Force

# --- 4. Notes -------------------------------------------------------------------------

$range = if ($previous) { "$previous..HEAD" } else { 'upstream/main..HEAD' }
if (-not $previous -and -not (Invoke-Git rev-parse --verify --quiet upstream/main)) { $range = 'origin/main..HEAD' }
$changes = Invoke-Git log --reverse --format=%s $range | ForEach-Object { '- ' + ($_ -replace '^Custom fork: ', '') }
$notes = @(
    "**Universal Media Server - Custom edition $version**, based on Universal Media Server $base.",
    '',
    'The TV shows the folders exactly as they are on the server''s disks, every title says whether it was already',
    'watched or where it was stopped, and the folders are managed from the browser. What the edition changes, for',
    "users: [README-Custom.md](https://github.com/$repo/blob/$tag/README-Custom.md).",
    '',
    '### Downloads (Windows x64)',
    '',
    "- ``UMS-Windows-$version-x86_64.exe`` - installer.",
    "- ``UMS-Windows-$version-x86_64-portable.zip`` - portable: unpack anywhere and start ``UMS.exe``; settings stay in its ``portable`` folder.",
    '',
    $(if ($previous) { "### Changes since $previous" } else { '### Changes compared to upstream' }),
    ''
) + $changes
$notesFile = Join-Path $out 'release-notes.md'
Set-Content -LiteralPath $notesFile -Encoding utf8NoBOM -Value $notes

Get-ChildItem -LiteralPath $out | ForEach-Object { '{0,-60} {1,8:N1} MB' -f $_.Name, ($_.Length / 1MB) }
if ($DryRun) {
    Write-Host "Dry run: nothing tagged or published. Assets and notes: $out" -ForegroundColor Yellow
    return
}

# --- 5. Tag and publish -------------------------------------------------------------------

if (-not $Yes) {
    $answer = Read-Host "Publish $tag on github.com/$repo? (y/n)"
    if ($answer -ne 'y') { Write-Host 'Not published.'; return }
}
Invoke-Git tag -a $tag -m "Universal Media Server - Custom edition $version" | Out-Null
Invoke-Git push --quiet origin $tag | Out-Null
& gh release create $tag $installer $zip --repo $repo --title "Universal Media Server - Custom edition $version" --notes-file $notesFile --verify-tag
if ($LASTEXITCODE -ne 0) { throw "gh release create failed; the tag $tag is pushed, rerun: gh release create $tag ..." }
Write-Host "Published: https://github.com/$repo/releases/tag/$tag" -ForegroundColor Green
