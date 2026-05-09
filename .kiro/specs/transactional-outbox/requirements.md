# 需求文档：Transactional Outbox（事务性发件箱）

## 简介

当前 j-store 系统使用 Spring `ApplicationEventPublisher` 进行领域事件发布，属于纯内存操作。业务数据库写入与事件发布不在同一个原子操作中，存在事件丢失和不一致的风险。本特性引入 Transactional Outbox 模式，将领域事件与业务数据在同一个数据库事务中持久化到 outbox 表，再由独立的发布进程异步投递事件，从而保证事件投递的可靠性和最终一致性。

## 术语表

- **Outbox_Table**: 数据库中用于持久化待发布领域事件的表，与业务数据在同一事务中写入
- **Outbox_Entry**: Outbox_Table 中的一条记录，包含事件类型、事件载荷、状态、创建时间、重试次数等字段
- **Outbox_Publisher**: 独立的后台进程，负责轮询 Outbox_Table 中未发布的事件并投递到 Domain_Event_Bus
- **Domain_Event_Bus**: 现有的领域事件总线（`DomainEventBus` 接口），负责将事件分发给已注册的监听器
- **Domain_Event_Publisher**: 现有的领域事件发布者（`DomainEventPublisher` 接口），应用服务通过该接口发布事件
- **Outbox_Event_Publisher**: `DomainEventPublisher` 的新实现，将事件写入 Outbox_Table 而非直接投递到事件总线
- **Aggregate_Root**: 聚合根（`AgreeGate` 接口），通过 `publishEvent()` 收集领域事件到内部队列
- **Event_Serializer**: 负责将 `DomainEvent` 对象序列化为 JSON 字符串以持久化到 Outbox_Table，以及反序列化回 `DomainEvent` 对象
- **Polling_Interval**: Outbox_Publisher 轮询 Outbox_Table 的时间间隔
- **Max_Retry_Count**: 单条 Outbox_Entry 投递失败后的最大重试次数
- **Dead_Letter**: 超过 Max_Retry_Count 仍投递失败的 Outbox_Entry，标记为死信状态，需人工介入

## 需求

### 需求 1：事件持久化到 Outbox 表

**用户故事：** 作为系统运维人员，我希望领域事件与业务数据在同一个数据库事务中持久化，以便在系统宕机时不会丢失事件。

#### 验收标准

1. WHEN Aggregate_Root 产生领域事件且应用服务调用 Domain_Event_Publisher 发布事件时，THE Outbox_Event_Publisher SHALL 将事件序列化并写入 Outbox_Table，状态为 PENDING
2. THE Outbox_Event_Publisher SHALL 在与业务数据相同的数据库事务中写入 Outbox_Entry，确保业务数据和事件记录的原子性
3. WHEN 业务事务回滚时，THE Outbox_Event_Publisher SHALL 确保对应的 Outbox_Entry 也被回滚，不会出现孤立的事件记录
4. THE Outbox_Entry SHALL 包含以下字段：唯一事件 ID、事件类型（全限定类名）、事件载荷（JSON）、聚合根类型、聚合根 ID、状态（PENDING/PUBLISHED/FAILED/DEAD_LETTER）、创建时间、更新时间、重试次数
5. THE Event_Serializer SHALL 使用 Jackson 将 DomainEvent 对象序列化为 JSON 字符串，并在 JSON 中保留事件类型信息以支持反序列化

### 需求 2：异步事件投递

**用户故事：** 作为系统运维人员，我希望有独立的后台进程将 outbox 中的事件投递到事件总线，以便事件消费者能够可靠地接收到所有领域事件。

#### 验收标准

1. THE Outbox_Publisher SHALL 按照可配置的 Polling_Interval 定期轮询 Outbox_Table 中状态为 PENDING 的 Outbox_Entry
2. WHEN Outbox_Publisher 获取到 PENDING 状态的 Outbox_Entry 时，THE Outbox_Publisher SHALL 将事件反序列化为 DomainEvent 对象并投递到 Domain_Event_Bus
3. WHEN 事件成功投递到 Domain_Event_Bus 后，THE Outbox_Publisher SHALL 将对应 Outbox_Entry 的状态更新为 PUBLISHED
4. THE Outbox_Publisher SHALL 按照 Outbox_Entry 的创建时间升序投递事件，保证同一聚合根的事件顺序
5. THE Outbox_Publisher SHALL 每次轮询获取的 Outbox_Entry 数量上限为可配置的批次大小（batch size）

### 需求 3：重试与死信处理

**用户故事：** 作为系统运维人员，我希望投递失败的事件能够自动重试，超过重试上限的事件进入死信状态，以便我能够及时发现和处理异常。

#### 验收标准

