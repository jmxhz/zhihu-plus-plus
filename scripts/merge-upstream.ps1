param(
    [ValidateSet("Check", "Start", "Continue", "Finalize", "Abort")]
    [string] $Action = "Check",
    [string] $Remote = "upstream",
    [switch] $DryRun,
    [switch] $Approved
)

$ErrorActionPreference = "Stop"
$RequiredBranch = "codex/custom-release"
$ExpectedUpstreamUrl = "https://github.com/zly2006/zhihu-plus-plus.git"

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)
    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-GitOutput {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)
    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    return @($output)
}

function Get-GitScalar {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)
    return [string] (Get-GitOutput @Arguments | Select-Object -First 1)
}

function Test-GitPath {
    param([string] $Name)
    $path = Get-GitScalar rev-parse --git-path $Name
    return Test-Path -LiteralPath $path
}

function Assert-NoGitOperation {
    $operationPaths = @("MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "rebase-merge", "rebase-apply", "BISECT_LOG")
    $active = @($operationPaths | Where-Object { Test-GitPath $_ })
    if ($active.Count -gt 0) {
        throw "An unfinished Git operation exists: $($active -join ', ')"
    }
}

function Assert-UpstreamRemote {
    $remoteUrl = (Get-GitScalar config --get "remote.$Remote.url").Trim()
    if ($Remote -ne "upstream" -or $remoteUrl -ne $ExpectedUpstreamUrl) {
        throw "Only the fetch-only upstream remote is allowed. Actual: $Remote ($remoteUrl)"
    }
    $pushUrl = (& git remote get-url --push $Remote 2>$null)
    if ($LASTEXITCODE -eq 0 -and "$pushUrl" -ne "DISABLED") {
        throw "upstream push URL must be DISABLED. Actual: $pushUrl"
    }
}

function Get-LatestStableRelease {
    $lines = @(Get-GitOutput ls-remote --tags --refs $Remote)
    $releases = foreach ($line in $lines) {
        if ($line -match '^([0-9a-f]{40})\s+refs/tags/(\d+\.\d+(?:\.\d+)?)$') {
            $components = $Matches[2].Split('.')
            $normalized = @($components + @('0', '0', '0'))[0..2] -join '.'
            [PSCustomObject]@{
                Sha = $Matches[1]
                Tag = $Matches[2]
                Version = [version] $normalized
            }
        }
    }
    $release = $releases | Sort-Object Version -Descending | Select-Object -First 1
    if ($null -eq $release) {
        throw "No stable semantic-version tag was found on $Remote."
    }
    return $release
}

function Fetch-Release {
    param($Release)
    Invoke-Git fetch --quiet --no-tags $Remote "refs/tags/$($Release.Tag)"
    $fetchedSha = (Get-GitScalar rev-parse FETCH_HEAD).Trim()
    if ($fetchedSha -ne $Release.Sha) {
        throw "Fetched SHA $fetchedSha does not match remote SHA $($Release.Sha)."
    }
}

function Get-StatePath {
    return (Get-GitScalar rev-parse --git-path custom-upstream-merge.json).Trim()
}

function Read-State {
    $path = Get-StatePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "No upstream merge state exists. Run Start first."
    }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json
}

function Write-State {
    param($State)
    $State | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Get-StatePath)
}

function Show-Check {
    param($Release)
    Fetch-Release $Release
    $targetSha = $Release.Sha
    $mergeBase = (Get-GitScalar merge-base HEAD $targetSha).Trim()
    $localFiles = @(Get-GitOutput diff --name-only "$mergeBase..HEAD")
    $upstreamFiles = @(Get-GitOutput diff --name-only "$mergeBase..$targetSha")
    $overlap = @($localFiles | Where-Object { $_ -in $upstreamFiles } | Sort-Object -Unique)

    Write-Host "Current:  $(Get-GitScalar log -1 --format='%h %s' HEAD)"
    Write-Host "Upstream: $($Release.Tag) / $targetSha"
    Write-Host "Commits only in current branch: $(Get-GitScalar rev-list --count "$targetSha..HEAD")"
    Write-Host "Commits only in upstream release: $(Get-GitScalar rev-list --count "HEAD..$targetSha")"
    Write-Host "Current-side changed files: $($localFiles.Count)"
    $localFiles | ForEach-Object { Write-Host "  $_" }
    Write-Host "Upstream-side changed files: $($upstreamFiles.Count)"
    $upstreamFiles | ForEach-Object { Write-Host "  $_" }
    Write-Host "Overlapping paths: $($overlap.Count)"
    $overlap | ForEach-Object { Write-Host "  $_" }
}

Invoke-Git rev-parse --is-inside-work-tree | Out-Null
Assert-UpstreamRemote
Invoke-Git config --local rerere.enabled true
Invoke-Git config --local rerere.autoupdate false

if ($DryRun) {
    $Action = "Check"
}

