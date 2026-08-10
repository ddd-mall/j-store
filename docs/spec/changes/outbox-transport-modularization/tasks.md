# Outbox 可插拔传输与模块分拆任务

- [x] T1：增加 transport ID、目标规划和唯一路由的失败测试。
- [x] T2：创建 `j-store-messaging-core`，迁移中立集成消息契约与 SPI。
- [x] T3：创建 `j-store-outbox-core`，迁移 Outbox 运行模型与路由端口。
- [x] T4：创建 `j-store-outbox-spring`，迁移 Spring/JPA 实现及测试。
- [x] T5：新增 `transport_id` migration 和持久化往返验证。
- [x] T6：迁移业务调用方依赖，移除 Order boot 的平台事件装配。
- [x] T7：更新项目与事件基础设施文档。
- [x] T8：运行相关模块、全量测试、bootJar 和质量门禁并记录证据。
- [x] T9：增加 ordering stream 模型、事务内数据库序号分配及持久化约束。
- [x] T10：按 `(transportId, orderingKey, sequenceNo)` 实现 claim 前驱屏障和并发验证。
- [x] T11：验证死信只阻塞目标 ordering stream，并保留带审计的重入队恢复能力。
- [x] T12：在 Broker envelope 中传播顺序元数据，并补充 adapter 契约测试。

## 验证证据

- `:j-store-outbox-core:test`：18 tests，全部通过，包含无审计死信重入队入口的回归保护。
- `:j-store-messaging-local-spring:test`：10 tests，全部通过。
- `:j-store-outbox-spring:test`：68 tests，全部通过，包含嵌入式 PostgreSQL 并发序号、回滚、claim 屏障、消费游标以及按 transport 隔离的健康与指标验证。
- `:j-store-boot:test`：17 tests，全部通过，包含 Flyway 回填验证。
- `:j-store-boot:bootJar`：通过。
- Windows 原生 `spotlessCheck licensee verifyLicenseArtifacts`：通过，53 个 JAR 制品许可证校验通过。
- Windows 原生 Order 模块边界测试和文件所有权检查：通过，1160 个仓库文件完成分类。
- Windows 原生全量 `test`：Outbox 及此前执行模块通过；在 `j-store-user-infrastructure` 被 5 个需要本机 Redis 的既有集成测试以连接 `IOException` 阻断。
- spec-dev、governance 和 tooling 共 54 个 Python 契约测试通过（使用官方 PyPI 源与 Python 3.14）。
- `j-store-user-infrastructure` 的 5 个 Redis 集成测试改用测试依赖携带的嵌入式 Redis，Lua、TTL、
  登录限流和并发 refresh-token 轮换全部通过，不再要求开发机预装 `redis-server`。
- `scripts/quality-gate.sh` 六阶段全部通过：治理、54 个 Python 契约测试、1161 个文件归属检查、
  Spotless、50 个模块的依赖许可证、全仓 Gradle 测试、`:j-store-boot:bootJar` 和 53 个 JAR 制品许可证验证。
