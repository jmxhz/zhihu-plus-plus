---
name: sync-upstream-release
description: Safely synchronize the latest stable zly2006/zhihu-plus-plus GitHub Release into the jmxhz/zhihu-plus-plus fork, preserve fork-specific behavior, validate, push codex/custom-release, and publish only a versioned Android Lite ARM64-v8a APK with a detailed release write-up. Use in this repository when the user says “同步”, “同步上游”, “同步上游 Release”, “检查上游更新”, or explicitly invokes $sync-upstream-release. This is a manual one-shot workflow, never a polling or scheduled task.
---

# Sync Upstream Release

Run one isolated, reviewable upstream synchronization. Never create a recurring automation.

## Fixed scope

- Upstream: `zly2006/zhihu-plus-plus` and fetch-only remote `https://github.com/zly2006/zhihu-plus-plus.git`.
- Fork: `jmxhz/zhihu-plus-plus`.
- Long-lived fork branch: `codex/custom-release`.
- Merge helper: `scripts/merge-upstream.ps1`.
- Release workflow: `.github/workflows/build.yml`.
- Sole fork Release asset: `zhihu++-lite-arm64-v8a-<tag>.apk`; the filename must include the release version (e.g. `zhihu++-lite-arm64-v8a-0.27.1.apk`).
- Every fork Release body must be a detailed Chinese write-up (see “Release 说明要求”); never leave auto-generated notes as the only content.
- Never build or publish Full APKs, universal or non-ARM64 APKs, mapping archives, or desktop artifacts.
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
- Preserve the fork release boundary even when upstream changes CI: `assembleLiteRelease` only, Gradle ABI split restricted to `arm64-v8a`, and exactly one versioned Release asset named `zhihu++-lite-arm64-v8a-<tag>.apk`.

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
4. Before triggering CI, verify `build.yml` and its reusable workflow build only `assembleLiteRelease`, do not contain desktop or Full build jobs, and publish only `zhihu++-lite-arm64-v8a-<tag>.apk`.
5. The tag push starts `build.yml`, which builds the Android Lite ARM64-v8a APK and creates the fork Release. Locate the run by exact merge SHA and workflow, then wait for a terminal result.
6. On success, require the fork Release asset set to equal exactly `zhihu++-lite-arm64-v8a-<tag>.apk`; set its title to `<tag> 自定义版`; then write the detailed Release 说明 below (PATCH the body via `gh api`, keeping the auto-generated changelog).

7. On workflow failure, keep history and the tag intact, report the run URL and failing job, and do not claim the Release is complete.

### Release 说明要求

每次 fork Release 正文必须用中文写详细说明，至少包含：

- 上游基线：上游 tag、上游 tag SHA、fork 合并提交 SHA。
- 本次同步内容：上游提交摘要列表，以及 fork 定制功能的保留/适配结论。
- 保留的定制功能：对照 `CLAUDE.md` 功能基线逐条列明并确认未回退。
- 验证结果：本地 `:shared:jvmTest`、`assembleLiteDebug`、ktlint、`git diff --check`，以及 CI release run 的 URL 与结论。
- 产物信息：文件名（带版本号）、大小、SHA-256。
- 已知风险与未验证平台：如未安装真机验证、已知独立检查失败等。
- 保留 workflow 自动生成的 changelog，追加在说明之后，不覆盖。

## Resume an incomplete release

When the fork tag exists but the Release does not:

1. Resolve annotated tags to their commit and verify both the upstream tag SHA and `origin/codex/custom-release` are ancestors of the fork tag commit.
2. Inspect the latest `build.yml` run for that exact commit. Rerun it only when its workflow already satisfies the sole-asset contract. If the immutable tag contains an older workflow, first commit the corrected workflow to `codex/custom-release`, then dispatch it from that branch with `--ref codex/custom-release -f release_tag=<tag>`; the workflow must checkout the tag before building.
3. Wait for completion and verify the Release contains exactly `zhihu++-lite-arm64-v8a-<tag>.apk`; if the immutable tag predates the versioned-asset workflow, dispatch the corrected workflow per step 2 and confirm the versioned asset before writing the detailed Release 说明. Never delete/recreate the tag merely to retrigger CI.

## Final report

Report in Chinese:

- upstream tag and SHA;
- whether the result was no-op, checked, merged, resumed, or blocked;
- how overlapping implementations were decided;
- commit, fork tag, Release URL, versioned artifact name and SHA-256 when published;
- tests/builds run and their results;
- conflicts, risks, or incomplete items.
