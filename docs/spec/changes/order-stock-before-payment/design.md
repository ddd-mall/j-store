# 设计：库存确认作为支付创建门禁

## 事件链

```text
OrderCreatedEvent
  -> ReserveInventoryCommand
  -> StockReservedEvent
  -> InventoryReservedIntegrationEvent
  -> Order.confirmStock()
  -> OrderStockConfirmedEvent
  -> CreatePaymentForOrderCommand
```

库存失败分支停在 `Order.markStockInsufficient()`，只产生既有的 `OrderCancelledEvent`，不会产生 `OrderStockConfirmedEvent`。

## 关键决策

- `OrderStockConfirmedEvent` 由订单聚合在成功完成状态转换时产生。支付创建门禁因此依赖订单领域已接受的库存事实，而不是直接依赖库存上下文事件。
- 新事件携带支付命令所需的稳定快照，Boot Translator 只负责语言转换，不查询订单仓储，也不承载业务判断。
- 订单保存与 `OrderStockConfirmedEvent` Outbox 写入继续位于同一订单用例事务；事件翻译后的支付命令继续写入集成 Outbox。
- 保留 `CreatePaymentForOrderCommand` v1，避免改变支付上下文公开契约。

## 验证

- 订单领域单元测试：成功确认产生事件；非法/重复确认不产生事件；库存不足不产生事件。
- Boot Translator 测试：订单创建不再发布支付命令；库存确认事件正确映射支付命令。
- 相关模块回归测试：order domain/application/boot、goods、payment、root boot。
