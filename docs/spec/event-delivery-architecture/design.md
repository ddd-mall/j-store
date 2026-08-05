# 事件投递架构重构设计

## 核心决策

1. 将原 `DomainEventBus` 破坏性重命名为 `LocalDomainEventBus`，保留同步、本进程、listener 与 relay delivery transaction 同事务的语义。
2. 新增与领域事件无继承关系的 `IntegrationMessage`、`IntegrationEvent`、`IntegrationCommand`，避免把领域类型直接变成远程契约。
3. 新增 `IntegrationMessagePublisher`。它根据 `IntegrationMessagingMode` 规划 LOCAL/BROKER 目标，并为每个目标写入独立 Outbox 记录。
4. `OutboxPublisher` 不再反序列化并直接依赖领域总线，而是调用 `OutboxDeliveryRouter`。Router 要求每条记录恰好匹配一个 `OutboxDeliveryChannel`。
5. 本地领域事件通道负责领域事件反序列化和 `LocalDomainEventBus` 调用；本地集成通道负责集成消息反序列化和 handler 调用；Broker 通道把原始 envelope 交给 `BrokerIntegrationMessageTransport`。
6. 集成消息 handler 使用稳定 handler ID。消费记录继续使用现有数据库唯一键，因此 local 模式与将来的 Broker consumer adapter 可共享幂等模型。
7. hybrid 模式通过两条 Outbox 记录实现目标级独立状态，不使用一个 relay 调用串行发送两个不可原子提交的目标。

## 核心模型

```text
DomainEventPublisher -> outbox(kind=DOMAIN_EVENT,target=LOCAL_DOMAIN)
                                      |
                                      v
                              LocalDomainEventBus

IntegrationMessagePublisher -> publication plan
  local  -> outbox(kind=INTEGRATION_*,target=LOCAL_INTEGRATION)
  broker -> outbox(kind=INTEGRATION_*,target=BROKER)
  hybrid -> 两条独立记录

OutboxPublisher -> OutboxDeliveryRouter -> exactly one OutboxDeliveryChannel
```

## 消息契约

`IntegrationMessageMetadata` 包含：

- `messageId`：全局稳定幂等键。
- `messageName + messageVersion`：稳定契约标识。
- `occurredAt`：事实发生或命令产生时间。
- `partitionKey`：目标系统的有序分区依据。
- `correlationId`：同一业务流程关联 ID。
- `causationId`：触发当前消息的上游消息 ID，可空。
- `tenantId`：租户或商户隔离标识，可空。

外部 DTO 只使用 JSON 稳定的标量、集合和专用 contract data class。

## 完成与失败语义

- LOCAL_DOMAIN：所有同步 listener 及 `markPublished` 在同一个本地数据库事务中成功。
- LOCAL_INTEGRATION：所有同步 handler 及 `markPublished` 在同一个本地数据库事务中成功。
- BROKER：Transport 确认 Broker 接收后执行 `markPublished`。若 Broker 已接收而数据库提交失败，relay 会重发；这是至少一次语义的预期窗口。
- 无匹配或多匹配通道视为配置错误，沿既有 FAILED/DEAD_LETTER 状态机处理。

## Spring 装配

- `jstore.messaging.mode` 默认 `local`。
- local transport 由 common-spring 提供。
- broker/hybrid 使用 `ObjectProvider<BrokerIntegrationMessageTransport>` 校验；缺失时启动失败。
- Broker 适配器位于未来独立 infrastructure 模块，不进入 common-core 或领域模块。

## 迁移策略

1. 先引入新模型和路由器，以适配器保持现有领域事件路径。
2. 扩展 Outbox PO、仓储和迁移。
3. 引入集成消息本地 handler 基础设施。
4. 将跨上下文 translator 改为领域事件到集成消息的映射；接收方 handler 调用本地应用服务。
5. 删除旧 `DomainEventBus` 命名和跨上下文 `DomainEventListener` 装配。

## 回滚

代码尚未上线，不提供运行时双版本兼容。若候选失败，回滚整个代码和新增 migration；不得只回滚代码而保留新配置模式。
