# Trade / Checkout 边界演进需求

## 背景与目标

Trade / Checkout 用于统一表达“一次购买如何达成”：接收用户结账意图，冻结成交条件，协调销售授权、库存承诺、订单计划和失败补偿，并为后续计价、活动、购物车、多商户拆单、统一支付和对账提供稳定主线。

Order 用于记录每个已形成订单的可信成交事实和买家生命周期。Order 不是被动日志，仍拥有单个订单的取消、支付/履约/退款事实投影和售后规则；但它不再是用户下单入口，也不负责结账试算、跨商户规划、销售授权或库存预留编排。

## 当前状态与已确认方向

- 迭代 1 已建立独立 Trade Process，并将销售授权、库存预留、失败补偿和取消释放从 Order 迁入 Trade。
- 当前实现仍先创建 provisional Order，再以 `orderId` 启动 Trade；该流程只作为迭代 1 的过渡状态。
- 已确认的目标是：Trade / Checkout 必须成为唯一面向用户的下单入口；Order 创建只能由 Trade 通过内部可信契约发起。
- 为避免公开 Checkout API 再次绑定单订单模型，下一迭代必须同时引入独立 `tradeId`、业务幂等和 `TradeOrderPlan`，而不是将多商户能力推迟到 API 迁移之后。

## 领域边界

- Cart 负责可反复修改的购买意图；Checkout 只读取并冻结一次提交快照。
- Pricing / Promotion 负责算价规则、活动、优惠券、红包等权益生命周期及优惠分摊；Trade 只接受版本化报价并协调锁定、核销和释放。
- Trade 负责统一用户结账入口、结账业务幂等、成交条件快照、多商户订单计划、销售授权、库存承诺、订单创建编排、截止时间和失败补偿。
- Order 负责单个已形成订单的不可变成交快照、订单生命周期以及支付、履约、退款和售后事实投影。
- Payment 负责支付机构交互、捕获、取消、退款和外部资金流水；一个 Trade 只创建一个 Payment，并按冻结分配向多个 Order 投影资金事实。
- Fulfillment / WMS / ERP 按履约、实物库存和外部业务集成事实协作；ERP 接入不成为 Trade 的内部职责。
- Accounting / Reconciliation 负责账务、结算、差异发现和审计修复；不得跨上下文直接改写 Trade、Order 或 Payment 聚合。

## 迭代 1：交易承诺编排迁移（已完成）

### TC-R1 持久化 Trade Process

1. 系统必须为每个已创建但尚未形成销售承诺的订单建立且仅建立一个 Trade Process。
2. 迭代 1 以 `orderId` 作为 Trade 的过渡身份和相关键。
3. Trade Process 必须持久化商户、冻结行项、应付金额、币种、当前状态、销售授权、库存预留、期限、失败原因和更新时间。
4. Trade Process 状态至少包括 `AUTHORIZING`、`RESERVING`、`COMMITTED`、`PAID`、`FAILED`、`CLOSED`。

### TC-R2 销售授权与库存预留

1. Order 创建事实到达 Trade 时，Trade 必须幂等建立流程并发布一次销售授权命令。
2. 销售授权成功时，Trade 必须校验授权覆盖全部 Offer、持久化授权并发布库存预留命令。
3. 销售授权失败时，Trade 必须进入 `FAILED` 并通知 Order 关闭，不得请求库存。
4. 库存预留成功时，Trade 必须保存 Reservation ID 和最早过期时间，进入 `COMMITTED` 并通知 Order 交易承诺已形成。
5. 库存预留失败时，Trade 必须进入 `FAILED`，通知 Order 关闭，并幂等释放已经取得的销售授权。

### TC-R3 Order 职责收缩

1. Order 不再保存 Trade Process 的阶段状态和销售授权集合。
2. Order 不再直接消费销售授权或库存预留结果。
3. Order 只消费 Trade 发布的最终承诺结果。
4. Order 的成交快照、金额快照、支付/履约/退款投影、订单取消和完成规则保持不变。

### TC-R4 取消、可靠性与补偿

1. 未支付 Order 取消时，Trade 必须收到稳定取消事实。
2. 已取得授权或库存预留的 Trade 关闭时，必须发布库存释放和销售授权释放命令。
3. Trade 状态保存与下一条集成命令写入 Outbox 必须位于同一用例事务。
4. Trade handler 必须通过业务状态实现幂等，不能只依赖 Inbox message ID。
5. 重复、乱序、重启或迟到结果不得越级推进状态或产生重复副作用。

## 迭代 2：统一 Checkout、独立 Trade 身份、多商户订单计划与统一支付

### TC-R5 Trade 是唯一用户下单入口

1. 认证用户必须通过 Trade / Checkout API 提交下单请求；Order 不得提供用户可调用的创建接口。
2. 第一版公开接口至少提供创建 Checkout、查询 Checkout 状态和取消尚未形成最终订单的 Checkout。
3. 买家身份只能来自认证上下文，不能由请求体提交或覆盖。
4. `checkoutRequestId` 必须在买家范围内提供业务幂等：相同请求重复提交返回同一 Trade；相同 ID 携带冲突内容必须明确失败。
5. 第一版可以只接受直接购买行项；Cart 作为后续输入源接入，不得阻塞统一入口落地。
6. 新接口和仓库内调用方切换完成后必须删除 `POST /api/orders`，不得保留双创建路径或兼容别名。

