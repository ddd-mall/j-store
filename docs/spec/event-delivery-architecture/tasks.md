# 事件投递架构重构任务

- [x] EDA-T1：以失败测试定义集成消息元数据、模式到投递目标规划和唯一通道路由。
- [x] EDA-T2：实现 `LocalDomainEventBus` 破坏性重命名并迁移 Spring 本地分发。
- [x] EDA-T3：扩展 Outbox 模型、PO、仓储和数据库迁移，保持可靠性状态机。
- [x] EDA-T4：实现集成消息 serializer/registry、publisher、local handler bus 和 Spring 条件装配。
- [x] EDA-T5：实现 Broker Transport SPI、broker/hybrid fail-fast 和目标级独立记录。
- [x] EDA-T6：迁移订单—库存—支付—履约的跨上下文消息边界。
- [x] EDA-T7：运行模块测试、Boot 测试、PostgreSQL 集成测试和质量门禁。
- [x] EDA-T8：更新技术架构文档与交付摘要，记录验证证据和残余风险。
