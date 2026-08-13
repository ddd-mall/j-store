# Outbox Relay 容量验证交付总结

## 已交付能力

- 新增独立 Gradle 容量验证任务，常规测试默认排除该测试。
- 支持配置消息数、生产并发度、batch size、drain 批次预算、超时和报告路径。
- 使用嵌入式 PostgreSQL、真实事务、Outbox 持久化、提交后 signal、coordinator 和 publisher。
- 输出消息完成度、总耗时、吞吐及 min/P50/P95/P99/max 提交后投递延迟。

## 本地基线证据

参数：200 条消息、4 个生产线程、batch size 50、每次 drain 最多 4 批、超时 60 秒。

- 200/200 成功投递，全部进入 `PUBLISHED`，没有 `FAILED` 或 `DEAD_LETTER`。
- 总耗时 193 ms，吞吐约 1036.04 条/秒。
- P50 86.975 ms、P95 114.254 ms、P99 117.946 ms、max 118.345 ms。

该数据来自开发机单次嵌入式 PostgreSQL 执行，只证明验证入口可运行，不构成生产 SLO、趋势基线或完整 Checkout 链路退出门禁。

## 验证证据

- `./gradlew :j-store-outbox-spring:test`：通过；常规任务未执行 `capacity` tag。
- `./gradlew :j-store-outbox-spring:outboxRelayCapacityTest ...`：通过；1 个容量测试、0 失败。
- `./scripts/quality-gate.sh`：通过治理与规格检查、格式、依赖许可、全模块回归和 53 个发布 JAR 的许可证验证。

## 待完成工作

- 在生产等价环境覆盖 Inventory、Payment、Broker/网络和消费者事务，执行多轮预热后的完整链路压测。
- 基于真实环境证据校准 batch size、drain 预算、连接池容量和积压告警阈值。