switch ($Action) {
    "Check" {
        Show-Check (Get-LatestStableRelease)
    }
    "Start" {
        Assert-NoGitOperation
        $branch = (Get-GitScalar branch --show-current).Trim()
        if ($branch -ne $RequiredBranch) {
            throw "Start is only allowed on $RequiredBranch. Actual: $branch"
        }
        $status = @(Get-GitOutput status --porcelain=v1 --untracked-files=all)
        if ($status.Count -gt 0) {
            throw "Start requires a clean working tree."
        }

        $release = Get-LatestStableRelease
        Fetch-Release $release
        $stamp = Get-Date -Format "yyyyMMdd-HHmmssfff"
        $backupBranch = "backup/custom-release-before-$($release.Tag)-$stamp"
        Invoke-Git branch $backupBranch HEAD
        Write-State ([PSCustomObject]@{
            targetTag = $release.Tag
            targetSha = $release.Sha
            backupBranch = $backupBranch
            startedFrom = (Get-GitScalar rev-parse HEAD).Trim()
            validatedTargetSha = $null
            validatedIndexTree = $null
        })

        & git merge --no-ff --no-commit $release.Sha
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Merge stopped for conflict resolution. Backup: $backupBranch"
            Invoke-Git diff --name-only --diff-filter=U
            exit 1
        }
        if (-not (Test-GitPath "MERGE_HEAD")) {
            Remove-Item -LiteralPath (Get-StatePath)
            throw "Git did not start a merge. The target may already be integrated. Backup preserved: $backupBranch"
        }
        Write-Host "Merge prepared without committing. Run Continue after reviewing and staging all resolutions."
        Write-Host "Backup: $backupBranch"
    }
    "Continue" {
        if (-not (Test-GitPath "MERGE_HEAD")) {
            throw "No merge is in progress."
        }
        $state = Read-State
        $mergeHead = (Get-GitScalar rev-parse MERGE_HEAD).Trim()
        if ($mergeHead -ne $state.targetSha) {
            throw "MERGE_HEAD $mergeHead does not match recorded target $($state.targetSha)."
        }
        $unmerged = @(Get-GitOutput diff --name-only --diff-filter=U)
        if ($unmerged.Count -gt 0) {
            throw "Unresolved conflicts remain: $($unmerged -join ', ')"
        }
        & git diff --quiet
        if ($LASTEXITCODE -ne 0) {
            throw "Unstaged changes remain. Stage every reviewed conflict resolution first."
        }
        $untracked = @(Get-GitOutput ls-files --others --exclude-standard)
        if ($untracked.Count -gt 0) {
            throw "Untracked files remain: $($untracked -join ', ')"
        }

        & .\gradlew.bat :shared:jvmTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw ":shared:jvmTest failed." }
        & .\gradlew.bat assembleLiteDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Lite build failed." }
        & .\gradlew.bat :shared:ktlintCheck :shared-local-db:ktlintCommonMainSourceSetCheck :shared-local-db:ktlintNativeMainSourceSetCheck --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "ktlintCheck failed." }
        Invoke-Git diff --check --cached

        $state.validatedTargetSha = $mergeHead
        $state.validatedIndexTree = (Get-GitScalar write-tree).Trim()
        Write-State $state
        Write-Host "Validation recorded for $($state.targetTag) / $mergeHead. Review the staged merge, then run Finalize -Approved."
    }
    "Finalize" {
        if (-not $Approved) {
            throw "Finalize requires -Approved after code review approval."
        }
        if (-not (Test-GitPath "MERGE_HEAD")) {
            throw "No merge is in progress."
        }
        $state = Read-State
        $mergeHead = (Get-GitScalar rev-parse MERGE_HEAD).Trim()
        $indexTree = (Get-GitScalar write-tree).Trim()
        if ($state.validatedTargetSha -ne $mergeHead -or $state.validatedIndexTree -ne $indexTree) {
            throw "The target SHA or index changed after validation. Run Continue again."
        }
        & git diff --quiet
        if ($LASTEXITCODE -ne 0) {
            throw "Unstaged changes exist after validation."
        }
        $untracked = @(Get-GitOutput ls-files --others --exclude-standard)
        if ($untracked.Count -gt 0) {
            throw "Untracked files exist after validation: $($untracked -join ', ')"
        }
        Invoke-Git commit -m "Merge upstream $($state.targetTag) ($($mergeHead.Substring(0, 8))) into $RequiredBranch"
        Remove-Item -LiteralPath (Get-StatePath)
        Write-Host "Merge finalized. Push only origin/$RequiredBranch after final review."
    }
    "Abort" {
        if (-not (Test-GitPath "MERGE_HEAD")) {
            throw "No merge is in progress."
        }
        $statePath = Get-StatePath
        $backup = if (Test-Path -LiteralPath $statePath) { (Read-State).backupBranch } else { "unknown" }
        Invoke-Git merge --abort
        if (Test-Path -LiteralPath $statePath) {
            Remove-Item -LiteralPath $statePath
        }
        Write-Host "Merge aborted. Backup branch preserved: $backup"
    }
}
