# j-store Agent Instructions

本文件是所有 AI coding 工具在本仓库工作的统一入口。具体、长期有效的规则维护在 `docs/`，本文件只提供必须读取的索引和不可绕过的边界。

## 开始工作前

1. 阅读 `docs/project-overview.md`。
2. 领域边界、聚合、状态机或跨上下文协作变更阅读 `docs/domain-modeling.md`。
3. Kotlin/Gradle 变更阅读 `docs/steering/ddd-guidelines.md`。
4. 依赖声明、版本调整、BOM、Gradle Platform 或供应链验证变更阅读 `docs/steering/dependency-management-guidelines.md`。
5. 新功能或行为修复阅读 `docs/steering/tdd-guidelines.md`。
6. agent 编排、自动维护、评审、发布和生产相关工作阅读 `docs/steering/agent-governance.md`。
7. 有适用规格时，以 `docs/spec/<feature>/requirement.md` 及已批准 delta 为产品意图来源。
8. 分支、PR、发布或热修复工作阅读 `docs/operations/branch-management.md`。
9. Agentic CI/CD Supervisor、GitHub Issue 控制面或 Symphony 接入工作阅读 `docs/operations/agentic-cicd-runbook.md`。

## 长期记忆与文档索引

本文件同时承担长期记忆索引职责，只存放统一指令、文档链接和简要说明；具体内容维护在 `docs/` 下的独立文档中。

### 项目理解

- [docs/project-overview.md](docs/project-overview.md)：项目技术栈、模块边界、分层结构、运行与测试入口概览。
- [docs/domain-modeling.md](docs/domain-modeling.md)：当前有界上下文、权威事实、聚合一致性边界、交易 Saga 与长期模型维护规则。

### 规范约束

- [docs/steering/ddd-guidelines.md](docs/steering/ddd-guidelines.md)：DDD 架构、模块依赖、领域对象、仓储、应用服务、反腐层、基础设施与禁止模式。
- [docs/steering/tdd-guidelines.md](docs/steering/tdd-guidelines.md)：TDD 工作流、测试分层、属性测试、集成测试、回归保护与执行建议。
- [docs/steering/dependency-management-guidelines.md](docs/steering/dependency-management-guidelines.md)：外部坐标、统一 Platform、版本与安全例外、供应链验证和依赖变更完成标准。
- [docs/steering/agent-memory-guidelines.md](docs/steering/agent-memory-guidelines.md)：Agent 长期记忆文件组织规则。
- [docs/steering/agent-governance.md](docs/steering/agent-governance.md)：长期自动维护的角色、权限、需求漂移、质量门禁和人工审批规则。

### 需求与规格

- [docs/spec/](docs/spec/)：按功能拆分的需求、设计、任务和验证文档。
- [docs/requirement/](docs/requirement/)：业务需求与模块规划材料。

### 自动化运维

- [docs/operations/agent-automation-runbook.md](docs/operations/agent-automation-runbook.md)：建议的周期任务、触发器、输出和人工接管规则。
- [docs/operations/agentic-cicd-runbook.md](docs/operations/agentic-cicd-runbook.md)：Symphony/Codex 编排的能力级别、运行配置、安全边界、停止与恢复步骤。
- [docs/operations/immutable-multi-cluster-delivery.md](docs/operations/immutable-multi-cluster-delivery.md)：不可变 OCI 制品在物理隔离但基础设施同构的多集群之间构建、晋级、部署与回滚的操作契约。

## 权威来源与冲突处理

- 代码、迁移和可执行测试描述当前已实现事实。
- 已批准的 requirement/delta 描述预期产品行为。
- design 和 steering 描述适用的技术决策与约束。
- 测试结果和审查证据描述完成状态，复选框本身不是完成证据。
- 上述来源冲突时必须报告 drift finding，并路由给相应所有者；不得用错误实现自动改写需求，也不得用陈旧文档掩盖当前事实。

## 开发阶段与兼容策略

- 本项目当前处于内部开发期，尚未形成需要维护的公开稳定版本或生产数据兼容承诺。
- 除非已批准的 requirement/delta 明确要求，功能迭代不得为旧内部接口、旧开发数据或旧实现新增兼容层、双写/双读、回填或数据迁移脚本；详细边界见 [项目概览](docs/project-overview.md#开发阶段与变更策略)。

## 验证

优先运行最小相关测试，交付前按影响范围扩大。治理和跨模块变更运行：

```bash
./scripts/quality-gate.sh
```

任何未运行或失败的检查都必须在交付中明确说明。

## 安全与权限

- 不提交密码、token、私钥、真实 `.env` 或生产数据。
- 不直接修改保护分支，不绕过 required checks，不自动合并或发布。
- 不执行生产写入、数据库迁移、权限变更或密钥操作，除非用户对确切目标明确授权。
- 实现者不能批准自己的变更；高风险变更需要独立评估和人工批准。
- 连续失败、需求不清、涉及外部行为或敏感权限时停止并升级，不进行无限修复循环。

## 长期记忆

按 `docs/steering/agent-memory-guidelines.md` 维护。稳定事实写入对应文档，临时执行日志不进入长期记忆。
