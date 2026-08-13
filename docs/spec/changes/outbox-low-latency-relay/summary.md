# Outbox 低延迟 Relay 交付总结

## 已交付行为

- 领域事件和集成消息写入 Outbox 后，在所属事务提交成功时请求即时 drain；事务回滚不触发。
- 同一事务的多次发布合并为一个 `afterCommit` 回调；跨事务并发唤醒再由 coordinator 合并。
- 单实例使用一个专用单线程 Relay executor，周期恢复扫描和即时唤醒共享同一个单飞门禁。
- Relay 每次最多连续处理 `maxBatchesPerDrain` 个非空批次，默认 10；预算耗尽后重新排队并让出任务边界。
- 连续 claim 能处理前一批 handler 新提交的下一跳消息，避免每一跳等待 5 秒恢复周期。
- Outbox 表继续作为可靠事实；即时调度失败或进程崩溃后，现有周期轮询仍负责恢复。
- 提交后的唤醒异常会被隔离并记录，不会让已经提交成功的业务请求返回伪失败。
- 健康状态由后台 drain 的实际成功或失败更新，不把“成功入队”误认为“成功投递”。

## 验收证据

- `TransactionAwareOutboxRelaySignalTest`：提交、回滚、同事务信号合并、唤醒异常隔离和缺失事务同步边界。
- `OutboxRelayCoordinatorTest`：10,000 次并发 signal 合并、单飞执行、结束竞态、预算续跑、executor 拒绝恢复和真实执行健康结果。
- `OutboxPublisherTest`：多批连续排空、空批停止、批次预算和配置校验。
- publisher 与自动配置测试：领域/集成 publisher 信号接入、专用 executor/coordinator/signal Spring 装配。
- `./gradlew :j-store-outbox-spring:test`：通过，包含现有嵌入式 PostgreSQL claim、顺序、租约、fencing 和事务回归。
- `./scripts/quality-gate.sh`：通过全部治理、规格、格式、许可证、全模块测试和发布产物检查。

## 兼容性与运维

- 无数据库迁移、消息契约或公开 API 变化。
- `jstore.outbox.polling-interval` 保留，现作为恢复扫描周期。
- 新增 `jstore.outbox.max-batches-per-drain`，必须为正整数，默认 10。
- 当前每实例固定一个 Relay worker；未来若需要增加跨 ordering key 并行度，应先用生产指标证明单 worker 吞吐不足，并维持全局数据库并发上限。

## 残余风险

- 本次验证了功能、并发不变量和数据库回归，但未在生产等价负载下测量订单到支付准备的 P95/P99。上线前应通过压测校准 batch size、drain 预算、连接池容量和积压告警阈值。
