# Agentic CI/CD 编排需求

## 文档职责

- 本文件是 Agentic CI/CD 长期产品意图和验收标准的唯一权威来源。
- [design.md](design.md) 维护当前技术决策；[tasks.md](tasks.md) 维护唯一当前进度；[review-log.md](review-log.md) 保存评审流水；[archive.md](archive.md) 保存已完成阶段摘要。
- 仍未收敛的 Level 1 变更保留在 [本地候选闭环规格](../changes/agentic-cicd-local-candidate-loop/)；完成后必须合并回本目录并归档，不再形成第二份当前状态。
- 部署命令、凭据步骤、停止与恢复操作只维护在 [运行手册](../../operations/agentic-cicd-runbook.md)，规格文件不复制操作流程。

## 背景与目标

j-store 已具备仓库级 Agent 治理、规格驱动开发、确定性质量门禁和受保护分支模板，但尚缺少一个能够持续承接目标、隔离实施、独立评审、跟踪 PR CI 并恢复执行的常驻编排层。

本能力以 GitHub Issue 作为工作控制面，以 OpenAI Symphony 的编排规范和参考实现作为外层 Supervisor，以 Codex App Server 承载具体规划与实现。自动化负责准备可审查候选；产品意图接受、敏感操作、正式合并和发布继续由人负责。

## 参与者与术语

- **发起人**：创建或确认目标、接受高影响产品决策并最终决定是否合并的人。
- **Supervisor**：轮询可调度 Issue、创建隔离 workspace、启动 Agent、协调重试和恢复的常驻服务。
- **Implementer**：在候选 workspace 中制定本轮计划、编写测试和实现代码的 Agent。
- **Independent Reviewer**：未修改当前候选版本、负责需求漂移和代码质量评审的独立 Agent。
- **Quality Gate**：仅运行确定性检查并报告 PASS、FAIL 或 SKIPPED 的只读角色。
- **工作控制面**：GitHub Issue、状态标签、单一 Workpad 评论、关联 Draft PR 和检查结果共同构成的持久任务状态。
- **正式 PR**：已有 Draft PR 在全部门禁满足后转换为 Ready for Review；不得为同一分支重复创建第二个 PR。

## 范围

- 接受带有明确目标、范围和验收标准的 GitHub Issue。
- 从执行时锁定的 `origin/develop` SHA 创建每任务独立 workspace 和 `codex/*` 分支。
- 根据目标、适用规格、当前代码事实和上一轮 review finding 制定迭代计划。
- 按 TDD 实现，运行聚焦检查，并交给独立 Reviewer 与 Quality Gate。
- 在 review 发现新问题时形成结构化反馈并重新规划，受熔断规则限制。
- 创建唯一 Draft PR，持续跟踪 required checks 和 review thread。
- 当 CI 或 review 失败且可归因于候选变更时恢复原任务继续修复。
- 当 required checks 全部通过、review thread 已处理且独立评审无新问题时，将 Draft PR 转为 Ready for Review，并通过 Issue 状态、Workpad 或 PR review request 完成 GitHub 原生人工交接。
- 记录状态、基线 SHA、候选 SHA、命令、结果、重试、turn/时间使用量和残余风险，支持 Supervisor 重启后的恢复。

## 非目标

- 不自动批准、合并或发布 PR。
- 不绕过 required checks、CODEOWNERS、review thread resolution 或分支保护。
- 不执行生产写入、数据库迁移、密钥轮换或权限变更；外部付费操作未经费用所有者对精确范围和残余风险批准不得执行。
- 第一版不支持多仓库事务、多 Supervisor 高可用或跨任务共享可变 workspace。
- 第一版不自动发起依赖升级，也不处理无界的“持续优化”目标。
- 不建设独立邮件通知链路；人工交接使用 GitHub Issue、PR 状态和 review request。
- 不用 LLM 判断替代编译、测试、安全扫描或 GitHub check conclusion。

## 功能需求

### AC-01 目标准入

当一个打开的 GitHub Issue 同时满足以下条件时，Supervisor 才能认领：

- 包含目标、范围、非目标和可验证验收标准；
- 带有唯一调度标签 `agent:queued`；
- 由允许的仓库成员创建，或经过允许成员显式加标签确认；
- 没有高风险未决决策、未完成 blocker 或另一个活跃执行声明。

不满足条件的 Issue 必须保持未执行，并留下可操作的缺失项说明；不得尝试补写产品意图。

### AC-02 可信基线与隔离

认领任务时必须先获取远端，记录实际 `origin/develop` SHA，并从该 SHA 创建唯一 workspace 和 `codex/gh-<number>-<slug>` 分支。不得从调用者陈旧的本地 `develop`、既有未清理工作树或另一个任务分支启动。

### AC-03 计划与实现输入

Implementer 每轮必须读取 `AGENTS.md`、适用 steering/spec 文档、Issue 目标、当前 diff、上一轮结构化 findings 和当前 CI 证据。第一轮 findings 为空。计划必须映射验收标准、验证命令和显式非目标。

