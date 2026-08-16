# Trade / Checkout 边界演进任务

## 迭代 1：交易承诺编排迁移（已完成）

- [x] TC1-T1：以失败的领域测试定义 Trade Process 状态机、重复与冲突语义。
- [x] TC1-T2：实现 `j-store-trade-domain` 聚合、值对象、错误和仓储端口。
- [x] TC1-T3：以失败的应用测试定义授权、预留、失败补偿和取消释放命令顺序。
- [x] TC1-T4：实现 `j-store-trade-application` 用例与集成消息 Handler。
- [x] TC1-T5：实现 `j-store-trade-infrastructure` JPA 映射、仓储与 PostgreSQL 验证。
- [x] TC1-T6：实现 `j-store-trade-boot` 事务装饰器和装配。
- [x] TC1-T7：新增 Trade/Order 集成契约，迁移 Store/Inventory destination 和契约测试。
- [x] TC1-T8：删除 Order 的授权、预留和补偿编排职责，改为消费 Trade 最终结果。
- [x] TC1-T9：接入 Order 取消/支付事实到 Trade，保持 Translator 只做语言转换。
- [x] TC1-T10：更新数据库迁移、领域文档和模块依赖。
- [x] TC1-T11：运行相关测试与质量门禁；实现提交为 `f2c2521d`。

## 迭代 2：统一 Checkout、独立 Trade 身份、多商户订单计划与统一结算

### 2026-08-15 破坏性重构批准

- 已确认项目未上线且开发数据可丢弃；本迭代直接替换过渡模型，不设计旧 API、消息和数据库兼容层。
- 交付必须形成一个完整主链：独立 `tradeId` → 全计划授权/库存预留 → 全计划 Order 创建 → 金额守恒屏障 → 唯一 SettlementPlan → 首期 `FULL` Payment 分期。
- 禁止分开合入会形成双主链的中间状态。实现可以拆分提交，但候选分支通过端到端门禁前不得合并到 `develop`。

### 2026-08-15 基础主链切片（本轮已落地）

- 已新增认证保护的 `POST /api/checkouts` 直接购买入口；买家来自认证上下文，公开请求不再接受 `buyerId` 或顶层 `merchantId`，商户由 Offer 快照派生。
- 已删除 `POST /api/orders`、旧创建请求 DTO 和公开 `OrderUseCase.createOrder`；Order 创建仅能通过 Trade 使用的 `InternalOrderCreationUseCase` 进入。
- 已删除迭代 1 的 `TradeProcess(orderId)`、`OrderCreated -> StartTradeProcess` 和 `CreatePaymentForOrderCommand` 主链；Checkout 先创建独立 Trade 与多商户计划，完成销售授权和库存预留后才内部创建 Order，随后建立唯一 SettlementPlan 与首期 `FULL` TradePayment。
- Store/Inventory 契约和持久化已改用 `tradeId + orderPlanId`，不再把计划 ID 填入名为 `orderId` 的字段。
- 本切片是可运行的基础主链，不等于迭代 2 生产就绪：可靠 Saga 基础已在 2H 收敛；取消竞争、渠道支付状态机、补偿完成确认、库存确认/履约放行和并发恢复证据仍按未勾选任务推进。

### 2A：产品门禁与契约基线

- [x] TC2-T1：产品所有者批准首期多商户全有或全无、全部 Order 成功后才建立唯一 SettlementPlan、支付成功优先及安全关单语义；批准规格见 `requirement.md` 的 TC-R9～TC-R11。
- [x] TC2-T1A：将唯一 Payment 修正为唯一 SettlementPlan；首期只实现 `PREPAID + FULL`，为 B2B/B2C、预售分期和应收账期保留稳定扩展点。
- [x] TC2-T1B：为 `BuyerParty(INDIVIDUAL|ORGANIZATION)`、独立 `actingPrincipalId`、结算条款和履约放行策略编写领域测试并落地首期值对象。
- [ ] TC2-T2：冻结公开 Checkout 契约：`checkoutRequestId`、直接购买输入、认证买家、创建/查询/取消响应、待支付对象、错误码和越权隐藏规则；证据为 Controller 契约测试草案与批准规格。
- [x] TC2-T3：冻结身份与相关键契约：`tradeId` 为 Checkout/Payment 主相关与支付幂等键，`orderPlanId` 为计划级幂等/分区键，Fulfillment 和单订单售后使用 `orderId`；同步消息生产者/消费者矩阵。
- [x] TC2-T4：确认当前最近迭代提供基础 Offer 报价和 Trade 统一 Payment 语义，但不实现 Promotion、Coupon、Cart、特定生产支付渠道、ERP 或完整对账。

