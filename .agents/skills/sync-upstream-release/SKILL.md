---
name: sync-upstream-release
description: Safely synchronize the latest stable zly2006/zhihu-plus-plus GitHub Release into the jmxhz/zhihu-plus-plus fork, preserve fork-specific behavior, validate, push codex/custom-release, and publish release artifacts. Use in this repository when the user says “同步”, “同步上游”, “同步上游 Release”, “检查上游更新”, or explicitly invokes $sync-upstream-release. This is a manual one-shot workflow, never a polling or scheduled task.
---

# Sync Upstream Release

Run one isolated, reviewable upstream synchronization. Never create a recurring automation.

## Fixed scope

- Upstream: `zly2006/zhihu-plus-plus` and fetch-only remote `https://github.com/zly2006/zhihu-plus-plus.git`.
- Fork: `jmxhz/zhihu-plus-plus`.
- Long-lived fork branch: `codex/custom-release`.
- Merge helper: `scripts/merge-upstream.ps1`.
- Release workflow: `.github/workflows/build.yml`.
- Never push, open a PR, or create a release in upstream.

## Select the mode

- Treat “检查”, “检查上游更新”, or “看看有没有新版” as **Check**. Inspect and report only.
- Treat “同步”, “同步上游”, the skill chip, or an explicit request to update as **Publish**. The user has defined these phrases as authorization to merge, push only the fork branch and tag, and publish the fork Release after validation.
- If the fork tag exists but its Release is missing, use **Resume**. Do not merge or replace the tag again; retry the release workflow only after verifying the tag contains the upstream release commit.

## Prepare an isolated checkout

Do not stash, reset, clean, or otherwise modify the user's active checkout. Create a temporary clone of the fork's `codex/custom-release` branch, even when the active checkout is clean:

1. Verify `gh auth status` and network access.
2. Create a uniquely named directory below the OS temporary directory.
3. Clone with `--no-tags --single-branch --branch codex/custom-release` from the fork URL.
4. Copy the active checkout's `local.properties` into the clone when it exists.
5. Add `upstream` with the fixed fetch URL, then run `git remote set-url --push upstream DISABLED`.
6. Verify both remote URLs before continuing.

Keep the temporary clone until the run succeeds or the failure is fully reported. Delete only the uniquely created temporary directory after verifying its resolved parent is the OS temporary directory; otherwise leave it and report the path.

## Resolve the exact release

1. Read `repos/zly2006/zhihu-plus-plus/releases/latest` with `gh api`.
2. Require a published, non-draft, non-prerelease tag matching `X.Y` or `X.Y.Z`.
3. Resolve the exact upstream tag SHA with `git ls-remote --tags --refs upstream refs/tags/<tag>`.
4. Pass that tag to every merge-helper call with `-Tag <tag>`. Do not substitute upstream `master`, `nightly`, or a higher unpublished tag.
5. Check the fork Release, fork tag, and `origin/codex/custom-release` separately:
   - If the fork Release exists and its tag commit contains the upstream tag SHA, report “already synchronized” and stop.
   - If the fork Release exists but does not contain that SHA, stop without moving the tag.
   - If the fork tag exists without a Release, enter **Resume**.

## Check

Run the helper in the temporary clone:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/merge-upstream.ps1 -Action Check -Tag <tag>
```

Report the release tag/SHA, upstream-only commits, fork-only commits, overlapping paths, existing fork tag/Release state, and whether Publish would require semantic conflict resolution. Do not push or publish.

## Merge and review

For **Publish**, use the required helper phases; do not rebase or replace the long-lived branch history:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/merge-upstream.ps1 -Action Start -Tag <tag>
```

`Start` may exit nonzero when conflicts are present. Continue inspecting the temporary clone; this expected conflict exit is not permission to bypass the helper.

Before resolving anything, read the current functional baseline in `CLAUDE.md`, the fork-only diff, the upstream-only diff, overlapping paths, relevant history, and tests. For every overlap:

- Prefer upstream code only when evidence shows it fully preserves the fork's required behavior and is more complete, correct, or maintainable.
- Otherwise preserve the fork behavior while adapting it to the upstream structure.
- Never resolve a whole file with `ours` or `theirs` without reviewing each changed hunk.
- Add or update regression tests when an existing baseline is not already protected.
- If code and tests cannot establish which behavior is correct, run `Abort`, keep the fork unchanged, and ask the user. Do not publish an uncertain merge.

Stage all reviewed resolutions, then run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/merge-upstream.ps1 -Action Continue -Tag <tag>
```

The helper runs the mandatory JVM tests, Lite debug build, ktlint checks, and cached diff check. Run additional targeted tests required by affected behavior. Review the complete staged diff after validation. If the index changes, run `Continue` again.

Only after the review and checks pass, finalize:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/merge-upstream.ps1 -Action Finalize -Tag <tag> -Approved
```

## Push and publish

1. Fetch `origin/codex/custom-release` again. Push only if the merge commit is a fast-forward from the fetched branch; never force-push.
2. Push the merge commit to `origin/codex/custom-release`.
3. Refuse to move or replace an existing fork tag. If absent, create an annotated `<tag>` at the merge commit and push only that tag.
4. The tag push starts `build.yml`, which builds signed Android artifacts and creates the fork Release. Locate the run by exact merge SHA and workflow, then wait for a terminal result.
5. On success, verify the fork Release exists and includes at least `zhihu++-lite.apk`; set its title to `<tag> 自定义版` without discarding generated notes.
6. On workflow failure, keep history and the tag intact, report the run URL and failing job, and do not claim the Release is complete.

## Resume an incomplete release

When the fork tag exists but the Release does not:

1. Resolve annotated tags to their commit and verify both the upstream tag SHA and `origin/codex/custom-release` are ancestors of the fork tag commit.
2. Inspect the latest `build.yml` run for that exact commit. Rerun a failed/cancelled run when available; otherwise dispatch `build.yml` with `--ref <tag>`.
3. Wait for completion and verify the required Release asset. Never delete/recreate the tag merely to retrigger CI.

## Final report

Report in Chinese:

- upstream tag and SHA;
- whether the result was no-op, checked, merged, resumed, or blocked;
- how overlapping implementations were decided;
- commit, fork tag, Release URL, and artifact names when published;
- tests/builds run and their results;
- conflicts, risks, or incomplete items.
