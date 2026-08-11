# Outbox 可插拔传输与模块分拆交付摘要

## 已实现

- 将集成消息中立契约迁移到 `j-store-messaging-core`。
- 将 Outbox 模型、仓储端口和按持久化 `transportId` 的唯一路由迁移到 `j-store-outbox-core`。
- 将进程内 Spring 事件/消息总线迁移到 `j-store-messaging-local-spring`。
- 将 Jackson、JPA、relay、调度、死信、监控和 Boot 自动配置迁移到 `j-store-outbox-spring`。
- 用 `jstore.messaging.targets` 替换旧 mode 枚举；每个目标生成独立 Outbox 记录。
- 新增 `transport_id` 数据迁移，历史目标回填后设为非空并建立 ready 索引。
- 外部中间件通过 `IntegrationMessageTransport.transportId` 接入；核心未引入 Kafka/RabbitMQ 客户端。
- 启动时同时校验配置目标和领域事件固定使用的 `local-domain` 通道均恰好存在一个，适配器不能通过保留 ID 引入重复路由。
- 指标与关键日志按 `transportId` 区分目标。

## 验证结论

改造相关的核心、Spring、本地总线、PostgreSQL 和根启动模块测试均通过，`bootJar`、格式和许可证检查通过。全量测试只被本地未提供 Redis 导致的既有用户基础设施集成测试阻断；按用户要求未运行 WSL/Linux 验证。
