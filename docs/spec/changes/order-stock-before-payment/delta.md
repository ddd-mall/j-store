# 变更：库存预扣成功后创建支付单

## MODIFIED：支付单创建时机

替代 `order-domain-boundary-refactor/requirement.md` 的 R3.1“下单后必须建立且仅建立一个对应 Payment 聚合”，并替代其设计中的 `order.created -> Payment` 事件流。

1. 创建订单后，系统必须先请求库存预扣，不得同时创建 Payment 聚合。
2. 只有库存预扣成功且订单成功从 `TradeStatus.CREATED` 转为 `TradeStatus.ACTIVE` 后，系统才可发布创建支付单命令。
3. 库存预扣失败时，订单必须转为 `TradeStatus.CLOSED`，且不得发布创建支付单命令。
4. 重复的库存成功消息不得创建多个支付单；现有 Outbox、消费幂等和支付应用服务按订单业务键幂等机制必须保持有效。

## 保持不变

- `CreatePaymentForOrderCommand` 的名称、版本、载荷与目的地保持不变。
- 支付捕获成功后向订单投影支付事实的流程保持不变。
- 订单创建后发起库存预扣、库存失败关闭订单、订单支付后确认扣减库存的流程保持不变。
- 本变更不新增支付单取消状态，不修改数据库结构或 HTTP API。

## 验收场景

### AC1：创建订单只请求库存预扣

给定一个新创建的订单，当 `OrderCreatedEvent` 被投递时，则系统发布 `ReserveInventoryCommand`，且不发布 `CreatePaymentForOrderCommand`。

### AC2：预扣成功后创建支付单

给定处于 `CREATED/UNPAID` 的订单，当库存预扣成功消息被处理时，则订单转为 `ACTIVE`，产生携带订单、商家、应付金额和币种的库存确认领域事件，并由该事件发布一次 `CreatePaymentForOrderCommand`。

### AC3：预扣失败关闭且不创建支付单

给定处于 `CREATED/UNPAID` 的订单，当库存预扣失败消息被处理时，则订单转为 `CLOSED/UNPAID`，且不产生任何可创建支付单的领域事件或集成命令。

### AC4：非法或重复确认不越过门禁

给定订单已离开允许确认库存的状态，当再次确认库存时，则领域操作失败，不产生库存确认领域事件，也不创建支付单。
