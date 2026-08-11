# 分支管理治理设计

## 分支拓扑

`master` 表示可发布历史，`develop` 表示下一版本集成状态。`feature/*`、`fix/*`、`refactor/*`、`perf/*`、`docs/*`、`test/*`、`build/*`、`ci/*`、`chore/*` 和 `codex/*` 只进入 `develop`。`release/v<semver>` 与 `hotfix/v<semver>` 只进入 `master`。

仓库不启用 Dependabot 定时升级。依赖版本可以由自动化报告，但升级候选必须由人工明确发起，使用 `chore/dependency-<slug>`，每个 PR 只覆盖一个依赖或不可拆分 BOM，并记录兼容矩阵、迁移风险和回滚方式。

发布和热修复合并后，必须创建 `master -> develop` PR，使版本元数据和修复回到下一版本。短分支合并后删除；不保留环境分支，也不允许用分支代替部署环境状态。

## 可执行策略

`.github/workflows/branch-policy.yml` 在目标为 `master` 或 `develop` 的 PR 上执行 `scripts/check-branch-policy.py`。检查器验证目标分支、来源分支和 PR 标题。工作流优先从 PR 基准提交提取检查器，避免候选分支通过修改检查器绕过策略；仅在首次引入、基准分支尚无检查器时使用候选中的脚本，并只允许固定的 `codex/branch-management-bootstrap -> master` bootstrap PR。检查器一旦存在于基准分支，bootstrap 参数不会再传入，该例外自动失效。Ruleset 必须在首次落地后才启用该 required check。

`quality.yml`、`security.yml` 与 Qodana 的 push 触发分支统一为 `master` 和 `develop`。Ruleset 模板要求分支策略、质量和安全检查成功，禁止删除和非快进推送，并要求通过 PR 和解决 review thread。

## 审查与合并

当前只有一个明确仓库所有者，因此 ruleset 的审批数量保持为 0，以免形成无法解除的治理死锁；这不表示允许自动合并，所有合并仍由人工决定。增加独立维护者后，应把 `master` 和 `develop` 的 `required_approving_review_count` 提升为 1，并启用最后推送者之外的批准。

日常短分支使用 squash merge 保持集成历史紧凑。`release/*`、`hotfix/*` 和 `master -> develop` 使用 merge commit 保留版本与回灌边界。禁止 rebase merge，开发者可在个人短分支上自行 rebase。

## 发布与恢复

发布候选从 `develop` 创建 `release/vX.Y.Z`，完成版本元数据和验证后进入 `master`。合并后在该 `master` 提交上人工创建签名 annotated tag `vX.Y.Z`，由现有 release-evidence 工作流生成候选证据。

线上紧急修复从 `master` 创建 `hotfix/vX.Y.Z`，通过同一质量、安全和发布审批后进入 `master`，再回灌 `develop`。错误合并使用新的 `revert/*` 或 `hotfix/*` 候选修复，不重写受保护分支历史。

## 启用顺序

先合并检查器、工作流和文档；再从管理员确认的集成基线创建远端 `develop`；观察 required check 名称稳定；最后通过 GitHub Settings 或 Rulesets API 应用 `develop.json` 和更新后的 `master.json`。现有临时长期分支在未合并工作迁移后删除。
