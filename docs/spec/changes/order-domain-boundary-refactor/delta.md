# 订单领域边界重构变更

## 变更结论

项目尚未上线，本变更直接替换订单、支付、履约和售后退款的旧契约，不保留旧 API、旧领域方法或旧开发数据兼容层。

## ADDED

### ODR-01 显式成交归属与金额快照

- 商品快照和订单必须显式保存 `merchantId`。
- 一个订单只能包含同一商户的商品。
- 订单必须保存币种、商品小计、优惠、运费、税额和应付金额组成的不可变成交金额快照。
- 订单必须分别保存已支付金额和已退款金额，不再使用创建时即等于应付金额的 `actualPay`。

### ODR-02 支付上下文

- 新增独立 Payment 聚合，负责支付捕获、退款请求、退款成功/失败和外部流水幂等。
- 订单只能根据已发生的支付事实更新支付投影，不得直接执行支付。

### ODR-03 履约上下文

- 新增独立 FulfillmentOrder 聚合，负责待发货、发货、物流信息和送达事实。
- 订单只能根据履约事实更新履约投影，不得直接执行备货、发货或签收。

### ODR-04 分阶段售后

- 售后审批只表达商家决策，不再等价于退款成功。
- 需退货售后必须先确认退货收货，再请求退款。
- 支付退款成功后，售后才完成，订单退款投影和财务退款分录才更新。

## MODIFIED

### ODR-05 订单状态语义

替换 `order-status-dimensions` 中“订单聚合直接执行支付和履约操作”的行为：

- 保留 `TradeStatus`、`PaymentStatus`、`FulfillmentStatus` 作为订单查询所需的事实投影。
- 状态变化由库存、支付、履约和退款集成事实驱动。
- `OrderPaidEvent` 仍作为订单已记录支付成功的兼容业务事实，但只能由支付捕获投影产生。

### ODR-06 售后退款语义

替换 `order-after-sale-aggregate` 中“批准即更新订单退款事实并记账”的行为：

- 批准后进入 `RETURN_REQUIRED` 或 `REFUND_PENDING`。
- 支付退款成功后进入 `COMPLETED`。
- 支付退款失败进入 `REFUND_FAILED`，允许重试，不释放售后容量。

## REMOVED

- `Order.pay(paidAmount)` 和 `OrderPayCMD`。
- `Order.confirmForShipment()`、`Order.ship()`、`Order.confirmDelivery()`。
- 订单支付、备货、发货、签收的直接 HTTP 操作。
- `AfterSaleMerchantResolver`、`ConfiguredAfterSaleMerchantResolver` 和全局 `jstore.order.merchant-id` 解析。
- `AfterSaleApprovedEvent` 直接驱动订单退款投影、库存恢复和财务退款分录的监听链路。
- `actual_pay` 字段及“创建订单时已实付”的旧语义。

## 明确排除

- 购物车与 CheckoutSession。
- 促销规则、优惠券和动态价格计算；本轮金额快照的优惠、运费和税额为后续结算域预留。
- 部分支付、部分发货、多包裹、多仓分配。
- 真实支付渠道、承运商和 WMS 适配；本轮提供端口友好的领域与应用契约。
- CNY 之外的实际交易，但币种必须进入模型且禁止跨币种运算。
