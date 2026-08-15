# Trade / Checkout 边界演进设计

## 状态与设计权威

迭代 1 已实现独立四层 Trade 上下文：

```text
j-store-trade-boot -> j-store-trade-application -> j-store-trade-domain -> common-core
                   -> j-store-trade-infrastructure -> j-store-trade-domain
```

当前代码已将 Trade / Checkout 提升为唯一用户下单入口，并建立独立 `tradeId`、多商户 `TradeOrderPlan`、承诺后 Order 创建和唯一 SettlementPlan。迭代 2H 已实现可信快照、Order 业务幂等和基础失败补偿；补偿完成确认、Payment 渠道状态机、取消竞争及显式履约放行仍是后续切片。本设计是 Trade 业务编排的权威来源；`checkout-reliable-async/iteration-plan.md` 只负责 Broker、Inbox、低延迟和运行恢复等横切技术路线。

2026-08-15 已确认项目尚未上线，本设计按目标模型做一次性破坏性替换。实现不得保留 `tradeId == orderId`、provisional Order、旧 `OrderCreated -> StartTradeProcess` 或每 Order 一个 Payment 的过渡分支。代码、数据库、消息、测试和文档必须在同一候选中收敛。

## 核心设计决策

| 决策 | 结论 | 约束理由 |
|---|---|---|
| Trade 身份 | Checkout 受理时立即生成独立 `TradeId` | Order 尚不存在时也能幂等受理、查询和恢复 |
| 订单规划 | 一个 Trade 包含一个或多个 `TradeOrderPlan` | 支持按商户/履约组拆单，避免公共 API 绑定单 Order |
| 库存与 Order 顺序 | 所有计划先完成授权和预留，再创建 Order | 避免产生缺货 Order；失败时优先补偿资源承诺 |
| Payment 屏障 | 全部计划 `RESERVED + ORDER_CREATED` 且金额守恒后才能准备支付 | 禁止部分订单可支付及库存结果未知时收款 |
| 结算基数 | 一个 Trade 全生命周期唯一 SettlementPlan | B2C 全款、预售分期和 B2B 应收共享同一交易主线 |
| 首期支付 | SettlementPlan 只含一个 `FULL` 分期 | 保持当前 B2C 交付范围，同时不固化唯一 Payment 假设 |
| 买方主体 | `BuyerParty` 与 `ActingPrincipal` 分离 | 同时表达个人消费者和由员工代表的企业采购方 |
| 履约放行 | Trade 按策略发布显式授权 | 不把全额支付写死为所有交易的履约前置条件 |
| 一致性方式 | 各上下文本地事务 + Outbox/Inbox + Trade Saga | 不使用跨库事务；重复、乱序和重启必须收敛 |
| 兼容策略 | 一次性替换旧 API、消息、表结构和调用方 | 项目未上线，不为可丢弃数据增加永久复杂度 |

## 已删除流程与当前流程

已删除的过渡流程：

```text
POST /api/checkouts
  -> Trade 校验直接购买输入并通过内部端口创建 provisional Order
  -> OrderCreated -> StartTradeProcess(orderId)
  -> Trade 请求销售授权和库存预留
  -> TradeCommitmentConfirmed / Failed -> Order
```

当前已实现的基础主链：

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
  -> Trade 建立唯一 SettlementPlan
  -> 首期按 FULL 分期幂等准备 Payment
  -> 支付渠道明确受理后进入 PAYMENT_READY 并返回待支付对象
