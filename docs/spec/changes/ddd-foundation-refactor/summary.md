# DDD 基座破坏性重构交付摘要

## 已实现结果

- 用 `Identifier`、`AggregateRoot`、`RecordsDomainEvents`、`EventRecordingAggregateRoot` 和 `AggregateRepository` 替换旧基座命名与公开可变事件队列。
- 领域事件直接提供稳定 envelope 元数据，并移除事件中的聚合对象引用；新事件 ID 在构造时生成且重试复用。
- 将分页契约移入 query 包，将消息消费幂等仓储移入 messaging 包；Spring 代理解析和持久化实现留在 common-spring。
- 迁移所有有界上下文、仓储、应用服务、序列化、监听器和测试到新契约。
- 为 Outbox 标识、版本、重试、租约和投递组合增加构造不变量。

## 验证证据

- `:j-store-common-core:test`：通过。
- `:j-store-common-spring:test`：通过。
- `compileKotlin`、`compileTestKotlin`：通过。
- 全仓 `test`：`BUILD SUCCESSFUL`；生成 124 份 JUnit 报告，共 405 个测试，0 failures、0 errors、0 skipped。
- 质量门禁的仓库治理检查通过；spec-dev 28 个测试与 governance 11 个测试通过；Gradle 回归测试通过。
- `scripts/quality-gate.sh` 在 WSL 中无法直接使用仅含 `java.exe` 的 Windows JDK，因此一体化包装命令未取得零退出码；上述三段检查均已分别取得通过证据。
- `git diff --check` 通过；Kotlin 源码已无旧 DDD 基座符号引用。

## 兼容性说明

本次未保留旧源码 API 的别名或适配层；数据库表名、事件名称和事件版本保持不变。工作区在重构前已有未提交修改，本次没有覆盖、重置或提交这些用户变更。
