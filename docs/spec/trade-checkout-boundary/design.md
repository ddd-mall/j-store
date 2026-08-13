# Trade / Checkout 边界演进设计

## 总体决策

采用独立四层 Trade 上下文：

```text
j-store-trade-boot -> j-store-trade-application -> j-store-trade-domain -> common-core
                   -> j-store-trade-infrastructure -> j-store-trade-domain
```

第一迭代保留现有“先创建 provisional Order，再取得销售承诺”的外部行为，以降低迁移风险；Trade Process 接管 Order 创建后的授权、预留和补偿。第二迭代再把公开 Checkout API 与 Order 创建时机迁出。

## 当前与目标流程

当前：

```text
OrderCreated -> Translator -> AuthorizeSale
SaleAuthorized -> Order -> OrderSaleAuthorized -> Translator -> ReserveInventory
InventoryReserved -> Order.confirmStock -> OrderStockConfirmed -> CreatePayment
```

迭代 1 目标：

```text
OrderCreated -> StartTradeProcess -> Trade.start -> AuthorizeSale
SaleAuthorized -> Trade.recordAuthorized -> ReserveInventory
InventoryReserved -> Trade.recordReserved -> TradeCommitmentConfirmed
TradeCommitmentConfirmed -> Order.confirmTradeCommitment -> OrderTradeCommitted -> CreatePayment
```

失败与取消：

```text
SaleAuthorizationFailed -> Trade.fail -> TradeCommitmentFailed -> Order.close
InventoryReservationFailed -> Trade.fail -> release authorization -> Order.close
OrderCancelled -> Trade.close -> release reservation + release authorization
```

## Trade 聚合

```text
TradeProcess
  id: TradeProcessId               // 迭代 1 与 orderId 同值
  orderId
  merchantId
  items: List<TradeItemSnapshot>
  payableAmount
  currency
  status
  authorizations
  reservationIds
  reservationExpiresAt
  failureReason
  createdAt / updatedAt
```

状态转换：

```text
AUTHORIZING -> RESERVING -> COMMITTED -> PAID
      |             |
      +-----> FAILED <----+

AUTHORIZING / RESERVING / COMMITTED -> CLOSED
```

- 重复成功事实在状态和业务标识一致时返回 `changed=false`。
- 同一阶段收到冲突的授权或 Reservation 结果时返回业务冲突。
- 迟到的成功事实不得把 `FAILED/CLOSED` 流程重新打开。
- 聚合只维护流程事实；发布何种集成命令由 application service 根据成功的状态转换决定。

## 应用服务

`TradeProcessUseCase` 提供：

```text
start(request)
recordSaleAuthorized(result)
recordSaleAuthorizationFailed(result)
recordInventoryReserved(result)
recordInventoryReservationFailed(result)
closeFromOrderCancellation(orderId, reason)
recordPaid(orderId)
get(orderId)
```

每个写用例执行：

```text
加载/创建聚合 -> 状态转换 -> 保存 -> 发布下一条 IntegrationMessage
```

Boot 使用 `TransactionTemplate` 保证聚合保存与 Outbox 同事务。Integration handler 只做契约到用例输入的映射，业务失败转换为可重试异常。

## 集成契约

保留既有 Store 和 Inventory 命令/结果载荷，第一迭代继续使用 `orderId` 作为 partition/correlation key。新增：

- `TradeCommitmentConfirmedIntegrationEvent`：Trade -> Order。
- `TradeCommitmentFailedIntegrationEvent`：Trade -> Order。
- `OrderCancelledIntegrationEvent`：Order -> Trade。
- `OrderPaidIntegrationEvent`：Order -> Trade，用于结束 Trade 主流程。

Store/Inventory 结果的逻辑 destination 从 `order.events` 调整为 `trade.events`。这属于未上线内部契约的直接演进，不保留双投递。

## Order 收缩

从 Order 删除：

- `CommitmentStatus`；
- `saleAuthorizations`；
- `recordSaleAuthorized`；
- `markSaleAuthorizationFailed`；
- 基于授权状态的 `confirmStock/markStockInsufficient`。

替换为：

- `confirmTradeCommitment()`；
- `markTradeCommitmentFailed(reason)`。

Order 仍以 `CREATED` 表示 provisional、等待 Trade 承诺的订单，承诺确认后进入 `ACTIVE`。第二迭代创建时机迁出后再评估是否删除 provisional 状态。

## 持久化

新增：

```text
trade_process
trade_process_item
trade_process_authorization
trade_process_reservation
```

`trade_process.order_id` 唯一。状态字段使用数据库 CHECK；授权 ID、Reservation ID 在流程内唯一。Repository 转换必须完整往返，写操作要求外层事务。

## 后续金额模型

第一迭代只原样保存现有应付金额。迭代 3 的 `PricingQuote` 必须提供行级分摊：原价、商品优惠、订单优惠分摊、优惠券分摊、平台/商户出资、运费、税费和最终应付。Trade 接受报价，Order 冻结结果，Trade 不拥有促销规则。

## 验证

- Trade domain 状态机单元测试覆盖正常、失败、重复、冲突和终态迟到消息。
- Trade application 测试验证状态保存、命令顺序、补偿及业务幂等。
- Trade infrastructure 测试验证 JPA 往返、唯一约束和事务要求。
- Order 回归测试确认只接受 Trade 最终承诺结果。
- 集成契约序列化和 Handler 唯一装配测试覆盖新增消息。
- 运行 trade、order、shop、inventory、payment、integration-contracts、root boot 相关测试及质量门禁。

## 风险与恢复

- 这是订单、库存和公共消息契约变更，必须独立评审后合并。
- 项目未上线，不提供旧开发数据兼容；开发数据库按当前迁移重建。
- 若迁移候选失败，回退整个候选分支；不得让 Order handler 与 Trade handler 同时消费相同成功事实。
