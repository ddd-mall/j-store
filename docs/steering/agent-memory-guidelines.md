---
inclusion: always
---

# Agent Memory Guidelines - j-store

## 文件职责

- `AGENTS.md` 是跨工具统一指令入口，同时承担长期记忆索引职责，只包含统一边界、链接和简短描述。
- 具体项目理解、架构约束、测试约束、流程约束必须拆分到 `docs/` 下的独立文件。
- 项目规范约束统一存放在 `docs/steering/` 下。

## 写入规则

- 新增长期记忆时，先判断主题归属，再创建或更新对应 `docs/` 文件。
- 更新 `AGENTS.md` 的长期记忆索引时只添加或调整链接和简短描述，不写大段规范、方案或实现细节；跨工具强制边界也统一写入 `AGENTS.md`。
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
- 若当前事实文档与代码冲突，以代码、迁移和当前测试作为事实证据并修正文档。
- 若预期产品行为与代码冲突，以已批准的 requirement/delta 作为意图来源，记录漂移并修复正确的所有者；不得用错误实现自动覆盖需求。
