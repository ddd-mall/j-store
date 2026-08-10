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

## Ordering stream

Outbox 的严格顺序边界是 `(transportId, orderingKey)`，而不是仅按聚合全局排序：

- 领域事件的 `orderingKey` 由 `<aggregateType, aggregateId>` 生成。
- 集成消息的 `orderingKey` 由 `<destination, partitionKey>` 生成。
- 组合键先使用 UTF-8 字节长度前缀形成无歧义规范值，再持久化其 SHA-256 小写十六进制摘要。Kotlin 写入路径与 PostgreSQL 迁移必须对任意 Unicode 输入产生相同的 64 字符结果，避免分隔符歧义、列宽越界和 B-tree 索引页限制。
- 每个 transport 拥有独立序号；同一业务消息投递到 `local`、`kafka` 时分别进入两条流。

`outbox_stream_position` 以 `(transport_id, ordering_key)` 为主键保存最后分配的序号。
发布者在业务事务内通过单条 PostgreSQL upsert 原子取得下一个 `sequenceNo`，再写入 Outbox。
因此数据库回滚会同时回滚业务数据、序号推进和 Outbox 记录；允许序号因已提交记录清理而不从 1 开始，
但同一流中不得重复或倒退。

claim 的前驱屏障只检查相同 `transport_id + ordering_key` 且序号更小的非终态记录。
只有 `PUBLISHED` 是完成态；`PENDING`、`IN_PROGRESS`、`FAILED`、`DEAD_LETTER` 都会阻塞本流后继记录。
这样重试不造成乱序，同时一个 Kafka 死信不会阻塞
同订单的 local 投递，也不会阻塞其它订单的 Kafka 投递。

严格顺序和越过毒消息不可兼得。管理员修复消息或根因后，只能通过既有带原因审计的重入队恢复本流。
通用 Outbox 仓储不暴露无操作者、无原因的重入队方法，避免内部调用绕过同一审计边界。
若未来需要永久丢弃，必须先设计可被所有下游消费的 tombstone/skip-marker 协议，不能只修改本地状态。

Broker envelope 携带 `transportId`、`orderingKey` 和 `sequenceNo`。adapter 必须使用 ordering key 作为目标系统的
分区/路由键；入站 consumer 必须以 `(consumerId, transportId, orderingKey)` 维护已处理序号，幂等忽略旧序号并
拒绝或暂存缺口；消费者游标按 transport 隔离，避免不同 transport
的同名流互相干扰。本变更提供出站 envelope 契约；具体 Broker 入站 adapter 仍属于后续模块。

## 路由和失败

`OutboxDeliveryRouter` 按记录的 `transportId` 匹配 channel。没有匹配或重复匹配属于确定性配置错误，沿现有 FAILED/DEAD_LETTER 状态机处理。

Broker transport 的 `publish` 只有在目标系统确认接收后才能返回。适配器抛出的异常由现有 relay 重试策略处理。

## 数据迁移

新增 `outbox_entry.transport_id`：

- 既有 `LOCAL_DOMAIN` 回填 `local-domain`。
- 既有 `LOCAL_INTEGRATION` 回填 `local`。
- 既有 `BROKER` 回填 `broker`，用于兼容尚未完成的历史 Broker 目标；部署方必须提供同 ID adapter 才能继续投递。

迁移后字段设为非空，并新增 `ordering_key`、`sequence_no`、流位置表、流顺序唯一约束和 claim 索引。
迁移会把本地集成消息总线的消费游标推进到每条流已发布的连续前缀，避免旧版本已完成记录让新游标
误报缺口；未来 Broker adapter 的新消费组必须按自身起始 offset 显式初始化游标。
本项目尚未上线，本次接受破坏性 schema 重构，不提供旧应用版本在新 schema 上继续写入的兼容保证。
旧 `delivery_target` 字段暂时保留，作为消息类别兼容和审计数据；核心路由不再依赖它选择具体 Broker。

## Spring 装配

`j-store-outbox-spring` 独立注册领域事件总线、集成消息总线、消费仓储、发布者、router、relay 和调度器，不再由 Order boot 提供平台 Bean。配置的每个 transport ID 在启动时必须有且仅有一个 channel。

健康快照按配置目标及数据库中已持久化的 `transportId` 返回独立的 lag、过期租约、死信数量和状态。
Micrometer 的积压、lag、过期租约及告警指标统一带 `transportId` 标签；`transportId=all` 保留全局汇总视图。

## 回滚

本项目尚未上线，本次采用破坏性 schema 重构。源码与数据库必须作为一个候选整体回滚；不得让旧应用
连接已启用 ordering stream 约束的新 schema。开发环境可重建数据库，未来上线前必须重新建立兼容迁移策略。
