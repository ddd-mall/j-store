# Agent Memory Index（兼容入口）

AI coding 工具应首先读取 [AGENTS.md](AGENTS.md)。本文件保留给仍使用 `AGENT.md` 的旧工具，并继续只承担长期记忆索引职责。

本文件只存放长期记忆索引和简要说明；具体内容维护在 `docs/` 下的独立文档中。

## 项目理解

- [docs/project-overview.md](docs/project-overview.md): j-store 项目技术栈、模块边界、分层结构、运行与测试入口概览。

## 规范约束

- [docs/steering/ddd-guidelines.md](docs/steering/ddd-guidelines.md): DDD 架构、模块依赖、领域对象、仓储、应用服务、反腐层、基础设施与禁止模式。
- [docs/steering/tdd-guidelines.md](docs/steering/tdd-guidelines.md): TDD 工作流、测试分层、属性测试、集成测试、回归保护与执行建议。
- [docs/steering/agent-memory-guidelines.md](docs/steering/agent-memory-guidelines.md): Agent 长期记忆文件组织规则，约束 `AGENT.md` 只作为索引。
- [docs/steering/agent-governance.md](docs/steering/agent-governance.md): 长期自动维护的角色、权限、需求漂移、质量门禁和人工审批规则。

## 需求与规格

- [docs/spec/](docs/spec/): 按功能拆分的需求、设计、任务文档。
- [docs/requirement/](docs/requirement/): 业务需求与模块规划材料。

## 自动化运维

- [docs/operations/agent-automation-runbook.md](docs/operations/agent-automation-runbook.md): 建议的周期任务、触发器、输出和人工接管规则。
