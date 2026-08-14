# Outbox 投递闭环修复总结

## 已交付

- Outbox 清理由每日单批改为默认每分钟执行，每类数据单轮最多 10 批、每批 500 行；多实例使用 `SKIP LOCKED` 减少重复等待。
- executor 拒绝现在进入既有 scheduler failure 计数和健康状态，同时不影响已经提交的业务事务。
- `DomainEventPublisher` 提供框架无关的提交确认钩子；Spring Outbox 只在事务 `afterCommit` 清除聚合事件，回滚保持待发布队列。
- 消费幂等明细默认保留 14 天，并要求不短于 Outbox 保留期；旧明细和安全空闲的顺序游标按批清理。
- 顺序游标回收后以当前消息的前序号重建，已有游标继续执行严格 `last + 1` 校验。
- 顺序游标回收限制为具备本地生产水位的内置 local consumer；普通新消费者仍从序号 1 开始，外部消费者游标在无法证明安全恢复时保留。
- Retention 索引直接归入创建对应表的当前 Flyway migration，避免清理查询依赖全表排序；不新增上线兼容迁移。
- 修正 Snowflake 的 worker/datacenter/timestamp 位移，避免当前时间生成负数 ID 阻断 Brand 等正数领域 ID 创建。

## 验证证据

- 聚焦测试覆盖清理预算、多批推进、executor 拒绝、commit/rollback 确认、配置约束。
- 嵌入式 PostgreSQL 测试覆盖消费明细批量删除、活跃流保护、空闲流恢复以及并发友好的删除 SQL。
- 审查回归测试覆盖新消费者乱序首条、local 未完成前序、外部游标保留和 Snowflake 正数/位布局。
- `./gradlew :j-store-outbox-spring:test :j-store-boot:test`：通过。
- `./scripts/quality-gate.sh`：全部通过，包括全模块回归和 53 个发布 JAR 的许可证验证。

## 运行边界

- 该版本修改了既有 Flyway migration，升级本地分支后需要重建开发数据库；不提供旧 checksum 或旧 `cleanup-cron` 兼容。
- 默认清理能力上限约为每类每分钟 5,000 行；部署前仍应按真实消息增速调整 `cleanupBatchSize`、`cleanupMaxBatchesPerRun` 和 `cleanupIntervalMillis`。
- 超过 `consumptionRetentionDays` 的任意历史重放不再承诺去重，这是显式的保留期边界。
- 本变更不替代生产等价环境中的完整 Checkout 延迟与积压恢复压测。