### 2B：Trade 身份、计划模型与持久化

- [ ] TC2-T5：先写失败的 Trade domain 测试，覆盖买家范围业务幂等、同键冲突、规范输入摘要、一个或多个 `TradeOrderPlan`、稳定 `orderPlanId`、计划状态和总体状态收敛。
- [x] TC2-T6：实现独立 `TradeId`、`checkoutRequestId`、版本化规范请求摘要、买家归属和 `TradeOrderPlan`，移除 Trade 聚合对 `orderId` 身份的依赖。
- [ ] TC2-T7：按批准的成功策略增加多计划授权、预留、Order 创建和反向补偿状态测试，覆盖重复、乱序、并发与迟到结果。
- [ ] TC2-T8：演进 Trade JPA 模型和数据库结构，验证买家范围幂等唯一约束、计划唯一约束、可空 `orderId` 唯一绑定、PO 往返和事务要求。

### 2C：可信快照、基础报价与拆单

- [ ] TC2-T9：以失败的应用测试定义 Catalog、Offer、User、Address 和基础报价端口的查询、错误传播及冻结语义。
- [ ] TC2-T10：将现有 OrderFactory 的商品、Offer、地址和基础金额准备迁入 Trade 编排边界；客户端顶层 `merchantId` 不再作为商户权威。
- [ ] TC2-T11：实现最小基础报价端口，保持现有 Offer 单价和小计行为，同时返回稳定报价标识/版本、币种、行金额和总额。
- [x] TC2-T12：实现按可信商户和履约分组生成 `TradeOrderPlan`，并验证同一请求中单商户、多商户和未来同商户多履约分组不会破坏模型。

### 2D：Trade 到 Order 的内部创建闭环

- [ ] TC2-T13：先写版本化内部契约测试，定义 Trade 创建 Order 的可信快照、`tradeId`、`orderPlanId`、Deadline、成功事实和不可恢复失败事实。
- [ ] TC2-T14：实现 Order 内部创建用例和事务装饰器，以 `orderPlanId` 业务幂等创建单商户 Order；重复命令不能产生多个 Order 或重复事件。
- [ ] TC2-T15：收缩 OrderFactory，使其只验证并组装可信成交快照，不再依赖 Goods、Offer、Pricing 或地址 ACL。
- [ ] TC2-T16：实现 Trade 消费 Order 创建结果、记录 `orderPlanId -> orderId`、区分技术重试和不可恢复失败，并通过独立幂等内部撤销契约关闭已创建 Order、回收全部资源。

### 2E：Trade 唯一 SettlementPlan 与资金安全状态机

- [ ] TC2-T17：先写 Trade 结算屏障测试：只有所有计划均为 `ORDER_CREATED` 且金额守恒时才能原子进入 `SETTLEMENT_PREPARING` 并产生一次结算准备命令；覆盖重复、乱序、并发和部分 Order 缺失。
- [ ] TC2-T18：升级结算与 Payment 契约为 `tradeId` 唯一 SettlementPlan、`settlementPlanId + installmentId` 唯一支付对象，定义冻结金额分配、渠道受理、准备失败、取消、状态未知、捕获和退款事实。
- [ ] TC2-T19：演进 Payment 聚合、仓储与数据库约束，以 `settlementPlanId + installmentId` 业务幂等；移除 `order_id UNIQUE` 单订单支付主链，并验证重复命令不重复请求渠道。
- [ ] TC2-T20：实现可替换支付渠道端口和测试适配器；本地落库保持 `PAYMENT_PREPARING`，仅在渠道明确受理并返回短期支付引用后发布 `PaymentPreparedEvent`。
- [ ] TC2-T21：实现 Payment `acceptBefore/expiresAt` 与 Trade/Order `closeAfter` 安全宽限期，使用渠道权威受理/捕获时间裁决，宽限到期不得替代状态查询或安全撤销。
- [ ] TC2-T22：实现 Payment 确定失败、可重试失败和状态未知的分流；只有确认未创建、未支付或已安全撤销后，Trade 才执行 Order 撤销和库存/授权/权益释放。
- [ ] TC2-T23：实现支付成功优先、关闭后迟到支付的幂等退款，以及原 Trade 过期关闭后禁止创建第二个 Payment；覆盖所有回调、取消、查询和关单交错顺序。
- [ ] TC2-T24：实现支付捕获金额按冻结分配投影给各 Order，并定义按 Order 发起退款时的分配上限和 Payment 总额守恒测试。

