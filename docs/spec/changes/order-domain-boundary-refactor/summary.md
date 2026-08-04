# 交付总结：订单领域边界重构

## 已实现

- 商品与商品快照显式保存 `merchantId`，订单工厂拒绝跨商户或请求商户不匹配的商品。
- 订单冻结完整金额组成，删除 `actualPay`、订单直接支付和直接履约行为。
- 新增独立 Payment 聚合，保存支付捕获、退款申请、成功、失败与重试事实。
- 新增独立 FulfillmentOrder 聚合，保存收货与行项快照、承运商和运单号。
- 售后状态机拆分审核、退货、退款处理中、失败和完成，只有渠道退款成功才形成订单退款事实。
- 财务入账改由 `payment.captured` 与 `payment.refund-succeeded` 驱动。
- Boot 层完成 Order、Payment、Fulfillment、AfterSale、Inventory、Accounting 间事件翻译。
- 移除全局单商户配置解析器和旧订单退款投影服务。
- 新增未上线环境专用破坏式迁移，删除旧订单/售后数据并建立新表、列和约束。

## 验证证据

- 全仓 `testClasses` 编译通过。
- Payment、Fulfillment、Order、Accounting 领域测试执行通过。
- Order infrastructure 测试通过。
- Boot 控制器、事件翻译器、Outbox 与售后并发测试执行通过。
- PostgreSQL 迁移测试从基线执行到 `V20260805`，确认新表/列存在且旧金额列已删除。
- 全仓 `gradlew test` 最终通过。

## 有意保留的首期限制

- 仅支持 CNY、整单全额支付、整单履约；不支持部分支付和部分发货。
- 支付/退款 HTTP 入口是预上线渠道模拟适配器，接真实 Provider 时必须替换为签名验签 Webhook。
- 折扣、运费、税额字段已进入订单快照，但当前下单流程均为零；后续应由 Pricing/Checkout 计算后传入。
- 退款库存恢复当前以退款成功为准；后续引入质检时应由退货质检通过事实驱动可售库存恢复。
