# DDD 基座质量治理设计

## 核心决策

1. 店铺上下文拆分为 `j-store-shop-domain/application/infrastructure/boot`。领域对象和仓储端口进入 domain，商户用例和用户查询端口进入 application，Controller、Spring 事务装饰器和 Bean 装配进入 boot。
2. 其它上下文对商户授权服务的依赖指向 shop application，不直接依赖 shop boot 或 infrastructure。
3. 店铺写仓储采用 `Propagation.MANDATORY`；跨 `Merchant` 与 `MerchantMembership` 的创建和成员变更由 shop boot 的用例事务统一包围。
4. 库存暂不引入新的持久化值对象，以避免扩大数据库映射变更；由聚合统一校验操作量，由工厂校验初始量。命令删除行为方法，保持纯数据载体。
5. `PaymentRefund` 与 `ReservationRecord` 保留当前持久化形态，但可变属性改为外部只读、内部受控修改。
6. 扩展既有 `tests/governance/test_commerce_context_module_boundaries.py`，加入 shop 和仓储事务约束。`quality-gate.sh` 已执行该测试，无需新增入口。

## 验证策略

| 需求 | 证据 |
|---|---|
| DQG-R1/R2/R3/R8 | governance 单元测试 |
| DQG-R4/R5/R6 | goods domain/application 回归测试，payment domain 编译与测试 |
| DQG-R7 | payment/fulfillment application 单元测试 |
| DQG-R9 | governance 检查与文档审查 |
| 全局回归 | `./scripts/quality-gate.sh` |

## 兼容与回滚

- 仅改变 Gradle 内部模块名称和依赖；Kotlin 包名与 HTTP 契约保持不变。
- 不新增迁移。候选失败时可整体回滚本次代码和模块配置，不涉及数据恢复。