### 2F：统一用户接口与旧路径移除

- [ ] TC2-T25：先写 Trade Boot Controller 测试，覆盖 `POST /api/checkouts`、状态查询、取消、认证买家、重复提交、同键冲突、越权隐藏和 `payment: null/PAYMENT_READY`。
- [ ] TC2-T26：实现 Checkout API 与只读状态 DTO，返回 Trade/计划状态、失败原因、期限、`statusUrl`、`orderIds`，并仅在渠道明确受理后返回唯一待支付对象。
- [ ] TC2-T27：实现 Checkout 取消与已形成 Order 的单订单取消边界，验证支付准备、支付未知、支付成功和关单后的取消竞争及幂等补偿。
- [x] TC2-T28：删除 `POST /api/orders`、公开 `OrderUseCase.createOrder`、旧请求 DTO 和 `OrderCreated -> StartTradeProcess` 启动链路；仓库内不得存在双创建路径。
- [ ] TC2-T29：评估并清理 Order provisional 状态及冗余 `commitmentStatus`；任何保留状态必须有独立订单业务含义和测试。

### 2G：迭代级验证与交付证据

- [ ] TC2-T30：运行 Trade/Order/Catalog/Shop/Inventory/User/Payment/Integration Contracts/Authentication/Observability/Root Boot 的最小相关测试与 PostgreSQL 集成测试；验证 correlation 透传、低基数标签和敏感支付/买家数据不进入日志、指标或 health details。
- [ ] TC2-T31：运行全仓 `test`、静态检查和 `./scripts/quality-gate.sh`，记录未运行项、失败、恢复方式和残余风险。
- [x] TC2-T32：更新 `docs/domain-modeling.md`、`docs/project-overview.md`、消息目录和数据库说明，使当前实现事实与规格一致。
- [ ] TC2-T33：由非实现者独立评审公共 API、买家隔离、金额分配、库存、订单状态、支付状态机、幂等、退款和补偿；经人工批准后才能合并。

### 2H：可靠 Saga 基础（当前迭代）

- [x] TC2H-T1：Trade 在 Checkout 阶段冻结买家资料、规范化地址、Catalog 展示信息、Offer 成交信息和金额，Order 创建不得再次查询 User、Geo、Catalog 或 Offer。
- [x] TC2H-T2：定义 `CreateOrderFromTrade` 内部可信契约，携带 `tradeId`、`orderPlanId` 和计划摘要；Order 按 `orderPlanId` 数据库唯一且同摘要重放返回原 Order，不同摘要返回冲突。
- [x] TC2H-T3：Order 聚合持久化来源 `tradeId/orderPlanId/planDigest`，并以已承诺的 `ACTIVE + CONFIRMED` 状态创建；删除新主链对 provisional 确认回调的依赖。
- [x] TC2H-T4：Trade 处理销售授权失败和库存预留失败，原子记录失败原因并发布已取得库存与授权的幂等释放命令；失败 Trade 永不创建 Order 或 SettlementPlan。
- [x] TC2H-T5：Order 创建业务失败时记录计划失败；已经创建的 Order 必须通过独立内部撤销用例关闭，随后释放所有计划资源。技术异常保持可重试，不伪装为业务失败。
- [x] TC2H-T6：补齐 Trade、Order 和 TradePayment PostgreSQL 往返、唯一约束、重复命令与空库 Flyway 验证。
- [x] TC2H-T7：运行相关模块、全仓测试和 `./scripts/quality-gate.sh`，回写完成证据与未完成风险。

完成证据（2026-08-15）：