```

失败与取消必须沿相反方向补偿已经形成的承诺。Translator 只映射语言，不决定拆单、成功策略、重试或补偿。

### 迭代 2H 已实现边界与剩余恢复责任

- Checkout 准备阶段通过消费方端口读取并冻结买家资料、规范化地址、Catalog 展示字段、Offer 成交字段和基础金额；后续 Order 创建只使用 Trade 持久化快照。
- Trade 到 Order 使用内部可信命令；`orderPlanId` 是业务幂等键，`planDigest` 区分安全重放与冲突，数据库唯一键及来源外键作为最终防线。
- Order 从可信计划直接创建为 `ACTIVE + CONFIRMED`。新主链不再等待 Order 自己重新确认销售承诺。
- 销售授权、库存预留或 Order 创建的业务失败会持久化 Trade 失败原因，并通过 Outbox 发布已取得资源的释放命令；部分 Order 已形成时通过版本化命令请求 Order 撤销。
- Trade 进入失败态后仍会接收迟到的销售授权、库存预留或在途 Order 创建成功事实；前两者立即发出对应释放命令，迟到 Order 则先保存可信 `orderId` 再通过内部命令撤销，避免异步竞态遗留资源承诺或可见订单。
- 买家取消 Trade 来源的未支付 Order 时，Order 先进入 `CANCELLATION_PENDING`，并将携带 `tradeId + orderPlanId` 的取消请求事实交由 Trade 裁决。若尚未开始结算，当前全有或全无策略通过消息命令关闭全部已形成 Order 并释放资源；若 Payment 已开始准备、就绪或结果未知，Trade 先进入 `CLOSING`，不把 OrderPlan 伪标为已关闭，并保留 Order、库存和授权，只有明确拒绝或后续渠道安全撤销事实才能继续补偿。Trade 内部撤销使用独立领域事实，避免再次触发买家取消补偿循环。
- Trade 来源 Order 支付完成后，以来源相关键发布 `ConfirmInventoryCommand`，库存确认仍按 `tradeId + orderPlanId` 幂等收敛；历史非 Trade Order 不进入该新链路。
- 技术异常继续抛出，由消息重试恢复，不被写成业务失败。
- 当前失败状态表示“已决定失败且补偿命令已可靠发出”，尚不表示所有补偿均已被下游确认。后续必须为 Order 撤销、库存释放和销售授权释放增加逐项确认状态、重试调度与最终 `CLOSED` 收敛，不能把 `FAILED` 当作补偿已完成。

## Trade 聚合演进

### 聚合与实体

```text
TradeProcess
  id: TradeId
  checkoutRequestId
  buyerParty: BuyerPartySnapshot
  actingPrincipalId
  requestDigest
  status
  orderPlans: List<TradeOrderPlan>
  commitmentPolicy: CommitmentPolicySnapshot
  settlementTerms: SettlementTermsSnapshot
  settlementPlanId?
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
- `BuyerPartySnapshot` 至少包含 `partyType` 与 `partyId`；首期个人交易的 `partyId` 等于认证用户 ID。`actingPrincipalId` 独立保存发起操作的认证主体，为企业采购授权留出稳定边界。
- `checkoutRequestId` 是买家范围内的幂等业务键；`requestDigest` 用于拒绝同键不同内容。摘要必须基于版本化的规范输入生成：字段顺序固定、集合按稳定业务键排序、金额使用最小货币单位、空值和默认值语义固定，并排除认证上下文派生值、时间戳及追踪字段。当前 `v2` 规范对每个字段使用显式空值标记与长度前缀，禁止通过 `|`、`:`、`;` 等用户可输入分隔符拼接，以免不同请求产生相同规范字节流。
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

### SettlementPlan 创建屏障

Payment 准备是 Trade 聚合上的单一原子裁决，不是应用服务对若干查询结果的临时拼接。只有同时满足以下谓词才能从 `CREATING_ORDERS` 转入 `PAYMENT_PREPARING`：

```text
orderPlans.isNotEmpty
&& orderPlans.all(status == ORDER_CREATED)
&& orderPlans.all(reservationIds.isNotEmpty && reservationExpiresAt > now)
&& orderPlans.all(orderId != null)
&& orderPlans.sum(amountAllocation.payable) == trade.payableAmount
&& desiredPaymentExpiresAt + safetyMargin <= orderPlans.min(reservationExpiresAt)
&& settlementPlanId == null
&& settlementPreparationRequested == false
```

状态保存与 `PrepareTradeSettlementCommand` 写入 Outbox 位于同一事务。重复 Order 成功事实只能得到“不变更”；并发接受最后几个计划结果时，由聚合版本和数据库乐观锁保证只有一个事务越过屏障。首期结算处理器创建 `PREPAID + FULL` 计划并准备一个 Payment；当前默认受理窗口为 1 分钟、支付动作窗口为 15 分钟、安全余量为 2 分钟。库存预留剩余时间不足以覆盖动作窗口和安全余量时不得请求渠道，本切片直接将 Trade 置为失败并撤销 Order、释放库存与授权；未来支持安全续期后可在该裁决前先尝试续期。

