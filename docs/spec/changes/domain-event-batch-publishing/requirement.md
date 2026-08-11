# 领域事件批量入箱需求

## 目标

`DomainEventPublisher` 必须支持一次接受一个有序领域事件列表，使聚合待发布事件能够作为一个事务性批次写入 Outbox，同时保留现有单事件发布入口。

## 验收标准

- DEBP-R1：`DomainEventPublisher` 提供批量发布接口，输入使用保持顺序的 `List<DomainEvent>`；空列表为 no-op。
- DEBP-R2：现有 `publishEvent(event)` 调用保持源码兼容，并与单元素批次具有相同行为。
- DEBP-R3：`publishPendingEvents` 必须只发布调用开始时取得的稳定事件快照，并且仅在整个批次成功后确认该快照。
- DEBP-R4：批次中任一事件校验、序列化、顺序号分配或持久化失败时必须传播异常，由调用方事务回滚；不得部分确认聚合事件。
- DEBP-R5：每个事件仍生成一条独立 Outbox 记录，不改变 relay、重试、死信、幂等或至少一次投递语义。
- DEBP-R6：同一 `(transportId, orderingKey)` 批次内的事件必须按照输入顺序取得连续且严格单调的 `sequenceNo`；不同 stream 相互隔离。
- DEBP-R7：Outbox 仓储支持批量保存，PostgreSQL 顺序号实现应按唯一 stream 批量申请连续区间，避免同一 stream 每个事件一次数据库往返。

## 质量目标

- 兼容性：业务模块现有单事件发布代码和测试替身无需强制迁移。
- 可靠性：批量入箱与业务数据继续处于同一数据库事务。
- 顺序性：不得削弱 `outbox-transport-modularization` 已定义的 ordering stream 约束。
- 可维护性：事件校验/序列化、顺序号分配和持久化保持职责分离。

## 非范围

- 不批量调用本地领域事件 listener。
- 不改变事件名称、版本、payload、Outbox 表结构或投递协议。
- 不为集成消息发布端口增加批量 API。
