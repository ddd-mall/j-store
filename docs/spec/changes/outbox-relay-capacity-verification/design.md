# Outbox Relay 容量验证设计

## 测量边界

容量测试为每条消息执行独立业务事务：

1. 将 `PENDING` Outbox 记录写入 PostgreSQL。
2. 在事务同步的 `afterCommit` 回调中记录提交时间。
3. 由同一事务注册的 relay signal 请求即时 drain。
4. 真实 `OutboxRelayCoordinator` 和 `OutboxPublisher` claim 并投递记录。
5. 测试 delivery channel 记录收到消息的时间。

单条延迟定义为“delivery channel 收到消息时间 - 事务 `afterCommit` 时间”。延迟与总耗时均使用 JVM 单调时钟计算，避免系统校时影响；`startedAt`、`completedAt` 仅用于报告时间定位。总吞吐的计时范围从并发生产者释放开始，到所有消息被 delivery channel 接收为止，因此同时包含写入、提交、claim 和投递成本。

这不是完整 Checkout 业务延迟：它不包含 Inventory、Payment、Broker、网络、消费者业务事务或状态查询。

## 执行入口

独立任务：

```bash
./gradlew :j-store-outbox-spring:outboxRelayCapacityTest \
  -PoutboxCapacity.messageCount=1000 \
  -PoutboxCapacity.producerConcurrency=8 \
  -PoutboxCapacity.batchSize=100 \
  -PoutboxCapacity.maxBatchesPerDrain=10 \
  -PoutboxCapacity.timeoutSeconds=60
```

默认报告位置为：

```text
j-store-outbox-spring/build/reports/outbox-relay-capacity/result.json
```

可通过 `-PoutboxCapacity.output=<path>` 覆盖。常规 `test` 任务排除 JUnit `capacity` tag，容量任务只包含该 tag 和容量测试类。

## 失败语义

- 任一生产事务失败，测试失败。
- 超时前没有全部投递，测试失败并报告未投递数量。
- 投递数量与输入不一致，测试失败。
- 完成后存在非预期状态，测试失败。

## 解释边界

嵌入式数据库、本机 CPU、JVM 预热、连接池和后台负载都会影响数值。报告用于验证入口、发现回归和形成调参假设；迭代计划中的 P95/P99 退出门禁仍需真实环境或等效容量环境的完整 Checkout 链路证据。