## 上下文职责与写权限

| 上下文 | 拥有的事实 | 接受的关键写入 | 明确禁止 |
|---|---|---|---|
| Trade | `tradeId`、请求幂等、计划、冻结报价、Saga 进度、SettlementPlan 关联 | Checkout 创建/取消、跨上下文结果事实 | 直接修改库存、订单、支付或应收表 |
| Inventory | ATP 与 Reservation | 按 `tradeId/orderPlanId` 预留、释放、确认消耗 | 创建 Order 或 Payment |
| Order | 单订单成交快照与生命周期 | 按 `orderPlanId` 内部幂等创建、内部撤销、支付/履约投影 | 对外创建订单、重新报价、启动 Trade |
| Payment | Payment、PaymentAttempt、渠道交互、捕获/退款流水 | 按 `settlementPlanId/installmentId` 幂等准备支付 | 把支付对象当作结算条款或应收事实 |
| Accounting/Receivable | 应收、到期、逾期与核销事实 | 按 SettlementPlan 产生和结清应收 | 用商户结算单替代买方应收 |

Trade 只协调命令和事实，不跨上下文写数据库。Cart、Promotion/Pricing 后续通过 Trade 消费方定义的端口或版本化消息接入，不得调用 Order 创建接口绕过 Trade。

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

Checkout 查询返回 Trade 和各订单计划的可公开状态、失败原因、期限与 `orderId`。只有状态达到 `PAYMENT_READY` 且支付动作仍有效时才附带唯一待支付对象：`paymentId`、公开状态、总额、币种、`expiresAt` 和短期 `payAction`；此前、失败/补偿状态以及动作自然过期后返回 `payment: null`，不能让支付动作过期导致整个 Checkout 查询失败。越权查询或取消按资源不存在处理，避免泄露交易存在性。

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

全部计划的 `OrderCreatedFromTradeEvent` 被 Trade 接受后，Trade 必须在一个聚合裁决中验证：所有计划均为 `ORDER_CREATED`、不存在未知结果、订单金额之和等于 Trade 应付金额。只有该裁决成功才能原子进入 `SETTLEMENT_PREPARING` 并写入唯一结算计划准备命令 Outbox。

内部 Order 创建必须以 `orderPlanId` 建立数据库唯一约束。业务拒绝发布 `OrderCreationRejectedFromTradeEvent` 并触发全局补偿；超时或基础设施异常不发布伪失败事实，由 Inbox/Outbox 重试和停留告警恢复。

## Trade 唯一 SettlementPlan、Payment 与金额分配

目标结算契约以 `tradeId` 为业务唯一键：

```text
PrepareTradeSettlementCommand
  tradeId
  settlementMode
  fulfillmentReleaseRule
  installments[]
    installmentId
    purpose
    amount
  amount / currency
  allocations[]
    orderPlanId
    orderId
    merchantId
    amount
  acceptBefore
  desiredPaymentExpiresAt
```

- 首期 SettlementPlan 固定包含一个 `FULL` 分期；Payment 本地聚合可以先进入 `PREPARING`，但只有渠道明确受理并返回可控短期支付引用后才发布 `PaymentPreparedEvent`。
- Payment 必须对 `settlementPlanId + installmentId` 建立数据库唯一约束；重复命令返回同一 Payment，不得重新请求渠道或为同一分期创建第二个支付单。
- 每个分期的 `paymentId` 只能由对应的 Payment 结果事实写回 Trade；Trade 按 `installmentId` 保存引用，不预生成或猜测 Payment 标识。数据库唯一约束是最终防线，Trade 聚合屏障和 Payment 应用幂等共同避免重复渠道请求。
- 分配快照在 Payment 创建时冻结并满足同币种金额守恒。后续 Accounting/Settlement 使用该快照分账，但不回写或重算原始 Payment。
- `PaymentCapturedEvent` 使用 `tradeId + installmentId` 相关，并携带 `providerTransactionId`、渠道受理/捕获时间和分配版本；Trade 再向每个 Order 投影对应金额。
- 单 Order 售后退款必须映射回该 Order 的冻结 Payment 分配；累计退款不得超过对应分配，全部分配之和不得超过 Payment 捕获金额。

