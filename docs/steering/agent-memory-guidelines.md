---
inclusion: always
---

# Agent Memory Guidelines - j-store

## 文件职责

- `AGENT.md` 只作为长期记忆索引，包含链接和简短描述。
- 具体项目理解、架构约束、测试约束、流程约束必须拆分到 `docs/` 下的独立文件。
- 项目规范约束统一存放在 `docs/steering/` 下。

## 写入规则

- 新增长期记忆时，先判断主题归属，再创建或更新对应 `docs/` 文件。
- 更新 `AGENT.md` 时只添加或调整索引，不写大段规范、方案或实现细节。
- 同一主题不要在多个文件重复维护；需要交叉引用时使用链接。
- 文件内容应短、稳定、可执行，避免记录一次性聊天过程。

## 推荐分类

- 项目整体理解：`docs/project-overview.md`
- 架构与 DDD 约束：`docs/steering/ddd-guidelines.md`
- 测试与 TDD 约束：`docs/steering/tdd-guidelines.md`
- Agent 记忆组织：`docs/steering/agent-memory-guidelines.md`
- 需求、设计、任务：`docs/spec/<feature>/`
- 业务调研与规划：`docs/requirement/`

## 维护原则

- 记忆文件记录长期有效的事实、约束和决策。
- 临时状态、执行日志、一次性排查过程不进入长期记忆。
- 若发现文档与代码冲突，以代码和当前测试为准，并更新文档消除冲突。

