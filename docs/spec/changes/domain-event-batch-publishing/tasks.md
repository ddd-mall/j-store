# 领域事件批量入箱任务

- [x] T1：以 common-core 测试锁定单次批量调用、稳定快照、成功确认和失败保留行为。
- [x] T2：扩展 `DomainEventPublisher` 并让 `publishPendingEvents` 使用批量接口。
- [x] T3：扩展 Outbox 仓储批量保存端口及 Spring/JPA 实现。
- [x] T4：扩展 stream sequence 批量端口及 PostgreSQL 连续区间实现。
- [x] T5：实现 `OutboxEventPublisher` 批量校验、构造和保存。
- [x] T6：补充单元测试及 PostgreSQL 顺序、并发、回滚集成测试。
- [x] T7：运行相关模块、全量测试和质量门禁并记录证据；完整结果见 `summary.md`。
