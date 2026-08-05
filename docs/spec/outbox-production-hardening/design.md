# Outbox 生产化加固设计

## 核心决策

1. 保持数据库 polling publisher，交付语义为 at-least-once。
2. 使用 `lock_token BIGINT` fencing。claim 原子递增 token；renew、publish、fail 均按 `id + worker + token + IN_PROGRESS` 条件更新。
3. 同聚合顺序通过 claim 查询中的前序屏障实现：候选记录不存在同聚合、创建顺序更早且状态非 PUBLISHED 的记录。以 `(created_at, id)` 作为确定性顺序。
4. 每轮最多 claim `min(batchSize, maxInFlightPerPoll)` 条；默认小批预取。处理单条前续租，必要时可由配置的 heartbeat 周期继续扩展。
5. requeue 重置 `retry_count=0`，清除租约/错误并记录审计。审计与状态变更同事务。
6. 运维 API 放在 boot 接口层；common-spring 提供服务、查询模型和持久化。采用 `@RequireLogin` + 配置化管理员 user ID allowlist，避免引入项目尚不存在的 RBAC 框架。

## 数据变化

- `outbox_entry.lock_token BIGINT NOT NULL DEFAULT 0`。
- 新建 `outbox_dead_letter_audit`，记录 entry、eventId、operatorId、action、reason、result、createdAt。
- 增加支持同聚合前序判断、过期锁和 dead-letter 查询的索引。

迁移作为新增版本文件执行；baseline 保持不动。

## 状态与事务

- claim：PENDING/到期 FAILED/过期 IN_PROGRESS → IN_PROGRESS，同时 attempt +1、token +1、设置租约。
- renew：仅当前 worker/token 可延长；返回 false 表示失去所有权。
- success/failure：仅当前 worker/token 可提交。listener 调用及 success 标记位于同一事务。
- crash：记录保持 IN_PROGRESS；租约到期后新 worker 以更高 token 恢复。
- requeue：仅 DEAD_LETTER → FAILED，retryCount 归零，写审计；并发不匹配时返回未更新结果并仍写可追踪审计结果。

## 可观测性

仓储提供 oldest ready 时间和 expired lock count。监控记录 relay 成功/失败、死信、重入队、scheduler 成功/失败时间；健康快照按阈值计算状态。阈值只影响告警，不阻塞投递。

## 安全

- 查询不返回完整 payload，避免敏感数据泄露；详情如后续需要须单独授权。
- 重入队必须提供非空原因，操作者取当前认证用户。
- 管理员 allowlist 为空时默认拒绝所有运维请求。

## 验证

使用 embedded PostgreSQL 启动真实数据库并由 Flyway 执行生产 migrations；并发测试使用独立事务/线程。故障测试通过领取后不提交结果模拟进程退出，再推进租约到期。Boot MVC 测试验证认证、授权与契约。