### TC-R6 独立 Trade 身份与订单计划

1. Trade 必须使用独立 `tradeId` 作为聚合身份、相关键和用户查询键，不再以 `orderId` 作为自身身份。
2. 一个 Trade 必须包含一个或多个 `TradeOrderPlan`；每个计划拥有稳定 `orderPlanId`，并保存商户、履约分组、行项、金额分配、授权、预留和订单创建进度。
3. `tradeId -> orderPlanId -> orderId` 的映射必须持久化；订单尚未创建时 `orderId` 可以为空。
4. 商户归属必须由可信 Offer/报价事实推导；客户端提交的顶层 `merchantId` 不得成为拆单权威。
5. 拆单规则必须位于 Trade 领域或其应用编排边界，Controller、OrderFactory 和 Translator 不得决定如何拆单。
6. 即使第一版测试数据只有单商户，公开契约、幂等键和持久化模型也不得退化为“一次 Trade 只能有一个 Order”的结构。

### TC-R7 可信成交快照与内部 Order 创建

1. Trade 必须在编排销售授权前取得并冻结 Catalog、Offer、买家、收货地址和基础金额快照。
2. 第一版基础金额可以保持现有 Offer 单价与小计语义，但必须通过可替换的报价端口产生，不能继续由 OrderFactory 自行计算。
3. Trade 必须按 `TradeOrderPlan` 请求销售授权和库存预留，并持久化每个计划的结果及补偿进度。
4. 只有满足已批准的多商户成功策略后，Trade 才能通过版本化内部命令创建 Order。
5. OrderFactory 只能从 Trade 提供的可信成交快照创建单商户 Order，不得再查询 Goods、Offer、报价或地址 ACL。
6. Order 必须以 `orderPlanId` 或等价稳定业务键幂等处理创建命令；重复投递不得创建重复 Order。
7. Order 创建成功或失败必须发布稳定结果事实，Trade 据此记录 `orderId`、重试或执行补偿。
8. 不可恢复的 Order 创建失败不得遗留不可见的销售授权或库存承诺。
9. 多商户首期采用全有或全无：只有所有订单计划均成功形成并被 Trade 接受后，才能发起唯一 Payment；任一 Order 未完成时不得产生可支付对象。

### TC-R8 查询、取消与既有 Order 能力

1. Checkout 查询必须返回 Trade 总体状态、每个订单计划状态、已生成的订单 ID、失败原因、可继续动作，以及在渠道明确受理后形成的唯一待支付对象。
2. Checkout 查询和取消必须校验认证买家；越权访问不得泄露 Trade 是否存在。
3. Checkout 取消由 Trade 统一裁决；尚未形成 Payment 时停止推进并补偿，Payment 已准备或支付结果不确定时必须先取得 Payment 的安全裁决，不能直接关闭 Order 或释放资源。
4. `GET /api/orders`、`GET /api/orders/{orderId}` 和单订单取消继续由 Order 提供。
5. 当 Order 只在可信交易承诺后创建时，必须评估并清理恒定或失去业务意义的 provisional 状态与 `commitmentStatus`，不得保留两个事实源。

### TC-R9 多商户全有或全无

1. 首期多商户 Checkout 必须采用全有或全无策略。
2. 任一订单计划授权、预留或创建发生不可恢复失败时，整个 Trade 必须停止推进，并撤销已经形成的 Order、Reservation、SaleAuthorization 及后续权益承诺。
3. Trade 必须持久化每项补偿的状态；只有必要补偿全部完成后才能进入最终 `CLOSED`。
4. Trade 失败撤销 Order 必须使用独立、幂等的内部契约，不得复用买家主动取消语义或形成循环补偿。

### TC-R10 Trade 唯一 Payment 与待支付对象

1. 一个 Trade 在完整生命周期内必须且只能创建一个 Payment；Payment 的业务唯一键和幂等键必须是 `tradeId`，不得按每个 `orderId` 分别创建。
2. 创建 Payment 前，Trade 必须确认所有订单计划均已取得可信 `orderId`，且所有 Order 应付金额之和严格等于 Trade 应付金额。
3. Payment 必须保存不可变的 Trade 总额、币种和按 `orderPlanId/orderId/merchantId` 冻结的金额分配；商户实际结算由后续 Accounting/Settlement 处理。
4. Payment 本地记录建立只表示 `PAYMENT_PREPARING`；只有支付渠道明确受理并返回可安全使用的短期支付引用后，才能进入 `PAYMENT_READY` 并向用户暴露待支付对象。
5. Checkout 首次受理可返回 `202 + tradeId + statusUrl`；Checkout 查询在 `PAYMENT_READY` 时返回 `paymentId`、状态、总额、币种、过期时间和受控支付动作，在此之前 `payment` 必须为空。
6. 支付凭证、渠道密钥和不可撤销的永久链接不得通过 Checkout 公共响应或广播事件暴露。
7. Payment 捕获事实必须以 `tradeId` 相关，并携带或引用稳定分配版本；Trade 据此向每个 Order 投影与冻结分配一致的实付事实。

