# Outbox Relay 容量验证需求

## 背景

`outbox-low-latency-relay` 已完成事务提交后即时唤醒、单飞执行与有界连续排空，但其交付总结仍保留一项风险：尚无可重复执行的 PostgreSQL 延迟与吞吐测量入口，无法用证据校准 batch size、drain 预算和生产等价环境验证方案。

## 目标

提供一个与常规单元测试隔离、可显式运行的本地容量验证入口，测量真实事务提交到本地 delivery channel 收到消息之间的延迟，并输出机器可读报告。

## 验收标准

1. 验证必须使用 PostgreSQL、真实 Outbox 持久化实现、事务提交后 signal、relay coordinator 和 publisher，不得用 mock 替代被测链路。
2. 操作者可以配置消息数、生产并发度、批大小、单次 drain 最大批次数和超时。
3. 报告至少包含实际投递数量、总耗时、吞吐，以及 min、P50、P95、P99、max 提交后投递延迟。
4. 验证只有在全部消息完成投递、全部 Outbox 记录进入 `PUBLISHED` 且没有 `FAILED` 或 `DEAD_LETTER` 时通过。
5. 常规 `test` 任务不得隐式运行容量验证；必须通过独立 Gradle 任务显式触发。
6. 本地嵌入式 PostgreSQL 结果只作为工具基线，不得被表述为生产 SLO 或 `OrderCreated -> CASHIER_READY` 业务链路的退出门禁证据。

## 质量目标

- 可重复：同一入口通过 Gradle 属性接收参数，并将 JSON 报告写入稳定位置。
- 低干扰：不引入生产代码、运行配置、数据库迁移或外部服务依赖。
- 可演进：后续生产等价压测可以复用相同指标口径，但必须另行记录环境和业务链路证据。
