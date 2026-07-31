# 交付总结：订单状态多维化

## 特性

`order-status-dimensions`

订单聚合已从单一 `OrderStatus` 替换为 `TradeStatus`、`PaymentStatus`、`FulfillmentStatus` 和 `AfterSaleStatus` 四个独立业务维度。旧订单级 `previousStatus`、旧 API `status` 和旧持久化列均已删除，不提供兼容投影或双写。

## 规格产物

- `requirement.md`：需求 1–8 及验收标准。
- `design.md`：四维状态、不变量、行为原子性、迁移和接口设计。
- `tasks.md`：全部实现与检查点任务均已完成。
- `review-log.md`：逐切片单 Agent 自审和环境阻塞解除记录。

## 主要设计与实现决策

- 订单聚合分别维护交易、支付、履约和售后状态，公开行为不再依赖单一状态图。
- 构造恢复和所有行为候选结果统一通过 `OrderStateInvariants` 校验。
- 行为按“完整前置校验 → 候选快照 → 不变量校验 → 一次提交 → 发布事件”执行，失败不产生部分更新。
- 售后摘要由支付状态和全部行项状态确定性推导；订单级前序状态已移除，行项 `previousItemStatus` 保留用于退款拒绝恢复。
- 部分退款支持对剩余行项继续申请；全额退款关闭交易但保留原履约事实和 `requireReturn` 判断。
- API 详情、创建和分页响应使用四个状态字段，行项 `status` 及其他契约保持不变。
- Flyway 迁移清空未上线开发环境订单数据，删除旧列/索引并建立四个非空受约束状态列和查询索引。
- PostgreSQL 集成测试使用 Zonky embedded PostgreSQL，在 Windows/JDK 25 上运行真实 PostgreSQL，无 Docker 前置条件。

## 测试覆盖

- 四个枚举及订单公开契约。
- 跨维度不变量和非法恢复拒绝。
- 库存、支付、备货、发货、签收、完成和取消流程。
- 退款集合校验、首次及后续申请、部分/全部批准、部分/全部拒绝。
- 失败原子性、交易终态和售后终态阻断。
- 领域事件名称、版本和关键载荷回归。
- JPA 四维枚举映射及 PO/领域对象往返。
- Flyway/PostgreSQL 列、默认值、约束、索引及旧结构移除。
- 单订单和分页 API 四维响应契约。
- 订单领域、订单基础设施、Boot 模块及全仓测试套件。

## 验证结果

- `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.migration.OrderStatusDimensionsMigrationTest"`：通过。
- `./gradlew.bat :j-store-order:test :j-store-order-infrastructure:test :j-store-boot:test`：通过。
- `./gradlew.bat test`：通过。
- 旧订单状态生产/测试引用搜索：通过。
- `git diff --check`：通过。

## 后续事项

无本特性范围内遗留项。并发锁、幂等、支付单/售后单/履约单独立聚合及真实退款到账仍属于规格明确排除的后续演进范围。
