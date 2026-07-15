# Fork 长期定制与发布规则

本仓库是 Zhihu++ 的长期定制 fork。任何上游同步、版本升级和发布都必须同时遵守以下规则。

## 上游同步

1. 长期定制分支为 `codex/custom-release`，保留已有 merge 历史，不得 rebase 或重写历史。
2. 只同步上游最新稳定语义化版本标签，不跟踪 `nightly` 或预发布标签。
3. 必须使用 `scripts/merge-upstream.ps1` 的 `Check / Start / Continue / Finalize / Abort` 流程。
4. `upstream` 只允许 fetch。不得向上游 push、提交 PR 或修改上游仓库；发布只允许推送 fork 的 `origin/codex/custom-release`。
5. 合并上游修改时必须保留下述长期定制功能。上游发生结构变化时，应迁移和更新实现及测试，不得以解决冲突为由删除或弱化功能。

## 必须长期保留的首页已读去重功能

- 首页过滤必须合并本机 `content_open_events`、本地历史导航记录和知乎账号云端阅读历史缓存。
- 内容必须按 canonical `type:id` 精确去重；回答严格按 `answer:id` 过滤，同一问题下其他未读回答必须保留。
- 强已读信号优先于已关注作者豁免；仅曝光内容继续使用既有曝光阈值和关注作者规则。
- Android/JVM 登录环境必须后台同步云历史：缓存立即生效，完整同步最多每小时一次，失败只记录日志并继续使用缓存；访客继续使用本机记录。
- Android、Web、Mixed、Local 首页来源必须使用统一 canonical content key；非首页列表继续使用原有 `stableKey`。
- Local 推荐必须经过相同过滤流水线，每批 20 条、最多四批；不得把未展示候选误记为曝光。
- Mixed 刷新必须重置两个子来源分页，结束状态取两个来源的组合结果。
- 一页全部被过滤时最多续取三页，并在出现可展示内容、来源结束或请求失败时停止。
- 打开回答返回首页时当前卡片必须保留以维持滚动位置；后续加载、刷新和重启不得再次加入该回答。
- “清除全部历史”不得清空或重置强已读去重数据库；不得升级现有 v7 schema 或使用 destructive migration 实现该功能。

## 每次发布要求

1. 每次同步上游正式版本后，都必须检查并更新上述定制功能，使其适配该版本的新首页、导航、分页和数据库代码。
2. 每次 fork 发布都必须包含上述定制功能及对应回归测试；不得仅发布未适配的上游代码。
3. 发布前至少运行：
   - `:shared:jvmTest`
   - `assembleLiteDebug`
   - `:shared:ktlintCheck`
   - `:shared-local-db:ktlintCommonMainSourceSetCheck`
   - `:shared-local-db:ktlintNativeMainSourceSetCheck`
   - `git diff --check`
4. 有可用 Android 设备时，按 `.agents/skills/ui-test/SKILL.md` 使用 testTag 验证打开回答、返回、刷新和继续滚动流程，不得使用硬编码点击坐标。
5. Release 必须发布到 fork，并附带当次构建的 Lite APK。Release 说明应写明上游基线、保留的定制功能、验证结果和未验证平台。