### AC-04 本地实现与确定性门禁

新行为或缺陷修复必须先形成失败测试，再完成最小实现和重构。候选进入独立评审前必须运行最小相关检查；进入 Draft PR 前必须运行适用的仓库级门禁。任何 required 命令失败均不得被 Agent 主观标为通过。

### AC-05 独立评审闭环

修改过当前候选版本的 Agent 不能给出同版本最终批准。Independent Reviewer 必须逐项检查验收覆盖、需求漂移、实现质量、安全风险和验证证据，并输出 PASS 或带稳定根因标识的 findings。

存在新 finding 时任务返回规划/实现阶段。相同根因最多允许两次有实质差异的自动修复；第三次遇到同一根因时必须标记 `agent:fused` 并转人工。

### AC-06 唯一 Draft PR

本地门禁和独立评审通过后，自动化可以提交、推送并创建一个目标为 `develop` 的 Draft PR。重复事件或 Supervisor 重启必须复用已有分支和 PR，不得产生重复候选。

### AC-07 PR CI 与 Review 反馈

Supervisor 必须跟踪仓库规定的 required checks、PR review、review thread、base SHA 和 head SHA。失败证据必须区分：

- 候选实现缺陷：返回规划/实现阶段；
- 基础设施或第三方故障：等待或有限重试，不修改业务代码；
- 已知 flaky：按独立重跑策略处理，不把重跑次数伪装成代码修复；
- 需求或高风险决策：转人工；
- 目标分支前移或冲突：同步可信基线并重新运行受影响检查。

### AC-08 Ready 与 GitHub 人工交接

仅当最新 head SHA 的所有 required checks 成功、所有 actionable review thread 已处理、Independent Reviewer 无新 finding、PR 元数据和证据完整时，自动化才能把已有 Draft PR 转为 Ready for Review。

转换后，Reconciler 必须根据已获能力合同尝试以下 GitHub 原生信号：将 Issue 迁移到 `agent:human-review`、更新唯一 Workpad、按仓库配置请求指定 PR reviewer。至少一种信号成功才算完成人工交接；其余失败信号作为增强项独立重试。全部信号都失败时不得回滚已通过的代码状态或 Ready 状态，但交接保持未完成并形成可重试的独立运维事项。所有信号必须绑定最新 head SHA并幂等执行。

### AC-09 恢复、取消与幂等

Supervisor 在规划、实现、等待 CI 和等待 review 任一阶段重启后，必须根据 Issue、Workpad、Git、workspace 和 PR 状态恢复，且不重复创建分支、PR 或人工交接副作用。Issue 被关闭、取消或移除调度资格后，运行中的 Agent 必须停止并释放 workspace。

### AC-10 人工边界

产品意图变化、公共兼容性破坏、认证授权、隐私、多租户、金额、库存、订单状态、不可逆数据操作、生产行为、密钥、权限和外部付费均必须停止并等待人工批准。自动化不得自动合并或发布，即使所有检查均通过。

## 质量目标

- **安全性**：Coding Agent 不继承 GitHub App、生产或 Supervisor 主凭据；运行环境默认只读，仅 Implementer 阶段按能力合同获得当前 workspace 写权限，网络和宿主工具按最小集合开放。
- **可靠性**：任务状态转换幂等；同一 Issue 同时最多一个活跃执行；重复 webhook/poll 不产生重复副作用。
- **资源约束**：并发、turn和墙钟时间受确定性硬限制；可信input/output token用量只作审计，不在仓库内维护模型费率或推算账单。
- **可恢复性**：单实例 Supervisor 重启后能在五分钟内从持久控制面恢复可继续任务。
- **可审计性**：每轮记录 Issue、base/head SHA、Agent 角色、命令结果、finding 根因、重试和 GitHub 人工交接事件 ID。
- **可运维性**：具备最大并发、每任务 turn/时间限制、熔断、取消和全局 kill switch。
- **可维护性**：长期治理继续以 `docs/steering/agent-governance.md` 为唯一权威；平台配置不得复制或弱化它。

## 外部依赖与前置条件

- `develop` 远端 ruleset 已实际启用并验证 required checks。
- 当前 `develop` 的 Security Gate 已绿色；历史 secret 已经审计和必要轮换。
- 具有最小权限的专用 GitHub App，安装范围仅限目标仓库。
- 已审查并固定提交的 Symphony 运行时，以及通过稳定版策略、App Server v2兼容性检查和制品身份记录验证的 Codex CLI；仓库不要求单一具体Codex版本。
- 专用、非生产网络的 Linux 运行主机或容器环境。

## 退出与回滚

- 任意时刻可通过移除调度标签或启用全局 kill switch 停止新任务。
- 停止 Supervisor 不得影响 GitHub Actions、现有 PR 或人工开发。
- 尚未合并的自动候选可关闭 PR 并删除短分支；不得重写长期分支历史。
- 若 Symphony 或 Agent 行为不符合边界，立即退回只读模式并保留审计证据。
