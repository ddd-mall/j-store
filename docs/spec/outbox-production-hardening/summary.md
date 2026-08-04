# Outbox 生产化加固交付摘要

## 已交付

- dead-letter requeue 重置 retry budget，可再次领取；查询分页且不暴露 payload。
- at-least-once 投递、同聚合严格有序、跨聚合并行；listener 外部副作用约束已文档化。
- PostgreSQL fencing token、租约有效期校验、处理前续租和每轮预取上限。
- 新增兼容 Flyway migration，保留五类既有状态记录，并建立死信审计表与索引。
- lag、过期锁、状态数、scheduler 成败/连续失败、死信及阈值告警指标与健康快照。
- `@RequireLogin` + 管理员 ID allowlist 的死信查询/requeue API，空 allowlist 默认拒绝，操作逐目标审计。

## 验证证据

- `:j-store-common-spring:test` 全套通过。
- `:j-store-boot:test --tests "com.jstore.outbox.*"` 通过。
- 真实 embedded PostgreSQL + Flyway 覆盖空库迁移和旧版本五状态升级保留。
- 双 worker 覆盖重复领取与同聚合后序屏障；过期 worker 的 renew/publish 被拒绝。
- 真实 Spring transaction manager 覆盖 listener 副作用、消费幂等记录和 PUBLISHED 的共同提交/回滚。
- 独立 evaluator 最终 verdict：PASS（converged）。

## 运行约束

- 语义为 at-least-once，不承诺外部系统 exactly-once。
- 单条 listener 执行时间必须小于租约；超长工作应写专用 outbox/任务，不在本地同步 listener 内执行。
- 上线前必须配置 `jstore.outbox.operations.admin-user-ids`；空值会拒绝全部运维请求。
