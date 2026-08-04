# 长期自动化维护治理设计

## 总体流程

```text
定时任务/Issue/CI/告警
        ↓
Maintenance Orchestrator
        ↓
Product Steward ──需要意图变更──> 人工批准 delta
        ↓
隔离分支或 worktree 中实现
        ↓
确定性 Quality Gate + 独立 Evaluator/Security Review
        ↓
人工批准合并/发布
        ↓
SRE 观察并反馈新事项
```

## 权威来源

不同类型的冲突使用不同权威来源：

- 已发布系统的实际行为：代码、数据库迁移和可执行测试是事实证据。
- 预期产品行为：用户批准的 `requirement.md` 和适用 `delta.md` 是意图来源。
- 技术决策：适用 `design.md` 和 steering 文档是约束来源。
- 完成状态：测试报告、质量门禁和审查证据优先于任务复选框。

任意两类来源冲突时创建 drift finding。agent 不得通过修改需求来让错误实现“合规”，也不得仅依据旧文档覆盖当前生产事实。

## Agent 边界

六个治理角色共享 `docs/steering/agent-governance.md`。`.codex/agents/*.toml` 是 Codex 适配器，不复制完整政策。

- Orchestrator 维护状态、预算、隔离和路由，不直接实现业务。
- Product Steward 只读审查需求追踪性，外部行为变化转人工。
- Quality Gate 只运行确定性检查并给出证据，不修被审代码。
- Security Agent 审计并可在单独任务中准备补丁，不接触真实密钥。
- SRE Agent 默认只读运行信号，不执行生产操作。
- Release Agent 准备候选和回滚材料，不执行生产发布。

## 确定性门禁

`scripts/quality-gate.sh` 是本地与 CI 的统一入口：

1. `scripts/check-agent-governance.sh` 检查关键治理文件、版本漂移和已知敏感值。
2. Python `spec-dev` 合同测试验证规格工具。
3. Gradle 全量测试验证 Kotlin/Spring 代码。

GitHub Actions 另外运行 CodeQL、依赖审查和 Gitleaks。AI 审查只能增加发现，不能替代这些门禁。

## 凭据策略

- Compose 从 `.env`/进程环境读取本地数据库密码，缺失时拒绝启动。
- Spring local profile 从 `JSTORE_*` 环境变量读取数据库、Redis 和 JWT 配置。
- `.env.example` 只描述变量，不提供可复用密码。
- 已经进入 Git 历史的凭据必须在外部系统轮换；删除当前文件中的值不等于完成轮换。

## 失败与停止策略

- 同一失败最多进行两次有实质差异的自动修复尝试。
- 无新证据、需要产品决策、涉及敏感权限或生产状态时立即停止并升级。
- 自动维护只创建 PR；不得绕过 required checks、CODEOWNERS 或人工审批。
