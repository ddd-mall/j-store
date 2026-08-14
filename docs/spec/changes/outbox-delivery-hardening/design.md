# Outbox 投递闭环修复设计

## 1. 有预算的持续清理

`OutboxCleaner` 每次调度对每类数据执行至多 `cleanupMaxBatchesPerRun` 个小批次；当返回数量小于 `cleanupBatchSize` 时立即停止。清理任务改为 `cleanupIntervalMillis` 固定延迟调度，默认一分钟，因此达到预算的积压会在下一轮继续推进。

清理顺序为：

1. 删除超过 `retentionDays` 的 PUBLISHED Outbox；
2. 删除超过 `consumptionRetentionDays` 的消费幂等明细；
3. 删除超过消费保留期、且不存在非 PUBLISHED Outbox 的空闲顺序游标。

每个 repository 调用保持独立短事务，避免把整个清理轮次包成大事务。

`OutboxCleaner` 强制依赖 `MessageConsumptionRetentionRepository`。项目未上线，因此不保留 nullable/no-op 兼容路径；装配不完整时启动失败。

## 2. executor 拒绝与健康状态

`OutboxRelayCoordinator.scheduleIfNeeded()` 捕获 executor 拒绝后先释放 single-flight 门禁，再通过现有 `OutboxRelayExecutionObserver` 记录失败。提交后 signal 仍吞掉异常，因此业务提交结果不受影响；周期调度调用同一路径，使连续拒绝能够推动健康状态从 DEGRADED 进入 FAILED。

## 3. 提交后领域事件确认

`DomainEventPublisher` 新增框架无关的 `afterPublicationCommitted` 钩子，默认立即执行，保持测试 publisher 和非事务实现的既有行为。

`publishPendingEvents` 在 publisher 接受稳定快照后，把事件 ID 的确认动作交给该钩子。Spring Outbox 实现注册 `TransactionSynchronization.afterCommit`；回滚时不执行确认。确认异常在数据库已经提交后只记录日志，不能把已提交事务伪装成失败。

当前应用用例对一个聚合在单个事务内只发布一次。若未来需要同一事务多次发布同一聚合，应引入 staged-event 状态，而不是重复写入相同 eventId。

## 4. 消费状态保留

新增框架无关的 `MessageConsumptionRetentionRepository` 端口，由现有 JPA repository 实现：

- 幂等明细按 `consumed_at` 分批删除；其保留期不得短于 Outbox 保留期。
- 只回收内置 local integration consumer 的顺序游标，并要求同一 `transport_id + ordering_key` 不存在非 PUBLISHED Outbox。外部消费者没有可查询的本地生产水位，因此保留其游标，避免把新消费者与已回收消费者混淆。
- 普通新消费者的顺序游标从 `0` 开始，继续严格执行 `last + 1` 校验。内置 local consumer 缺少游标时，仅在本地 Outbox 中不存在未完成前序消息的情况下以 `delivery.sequenceNo - 1` 恢复；否则同样从 `0` 开始并报告 sequence gap。

Retention 索引直接写入创建对应表的当前 Flyway migration。开发数据库必须重建，不新增兼容迁移或 checksum 修复逻辑。

## 5. 验证策略

- 单元测试：多批清理、预算停止、空批停止、executor 拒绝失败观测、默认即时确认。
- Spring 事务测试：commit 后确认、rollback 保留、提交后确认异常隔离。
- PostgreSQL 测试：消费明细批量删除；活跃流不可回收；空闲流回收后从当前序号恢复。
- 回归测试：普通新消费者不能从序号 2 开始，local consumer 不能越过未完成前序，外部消费者游标不会在缺少生产水位时回收。
- 公共 ID 测试：当前时间生成的 Snowflake ID 保持正数，并验证 worker/datacenter 位布局。
- 装配与配置测试：默认值、非法保留期和新的 fixed-delay 调度属性。
