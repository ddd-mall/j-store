# Agent Automation Runbook

本文件定义外部 Codex Automations、Jules Scheduled Tasks 或同类调度器接入本仓库时应创建的任务。仓库本身已提供 CI 和角色契约，但云端调度需要仓库管理员在所选平台授权后启用。

## 周期任务

| 周期 | 角色 | 任务 | 允许输出 |
|---|---|---|---|
| 每个工作日 | Maintenance Orchestrator | 分诊新 issue、失败 CI 和人工提出的依赖升级请求，去重并路由 | issue 评论、任务状态，不改业务代码或自动创建升级 PR |
| 每周 | Product Steward | 抽查活跃规格与最近合并变更的验收覆盖和术语漂移 | drift report 或 issue |
| 每周 | Security & Supply-chain | 汇总 Semgrep、OSV Scanner、Gitleaks 和预发布依赖 | 风险报告、独立修复 PR |
| 每周 | Quality Gate | 分析定时全量测试失败，归类环境/实现/规格问题 | 失败报告，不修改候选 |
| 每次发布候选 | Release & Migration | 核对版本、迁移、兼容性、回滚和观察窗口 | 发布检查单，不执行发布 |
| 告警触发 | SRE / Incident | 对脱敏运行信号形成时间线、影响和处置建议 | incident issue，不写生产 |

## 调度提示词共同前缀

```text
Read AGENTS.md and docs/steering/agent-governance.md first. Work only in an
isolated branch/worktree. Never merge, deploy, mutate production, change
credentials, or accept product intent changes. Produce reproducible evidence,
deduplicate existing issues/PRs, and stop when human approval is required.
```

随后指定 `.codex/agents/` 中对应角色和单一、有界的任务。不要使用“修复所有问题”“持续优化”等无界提示。

## 接管与熔断

- 同一根因最多两次有实质差异的自动修复尝试。
- 发生权限/密钥、生产数据、公共兼容性、金额、库存、订单状态或隐私变化时立即转人工。
- 调度器只能创建 issue、分支或 PR；主分支合并和部署始终依赖 GitHub 保护规则及人工批准。
- 连续三次周期运行没有新发现时正常结束，不生成空 PR 或重复 issue。

## 外部启用检查单

1. 按 [branch-management.md](branch-management.md) 的启用顺序保护 `master` 和 `develop`，并把 `Branch Policy / branch-policy`、`Quality Gate / quality`、`Security Gate / static-analysis`、`Security Gate / dependency-vulnerability-scan`、`Security Gate / dependency-license-audit`、`Security Gate / secret-scan` 设为 required checks。
2. 禁止 force push、删除和直接提交两个长期分支。仓库存在独立审查者时再要求至少一人审批；只有一位所有者时仍必须通过 PR 和 required checks，由所有者人工决定是否合并。
3. 为调度器使用最小权限 GitHub App；默认只授予 contents read、issues write、pull requests write。
4. 不向 agent 提供生产密钥；运行信号必须先脱敏。
5. 先观察两周只读报告，再逐步允许创建修复 PR。