- `./gradlew test`：通过，212 个可执行 Gradle task（34 executed，178 up-to-date）。
- Trade、Order、TradePayment PostgreSQL 测试：可信快照/来源字段往返及 Checkout、OrderPlan、PaymentInstallment 唯一约束通过。
- Root Boot 空库 Flyway 与 Spring 上下文测试：通过。
- `./scripts/quality-gate.sh`：6/6 通过，包含治理契约、格式、依赖解析、许可证、全仓回归和制品检查。
- 回归补强：失败 Trade 的迟到授权/预留结果会被幂等释放；买家取消事实可相关到 Trade 且内部撤销不会形成事件循环；Trade 来源 Order 支付事实恢复计划级库存确认。
- 剩余风险：补偿命令已可靠发出但尚未逐项记录下游完成确认；支付渠道状态未知、取消竞争、超时恢复和显式履约放行继续留在迭代 2 未完成任务中。

### 2I：支付准备闭环与 Checkout 可见性（当前迭代）

- [x] TC2I-T1：冻结 `PreparePaymentInstallmentCommand` 及 `PaymentPrepared/Rejected/Uncertain` 结果契约；所有消息以 `tradeId` 相关，以 `settlementPlanId + installmentId` 业务幂等。
- [x] TC2I-T2：以失败测试定义 TradePayment 的 `PREPARING -> READY|REJECTED|UNCERTAIN` 转换、相同结果幂等、冲突结果拒绝及受控支付动作有效期。
- [x] TC2I-T3：实现 Payment 准备用例和可替换渠道端口；渠道请求使用稳定幂等键，只有明确受理才进入 `READY`，明确拒绝与结果未知必须发布不同事实。
- [x] TC2I-T4：移除 Trade 对 Payment 仓储的直接调用；Trade 只发布支付准备命令并消费结果事实，`UNCERTAIN` 保留 Order、库存和授权。
- [x] TC2I-T5：Trade 按 `installmentId` 持久化 Payment 引用和支付准备状态；Checkout 查询只有在 `PAYMENT_READY` 时通过只读 ACL 返回当前待支付对象，其余状态返回 `payment: null`。
- [x] TC2I-T6：更新空库迁移、JPA 往返、消息序列化、Handler 装配和 Controller 契约测试，运行相关模块、全仓测试及质量门禁并回写证据。

完成证据（2026-08-15）：

- Payment/Trade 领域、应用、基础设施和 Boot 测试通过；覆盖明确受理、明确拒绝、渠道异常转未知、重复准备、支付取消安全等待、分期校验、过期动作不阻断 Checkout 查询及非等值支付窗口。
- Trade 与 TradePayment PostgreSQL 往返和 Root Boot 空库 Flyway/Spring 上下文测试通过；Provider 字段列归属、分期 Payment 引用及准备状态可完整恢复。
- 支付准备消息 Jackson 往返、Handler 装配和 Checkout Controller 待支付响应契约通过。
- `./scripts/quality-gate.sh`：6/6 通过；全仓 212 个可执行 Gradle task 全部通过，发布制品许可证校验覆盖 58 个 JAR。
- 审查修复：READY/UNCERTAIN Payment 不再允许本地伪取消；支付阶段买家取消进入 `CLOSING` 且只在明确拒绝后补偿；渠道字段长度在领域/应用边界与数据库列一致，超限结果收敛为 `UNCERTAIN`。
- 审查修复：Order 创建改为 Trade 命令、Order 结果事实驱动的异步 Saga；Trade 来源 Order 先进入 `CANCELLATION_PENDING`，资金安全裁决前不删除订单行或伪造 OrderPlan 关闭；Payment 对尚不能安全撤销的 READY/UNCERTAIN 命令作幂等确认，避免毒消息重试。
- 审查修复：Checkout 输入长度在应用边界与持久化契约对齐；并发相同幂等键命中唯一约束后，在新只读事务中恢复胜出 Trade，相同请求返回原结果、不同摘要返回冲突。
- 审查修复：Payment 先在独立事务提交稳定 `PREPARING/paymentId`，再无事务调用渠道并以第二个独立事务保存结果；崩溃重试复用同一身份。支付准备采用 1 分钟受理、15 分钟动作及 2 分钟安全余量，库存窗口不足时失败并补偿；Checkout `requestDigest v2` 使用空值标记和长度前缀编码，消除可控分隔符碰撞。
- 审查修复：Payment 撤销采用 `CANCELLING -> CANCELLED` 两阶段状态和稳定渠道幂等键；结果未知保持处理中并重试，Trade 仅在撤销确认事实后补偿。Trade 按分期关联 Payment，Order 内部取消命令同时校验 `tradeId + orderPlanId`，避免跨交易误取消。
- 审查修复：FAILED Trade 会补记并撤销其它计划迟到创建的 Order；支付拒绝与撤销确认按相同分期 Payment 身份幂等收敛；`CLOSING` 幂等吸收其它 Order 的并发取消并保留首次失败原因，避免正常乱序形成毒消息。
- 审查修复：支付准备与撤销并发时，Payment 以 `PREPARATION_CANCELLING` 阻止在途准备完成前发布撤销确认；迟到 `Accepted` 会保存渠道引用并触发新一轮安全撤销，撤销完成只接受与请求所见渠道引用一致的结果。`Accepted` 不替代 `Captured`，关闭后捕获仍按 TC-R11 进入幂等退款。
- 审查修复：迟到 `Accepted` 的公开动作或时间字段非法时仍完整保存补偿渠道身份并进入 `CANCELLING`；业务取消原因与渠道诊断分列持久化，后续确认不会用技术超时覆盖用户取消原因。移除缺少 Trade 消费者和分期关联的临时 `OrderPaidIntegrationEvent`，统一捕获契约继续由 TC2-T21～T24 实现。
- 剩余风险：当前准备与撤销渠道实现仅在非生产 Profile 提供；生产渠道接入、定时主动查单、捕获、迟到支付退款和最终关单仍未实现，`CLOSING` 在获得渠道权威结果前会有意保留资源。