旧 `CreatePaymentForOrderCommand` 已从主链删除；新支付对象以 `settlementPlanId + installmentId` 唯一。既有 `PaymentOrder` 仅供尚未迁移的退款/管理能力使用，不得重新接入 Checkout 主链。

### 支付准备闭环

Trade 越过结算屏障后只发布 `PreparePaymentInstallmentCommand`，不得通过适配器直接写 Payment 仓储。命令冻结 `tradeId`、`settlementPlanId`、分期、金额、币种、Order 分配、`acceptBefore` 与 `expiresAt`；Payment 以 `settlementPlanId + installmentId` 作为渠道幂等键。

Payment 的准备结果严格分为三类：

- `PaymentPreparedEvent`：渠道明确受理并返回受控短期支付动作，Payment 进入 `READY`；
- `PaymentPreparationRejectedEvent`：渠道明确确认未创建或未受理，Payment 进入 `REJECTED`，Trade 可以开始补偿；
- `PaymentPreparationUncertainEvent`：超时或结果不可判定，Payment 与 Trade 进入 `UNCERTAIN`，必须保留 Order、库存和授权，等待后续权威查询。

渠道准备采用三个明确阶段：独立事务先提交稳定的 `PREPARING/paymentId`；挂起数据库事务后以该 paymentId 和业务幂等键调用渠道；再用独立事务保存 `READY/REJECTED/UNCERTAIN` 并写入结果 Outbox。渠道受理后即使进程崩溃或结果事务提交失败，重试也必须复用原 paymentId 与幂等键，不得生成与渠道元数据不一致的新身份。实现不得把网络异常解释为明确拒绝。Trade 按 `installmentId` 持久化 Payment 引用与支付阶段，不复制渠道凭证或支付动作。Checkout 查询通过只读 Payment ACL 获取当前且未过期的 `READY` Payment，并且只返回 `paymentId`、状态、金额、币种、`expiresAt` 和受控 `payAction`；其它阶段或动作过期后必须返回 `payment: null`。公开支付动作、时间和失败原因在进入可展示状态前必须满足数据库持久化契约，非法或超限的渠道结果按 `UNCERTAIN` 处理。若渠道已明确受理，补偿所需的原始渠道身份必须独立、完整持久化，即使其它公开元数据非法也不能丢弃撤销能力或形成无限重试。

渠道撤销同样采用持久化意图、无事务外部调用和持久化结果三个阶段。若准备调用仍在途，Payment 先进入 `PREPARATION_CANCELLING`，此时不得调用撤销渠道或向 Trade 发布撤销确认；原准备命令必须用稳定幂等键完成本次渠道裁决。明确拒绝可直接确认未创建，结果未知则进入 `CANCELLING` 并查询/撤销，明确 `Accepted` 则先保存渠道引用再进入 `CANCELLING`，随后以 `settlementPlanId + installmentId + cancel` 为稳定幂等键重新请求渠道。撤销结果必须与请求发起时所见的渠道引用一致，早于受理事实取得的结果不得关闭更新后的 Payment。业务 `cancellationReason` 与最近一次渠道技术失败必须分开持久化，撤销确认和后续 Order 关闭始终使用原始业务原因。Trade 只有消费有效撤销确认事实后才从 `CLOSING` 进入失败补偿，不能把命令受理或网络异常当作资金安全结论。这里的 `Accepted` 仅表示渠道支付单已受理，不等于资金捕获；若 Trade/Order 已关闭后收到合法 `Captured`，保持业务关闭并进入幂等退款流程，禁止恢复履约。在统一 `PaymentCaptured` 契约和 Trade 消费者落地前，不发布缺少 `tradeId + installmentId` 的临时 `OrderPaid` 事实到 `trade.events`。

