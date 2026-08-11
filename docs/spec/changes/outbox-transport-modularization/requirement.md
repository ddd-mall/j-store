# Outbox 可插拔传输与模块分拆需求

## 目标

将事件投递能力从 `common` 模块中拆出，并建立 transport-neutral 的消息契约、Outbox 核心和 Spring/JPA 运行时，使同一业务代码可按部署环境选择本地、Kafka、RabbitMQ 或后续其它消息传输实现。

本次允许破坏仓库内部源码兼容，但不得改变业务行为、消息名称与版本、Outbox 可靠性语义或既有运维授权边界。

## 验收标准

- OTM-R1：业务 domain/application 只能依赖框架无关的领域事件和集成消息端口，不得依赖 Spring、JPA、具体 Broker 或 Outbox 持久化类型。
- OTM-R2：集成消息契约、handler、publisher 和 broker envelope 位于独立的 `j-store-messaging-core`，且不得引用 Outbox 类型。
- OTM-R3：Outbox 运行模型、路由器和 delivery channel SPI 位于独立的 `j-store-outbox-core`，且不得依赖 Spring、JPA 或具体 Broker SDK。
- OTM-R4：每条 Outbox 记录必须持久化稳定 `transportId`。重试和积压恢复必须继续使用记录创建时的 transport，不得因后续环境配置变化重新解释目标。
- OTM-R5：集成消息发布配置支持一个或多个 transport ID；每个目标产生独立 Outbox 记录和独立重试状态。领域事件固定使用 `local-domain`。
- OTM-R6：relay 投递每条记录时必须按 `transportId` 精确匹配一个 channel；零个或多个匹配都必须显式失败。
- OTM-R7：Spring/JPA Outbox 实现位于 `j-store-outbox-spring`，保留至少一次、同聚合顺序、租约、fencing、重试、死信、审计、清理和可观测性能力。
- OTM-R8：本地集成消息投递作为 `local` transport 提供；Kafka、RabbitMQ 等实现只需实现稳定 transport SPI，不得修改业务模块或 Outbox 核心。
- OTM-R9：Broker publish 成功只表示 Broker 已确认接收。远端消费继续依赖稳定 message ID 幂等，不承诺 exactly-once。
- OTM-R10：现有 `jstore.messaging.mode=local|broker|hybrid` 配置被新的 transport 目标配置破坏性替代；本地默认行为保持不变。
- OTM-R11：既有 Outbox 数据通过新增 Flyway migration 回填 `transport_id`；不得修改既有 migration。
- OTM-R12：Outbox 运维 API、认证授权、事件/消息 payload、名称与版本保持不变。
- OTM-R13：每条 Outbox 记录必须持久化稳定 `orderingKey` 和同一
  `(transportId, orderingKey)` 内严格单调的 `sequenceNo`；序号必须在业务事务内由数据库原子分配，
  不得用墙钟时间或跨节点 ID 推断业务顺序。
- OTM-R14：relay 只允许领取同一 `(transportId, orderingKey)` 流中最早的未完成记录。
  重试或死信必须阻塞本流后继记录，但不得阻塞其它 transport 或其它 ordering stream。
- OTM-R15：死信必须持续阻塞其 ordering stream，且只能通过带操作者、原因和审计记录的重入队恢复；
  系统不得自动或人工静默跳过失败消息，否则下游无法区分授权丢弃与传输缺口。
- OTM-R16：发送给 Broker 的 envelope 必须包含 `transportId`、`orderingKey` 和 `sequenceNo`，使 adapter
  能按 ordering key 分区，并使远端消费者能够拒绝或暂存序号缺口。

## 质量目标

- 可维护性：消息契约、Outbox 核心、Spring/JPA 实现和具体 transport adapter 依赖单向、职责独立。
- 可靠性：切换部署配置不会改变已持久化记录的目标；失败不会静默降级或丢失消息。
- 顺序性：重试、进程崩溃和并发 relay 不得打破同一 transport、同一 ordering stream 的顺序；
  不同流保持故障隔离和并行能力。
- 可扩展性：新增 Kafka/RabbitMQ adapter 不需要修改核心路由器或业务模块。
- 可观测性：指标和健康信息能够按 transport ID 区分投递目标。

## 非范围

- 本次不引入 Kafka 或 RabbitMQ 客户端依赖，也不连接外部 Broker。
- 本次不实现 Broker 入站 consumer；但出站 envelope、transport SPI 和幂等端口必须为后续入站 adapter 保持中立边界。
- 不改变业务 Saga、聚合状态机和跨上下文消息内容。
