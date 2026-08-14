# Trade / Checkout 边界演进设计

## 状态与设计权威

迭代 1 已实现独立四层 Trade 上下文：

```text
j-store-trade-boot -> j-store-trade-application -> j-store-trade-domain -> common-core
                   -> j-store-trade-infrastructure -> j-store-trade-domain
```

当前代码仍采用“先创建 provisional Order，再以 `orderId` 启动 Trade”的过渡流程。下一主迭代将 Trade / Checkout 提升为唯一用户下单入口，并同时建立独立 `tradeId` 和多商户 `TradeOrderPlan`。本设计是 Trade 业务编排的权威来源；`checkout-reliable-async/iteration-plan.md` 只负责 Broker、Inbox、低延迟和运行恢复等横切技术路线。

## 当前流程与目标流程

当前已实现流程：

```text
POST /api/orders
  -> Order 查询 Goods / Offer / User / Address 并创建 provisional Order
  -> OrderCreated -> StartTradeProcess(orderId)
  -> Trade 请求销售授权和库存预留
  -> TradeCommitmentConfirmed / Failed -> Order
```

下一迭代目标：

```text
POST /api/checkouts(checkoutRequestId)
  -> Trade 创建 tradeId 并冻结买家、收货和购买输入
  -> Trade 查询可信 Catalog / Offer 快照并取得基础报价
  -> Trade 生成一个或多个 TradeOrderPlan
  -> 每个计划取得 SaleAuthorization 与 StockReservation
  -> 已批准的多商户成功策略满足
  -> Trade 发布内部 Order 创建命令
  -> Order 幂等创建可信单商户订单并返回创建事实
  -> Trade 确认全部 Order 已创建且金额守恒
  -> Trade 按 tradeId 幂等准备唯一 Payment
  -> 支付渠道明确受理后进入 PAYMENT_READY 并返回待支付对象
```

失败与取消必须沿相反方向补偿已经形成的承诺。Translator 只映射语言，不决定拆单、成功策略、重试或补偿。

## Trade 聚合演进

### 聚合与实体

```text
TradeProcess
  id: TradeId
  checkoutRequestId
  buyerId
  requestDigest
  status
  orderPlans: List<TradeOrderPlan>
  currentDeadline
  failureCode / failureReason
  createdAt / updatedAt

TradeOrderPlan
  orderPlanId
  merchantId
  fulfillmentGroup
  items: List<TradeItemSnapshot>
  amountAllocation
  status
  authorizationIds
  reservationIds / reservationExpiresAt
  orderId?
  failureCode?
```

- `TradeId` 是 Trade 的聚合身份、用户查询键和主相关键。
- `checkoutRequestId` 是买家范围内的幂等业务键；`requestDigest` 用于拒绝同键不同内容。摘要必须基于版本化的规范输入生成：字段顺序固定、集合按稳定业务键排序、金额使用最小货币单位、空值和默认值语义固定，并排除认证上下文派生值、时间戳及追踪字段。
- `TradeOrderPlan` 是拆单结果，不是 Order 聚合。它保存创建 Order 所需的可信计划及跨上下文承诺进度。
- `orderPlanId` 是内部 Order 创建的幂等键；同一 Trade 可包含多个计划，且未来可在同商户内按履约策略进一步拆分。
- `orderId` 只在 Order 创建成功后写回计划，不再作为 Trade 的前置身份。

### 状态表达

Trade 总体状态至少需要表达：

```text
PREPARING -> AUTHORIZING -> RESERVING -> CREATING_ORDERS
                                                |
                                                v
                                      PAYMENT_PREPARING
                                                |
                              +-----------------+----------------+
                              |                                  |
                              v                                  v
                       PAYMENT_READY                    PAYMENT_UNCERTAIN
                              |                                  |
                              v                                  |
                            PAYING <------------------------------+
                              |
                              v
                             PAID

确定未受理 / 已安全撤销 -> COMPENSATING -> CLOSED
```

