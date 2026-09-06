# 购物车自动刷新事务修复

## 意图与范围

恢复 CART-R2 的幂等刷新及可靠性约束：上游暂时不可用时可以重试，事实采集不持有 Cart 或 Outbox 投递事务；Assessment 与消费确认保持原子性。保持现有 HTTP 契约、价格与库存权威、Checkout 规则不变。无需数据库结构迁移或历史兼容层。

## 根因与修复

- 自动事件监听器直接调用应用服务，绕过同步入口的事务阶段；引入可选的本地事件准备阶段，投递事务开始前读取 Cart 并采集事实，事务内完成投影与消费确认。
- 应用服务返回的 REFRESH_UNAVAILABLE 被监听器忽略；在事件边界转换为异常驱动重试，过期版本允许丢弃。
- Assessment 先查后插在并发下违反唯一约束；用 PostgreSQL 冲突安全插入仲裁，胜者写明细，其他调用返回同一持久化结果。
- 新增 infrastructure 测试使用现有 catalog 的 JUnit launcher，不升级依赖版本。

## 验收与证据

- 测试先行：automatic refresh propagates upstream failure for redelivery 在修复前失败（未抛异常）。
- 测试先行：concurrent writers return the same persisted assessment 在真实 PostgreSQL 上复现唯一约束异常。
- 同步与自动路径：外部查询前读事务结束，自动 completion 在投递事务内执行。
- 本地总线：预备型、普通领域监听器及原生 Spring 监听器混合投递，消费去重仍有效。
- 数据库：非空且不同候选竞争只保存一套结果；胜者回滚后另一候选可以完成。
- Outbox：准备失败、完成失败、fencing 失败均不提交副作用或消费回执，恢复后同一事件可成功提交。

## 最终验证

- `:j-store-cart-application:test`、`:j-store-cart-infrastructure:test`、`:j-store-cart-boot:test`、`:j-store-messaging-local-spring:test`、`:j-store-outbox-spring:test` 相关回归通过。
- 真实 PostgreSQL 并发测试 2/2、混合监听器测试 2/2、Outbox PostgreSQL 测试 32/32 通过，无失败或跳过。
- `./scripts/quality-gate.sh` 全部通过：仓库治理、规格/治理/工具测试、文件归属、Spotless、依赖解析、许可证审计、全量 Gradle 测试及 66 个 JAR 制品许可证验证。
- 独立只读复评：事务修复范围 PASS，无剩余阻断问题；未由实现者自行批准合并。
- 验证范围为本地检查，未执行远端 CI、部署或生产写入。

准备阶段允许重复读取；准备型 Cart 监听器只能由分阶段 Outbox 入口调用，直接发布会快速失败。回退方式为撤回本候选代码；未修改消息格式或数据库结构。
