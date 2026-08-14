# Trade / Checkout 边界演进任务

## 迭代 1：交易承诺编排迁移

- [x] TC-T1：以失败的领域测试定义 Trade Process 状态机、重复与冲突语义。
- [x] TC-T2：实现 `j-store-trade-domain` 聚合、值对象、错误和仓储端口。
- [x] TC-T3：以失败的应用测试定义授权、预留、失败补偿和取消释放命令顺序。
- [x] TC-T4：实现 `j-store-trade-application` 用例与集成消息 handler。
- [x] TC-T5：实现 `j-store-trade-infrastructure` JPA 映射、仓储与 PostgreSQL 验证。
- [x] TC-T6：实现 `j-store-trade-boot` 事务装饰器和装配。
- [x] TC-T7：新增 Trade/Order 集成契约，迁移 Store/Inventory destination 和契约测试。
- [x] TC-T8：删除 Order 的承诺阶段与销售授权职责，改为消费 Trade 最终结果。
- [x] TC-T9：删除承担流程决策的 Translator，接入 Order 取消/支付事实到 Trade。
- [x] TC-T10：更新数据库迁移、领域文档和模块依赖。
- [x] TC-T11：运行相关测试、全仓质量门禁并记录证据与残余风险。

## 后续迭代

- [ ] TC-N1：Checkout API、业务幂等与 Order 内部创建契约。
- [ ] TC-N2：PricingQuote、Promotion/Coupon 锁定核销释放与行级优惠分摊。
- [ ] TC-N3：Cart 上下文与 Cart -> Checkout 转换。
- [ ] TC-N4：独立 tradeId、跨商户拆单和 Trade 级支付分配。
- [ ] TC-N5：业务一致性对账、支付渠道对账和审计修复命令。