计划状态至少区分 `PLANNED`、`AUTHORIZED`、`RESERVED`、`ORDER_CREATING`、`ORDER_CREATED`、`FAILED`、`COMPENSATING`、`CLOSED`。最终枚举和合法转换由迭代 2 的失败测试先行确定，但不得把多个计划压缩成一个全局 `orderId` 状态。

首期已经批准多商户全有或全无。任何计划失败都会阻止 Payment 准备并触发全局补偿；事件到达顺序不得隐式形成部分成功。

## 公开 Checkout API

Trade Boot 提供用户接口：

```text
POST /api/checkouts
GET  /api/checkouts/{tradeId}
POST /api/checkouts/{tradeId}/cancel
```

创建请求第一版包含 `checkoutRequestId`、直接购买行项和收货信息。认证买家 ID 由 `@CurrentUserId` 注入；客户端不能提交买家或可信商户身份。顶层 `merchantId` 从公开请求移除，商户由 Offer/报价事实推导。

创建响应至少包含 `tradeId`、总体状态、`statusUrl` 和已经形成的 `orderIds`。第一版允许返回 `202` 持久化受理结果；HTTP 请求结束不得终止后台流程。

相同买家和 `checkoutRequestId`：

- 请求摘要一致时返回原 Trade，不重新授权、预留或创建 Order。
- 请求摘要冲突时返回明确业务错误。
- 不同买家之间不能观察或复用对方的幂等键。

Checkout 查询返回 Trade 和各订单计划的可公开状态、失败原因、期限与 `orderId`。只有状态达到 `PAYMENT_READY` 后才附带唯一待支付对象：`paymentId`、公开状态、总额、币种、`expiresAt` 和短期 `payAction`；此前以及失败/补偿状态返回 `payment: null`。越权查询或取消按资源不存在处理，避免泄露交易存在性。

## 成交快照与基础报价

Trade Application 通过消费方定义的 ACL/端口取得：

- Catalog 已发布商品资料和版本化展示快照；
- Offer 的商户、店铺、SKU、版本、当前基础价格、渠道和履约策略；
- ACTIVE 买家资料快照；
- 规范化收货地址；
- 基础报价结果。

第一版报价端口可以使用当前 Offer 单价并计算小计，以保持既有行为，但必须返回稳定的报价标识/版本、币种、行金额和总额。Order 不得自行重新计算或回查当前事实。

后续 Pricing / Promotion 上下文替换报价端口实现，并增加优惠锁定、核销和释放协议；Trade 只冻结报价与分摊，不拥有促销规则。

## 拆单与承诺编排

Trade 根据可信 Offer 商户和履约分组生成 `TradeOrderPlan`。每个计划独立记录授权和预留，但 Trade Process 统一裁决是否可以进入 Order 创建阶段。

已经批准的首期策略是全有或全无：

1. 所有计划完成销售授权后才认为授权阶段通过。
2. 所有计划完成库存预留后才进入 Order 创建阶段。
3. 任一计划失败时停止未发出的后续命令，并释放其它计划已经取得的 Reservation 和 SaleAuthorization。
4. Order 创建发生不可恢复失败时，关闭或撤销已经创建的订单，并补偿全部资源承诺。

未来若提出部分成功，必须作为新的产品变更独立设计用户确认、金额调整、支付、取消和售后语义，不能只放宽一条状态判断。

## Trade 到 Order 的内部创建契约

Order 创建降为版本化内部写契约，概念载荷为：

```text
CreateOrderFromTradeCommand
  tradeId
  orderPlanId
  merchantId
  buyerSnapshot
  recipientSnapshot
  itemSnapshots
  amountAllocation
  occurredAt / deadline
```

Order Application 使用 `orderPlanId` 或等价稳定业务键幂等创建单商户 Order。OrderFactory 只验证和组装可信成交快照，不调用 Goods、Offer、Pricing 或地址 ACL。

结果契约至少区分：

- `OrderCreatedFromTradeEvent(tradeId, orderPlanId, orderId)`；
- 明确不可恢复业务失败的创建失败事实；
- 基础设施失败通过消息重试恢复，不能伪装为业务拒绝。

Order 保存与创建成功事实的 Outbox 必须位于同一事务。重复创建命令、重复成功事实和进程重启不得产生多个订单或多次后续支付效果。

