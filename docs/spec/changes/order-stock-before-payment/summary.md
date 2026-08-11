# 交付总结：库存预扣成功后创建支付单

## 已实现行为

- `OrderCreatedEvent` 只保留库存预扣入口，不再直接创建支付单。
- 订单聚合仅在 `confirmStock()` 成功把交易状态从 `CREATED` 转为 `ACTIVE` 时产生 `OrderStockConfirmedEvent`。
- `OrderStockConfirmedToPaymentTranslator` 将该事件转换为既有的 `CreatePaymentForOrderCommand` v1。
- 库存不足关闭订单、非法或重复库存确认均不会产生支付创建门禁事件。
- 既有支付命令契约、支付消费者、Outbox 和消费幂等机制保持不变。

## 验收证据

| 验收场景 | 实现与测试证据 |
| --- | --- |
| AC1 创建订单只请求库存预扣 | 移除 `OrderCreatedToPaymentTranslator`；`OrderStockBeforePaymentTranslatorTest` 验证创建事件映射为 `ReserveInventoryCommand`。 |
| AC2 预扣成功后创建支付单 | `OrderImpl.confirmStock()` 产生 `OrderStockConfirmedEvent`；领域、应用服务和 Translator 三层测试覆盖状态转换、事件发布和命令字段映射。 |
| AC3 预扣失败关闭且不创建支付单 | `OrderLifecycleRegressionTest` 验证状态为 `CLOSED`、只产生 `OrderCancelledEvent`。 |
| AC4 非法或重复确认不越过门禁 | `OrderLifecycleRegressionTest` 验证重复确认失败且门禁事件只有一个。 |

## 验证结果

- TDD RED：定向测试最初因 `OrderStockConfirmedEvent` 和 `OrderStockConfirmedToPaymentTranslator` 不存在而编译失败。
- 定向领域与 Boot Translator 测试：通过。
- 订单应用服务定向测试：通过。
- 仓库治理检查：通过。
- spec-dev 与治理测试：39 个测试通过。
- Windows JDK 25 执行 `gradlew.bat test --no-daemon --console=plain`：通过，128 个 Gradle 任务，`BUILD SUCCESSFUL`。

## 环境说明

`scripts/quality-gate.sh` 的前两阶段通过；第三阶段在 WSL 中因找不到 Java 而停止。相同的全仓 Gradle 回归已使用仓库实际运行环境的 Windows JDK 25 成功完成。这是门禁脚本的跨环境启动限制，不是代码或测试失败。

## 兼容与运维

- 没有数据库、HTTP API 或集成命令契约变更。
- 系统尚未上线，本变更不迁移或清理已有开发环境中的提前创建 Payment 数据。
- 回滚时仅需恢复旧 Translator 触发点并移除新增领域事件；无数据结构回滚步骤。