1. WHEN Outbox_Publisher 投递事件到 Domain_Event_Bus 失败时，THE Outbox_Publisher SHALL 将对应 Outbox_Entry 的状态更新为 FAILED 并将重试次数加 1
2. WHEN Outbox_Publisher 轮询时，THE Outbox_Publisher SHALL 同时获取状态为 FAILED 且重试次数小于 Max_Retry_Count 的 Outbox_Entry 进行重投递
3. WHEN Outbox_Entry 的重试次数达到 Max_Retry_Count 时，THE Outbox_Publisher SHALL 将该 Outbox_Entry 的状态更新为 DEAD_LETTER
4. THE Max_Retry_Count SHALL 通过 Spring 配置属性进行配置，默认值为 5
5. IF Outbox_Publisher 在投递过程中自身发生异常，THEN THE Outbox_Publisher SHALL 记录错误日志并在下一个 Polling_Interval 继续轮询，不中断服务

### 需求 4：事件序列化与反序列化

**用户故事：** 作为开发人员，我希望领域事件能够可靠地序列化和反序列化，以便事件在持久化和恢复时保持数据完整性。

#### 验收标准

1. THE Event_Serializer SHALL 将任意实现 DomainEvent 接口的对象序列化为 JSON 字符串
2. THE Event_Serializer SHALL 将 JSON 字符串结合事件类型信息反序列化为原始的 DomainEvent 对象
3. FOR ALL 有效的 DomainEvent 对象，序列化后再反序列化 SHALL 产生与原始对象等价的对象（round-trip 属性）
4. IF Event_Serializer 遇到无法识别的事件类型，THEN THE Event_Serializer SHALL 抛出包含事件类型信息的描述性异常
5. IF Event_Serializer 遇到格式错误的 JSON 载荷，THEN THE Event_Serializer SHALL 抛出包含载荷摘要信息的描述性异常

### 需求 5：与现有事件基础设施集成

**用户故事：** 作为开发人员，我希望 Transactional Outbox 能够无缝集成到现有的事件框架中，以便现有的应用服务代码无需修改即可获得可靠事件投递能力。

#### 验收标准

1. THE Outbox_Event_Publisher SHALL 实现现有的 DomainEventPublisher 接口，作为 SpringDomainEventPublisher 的替代实现
2. WHEN 启用 Transactional Outbox 功能时，THE 系统 SHALL 将 Outbox_Event_Publisher 注册为 DomainEventPublisher 的 Spring Bean，替换原有的 SpringDomainEventPublisher
3. THE Outbox_Publisher SHALL 通过现有的 Domain_Event_Bus 投递事件，确保已注册的 DomainEventListener 和 Spring @EventListener（包括事件翻译器）能够正常接收事件
4. THE 现有应用服务（如 OrderService）中 `order.getDomainEvent().forEach { domainEventPublisher.publishEvent(it) }` 的调用模式 SHALL 无需修改即可工作
5. WHERE 需要禁用 Transactional Outbox 功能时，THE 系统 SHALL 通过 Spring 配置属性回退到原有的 SpringDomainEventPublisher 实现

### 需求 6：Outbox 表清理

**用户故事：** 作为系统运维人员，我希望已发布的事件记录能够被定期清理，以便 Outbox_Table 不会无限增长影响数据库性能。

#### 验收标准

1. THE 系统 SHALL 提供定时任务，定期删除状态为 PUBLISHED 且创建时间超过可配置保留天数的 Outbox_Entry
2. THE 保留天数 SHALL 通过 Spring 配置属性进行配置，默认值为 7 天
3. THE 清理任务 SHALL 每次删除的记录数量上限为可配置的批次大小，避免长事务锁表
4. THE 清理任务 SHALL 不删除状态为 DEAD_LETTER 的 Outbox_Entry，死信记录需人工确认后手动处理

### 需求 7：可观测性

**用户故事：** 作为系统运维人员，我希望能够监控 Outbox 的运行状态，以便及时发现事件积压或投递异常。

#### 验收标准

1. THE Outbox_Publisher SHALL 在每次轮询投递完成后记录 INFO 级别日志，包含本次投递的事件数量和失败数量
2. WHEN Outbox_Entry 状态变更为 DEAD_LETTER 时，THE Outbox_Publisher SHALL 记录 WARN 级别日志，包含事件 ID、事件类型和重试次数
3. WHEN Outbox_Publisher 投递事件失败时，THE Outbox_Publisher SHALL 记录 ERROR 级别日志，包含事件 ID、事件类型和异常信息

### 需求 8：模块归属与架构合规

**用户故事：** 作为开发人员，我希望 Transactional Outbox 的代码按照项目 DDD 架构规范放置在正确的模块中，以便保持架构一致性。

#### 验收标准

1. THE Outbox_Entry 的领域接口和 Event_Serializer 接口 SHALL 放置在 j-store-common-core 模块中，不依赖任何 Spring 或 JPA 框架
2. THE Outbox_Event_Publisher、Outbox_Publisher、JPA 持久化实现和 Spring 调度配置 SHALL 放置在 j-store-common-spring 模块中
3. THE j-store-common-core 模块中的 Outbox 相关代码 SHALL 不引入任何新的框架依赖
4. THE Outbox_Table 的 DDL 脚本 SHALL 作为数据库迁移脚本提供，放置在 j-store-boot 模块的资源目录中
