# 交付摘要：订单四层模块与事务修复

## 已实现

- 删除原 `j-store-order`，迁移为 domain/application/infrastructure/boot 四个 Gradle 模块。
- domain/application 不含 Spring、Jakarta Persistence 或 Hibernate 引用，并由治理测试持续校验。
- application 暴露 `OrderUseCase`、`AfterSaleUseCase`；Controller 与集成消息 handler 只依赖用例端口。
- boot 使用 Spring `TransactionTemplate` 装饰所有读写用例，查询为只读事务，写用例覆盖聚合保存和 Outbox 发布。
- 仓储写方法以 `MANDATORY` 防止绕过用例事务；售后仓储不再持有事件发布器或自行开启事务。
- 领域事件采用“稳定快照 → 全部发布成功 → 清队列”语义，并补齐 `OrderCompletedEvent` 发布。
- 售后幂等回执改用 PostgreSQL `ON CONFLICT DO NOTHING` 原子抢占，保留并发容量锁定语义。

## 验证证据

- `python -m unittest discover -s tests/governance -p 'test_*.py'`：8 tests，PASS。
- `scripts/check-agent-governance.sh`：PASS。
- spec-dev contract tests：28 tests，PASS。
- 四个订单模块及根 `j-store-boot` 定向回归：PASS。
- `gradlew.bat test --no-daemon --console=plain`：96 tasks，BUILD SUCCESSFUL。
- `git diff --check`：PASS。

`scripts/quality-gate.sh` 的前两段在 WSL 中通过；脚本第三段因 WSL 没有 Linux JDK、无法使用 Windows `JAVA_HOME` 而未在同一 Bash 进程执行。相同的第三段 Gradle 命令已在 PowerShell/Windows JDK 25 下单独完整通过。

## 兼容与剩余边界

- HTTP 与集成消息契约、数据库迁移均未改变。
- 模块化单体路径已就绪。
- 微服务集群仍需要独立订单 launcher/config/migration 交付物，以及 Broker 出站和入站适配器；当前 Broker SPI 的缺失适配器 fail-fast 行为保持不变。
- 历史已批准规格中的 `j-store-order` 路径保留为当时实现快照，不批量改写历史事实；当前权威模块映射见项目概览与本变更设计。
