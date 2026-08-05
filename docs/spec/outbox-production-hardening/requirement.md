# Outbox 生产化加固需求

## 目标

将现有 transactional outbox 从开发可用提升为可在多实例生产环境中运行、恢复、观测和受控运维的可靠事件基础设施。

## 投递语义

- R1：系统必须提供 **at-least-once** 投递，不承诺 exactly-once；任何崩溃窗口都不得静默丢失已提交事件。
- R2：同一 `aggregateType + aggregateId` 的事件必须按创建顺序串行投递；前序事件未发布成功时，后序事件不得越过。不同聚合允许并行。
- R3：listener 的数据库副作用及消费幂等记录必须加入同一 relay 事务。不可回滚的外部副作用不得直接执行，除非下游以 `eventId` 提供可验证幂等，或 listener 在本地事务内写入专用 outbox。

## 功能验收

- R4：死信重新入队后必须获得新的重试预算，并能在下一次到期轮询中被领取；不存在的、非死信或并发已变更记录不得被误更新。
- R5：每次领取必须产生单调递增且不可复用的 fencing token；只有当前 token 持有者才能续租、标记成功或标记失败。
- R6：relay 必须限制每轮预取数量，并保证配置值为正；worker 在处理前必须续租，且失去租约或 fencing token 的 worker 不得提交状态结果。单条处理耗时不得超过配置租约，超长 listener 后续可扩展 heartbeat。
- R7：死信必须支持分页/受限查询和批量重新入队；每次操作必须记录操作者、动作、时间、目标事件、原因和结果。
- R8：运维查询和重入队入口必须要求登录，并仅允许配置的 Outbox 运维管理员；未登录返回 401，无权限返回 403。

## 数据库与兼容性

- R9：必须以新增 Flyway migration 演进既有表，不修改已发布 baseline 的语义；迁移须兼容已有记录并可重复通过全量 migration 验证。
- R10：升级期间不得删除已有 PENDING/FAILED/IN_PROGRESS/DEAD_LETTER/PUBLISHED 记录；新增字段必须有安全默认值或允许空值后受状态规则约束。

## 可观测性与运行保障

- R11：必须提供 oldest-ready-event lag、过期锁数量、各状态数量、成功/失败/死信计数、scheduler 最近成功时间和连续失败状态。
- R12：达到可配置 lag、过期锁或死信阈值时必须产生 Micrometer 告警状态/计数；无 MeterRegistry 时不得影响投递。
- R13：调度器异常必须被记录，且健康快照能区分未运行、健康、降级和失败。

## 验证要求

- R14：真实 PostgreSQL + Flyway 测试必须覆盖全量迁移、并发 worker 不重复领取、同聚合不越序、worker 崩溃后的过期租约恢复、旧 fencing token 被拒绝。
- R15：事务端到端测试必须覆盖业务数据与 outbox 同提交/同回滚，以及 listener 失败时副作用、消费记录和 PUBLISHED 状态共同回滚。
- R16：权限测试必须覆盖 401、403、管理员成功查询/重入队和审计落库。

## 非范围

- 本次不引入 Kafka/RabbitMQ/Debezium。
- 不承诺跨数据库或外部 HTTP 副作用的 exactly-once。
- 不建设前端管理页面；“运维入口”交付为受保护的管理 API 和指标/健康接口。
- 不对既有业务事件 payload 做大规模重构。
