<#
.SYNOPSIS
    Validates and lists the custom-patch tags in source-custom (branch "custom").

.DESCRIPTION
    Tag format (Java/JS/TS comment "//", properties/conf comment "#", JSX "{/* */}", XML "<!-- -->"):
      // <custom:name> Description          our code (added or replacing base code)
      // </custom:name>
      // <custom-remove:name>                 base code we disabled, kept as comments
      // </custom-remove:name>

    Checks every tracked text file:
      - each opening tag has a matching closing tag with the same kind and name, in order (no nesting)
      - every line inside a custom-remove block is a comment (or blank)
      - no leftover merge conflict markers
    Prints a summary per patch name: files and block counts.
    Exit code 1 if anything is wrong.

.PARAMETER Path
    Repository root to check. Default: the repository of this script (build\..).
#>
[CmdletBinding()]
param(
    [string]$Path = (Join-Path $PSScriptRoot '..')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $Path).Path
# "//" Java/TS, "#" properties/conf, "{/*" JSX children, "<!--" XML (pom.xml)
$tagRegex = '^\s*(//|#|\{/\*|<!--)\s*<(/?)(custom|custom-remove):([A-Za-z0-9_.-]+)>(.*)$'
$textExt = '\.(java|js|jsx|ts|tsx|properties|conf|xml|txt|md|css|html)$'

$files = & git -C $root ls-files | Where-Object { $_ -match $textExt }
$files += & git -C $root ls-files --others --exclude-standard | Where-Object { $_ -match $textExt }
$errors = New-Object System.Collections.Generic.List[string]
$summary = @{}

foreach ($rel in ($files | Sort-Object -Unique)) {
    $full = Join-Path $root $rel
    if (-not (Test-Path -LiteralPath $full)) { continue }
    $lines = [IO.File]::ReadAllLines($full)
    if (-not ($lines -match '<(/?)(custom|custom-remove):|^(<{7}|={7}|>{7})( |$)')) { continue }
    $open = $null
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $n = $i + 1
        if ($line -match '^(<{7}|>{7})( |$)' -or $line -eq '=======') {
            $errors.Add("${rel}:${n}: merge conflict marker")
            continue
        }
        if ($line -match $tagRegex) {
            $closing = $Matches[2] -eq '/'
            $kind = $Matches[3]
            $name = $Matches[4]
            if (-not $closing) {
                if ($open) {
                    $errors.Add("${rel}:${n}: <${kind}:${name}> opened inside <$($open.Kind):$($open.Name)> from line $($open.Line)")
                }
                $open = @{ Kind = $kind; Name = $name; Line = $n }
                if (-not $summary.ContainsKey($name)) { $summary[$name] = @{ Files = @{}; Custom = 0; Remove = 0 } }
                $summary[$name].Files[$rel] = $true
                if ($kind -eq 'custom') { $summary[$name].Custom++ } else { $summary[$name].Remove++ }
            } else {
                if (-not $open) {
                    $errors.Add("${rel}:${n}: </${kind}:${name}> without an opening tag")
                } elseif ($open.Kind -ne $kind -or $open.Name -ne $name) {
                    $errors.Add("${rel}:${n}: </${kind}:${name}> closes <$($open.Kind):$($open.Name)> from line $($open.Line)")
                }
                $open = $null
            }
            continue
        }
        if ($open -and $open.Kind -eq 'custom-remove' -and $line.Trim() -ne '' -and $line -notmatch '^\s*(//|#|\*|/\*|\{/\*|<!--)') {
            $errors.Add("${rel}:${n}: code line inside <custom-remove:$($open.Name)> is not commented out")
        }
    }
    if ($open) {
        $errors.Add("${rel}:$($open.Line): <$($open.Kind):$($open.Name)> is never closed")
    }
}

Write-Host "Custom patches in ${root}:"
foreach ($name in ($summary.Keys | Sort-Object)) {
    $s = $summary[$name]
    Write-Host ("  {0,-20} {1,2} custom, {2,2} custom-remove blocks in {3} files" -f $name, $s.Custom, $s.Remove, $s.Files.Count)
    $s.Files.Keys | Sort-Object | ForEach-Object { Write-Host "      $_" }
}
if ($errors.Count) {
    Write-Host ''
    Write-Host "$($errors.Count) problem(s):" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Host 'Tags OK' -ForegroundColor Green