全部计划的 `OrderCreatedFromTradeEvent` 被 Trade 接受后，Trade 必须在一个聚合裁决中验证：所有计划均为 `ORDER_CREATED`、不存在未知结果、订单金额之和等于 Trade 应付金额。只有该裁决成功才能原子进入 `PAYMENT_PREPARING` 并写入唯一支付准备命令 Outbox。

## Trade 唯一 Payment 与金额分配

目标 Payment 契约以 `tradeId` 为业务唯一键：

```text
PrepareTradePaymentCommand
  tradeId
  amount / currency
  allocations[]
    orderPlanId
    orderId
    merchantId
    amount
  acceptBefore
  desiredPaymentExpiresAt
```

- Payment 本地聚合可以先进入 `PREPARING`，但只有渠道明确受理并返回可控短期支付引用后才发布 `PaymentPreparedEvent`。
- Payment 必须对 `tradeId` 建立数据库唯一约束；重复命令返回同一 Payment，不得重新请求渠道或创建第二个支付单。
- 分配快照在 Payment 创建时冻结并满足同币种金额守恒。后续 Accounting/Settlement 使用该快照分账，但不回写或重算原始 Payment。
- `PaymentCapturedEvent` 使用 `tradeId` 相关，并携带 `providerTransactionId`、渠道受理/捕获时间和分配版本；Trade 再向每个 Order 投影对应金额。
- 单 Order 售后退款必须映射回该 Order 的冻结 Payment 分配；累计退款不得超过对应分配，全部分配之和不得超过 Payment 捕获金额。

现有 `CreatePaymentForOrderCommand`、按 `orderId` 查询和 `payment_orders.order_id UNIQUE` 是待替换的当前实现，不保留双主链兼容层。

## Order 收缩与保留能力

迁移完成后从公开 Order 边界删除：

- `POST /api/orders` 及其请求 DTO；
- 面向用户的 `OrderUseCase.createOrder`；
- OrderFactory 对 Goods、Offer、Pricing 和地址 ACL 的依赖；
- `OrderCreated -> StartTradeProcess` 的旧启动链路。

Order 继续提供：

- 买家订单列表和详情；
- 已形成订单的单订单取消；
- 支付、履约、退款、售后事实投影和订单生命周期规则。

当 Order 只在承诺形成后创建时，应直接以可信成交状态创建，并评估删除 `CREATED/PENDING_OFFER/FAILED` 等 provisional 维度。若保留某个状态，必须有独立业务含义，不能与 Trade 状态形成第二权威。

## 取消、过期与两阶段关单

- Checkout 取消：由 Trade 裁决尚未形成订单的计划，停止后续推进并补偿授权、库存及后续权益锁定。
- Payment 已进入准备、就绪、支付中或结果未知后，取消先转换为 `CLOSING`/取消处理中并请求 Payment 查询或安全撤销；资金状态未知时不得释放资源。
- Payment 的 `acceptBefore` 是渠道最晚受理新支付的截止时间；Order/Trade 的 `closeAfter` 必须晚于该时间，并包含可配置安全宽限期。
- 宽限期根据渠道回调 SLA、主动查询周期、网络长尾和时钟偏差配置；到达 `closeAfter` 仍不能替代 Payment 权威裁决。
- 成功与关单竞争以渠道 `providerAcceptedAt/providerCapturedAt` 判断，不以回调到达时间判断。截止前已被渠道受理的支付优先进入已支付路径。
- 只有收到“确认未创建、未支付或已安全撤销”的 Payment 事实后，Trade 才发出幂等内部撤销 Order、释放 Reservation/SaleAuthorization/权益命令，并等待必要补偿完成后进入 `CLOSED`。
- 若合法支付事实在 Trade/Order 已关闭后迟到，保持订单关闭且禁止恢复履约，发起幂等退款并记录资金审计。
- Payment 过期关单后不得为原 Trade 创建第二个 Payment；再次购买从新的 Checkout/Trade 开始。
- Order 取消：由 Order 校验单订单生命周期并发布稳定取消事实；Trade/Payment/Inventory 等上下文消费事实完成各自补偿。
- 一个 Trade 产生多个 Order 后，取消单个 Order 不等同于自动取消整个 Trade；是否联动由后续明确的产品规则决定。