### TC-R11 Payment 失败、过期与关单竞争

1. 明确不可恢复且确认渠道未受理的 Payment 创建失败可以进入补偿；可重试基础设施失败必须保持 `PAYMENT_PREPARING` 并幂等重试。
2. 渠道调用超时、回调缺失或资金结果不确定时，Trade 必须进入 `PAYMENT_UNCERTAIN` 或等价状态，主动查询或安全撤销；在得到权威裁决前不得关闭 Order 或释放库存等资源。
3. Payment 必须具有受理截止时间。截止后禁止新的支付尝试；原 Trade 不得创建第二个 Payment。
4. Order/Trade 最终关单时间必须晚于 Payment 受理截止时间，保留可配置的安全宽限期；宽限期由渠道回调 SLA、主动查询周期和时钟偏差共同确定。
5. 支付是否按时以支付渠道的权威受理/捕获时间为准，不以回调到达本系统的时间为准。
6. 确认 Payment 未创建、未支付或已安全撤销后，Trade 才能关闭相关 Order，并幂等释放库存、销售授权和已锁定权益。
7. 支付成功优先于关单：关单竞争中确认已经支付时必须进入 `PAID`；如果 Trade/Order 已经关闭后才收到合法支付事实，必须进入退款流程并保留审计，不得静默拒绝或恢复履约。
8. Payment 过期并完成安全关单后，用户重新支付必须重新 Checkout 并创建新 Trade。

## 后续迭代

### 迭代 3：Pricing、Promotion 与权益生命周期

- 建立版本化 `PricingQuote`，冻结商品原价、成交价、运费、税费、订单级优惠和最终应付。
- 定义商品级、订单级、平台出资和商户出资优惠的行级分摊。
- 通过稳定契约协调活动、优惠券、红包等权益的锁定、核销、释放和过期。
- Trade 接受并冻结报价，不拥有活动匹配、叠加/互斥或权益余额规则。

### 迭代 4：Cart 接入

- 建立独立 Cart 上下文和可变购买意图模型。
- 支持从 Cart 发起 Checkout，并在受理时冻结 Cart 版本或内容摘要。
- Cart 变化不得修改已经受理的 Trade；重复提交仍由 `checkoutRequestId` 幂等收敛。

### 迭代 5：支付体验强化、关单运行治理与低延迟体验

- 在迭代 2 已建立的 Trade 唯一 Payment 和两阶段关单语义上，接入生产渠道策略、主动查询调度和自动退款运营门禁。
- 提供有限同步等待以及 `202 + tradeId + statusUrl` 慢路径。
- Checkout 关键链路使用独立容量、可观测 Deadline 和经过压测验证的延迟目标。

### 迭代 6：对账、ERP 协作与恢复

- 建立 Trade、Order、Promotion、Reservation、Payment、Accounting 的差异查询和审计修复命令。
- 支付渠道对账归 Payment/Accounting/Reconciliation，不归 Trade。
- ERP 通过 Order、Fulfillment、Inventory、WMS 等上下文发布的稳定事实接入，不直接依赖 Trade 内部表或修改聚合。
- 对账与 ERP 修复只能调用已授权命令，不得跨上下文直接写数据库。

### 并行技术路线：可靠异步与微服务拆分

- Broker、Inbox、支付关单、容量验证和微服务灰度继续按 `checkout-reliable-async/iteration-plan.md` 推进。
- 该技术路线必须以本规格的 `tradeId`、`orderPlanId` 和上下文所有权为业务语义来源；不得恢复以 Order 为 Process Manager 的旧模型。

## 质量目标

- 数据完整性：任何失败路径都不得遗留不可见的授权、库存承诺、重复 Order 或错误金额分配。
- 资金安全：支付状态未知时不释放资源；迟到支付必须可退款、可审计且不得恢复已关闭订单的履约。
- 可恢复性：重复、乱序、进程重启和部分基础设施失败不越级推进状态。
- 可维护性：Trade、Order、Pricing、Cart、Payment、ERP/Reconciliation 各自只有清晰的主要变化原因。
- 可演进性：增加优惠、购物车、拆单和统一支付时不再修改 Order 的创建入口职责。
- 可观测性：每个 Trade 和订单计划的当前状态、失败原因、期限和已创建订单可查询。
- 安全性：买家身份来自认证上下文，查询和取消不能跨买家访问。

## 当前最近迭代的非范围

- 不实现完整活动规则、优惠券、红包或动态运费/税费。
- 不实现 Cart 聚合。
- 不接入特定生产支付渠道；本迭代实现渠道受理/查询/撤销端口、Trade 唯一 Payment 语义和可替换测试适配器。
- 不接入 ERP 产品或完整业务/支付渠道对账。
- 不接入新的具体 Broker。
