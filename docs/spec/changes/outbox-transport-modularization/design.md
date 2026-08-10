# Outbox 可插拔传输与模块分拆设计

## 模块结构

```text
j-store-common-core
  DDD 原语、DomainEvent、领域事件发布端口

j-store-messaging-core
  IntegrationMessage、Handler、Publisher、Envelope、Transport SPI

j-store-outbox-core
  OutboxEntry、Repository、Router、DeliveryChannel、transport 规划

j-store-messaging-local-spring
  Spring 进程内领域事件与集成消息总线

j-store-outbox-spring
  Jackson、JPA、relay、调度、死信、监控、自动配置

future adapters
  j-store-messaging-kafka-spring
  j-store-messaging-rabbitmq-spring
```

依赖方向为 `outbox-spring -> outbox-core -> messaging-core`，同时 `outbox-spring -> messaging-local-spring -> common-core`。具体 Broker adapter 只需依赖 `messaging-core` 的 transport SPI；业务模块不依赖 adapter。

## Transport 模型

`transportId` 是稳定、非空、持久化的字符串。内建 ID：

- `local-domain`：进程内领域事件投递，仅供 `DomainEvent`。
- `local`：进程内集成消息 handler 投递。
- `kafka`、`rabbitmq`：预留给独立 adapter 模块，不在本次提供实现。

集成消息发布配置使用 transport ID 集合。发布者为每个 transport 创建一条 Outbox 记录；记录之后的 claim、重试和死信均不重新读取发布配置。

## 路由和失败

`OutboxDeliveryRouter` 按记录的 `transportId` 匹配 channel。没有匹配或重复匹配属于确定性配置错误，沿现有 FAILED/DEAD_LETTER 状态机处理。

Broker transport 的 `publish` 只有在目标系统确认接收后才能返回。适配器抛出的异常由现有 relay 重试策略处理。

## 数据迁移

新增 `outbox_entry.transport_id`：

- 既有 `LOCAL_DOMAIN` 回填 `local-domain`。
- 既有 `LOCAL_INTEGRATION` 回填 `local`。
- 既有 `BROKER` 回填 `broker`，用于兼容尚未完成的历史 Broker 目标；部署方必须提供同 ID adapter 才能继续投递。

迁移后字段设为非空并建立 transport ready 索引。旧 `delivery_target` 字段在本次保留，作为消息类别兼容和审计数据；核心路由不再依赖它选择具体 Broker。

## Spring 装配

`j-store-outbox-spring` 独立注册领域事件总线、集成消息总线、消费仓储、发布者、router、relay 和调度器，不再由 Order boot 提供平台 Bean。配置的每个 transport ID 在启动时必须有且仅有一个 channel。

## 回滚

源码模块变更必须整体回滚。数据库 migration 只增加兼容字段；若回滚旧代码，旧代码忽略新增列，可继续运行。不得回滚或删除已执行 migration。
