# 事件投递架构重构需求

## 背景与目标

当前 `DomainEventBus`、Outbox relay 和 Spring 本地监听器共同形成可靠的模块化单体事件管道，但其完成语义绑定本进程 listener，无法直接承载独立部署服务之间的消息投递。

本次重构允许破坏内部兼容性，目标是在不削弱既有 Outbox 可靠性的前提下，分离本地领域事件和跨边界集成消息，使单体部署、Broker 部署及迁移期混合部署共享稳定的领域模型。

本规格是 `outbox-production-hardening` 的增量规格；发生冲突时，以本规格对投递边界和 `PUBLISHED` 语义的修订为准。

## 行为需求

- EDA-R1：领域事件必须通过只负责本进程同步分发的 `LocalDomainEventBus` 交付；该接口不得包含 Broker、topic 或远程订阅语义。
- EDA-R2：跨限界上下文消息必须使用独立的 `IntegrationMessage` 契约，并区分事实广播 `IntegrationEvent` 与单接收方意图 `IntegrationCommand`。
- EDA-R3：集成消息必须携带稳定 `messageId`、名称、版本、发生时间、分区键、相关 ID、因果 ID和可选租户 ID；外部 payload 不得依赖某个上下文的聚合、实体或值对象类型。
- EDA-R4：`IntegrationMessagePublisher` 必须在调用方事务内把消息及目标写入 Outbox，不得直接调用 Broker。
- EDA-R5：Outbox relay 必须按记录的消息类别和目标选择唯一投递通道；没有通道或多个通道匹配时必须失败并进入既有重试/死信流程。
- EDA-R6：系统必须支持 `local`、`broker`、`hybrid` 三种集成消息模式。`local` 写入本地目标，`broker` 写入 Broker 目标，`hybrid` 为两个目标分别写入可独立重试的记录。
- EDA-R7：本地目标必须同步调用 `IntegrationMessageHandler`，并复用 listener/handler ID + message ID 的数据库幂等约束。
- EDA-R8：Broker 目标的成功定义为 Transport 已确认接收消息；不表示远端消费者已完成。发送成功但 Outbox 状态提交失败时允许重复发送，远端必须幂等。
- EDA-R9：选择 `broker` 或 `hybrid` 且未提供 Broker Transport 时，应用必须在启动阶段快速失败，不能静默降级到本地。
- EDA-R10：既有至少一次、同聚合顺序、fencing token、租约、重试、死信、审计和可观测性能力必须保持。
- EDA-R11：订单、库存、支付和履约之间要求对方执行动作的消息必须建模为集成命令；对已完成事实的通知必须建模为集成事件。跨上下文 handler 不得再监听其他上下文的领域事件。
- EDA-R12：单体默认使用 `local`，现有核心业务链路在默认配置下行为不得回归。

## 数据与兼容性

- Outbox 记录必须新增消息类别、投递目标、目标地址、分区键、相关 ID、因果 ID、租户 ID。
- 项目尚未上线，允许通过新增迁移和内部 API 重命名完成破坏性重构；不得删除现有可靠性字段或运维能力。
- 同一个集成消息在 hybrid 模式下可以存在多条目标不同、`messageId` 相同的 Outbox 记录。

## 验收与质量目标

- 单元测试验证模式到目标的规划、唯一通道路由、消息元数据校验及 handler 幂等。
- Spring 装配测试验证默认 local、缺失 Broker Transport 的 fail-fast 和显式 Transport 注入。
- PostgreSQL 集成测试验证新增字段持久化及既有 claim/fencing/顺序行为。
- Boot 测试验证当前跨上下文核心链路已使用集成消息适配器装配。
- 交付前运行 `:j-store-common-core:test`、`:j-store-messaging-core:test`、`:j-store-outbox-core:test`、`:j-store-messaging-local-spring:test`、`:j-store-outbox-spring:test`、受影响业务模块测试、`:j-store-boot:test` 和 `scripts/quality-gate.sh`。

## 非范围

- 本次不绑定 Kafka、RabbitMQ、Pulsar 或云厂商产品；Broker Transport 作为独立适配器 SPI 交付。
- 本次不建设完整 Saga/Process Manager 持久化状态机；消息相关/因果元数据为后续流程管理提供契约基础。
- 不承诺跨数据库 exactly-once。
