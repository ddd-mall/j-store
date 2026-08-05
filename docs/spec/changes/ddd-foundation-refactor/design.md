# DDD 基座破坏性重构设计

## 核心契约

```text
Identifier
  └─ Entity<ID>
       └─ AggregateRoot<ID>

RecordsDomainEvents
  └─ EventRecordingAggregateRoot<ID> : AggregateRoot<ID>

AggregateRepository<ID, A : AggregateRoot<ID>>
```

`AggregateRoot` 仅表达一致性边界。`EventRecordingAggregateRoot` 使用私有有序集合保存事件，并向外只暴露不可变快照和基于事件 ID 的确认操作。应用层扩展函数负责“快照 → 全部发布 → 确认”协议。

## 事件契约

`DomainEvent` 直接声明 `eventId`、`eventName`、`eventVersion`、`occurredAt`、`aggregateType` 和 `aggregateId`。事件类型通过 `@DomainEventType` 注册，但注解移动到领域事件包；注册器校验注解与事件实例元数据一致。

事件 ID 默认使用随机 UUID，在事件构造时固化。该选择优先保证多个同类同时间事件的唯一性；同一事件对象重试仍复用相同 ID。反序列化恢复原 ID，不重新生成。

## 仓储边界

原 `Repository` 替换为 `AggregateRepository`。当前被当作独立持久化边界的 `GoodsStyle`、`Inventory`、`ReservationRecord` 和 `SpuSnapshot` 将显式分类：具备独立一致性与生命周期者声明为聚合根；只读快照使用专用 `SnapshotRepository`，不伪装成聚合仓储。

## 框架边界

- common-core 不再反射加载 Spring AOP。
- listener 明确暴露所处理的事件类型，Spring 适配层负责代理类处理。
- 消费仓储更名为 `MessageConsumptionRepository`，同时服务领域事件与集成消息。
- 无幂等实现仅保留为测试显式工具，不参与生产自动装配 fallback。
- Outbox 类型继续位于独立包，并通过构造校验保护运行时状态。

## 迁移与回滚

本次直接迁移全部源码，不提供 `typealias`、弃用桥接或双写。事件名称、版本、payload 业务字段和数据库结构保持不变。若候选失败，应整体回滚本次源码变更，不进行部分 API 回滚。

## 验证

- common-core 单元测试覆盖 ID 相等、事件封装、成功确认和失败保留。
- common-spring 测试覆盖事件注册、序列化、幂等消费与 Outbox 状态校验。
- 所有受影响 domain/application/infrastructure/boot 模块编译并运行测试。
- 最终执行仓库质量门禁。
