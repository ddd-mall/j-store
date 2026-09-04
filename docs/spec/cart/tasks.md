# 购物车实现任务

所有行为变更遵循 Red → Green → Refactor。任务完成状态只能由对应测试、迁移或契约证据支持，不能仅勾选复选框。

## T1 建立模块和稳定边界

**结果**：仓库具备 Cart 四层模块、`cart-api` 和 `inventory-api`，依赖方向通过构建验证。

- 在 `settings.gradle.kts` 注册 `j-store-cart-api/domain/application/infrastructure/boot` 和 `j-store-inventory-api`。
- 建立与现有上下文一致的 Gradle 依赖；domain/application 不引入 Spring/JPA。
- 在根 `j-store-boot` 组合 Cart boot。
- 先增加架构测试，证明 Cart domain/application 无 Spring、JPA、其它上下文 infrastructure 依赖。

**覆盖**：CART-R6。

**证据**：模块编译、架构测试、依赖报告。

## T2 以 TDD 实现 Cart 聚合

**结果**：认证买家的活动 Cart 可以把具体 Offer 的 SKU 设置到绝对目标数量、收敛相同目标重试并管理 Selection。

测试先行覆盖：

- 首次设置目标数量创建已选择行并增加版本。
- 同 Offer 替换为绝对目标数量；同 SKU 不同 Offer 不合并。
- 相同目标的陈旧版本重试 no-op；不同目标的陈旧版本请求冲突。
- 不同商户、相同 Settlement Scope 的 Offer 可以共存；不同 market、channelId 或 currency 的 Offer 加购失败且 Cart 版本不变。
- 非正数量、数量溢出、超过 100 行失败。
- 原子替换 Selection、未知行失败、相同 Selection no-op、空 Selection 合法。
- 每次实际变化记录一次带新内容版本的 `CartRefreshRequestedEvent`。

实现 `Cart`、`CartLine`、类型化 ID、命令、错误、工厂、Repository 和领域事件。

**覆盖**：CART-R1、CART-R4.1-3、CART-R6.1。

**证据**：`j-store-cart-domain:test`，包含数量与选择的属性测试。

## T3 实现 CartAssessment 与纯计算器

**结果**：给定 Cart 快照和规范化事实，可以确定性地产生 COMPLETE/PARTIAL/EMPTY Assessment。

测试先行覆盖 CART-R3 决策表：

- 未选择、Catalog 不可用、Offer 不可用、ATP=0、ATP不足和 Eligible 的固定优先级。
- 只有 Eligible 行计价。
- 金额等于 `Price.sumOf(unitPrice * quantity)`，空结果为零且不溢出。
- 同一 Cart 版本的输入顺序变化不改变结果。
- 属性测试证明所有排除行贡献金额恒为零。

实现 `CartAssessment`、Assessment Line、状态和 `CartAssessmentCalculator`，保持无外部依赖。

**覆盖**：CART-R2.3-4、CART-R3、CART-R6.2。

**证据**：`j-store-cart-domain:test`。

## T4 发布 Catalog、Offer 和 ATP 只读契约

**结果**：Cart 可以批量取得当前发布、销售资格、价格和 ATP，而不越过上下文边界。

- Goods API：新增按 SKU 查询当前发布状态及最新有效快照信息；用测试证明归档商品不会被误判为当前可用。
- Shop API：新增/扩展批量 Offer 查询，覆盖 Store 状态、Offer 状态、有效期、SKU 引用、当前价格、版本、履约节点、market、channelId 和 currency。
- Inventory API：新增批量 `skuId + fulfillmentNodeId` ATP 查询及实现，返回 ATP、上游来源版本和随可用量变化的可用性版本；只读、不锁行、不创建 Reservation。
- 为三个发布契约增加 API DTO/实现契约测试。

**覆盖**：CART-R2、CART-R3、CART-R6.3。

**风险**：当前 `GoodsSnapshotQueryService` 只表达历史最新发布快照，不能直接承担当前发布状态；必须新增语义明确的契约而非改变旧接口含义。

