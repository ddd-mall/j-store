# Agentic CI/CD 迭代落地计划

## 使用方式

本计划按可独立验收的迭代推进。完成复选框必须同时具备实现和下方列出的证据；文件存在或 Agent 自述不构成完成。发现新的安全、权限或产品意图决策时，更新对应 requirement/design 后再继续。

## 当前状态

- 当前推进分支：`codex/agentic-cicd-kubernetes-level0`
- 初始基线：`origin/develop@daf184ab9bb3f3bf811ae2158de704df6762b2a8`
- 当前可信基线：`origin/develop@73dd00fd64ab35649db58b8df96a5e1c8891d77b`
- 当前阶段：迭代 3 — 在开发 Kubernetes 集群落地固定运行时 Level 0，随后进行真实只读 Agent 演练
- 外部阻塞：ruleset 正反例演练、GitHub App 短期只读 token、付费模型 turn 和 disposable Issue 均尚未形成完整证据。

## 迭代 0：远端基线可用

目标：在允许自动代码写入前，确保长期分支和当前基线本身可被确定性门禁信任。

- [x] `I0-01` 审计最新 `develop` Security Gate 中两个 Gitleaks finding，不在日志或文档中暴露秘密。
  - 依赖：仓库管理员和可能的凭据所有者。
  - 证据：finding 分类、是否曾有效、轮换确认和处置决策。
  - 证据：两个 finding 仅来自未合并测试 fixture，按 commit/path/rule/line 精确 fingerprint 处置；没有扩大 Gitleaks 规则范围，见 `review-log.md` 远端准入审计。
  - 人工门：未执行真实密钥轮换或历史重写；未来出现真实凭据时仍必须单独批准。
- [x] `I0-02` 使 `develop` 最新 push 的 Quality、Security 和 Qodana 全部绿色。
  - 证据：同一 `develop@2542ee92a50bf87c427637d81d6445e4b2cea1db` 的三个 push workflow 均为 success，见 `review-log.md`。
- [x] `I0-03` 按 `.github/rulesets/develop.json` 启用远端 ruleset。
  - 证据：GitHub Rulesets API 返回 active `Protect develop`，required contexts 精确匹配模板。
  - 人工门：远端 Administration 写操作。
- [ ] `I0-04` 用合法和故意违规的 disposable Draft PR 验证分支方向、required checks、禁止删除和禁止 force push。
  - 证据：测试 PR、check 和 ruleset 拒绝结果。

退出条件尚未完全满足：`develop` 已受保护且绿色，但 I0-04 的正反例 enforcement 演练尚无证据。完成前保持 Level 0，不开放自动 push/PR。

## 迭代 1：控制面与只读运行骨架

目标：让一个合格 GitHub Issue 能被安全识别、形成可恢复计划，但不能修改业务代码或创建 PR。

- [x] `I1-01` 建立 requirement、design 和本迭代计划，覆盖 AC-01 至 AC-10。
  - 证据：本目录三个规格文件及验收追踪。
- [x] `I1-02` 增加机器可读的状态、required checks、重试和人工终态合同。
  - 证据：`config/agentic-cicd/state-contract.json` 及合同测试。
- [x] `I1-03` 增加 GitHub Agent Goal Issue Form，收集目标、范围、非目标、验收、风险和验证要求。
  - 证据：`.github/ISSUE_TEMPLATE/agent-goal.yml` 和静态合同测试。
- [x] `I1-04` 增加受信 `WORKFLOW.md`，配置 GitHub Issues adapter、单并发、只读 sandbox、阻塞式 approval policy 和治理提示。
  - 证据：`WORKFLOW.md` 和静态合同测试。
- [x] `I1-05` 固定 Symphony reference implementation 来源和安全基线。
  - 证据：`config/agentic-cicd/symphony.lock.json`，commit 为完整 SHA，包含指定安全修复。
- [x] `I1-06` 建立确定性合同校验器并纳入仓库治理检查，继而由 `scripts/quality-gate.sh` 执行。
  - 证据：先失败后通过的 `tests/governance/test_agentic_cicd_contract.py`；校验器非零退出会阻断质量门禁。
- [x] `I1-07` 编写只读部署和标签初始化 runbook，不执行远端写操作。
  - 证据：`docs/operations/agentic-cicd-runbook.md` 包含安装、凭据、启动、停止、恢复和审计步骤。
