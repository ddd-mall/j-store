# 设计：原子可售库存预留

> 已被 `commerce-sellability-boundaries` 取代；SPU 与库存同库的事务边界仅为历史过渡方案。

## 一致性边界

商品 SPU、SKU 与库存由商品上下文拥有。销售预留处理器通过商品基础设施提供的 `SellableOfferGuard` 对全部 SPU 以 ID 排序后执行数据库悲观写锁。在包围整个处理器的商品侧事务内完成：

1. 锁定并读取 SPU；
2. 校验 `ON_SALE`、商户、版本、SKU 与价格；
3. 调用幂等库存预留；
4. 写入成功或失败 Outbox 事实。

下架事务对相同 SPU 的更新与该锁互斥。同步快照查询不参与最终一致性判断。

## 契约演进

- `OrderItemSnapshot` 增加 `spuId`、`snapshotVersion` 和 `unitPrice`，`order.created` 升级版本。
- `ReserveInventoryCommand` 的行项改为强类型 `ContractSaleItem`，`inventory.reserve` 升级版本。
- 新增 `OrderStockConfirmedEvent`，订单聚合从 `CREATED` 转为 `ACTIVE` 时产生。
- 支付命令翻译器从监听 `OrderCreatedEvent` 改为监听 `OrderStockConfirmedEvent`。

项目尚未上线，不为旧 Outbox 消息提供双读兼容层；发布前必须清理或重建开发环境中的旧版本待投递消息。

## 失败与幂等

- 同一个订单/SKU 使用稳定 `ORDER-{orderId}-SKU-{skuId}` 业务码，库存服务复用既有预留记录。
- 商品校验先于任何库存变更。
- 多 SKU 预留中途失败时，在同一事务内释放本次已成功预留的行项；无法安全补偿时抛出异常使事务整体回滚。
- 订单端既有消息消费记录继续保证同一集成消息不会重复推进状态。

## 验证

- 商品应用测试覆盖下架、商户不匹配、版本漂移、价格漂移、SKU 缺失、库存不足补偿及成功路径。
- 订单领域测试覆盖 `CREATED → ACTIVE` 产生库存确认事件，非法或重复确认不产生新事实。
- 翻译器与 Jackson 测试覆盖新字段和消息版本。
- 商品基础设施测试覆盖悲观锁查询声明；完整门禁覆盖模块依赖与格式。