`acceptBefore` 表示 Payment 最晚可以开始受理命令的时间，`expiresAt` 表示库存承诺允许支付动作存续的上限；渠道返回的动作可以早于该上限过期，只要 `acceptedAt < providerExpiresAt <= expiresAt`。内部开发渠道不得在 `production` Profile 中注册；生产环境未提供真实渠道适配器时必须启动失败。

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
- 当前切片已为 `PREPARING/READY/REJECTED/UNCERTAIN` Payment 建立统一渠道撤销协议；在途准备先进入 `PREPARATION_CANCELLING` 等待准备结果，其它状态进入 `CANCELLING`，只有与当前支付事实匹配的渠道确认才进入 `CANCELLED` 并通知 Trade。生产环境仍需接入真实撤销适配器；定时主动查单与人工恢复能力尚未落地。
- Payment 的 `acceptBefore` 是渠道最晚受理新支付的截止时间；Order/Trade 的 `closeAfter` 必须晚于该时间，并包含可配置安全宽限期。
- 宽限期根据渠道回调 SLA、主动查询周期、网络长尾和时钟偏差配置；到达 `closeAfter` 仍不能替代 Payment 权威裁决。
- 成功与关单竞争以渠道 `providerAcceptedAt/providerCapturedAt` 判断，不以回调到达时间判断。截止前已被渠道受理的支付优先进入已支付路径。
- 只有收到“确认未创建、未支付或已安全撤销”的 Payment 事实后，Trade 才发出幂等内部撤销 Order、释放 Reservation/SaleAuthorization/权益命令，并等待必要补偿完成后进入 `CLOSED`。
- 若合法支付事实在 Trade/Order 已关闭后迟到，保持订单关闭且禁止恢复履约，发起幂等退款并记录资金审计。
- Payment 过期关单后不得为原 Trade 创建第二个 Payment；再次购买从新的 Checkout/Trade 开始。
- Order 取消：由 Order 校验单订单生命周期并发布稳定取消事实；Trade/Payment/Inventory 等上下文消费事实完成各自补偿。
- 首期多商户全有或全无策略下，支付前取消任一 Order 会联动关闭整个 Trade；未来若引入部分成功，必须另行批准单 Order 取消后的金额、Payment 与其它 Order 保留规则。

## 集成契约与相关键迁移

Checkout 和统一 Payment 主链使用 `tradeId` 作为 correlation/业务幂等键，使用 `orderPlanId` 作为订单计划级 partition/幂等键；Fulfillment 和单订单售后继续以 `orderId` 为业务键，退款通过冻结分配关联回 Trade Payment。

迁移必须一次性更新仓库内生产者、消费者、序列化测试、路由和数据库约束。项目尚未发布，不保留 `orderId` 和 `tradeId` 双主链、双投递或兼容消息别名。

目标主链消息矩阵：

| 消息 | 方向 | 业务幂等键 | correlation / partition |
|---|---|---|---|
| `AuthorizeTradeSaleCommand` | Trade → Store | `orderPlanId` | `tradeId` / `orderPlanId` |
| `ReserveTradeInventoryCommand` | Trade → Inventory | `orderPlanId` | `tradeId` / `orderPlanId` |
| `CreateOrderFromTradeCommand` | Trade → Order | `orderPlanId` | `tradeId` / `orderPlanId` |
| `OrderCreatedFromTradeEvent` | Order → Trade | `orderPlanId` | `tradeId` / `orderPlanId` |
| `PrepareTradeSettlementCommand` | Trade → Settlement | `tradeId` | `tradeId` / `tradeId` |
| `PreparePaymentInstallmentCommand` | Settlement → Payment | `settlementPlanId + installmentId` | `tradeId` / `tradeId` |
| `PaymentPreparedEvent` | Payment → Trade | `settlementPlanId + installmentId` | `tradeId` / `tradeId` |
| `PaymentCapturedEvent` | Payment → Trade | `installmentId + providerTransactionId` | `tradeId` / `tradeId` |

旧的 `StartTradeProcessCommand`、`TradeCommitmentConfirmedIntegrationEvent`、`TradeCommitmentFailedIntegrationEvent` 和 `CreatePaymentForOrderCommand` 从主链删除。若其它后续上下文仍需要订单事实，应消费新的 Order 生命周期事件，不得借旧消息恢复 Order 作为协调者。