本切片不实现渠道主动查询、支付捕获、迟到支付退款或最终关单；这些行为继续由 TC2-T21～T24 约束，不能用本地超时直接释放资源。

## 迭代 3：Pricing、Promotion 与权益生命周期

- [ ] TC3-T1：定义版本化 `PricingQuote`、报价有效期、行级金额和商户/平台出资分摊。
- [ ] TC3-T2：定义活动、优惠券、红包等权益的叠加/互斥、锁定、核销、释放和过期协议。
- [ ] TC3-T3：让 Trade 冻结报价与权益承诺，并在失败/取消时幂等释放；Order 只保存最终金额快照。
- [ ] TC3-T4：覆盖金额守恒、舍入、重复核销、并发使用和跨商户优惠分摊测试，并完成独立金额评审。

## 迭代 4：Cart 接入

- [ ] TC4-T1：建立独立 Cart 上下文、可变购买意图和买家隔离。
- [ ] TC4-T2：支持从 Cart 发起 Checkout，并冻结 Cart 版本或内容摘要。
- [ ] TC4-T3：验证 Cart 后续变化不修改已受理 Trade，重复提交由 `checkoutRequestId` 收敛。

## 迭代 5：支付体验强化、关单运行治理与低延迟体验

- [ ] TC5-T1：在已实现的 Trade 唯一 SettlementPlan 契约上接入具体生产支付渠道、主动查询调度、查询限流和渠道级可观测性。
- [ ] TC5-T2：建立自动退款运营门禁、人工审核例外、支付未知告警和渠道对账入口。
- [ ] TC5-T3：实现经压测验证的有限同步等待，同时保留 `202 + tradeId + statusUrl` 慢路径。
- [ ] TC5-T4：完成 Checkout 关键链路容量、延迟、积压、故障窗口和资金安全演练。

## 迭代 6：对账、ERP 协作与恢复

- [ ] TC6-T1：建立 Trade、Order、Promotion、Reservation、Payment、Accounting 差异查询和带审计修复命令。
- [ ] TC6-T2：定义 ERP 与 Order、Fulfillment、Inventory、WMS 的稳定契约，禁止读取内部表或跨上下文直接写数据。
- [ ] TC6-T3：完善 DLQ 重放、状态停留告警、人工授权、停止条件和故障演练证据。

## 并行技术路线

- [ ] TECH-T1：按 `checkout-reliable-async/iteration-plan.md` 继续 Broker、Inbox、可靠投递和微服务灰度，但将业务相关键升级为 `tradeId/orderPlanId/orderId` 分阶段语义。
- [ ] TECH-T2：技术路线不得恢复 Order 作为 Checkout Process Manager，也不得与本计划建立第二套状态机或用户创建入口。
