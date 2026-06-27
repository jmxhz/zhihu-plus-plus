param(
    [string] $Remote = "upstream",
    [string] $Branch = "master",
    [switch] $DryRun,
    [switch] $AutoStash
)

$ErrorActionPreference = "Stop"

function Run-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Args)
    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-GitOutput {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Args)
    $output = & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
    return $output
}

Run-Git rev-parse --is-inside-work-tree | Out-Null

$currentBranch = (Get-GitOutput branch --show-current).Trim()
if (-not $currentBranch) {
    throw "Detached HEAD is not supported by this helper."
}

$remoteUrl = (Get-GitOutput remote get-url $Remote).Trim()
if ($Remote -eq "upstream" -and $remoteUrl -ne "https://github.com/zly2006/zhihu-plus-plus.git") {
    throw "Unexpected upstream URL: $remoteUrl"
}

$status = @(Get-GitOutput status --short --untracked-files=all)
$remoteRef = "refs/remotes/$Remote/$Branch"
$hasRemoteRef = $true
& git rev-parse --verify --quiet $remoteRef | Out-Null
if ($LASTEXITCODE -ne 0) {
    $hasRemoteRef = $false
}

Write-Host "Current branch: $currentBranch"
Write-Host "Remote: $Remote ($remoteUrl)"
Write-Host "Branch: $Branch"
if ($status.Count -gt 0) {
    Write-Host "Working tree has local changes:"
    $status | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "Working tree is clean."
}

Write-Host "Fetch preview:"
& git fetch --dry-run $Remote $Branch
if ($LASTEXITCODE -ne 0) {
    throw "git fetch --dry-run $Remote $Branch failed with exit code $LASTEXITCODE"
}

if ($hasRemoteRef) {
    $ahead = @(Get-GitOutput log --oneline "HEAD..$Remote/$Branch")
    Write-Host "Upstream commits not in HEAD: $($ahead.Count)"
    $ahead | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
}

if ($DryRun) {
    Write-Host "Dry run complete. No refs or files were changed."
    exit 0
}

if ($status.Count -gt 0 -and -not $AutoStash) {
    throw "Working tree is not clean. Re-run with -AutoStash or stash/commit local changes first."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupBranch = "backup/$($currentBranch -replace '[^A-Za-z0-9._-]', '-')-before-$Remote-$Branch-$stamp"
Run-Git branch $backupBranch
Write-Host "Created backup branch: $backupBranch"

$stashCreated = $false
if ($status.Count -gt 0 -and $AutoStash) {
    Run-Git stash push --include-untracked -m "pre-$Remote-$Branch-merge-$stamp"
    $stashCreated = $true
}

Run-Git fetch $Remote $Branch

& git merge "$Remote/$Branch"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Merge stopped with conflicts."
    Write-Host "Conflicted files:"
    & git diff --name-only --diff-filter=U
    if ($stashCreated) {
        Write-Host "Local changes were stashed before merge. Re-apply them after resolving the merge with: git stash pop"
    }
    Write-Host "Backup branch: $backupBranch"
    exit $LASTEXITCODE
}

if ($stashCreated) {
    & git stash pop
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Stash pop stopped with conflicts. Resolve them manually."
        Write-Host "Backup branch: $backupBranch"
        exit $LASTEXITCODE
    }
}

Write-Host "Merge complete."
Write-Host "Recommended checks:"
Write-Host "  ./gradlew assembleLiteDebug"
Write-Host "  ./gradlew testLiteDebugUnitTest"
Write-Host "  git diff -- app/src/main/java/com/github/zly2006/zhihu/viewmodel/filter shared/src/commonMain/kotlin/com/github/zly2006/zhihu/viewmodel/filter"
