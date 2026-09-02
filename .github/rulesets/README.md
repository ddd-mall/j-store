# GitHub Ruleset 模板

本目录保存受保护分支的期望配置，不代表远端已自动启用。仓库管理员必须在工作流落地并产生稳定 check context 后，通过 GitHub Settings 或 Rulesets API 应用模板。

启用顺序：

1. 合并 `branch-policy.yml`、策略脚本和现有门禁的分支触发调整。
2. 从管理员确认的集成基线创建远端 `develop`，禁止从未审查的临时分支直接提升。
3. 分别在 `develop` 和 `master` PR 上确认七个 required check context 均出现。
4. 应用 `develop.json`，再更新 `master.json`；核对无 bypass actor、禁止删除和强推。
5. 使用故意违规的草稿 PR 验证 `feature/* -> master` 会失败，且正常 `feature/* -> develop` 会通过。

当前仓库只有一位明确所有者，模板的审批数量为 0，以避免无法满足的保护死锁；PR、required checks、review thread 解决和人工合并仍不可跳过。增加独立维护者后，应把两个模板的 `required_approving_review_count` 改为 1，并把 `require_last_push_approval` 改为 `true`。
