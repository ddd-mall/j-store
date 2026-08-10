# Outbox 可插拔传输与模块分拆任务

- [x] T1：增加 transport ID、目标规划和唯一路由的失败测试。
- [x] T2：创建 `j-store-messaging-core`，迁移中立集成消息契约与 SPI。
- [x] T3：创建 `j-store-outbox-core`，迁移 Outbox 运行模型与路由端口。
- [x] T4：创建 `j-store-outbox-spring`，迁移 Spring/JPA 实现及测试。
- [x] T5：新增 `transport_id` migration 和持久化往返验证。
- [x] T6：迁移业务调用方依赖，移除 Order boot 的平台事件装配。
- [x] T7：更新项目与事件基础设施文档。
- [ ] T8：运行相关模块、全量测试、bootJar 和质量门禁并记录证据。

## 验证证据

- `:j-store-outbox-core:test`：15 tests，全部通过。
- `:j-store-messaging-local-spring:test`：9 tests，全部通过。
- `:j-store-outbox-spring:test`：58 tests，全部通过，包含嵌入式 PostgreSQL 持久化往返。
- `:j-store-boot:test`：16 tests，全部通过。
- `:j-store-boot:bootJar`：通过。
- Windows 原生 `spotlessCheck licensee verifyLicenseArtifacts`：通过，53 个 JAR 制品许可证校验通过。
- Windows 原生 Order 模块边界测试和文件所有权检查：通过，1160 个仓库文件完成分类。
- Windows 原生全量 `test`：Outbox 及此前执行模块通过；在 `j-store-user-infrastructure` 被 5 个需要本机 Redis 的既有集成测试以连接 `IOException` 阻断。
- spec-dev Python 契约测试因本机缺少 `jsonschema` 未完成；按用户要求跳过 WSL/Linux 验证，完整 `scripts/quality-gate.sh` 未标记为通过。