## 集成契约与相关键迁移

Checkout 和统一 Payment 主链使用 `tradeId` 作为 correlation/业务幂等键，使用 `orderPlanId` 作为订单计划级 partition/幂等键；Fulfillment 和单订单售后继续以 `orderId` 为业务键，退款通过冻结分配关联回 Trade Payment。

迁移必须一次性更新仓库内生产者、消费者、序列化测试、路由和数据库约束。项目尚未发布，不保留 `orderId` 和 `tradeId` 双主链、双投递或兼容消息别名。

## 持久化演进

现有 `trade_process.order_id` 唯一模型需要直接演进为：

```text
trade_process
  trade_id PK
  buyer_id
  checkout_request_id
  request_digest
  status / deadline / failure

trade_order_plan
  order_plan_id PK
  trade_id FK
  merchant_id
  fulfillment_group
  status
  order_id NULL UNIQUE

trade_order_plan_item
trade_order_plan_authorization
trade_order_plan_reservation

payment_order
  payment_id PK
  trade_id UNIQUE
  amount / currency
  status
  provider_reference
  accept_before / expires_at

payment_allocation
  payment_id FK
  order_plan_id UNIQUE
  order_id UNIQUE
  merchant_id
  amount
```

数据库必须保证买家范围内 `checkout_request_id` 唯一、`order_plan_id` 唯一以及一个计划最多绑定一个 Order。PO 与领域模型必须完整往返，写仓储要求外层事务。

## 后续能力接入

- Pricing / Promotion：提供版本化报价、优惠分摊和权益生命周期协议。
- Cart：提供可变购买意图；Trade 受理后冻结版本，后续 Cart 变化不反向修改 Trade。
- Payment 渠道运营：在统一支付契约上接入具体生产渠道、查询限流、自动退款门禁和渠道级 SLO。
- ERP：消费 Order、Fulfillment、Inventory、WMS 的稳定事实或通过各上下文授权命令协作，不读取 Trade 内部表。
- Reconciliation：比较跨上下文事实并生成审计修复命令，不成为新的事实权威。

## 验证策略

- Trade domain：独立身份、规范请求摘要、拆单不变量、计划状态、全有或全无、支付屏障、补偿和迟到事件。
- Trade application：快照准备、命令顺序、每计划并发、全部 Order 汇聚、唯一 Payment、取消/过期裁决和重启恢复。
- Trade infrastructure：JPA 往返、唯一约束、乐观并发和事务要求。
- Trade boot：认证、请求校验、越权隐藏、创建/查询/取消 HTTP 契约。
- Order domain/application：可信快照创建、`orderPlanId` 幂等、单商户不变量和既有生命周期回归。
- Integration contracts：序列化、版本、稳定 ID、correlation/partition 和 Handler 唯一装配。
- Observability：Trade/Payment 使用低基数状态、阶段和结果标签，并通过 `j-store-observability-spring` 复用 HTTP correlation、Tracing 与 Prometheus 运行时；不得在通用模块或日志/指标中放入支付凭证、买家资料、消息 payload、动态 Trade/Order ID 标签。
- 端到端：单商户、多商户、重复提交、任一计划失败、Order 创建失败、支付准备失败/未知/过期、取消与支付竞争、关单后迟到支付退款和进程重启。
- 交付前运行相关模块测试、全仓质量门禁，并由非实现者评审公共 API、金额、库存和订单状态语义。

## 风险与恢复

- 多商户全有或全无、Trade 唯一 Payment、支付成功优先和安全关单已经成为批准的产品行为；实现不得退化为每 Order 独立支付或计时到点直接释放资源。
- 这是公共 API、金额、库存、订单状态和消息契约变更，必须形成独立评审证据。
- 开发期不保留旧接口或旧数据兼容；候选失败时回退整个候选分支并重建开发数据库。
- 禁止让旧 `OrderCreated -> Trade` 与新 `Trade -> Order` 两条真实创建链路同时启用。