- [x] `I1-08` 执行聚焦合同测试、治理检查和 diff review。
  - 证据：命令、退出码和残余风险写入 `review-log.md`。

退出条件：已满足。仓库配置可以确定性证明 Issue Form 只产生非调度 `agent:candidate`，只有人工添加 `agent:queued` 才能调度；当前能力合同禁用分支、push、PR、邮件、合并、发布和生产写入。

## 迭代 2：Workspace 与任务状态协调器

目标：实现可测试的确定性协调组件，负责基线锁定、workspace 生命周期、状态迁移和恢复；暂不调用真实 Codex。

- [x] `I2-01` 先以测试定义合法/非法状态转换、互斥 claim、取消和熔断行为。
- [x] `I2-02` 实现从实际 `origin/develop` SHA 创建规范化 workspace 和 `codex/gh-*` 分支。
- [x] `I2-03` 实现 Issue/Workpad/branch/PR 的恢复快照和幂等键。
- [x] `I2-04` 实现根因化计数：语义修复最多两次，基础设施重试单独计数。
- [x] `I2-05` 实现预算和 kill switch：并发、turn、墙钟时间和费用上限。
- [x] `I2-06` 使用临时 Git 仓库完成创建、恢复、重复认领、取消和安全清理测试。

退出条件：仓库内组件级条件已满足。原子快照、幂等键、可信 workspace 元数据和重复调用测试证明本地恢复不会重复分支或副作用；真实 Supervisor 进程故障注入将在迭代 4/6 完成。

## 迭代 3：Codex 规划、实现与独立评审

目标：在隔离 workspace 中完成“计划—TDD 实现—门禁—独立评审—有限返工”闭环，不推送远端。

- [x] `I3-01` 接入固定版本的 Codex App Server，使用结构化 `IterationPacket`。
  - 证据：CLI `0.146.0` 锁、JSONL client、v2 动态 schema 校验和无模型调用的真实初始化 smoke。
- [x] `I3-02` 将 repo 内 spec-planner/spec-generator 或适用角色路由为 Implementer；不得使用其自述替代 gate。
  - 证据：`role-routing.json` 明确 Implementer、Quality Gate、Independent Reviewer 和 Security Reviewer 的写入/批准边界。
- [x] `I3-03` 运行聚焦测试并调用只读 Quality Gate，保留命令和退出码。
  - 证据：协议测试 12/12、完整门禁 spec-dev 28、governance 31、tooling 47 tests；Gradle 和许可证阶段全部 PASS。
- [ ] `I3-04` 为固定 head SHA 启动独立 Product Steward/Evaluator 会话。
- [x] `I3-05` findings 结构化并回流下一轮；新提交使旧 PASS 失效。
  - 证据：ReviewDecision schema、host-owned identity/SHA 校验、快照恢复测试和 exact-head PASS 查询。
- [x] `I3-06` 验证相同根因第三次出现时进入 `agent:fused`。
  - 证据：不同 strategy fingerprint 的第三次修复被协调器熔断；重复 strategy 不消耗次数。
- [ ] `I3-07` 在 disposable Issue 上完成无远端写的端到端演练。
- [ ] `I3-08` 将 Coordinator、workspace metadata、IterationPacket 和独立 Reviewer 决定接入唯一 Symphony 运行路径；不得建立第二个并行 Supervisor。
  - 依赖：I3-04 和 I3-07 必须通过该真实运行路径取得证据，组件单测不能替代运行闭环。
- [x] `I3-09` 增加固定 Symphony/Codex 运行时预检，不启动服务或模型 turn。
  - 证据：`scripts/check-agentic-cicd-runtime.py`、6 个聚焦测试，以及本机对锁定 Symphony 源码成功、Codex/Elixir 环境漂移失败的确定性报告。

退出条件尚未满足：协议、身份分离、熔断机制和固定运行时预检已具备，但尚未接入真实 Symphony 运行闭环。I3-04、I3-07 和 I3-08 仍需完成；付费模型 turn 和 disposable GitHub Issue 需要后续明确授权。

## 迭代 4：唯一 Draft PR 与 CI/Review 闭环

目标：开放最小远端写能力，自动准备候选并根据 GitHub 证据继续修复。