**证据**：goods/shop/inventory 的 application 与必要 infrastructure 测试。

## T5 实现消费方 ACL 和 Refresh 用例

**结果**：刷新事件可幂等生成当前版本 Assessment，技术失败不会伪装成业务不可售。

测试先行覆盖：

- 去重并批量查询 SKU、Offer 和库存键，无 N+1。
- 缺失的业务实体转换为 unavailable 行；调用异常使整次刷新失败。
- 重复事件 no-op。
- requestedVersion 旧于当前 Cart 时丢弃。
- 计算期间 Cart 版本改变时拒绝保存旧 Assessment。
- 技术失败保留最近成功结果，但查询标记 STALE/UNAVAILABLE。

实现 `CartCommerceFactsService` ACL、`CartRefreshService`、Assessment Repository 和事件 handler。Cart 保存与 Outbox 事件写入由 Cart boot 的短写事务统一提交；显式刷新拆成短读事务、无事务事实采集和短写事务，依靠 Assessment 业务唯一键幂等，不保存请求历史。

**覆盖**：CART-R2、CART-R3.3、可靠性和性能目标。

**证据**：cart application 单测、Outbox 事务测试、批量调用验证。

## T6 实现 PostgreSQL 持久化与并发约束

**结果**：Cart、行和 Assessment 可以完整往返，并由数据库约束兜底。

- 为 Cart 与 Assessment 建立各自 Repository 实现和 PO Converter。
- 建立一个 buyer 一个 ACTIVE Cart、一个 Cart 一个 Offer 行、一个 Cart 版本一个 Assessment 的唯一约束。
- 分离 `contentVersion` 与 JPA `persistenceVersion`。
- 增加并发更新、陈旧版本和重复事件的 PostgreSQL 测试。
- 更新当前数据库基线/初始化快照；不增加数据回填或双写逻辑。

**覆盖**：CART-R1.5、CART-R2.5-6、并发与可靠性目标。

**证据**：`j-store-cart-infrastructure:test`、迁移测试。

## T7 实现 Cart HTTP 用例和买家隔离

**结果**：认证买家可以加购、读取、替换选择和显式刷新自己的 Cart。

按顺序为 application 与 controller 契约增加失败测试，再实现：

- `PUT /api/carts/current/items`
- 数量设置先在短事务中检查收敛状态，在无事务阶段解析 Offer，再于新短事务中重新加载并提交；Cart 写入成功不依赖同步 Assessment 刷新成功。
- `PUT /api/carts/current/selection`
- `POST /api/carts/current/refresh`
- `GET /api/carts/current`

验证 buyerId 只来自 `@CurrentUserId`；越权按不存在；价格、版本和 ATP 不接受客户端输入；错误映射符合设计。

**覆盖**：CART-R1、CART-R4.1-3、CART-R5。

**证据**：cart application 测试、cart boot controller/transaction 测试。

## T8 接入 Trade Checkout 来源

**结果**：Trade 同时支持 Direct 和 Cart 来源，Cart 变化不影响已受理 Trade。

测试先行：

- `CheckoutSourceResolver` 将直接购买和 Cart 来源规范化为相同的内部 Checkout Intent。
- Cart 来源校验 buyer、expectedCartVersion，并使用共享事实采集器与纯计算器同步生成新鲜 Assessment 候选；不等待异步刷新，也不复用旧投影。
- 只返回已选择且 Eligible 行；没有 Eligible 行拒绝创建 Trade。
- 下架、售罄或库存不足行不进入任何 TradeOrderPlan。
- Trade 冻结 `CheckoutSourceSnapshot(CART, cartId, version, digest)`。
- 同一 Cart 的多商户 Eligible Line 按 `merchantId + fulfillmentNodeId` 拆成多个 TradeOrderPlan，Trade 金额等于所有计划金额之和。
- 所有行必须与 Cart 的 market、channelId、currency 一致；不一致时拒绝创建 Trade。
- 任一商户计划授权或库存预留失败时整笔 Trade 失败，并幂等补偿其他计划已经成功的授权和预留；全部计划完成预留前不创建 Order。
- 相同 `checkoutRequestId + sourceDigest` 幂等；版本或摘要变化冲突。
- Trade 受理后修改 Cart 不改变 Trade 行项、金额或计划。
- ATP 在试算后下降时，由现有 Reservation 失败和补偿链路裁决。

