# Outbox 低延迟 Relay 设计

## 核心决策

1. Outbox 表继续作为唯一可靠工作来源；唤醒信号只降低发现延迟，不承载业务数据。
2. `TransactionAwareOutboxRelaySignal` 在 publisher 保存成功后注册事务同步，并仅在 `afterCommit` 请求 drain。
3. `OutboxRelayCoordinator` 使用单飞门禁和 pending 标记合并信号。高并发 signal 只调度固定数量的后台工作，不为每条记录创建任务。
4. `OutboxPublisher.drainAndPublish()` 连续执行现有单批投递，最多执行 `maxBatchesPerDrain` 个非空批次；每批重新 claim，因而能看到上一批 handler 新提交的下一跳消息。
5. 达到预算时 coordinator 重新安排后续 drain。空批次立即结束，避免忙等。
6. `OutboxScheduler` 不再直接调用 publisher，而是请求同一个 coordinator drain；因此恢复轮询和低延迟路径共享并发边界。
7. scheduler 健康状态在 coordinator 实际执行 drain 成功或失败后更新，而不是在唤醒请求入队时更新，避免异步数据库故障被误报为健康。

## 执行流程

```text
business transaction
  save aggregate + save outbox
  register afterCommit callback
             |
             v commit only
OutboxRelayCoordinator.requestDrain()
  coalesce pending signals
  submit at most one local worker
             |
             v
OutboxPublisher.drainAndPublish()
  claim batch -> delivery transaction -> mark result
  claim next batch, up to configured budget
```

周期调度只执行 `coordinator.requestDrain()`。若即时 executor 拒绝任务或进程在提交后崩溃，Outbox 记录保持 ready，后续周期扫描仍可恢复。

## 并发与背压

- Spring 装配提供单线程、极小队列的专用 Relay executor。
- coordinator 的 `running`/`pending` 状态负责 signal 合并和结束竞态处理。
- publisher 每批仍最多 claim `min(batchSize, maxInFlightPerPoll)` 条。
- `maxBatchesPerDrain` 给单次占用设置上限；达到上限后通过重新调度让 executor 获得任务边界。
- 多实例之间继续依赖数据库 claim、lease 和 fencing，而不是共享进程内门禁。

## 失败语义

- 业务事务回滚：`afterCommit` 不执行，不产生即时 signal；Outbox 写入也随事务回滚。
- 提交后唤醒失败（包括 executor 拒绝）：记录告警且不向已提交业务事务传播；周期调度后续恢复。
- 单条投递失败：保持现有 FAILED/DEAD_LETTER 处理，不中断同批其它独立记录。
- drain 顶层异常：记录 relay/scheduler 失败健康状态并释放门禁；ready 记录仍在数据库，周期调度可恢复。
- signal 与 worker 结束竞态：worker 释放门禁后再次检查 pending，必要时重新取得门禁并调度。

## 验证策略

- 纯单元测试验证 coordinator 合并、非重叠、重调度和拒绝恢复。
- publisher 单元测试验证多批排空、空批停止和预算边界。
- Spring 事务测试验证 commit/rollback 信号边界。
- 自动配置测试验证专用 executor、publisher signal 注入和 scheduler coordinator 装配。
- 运行现有 PostgreSQL 集成测试确认 claim、fencing、顺序与事务语义未回归。
