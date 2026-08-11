# 分支管理治理交付摘要

## 完成结果

- 建立 `master` 可发布线、`develop` 集成线和短期 feature/release/hotfix 分支拓扑。
- 新增可信基准版本的分支策略检查器，执行来源/目标方向、小写命名、严格 SemVer 和 PR 标题约束。
- 新增 `develop` ruleset，并使两个长期分支要求相同的分支、质量和安全 check context。
- 质量、安全和 Qodana 工作流覆盖 `develop`/`master`；定时依赖升级 PR 被禁用，依赖升级改为人工发起和逐项兼容性评估。
- 发布证据工作流拒绝不属于远端 `master` 历史的标签。
- 补齐日常开发、发布、热修复、回灌、revert、清理、agent 权限和首次启用操作手册。
- 首次 bootstrap 仅允许固定治理分支进入尚无策略的 `master`，基准分支出现检查器后例外自动关闭。
- Qodana 对齐 JDK 25，PR 只分析改动文件并以零新增问题作为失败阈值；启用门禁前的 8 个既有告警已清理或局部说明抑制。

## 验收映射

| 需求 | 主要证据 |
|---|---|
| BMG-R1、BMG-R5 | `master.json`、`develop.json`、治理契约测试 |
| BMG-R2、BMG-R3、BMG-R4 | `check-branch-policy.py`、Branch Policy workflow、13 个策略测试 |
| BMG-R6 | Dependabot 配置缺失契约、agent 治理与人工依赖升级流程 |
| BMG-R7 | Release Evidence 的 master ancestry 校验与发布手册 |
| BMG-R8 | `docs/operations/branch-management.md` |
| BMG-R9 | 两份 ruleset、ruleset README 和审查升级说明 |
| BMG-R10 | Branch Policy bootstrap 输出和策略测试 |
| BMG-R11 | Qodana 配置契约、JDK 25、PR 增量模式、零容忍阈值和既有告警清理 |

## 远端启用边界

仓库内文件只描述并验证期望状态，不会自动创建远端 `develop` 或应用 ruleset。管理员必须按运行手册的顺序创建集成基线、观察 check context、应用两份 ruleset，并验证违规 PR 被拒绝。当前只有一位明确所有者，因此审批数保持为 0；增加独立维护者后应提升为 1 并要求最后推送者之外的批准。
