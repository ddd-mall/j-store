# j-store Agent Instructions

本文件是所有 AI coding 工具在本仓库工作的统一入口。具体、长期有效的规则维护在 `docs/`，本文件只提供必须读取的索引和不可绕过的边界。

## 开始工作前

1. 阅读 `docs/project-overview.md`。
2. Kotlin/Gradle 变更阅读 `docs/steering/ddd-guidelines.md`。
3. 新功能或行为修复阅读 `docs/steering/tdd-guidelines.md`。
4. agent 编排、自动维护、评审、发布和生产相关工作阅读 `docs/steering/agent-governance.md`。
5. 有适用规格时，以 `docs/spec/<feature>/requirement.md` 及已批准 delta 为产品意图来源。

## 权威来源与冲突处理

- 代码、迁移和可执行测试描述当前已实现事实。
- 已批准的 requirement/delta 描述预期产品行为。
- design 和 steering 描述适用的技术决策与约束。
- 测试结果和审查证据描述完成状态，复选框本身不是完成证据。
- 上述来源冲突时必须报告 drift finding，并路由给相应所有者；不得用错误实现自动改写需求，也不得用陈旧文档掩盖当前事实。

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