实现 `j-store-cart-api` 的 Checkout 来源查询、Trade 侧 source resolver、公共请求 one-of 契约和 Trade 来源持久化。重构现有 Checkout preparation 时保持用户、地址、商品和 Offer 准备职责清晰，不向一个适配器继续堆叠 Cart 分支。

**覆盖**：CART-R4.4-10、CART-R6.4-5。

**证据**：cart-api 契约测试、trade domain/application/infrastructure/boot 测试、Checkout HTTP 契约测试。

## T9 更新长期模型和运行时文档

**结果**：实现事实、上下文图、模块清单和测试入口一致。

- 实现完成后更新 `docs/domain-modeling.md`，将 Cart 与 Cart Assessment 加入权威事实和上下文关系；方案阶段不提前把未实现模型写成当前事实。
- 更新 `docs/project-overview.md`、README 和 Cart 模块测试命令。
- 若 Trade Checkout 公共请求发生破坏性变化，同步更新所有仓库内调用方和契约测试，不保留内部兼容分支。

**覆盖**：CART-R6，仓库漂移规则。

**证据**：文档/代码/测试语义审查。

## T10 特性级验证与独立评估

**结果**：购物车功能在领域、持久化、跨上下文和 Checkout 主链上形成完整证据。

最小验证序列：

```bash
./gradlew :j-store-cart-domain:test :j-store-cart-application:test
./gradlew :j-store-cart-infrastructure:test :j-store-cart-boot:test
./gradlew :j-store-goods-api:test :j-store-goods-application:test
./gradlew :j-store-shop-api:test :j-store-shop-application:test
./gradlew :j-store-inventory-api:test :j-store-inventory-application:test :j-store-inventory-infrastructure:test
./gradlew :j-store-trade-domain:test :j-store-trade-application:test :j-store-trade-infrastructure:test :j-store-trade-boot:test
./gradlew :j-store-boot:bootJar
./scripts/quality-gate.sh
```

特性级场景：

1. 多 SKU 加购 → 刷新 → 金额试算。
2. 重新选择 → 新版本 Assessment → 从 Cart Checkout。
3. 下架 + 售罄 + 正常三行只结算正常行。
4. 陈旧刷新、重复事件和多设备版本冲突。
5. Checkout 后库存竞态由 Reservation 安全失败。
6. 越权访问不泄露 Cart 存在性。
7. 商户 A 两个商品、商户 B 一个商品 → 一次 Checkout → 一个 Trade → 两个 TradeOrderPlan → 两个商户订单，且金额逐层守恒。
8. 多商户中的任一授权或库存预留失败 → 整笔 Trade 失败 → 已成功承诺被补偿 → 不创建部分订单。
9. 跨 market、channelId 或 currency 的 Offer 不能加入当前 Cart，也不能绕过 Cart 来源校验进入 Trade。

由于该变更涉及公开 Checkout 契约、金额、库存与跨上下文边界，完成前需要非实现者独立评估需求覆盖、聚合边界、并发、金额守恒和残余风险。

**证据**：测试报告、质量门禁输出、独立 review log；任何未运行或失败的检查必须写入最终 summary。

## 需求追踪

| 需求 | 主要任务 |
|---|---|
| CART-R1 | T2、T6、T7 |
| CART-R2 | T3、T4、T5、T6 |
| CART-R3 | T3、T4、T5、T8 |
| CART-R4 | T2、T7、T8 |
| CART-R5 | T7、T8 |
| CART-R6 | T1、T3、T4、T8、T9 |
| CART-R7 | T2、T4、T7、T8、T10 |
| 质量目标 | T5、T6、T10 |