- [ ] `I4-01` 经管理员批准，为 GitHub App 增加 Contents/PR write；不增加 Administration、Secrets、Deployments 或 Workflows write。
- [ ] `I4-02` 实现每任务短期 installation token 和 host-side push/PR 操作，token 不进入 Codex 子进程。
- [ ] `I4-03` 幂等创建或复用唯一 Draft PR，目标固定为 `develop`。
- [ ] `I4-04` 实现 required check 聚合，并绑定最新 head SHA。
- [ ] `I4-05` 收集 review threads 和评论，生成标准化 `ReviewPacket`。
- [ ] `I4-06` 区分候选、基线、基础设施、flaky、需求/权限五类失败。
- [ ] `I4-07` 处理 base 前移与冲突并重新运行受影响门禁，禁止绕过和静默 force push。
- [ ] `I4-08` 使用 disposable 仓库验证重复事件、失败 CI、review 返工和 Supervisor 重启。

退出条件：一个任务始终只有一个开放 PR；最新 head 的 required checks 和 review 状态能够可靠驱动返工或就绪。

## 迭代 5：Ready、邮件和人工交接

目标：全部硬门禁通过后完成一次性通知，终点停在人工审批。

- [ ] `I5-01` 实现 Draft -> Ready 前置条件校验，不允许 Agent 直接宣告就绪。
- [ ] `I5-02` 经人工选择邮件 provider、发件身份、收件人白名单和费用策略。
- [ ] `I5-03` 实现独立 Notifier 和 `ready:<repo>:<pr>:<head>` 幂等键。
- [ ] `I5-04` 通知失败形成独立运维 finding，不回滚候选代码状态。
- [ ] `I5-05` 验证自动化没有 approve、merge、release 或 deployment 权限和路径。
- [ ] `I5-06` 完成真实低风险 PR 演练，由人手工合并。

退出条件：全绿 PR 只转换为 Ready 并准确通知一次；合并必须由人完成。

## 迭代 6：灰度与生产化

目标：以可观测、可停止、可回退的方式逐步扩大任务范围。

- [ ] `I6-01` 两周只读观察，统计误调度、恢复、成本和人工接管原因。
- [ ] `I6-02` 仅开放 docs/test/低风险内部重构，并发保持 1。
- [ ] `I6-03` 完成提示注入、token 泄漏、恶意 workflow/hook 和路径穿越安全测试。
- [ ] `I6-04` 完成规划、实现、等待 CI、通知四个故障点的 kill/restart 演练。
- [ ] `I6-05` 建立 dashboard、告警、磁盘清理、日志保留和应急停止 runbook。
- [ ] `I6-06` 根据证据决定是否提高并发或扩大领域范围；高风险领域仍需人工预批准。
- [ ] `I6-07` 生成 `summary.md`，逐项映射 AC-01 至 AC-10 的实现和验证证据。

退出条件：所有验收标准有可复现证据，没有未接受的高风险残余项；否则保持试点状态。

## 验收追踪

| 验收 | 主要迭代 | 计划证据 |
|---|---|---|
| AC-01 目标准入 | I1、I2 | Issue Form、标签合同、准入测试 |
| AC-02 可信基线与隔离 | I2 | 临时 Git 集成测试、base SHA 记录 |
| AC-03 计划输入 | I1、I3 | WORKFLOW、IterationPacket、Agent trace |
| AC-04 确定性门禁 | I1、I3 | quality gate、命令退出码 |
| AC-05 独立评审 | I3 | 独立会话 identity/head SHA、finding loop |
| AC-06 唯一 Draft PR | I4 | 重复事件和重启测试 |
| AC-07 CI/Review 反馈 | I4 | fake API、disposable repo E2E |
| AC-08 Ready 与通知 | I5 | readiness decision、notifier 幂等测试 |
| AC-09 恢复与取消 | I2、I4、I6 | fault injection 和恢复记录 |
| AC-10 人工边界 | I1、I5、I6 | 权限审计、负向测试、人工合并记录 |

## 人工审批点

以下任务不得因“逐步实现”而推定授权：

- 应用远端 ruleset 或修改 GitHub Administration 设置；
- 轮换密钥、重写 Git 历史或扩大 secret ignore；
- 创建/安装 GitHub App 并授予写权限；
- 配置真实邮件账号、收件人或付费 provider；
- 推送候选、创建 PR、将 PR 转 Ready、合并或发布；
- 访问生产网络、数据或凭据。