## 持久化演进

持久化模型已直接演进为：

```text
trades
  trade_id PK
  buyer_party_type / buyer_party_id
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

trade_payment
  payment_id PK
  settlement_plan_id + installment_id UNIQUE
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

由于没有生产兼容要求，数据库交付采用空库重建策略：直接修订当前基线/开发迁移链和测试夹具，不新增旧列、兼容视图、双写、回填或数据转换脚本。候选切换前删除并重建开发数据库，Flyway 从空库执行必须成功。

## 失败与补偿矩阵

| 失败位置 | 是否允许创建 Payment | Trade 动作 | 补偿完成条件 |
|---|---:|---|---|
| 快照/报价失败 | 否 | Trade 失败，无外部承诺 | 无 |
| 销售授权失败 | 否 | 停止后续计划，释放已取得授权 | 所有授权已释放 |
| 库存预留失败/过期 | 否 | 停止创建 Order，释放 Reservation 与授权 | 所有资源承诺已释放 |
| 部分 Order 创建失败 | 否 | 内部撤销已创建 Order，释放全部 Reservation 与授权 | Order 撤销及资源释放均确认 |
| Payment 明确未受理 | 否（不得重建第二个） | 撤销 Order 并补偿资源 | Payment 未受理事实明确且补偿完成 |
| Payment 结果未知 | 否 | 保持资源与 Order，查询或安全撤销 | 获得渠道权威结果后再裁决 |
| Payment 已捕获 | SettlementPlan 的对应分期已存在 Payment | 投影各 Order 实付，确认消耗库存 | 金额分配守恒并全部投影 |

## 后续能力接入

- Pricing / Promotion：提供版本化报价、优惠分摊和权益生命周期协议。
- Cart：提供可变购买意图；Trade 受理后冻结版本，后续 Cart 变化不反向修改 Trade。
- Payment 渠道运营：在统一支付契约上接入具体生产渠道、查询限流、自动退款门禁和渠道级 SLO。
- ERP：消费 Order、Fulfillment、Inventory、WMS 的稳定事实或通过各上下文授权命令协作，不读取 Trade 内部表。
- Reconciliation：比较跨上下文事实并生成审计修复命令，不成为新的事实权威。

## 验证策略

- Trade domain：独立身份、规范请求摘要、拆单不变量、计划状态、全有或全无、支付屏障、补偿和迟到事件。
- Trade application：快照准备、命令顺序、每计划并发、全部 Order 汇聚、唯一 SettlementPlan、取消/过期裁决和重启恢复。
- Trade infrastructure：JPA 往返、唯一约束、乐观并发和事务要求。
- Trade boot：认证、请求校验、越权隐藏、创建/查询/取消 HTTP 契约。
- Order domain/application：可信快照创建、`orderPlanId` 幂等、单商户不变量和既有生命周期回归。
- Integration contracts：序列化、版本、稳定 ID、correlation/partition 和 Handler 唯一装配。
- Observability：Trade/Payment 使用低基数状态、阶段和结果标签，并通过 `j-store-observability-spring` 复用 HTTP correlation、Tracing 与 Prometheus 运行时；不得在通用模块或日志/指标中放入支付凭证、买家资料、消息 payload、动态 Trade/Order ID 标签。
- 端到端：单商户、多商户、重复提交、任一计划失败、Order 创建失败、支付准备失败/未知/过期、取消与支付竞争、关单后迟到支付退款和进程重启。
- 交付前运行相关模块测试、全仓质量门禁，并由非实现者评审公共 API、金额、库存和订单状态语义。

## 风险与恢复

- 多商户全有或全无、Trade 唯一 SettlementPlan、支付成功优先和安全关单已经成为批准的产品行为；实现不得退化为每 Order 独立支付或计时到点直接释放资源。
- 这是公共 API、金额、库存、订单状态和消息契约变更，必须形成独立评审证据。
- 开发期不保留旧接口或旧数据兼容；候选失败时回退整个候选分支并重建开发数据库。
- 禁止让旧 `OrderCreated -> Trade` 与新 `Trade -> Order` 两条真实创建链路同时启用。
