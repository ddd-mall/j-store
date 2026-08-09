# 设计：销售资格、ATP 与仓储边界重构

## 权威事实

| 上下文 | 权威事实 | 不拥有 |
|---|---|---|
| Catalog（现 goods 四层模块） | 商品资料和 `DRAFT/PUBLISHED/ARCHIVED` 生命周期 | 店铺上下架、成交价、库存 |
| Store/Offer（shop 四层模块） | 店铺销售意愿、价格、渠道、时段、限购、履约策略、销售授权 | 商品资料、实物库存 |
| Inventory/ATP（新增四层模块） | 可承诺库存镜像、预留、确认、释放 | 商品上下架、WMS 库位作业 |
| WMS（重组为四层模块） | 实物库存数量及单调来源版本 | 销售许可、订单交易状态 |
| Order | 冻结快照、Saga 阶段、交易承诺 | Offer/ATP 的当前事实 |

`canSell` 只存在于组合查询 DTO：`catalogAvailable && offerEligible && atp > 0`。它不是表字段、聚合状态或下单承诺。

## Store/Offer

`SalesOffer` 是聚合根。普通激活、暂停和结束都在 Offer 本地事务内更新版本。`SaleAuthorization` 是独立聚合，以 `(orderId, offerId)` 唯一，保存授权时的 Offer 版本、价格、数量、失效时间和状态。

授权事务锁定 Offer 后完成校验并写入授权与 Outbox。锁只解决 Store 自己的并发：若暂停先提交则授权失败；若授权先提交则普通暂停只阻止新授权。授权过期或显式释放后不可用于库存预留。

## Inventory/ATP

`StockPosition` 以 `skuId + fulfillmentNodeId` 标识，维护实物镜像、预留量、安全库存、隔离配额和 WMS 来源版本。`StockReservation` 以订单和 SKU 的稳定业务键幂等创建，并记录节点分配。

WMS 事件只更新 `onHand` 镜像；旧来源版本被忽略。预留操作在 Inventory 自己的数据库事务中锁定涉及的 StockPosition，绝不锁 Catalog、Store 或 WMS 数据库。

## Order Saga

订单新增 `CommitmentStatus`：

```text
PENDING_OFFER -> OFFER_AUTHORIZED -> STOCK_RESERVED -> CONFIRMED
       |                 |                 |
       +-----------------+-----------------+-> FAILED
```

- `OrderCreatedEvent` 翻译为 `AuthorizeSaleCommand`。
- `SaleAuthorizedIntegrationEvent` 推进订单并产生 `OrderSaleAuthorizedEvent`。
- 该事件翻译为 `ReserveStockCommand`，命令携带授权 token 和冻结的行项。
- `StockReservedIntegrationEvent` 推进订单为 `CONFIRMED/ACTIVE`，随后创建支付单。
- 授权失败直接关闭订单；库存失败关闭订单并由取消事件翻译为释放库存和销售授权命令。释放操作必须幂等。

每个上下文使用本地事务 + Outbox/Inbox。Saga 接受短暂中间态，不构造全局原子事务。

## 查询与订单输入

Catalog 查询继续提供商品名称、SKU 描述和资料版本。Store Offer 查询提供 `offerId`、`storeId`、`merchantId`、SKU、Offer 版本、价格和履约策略。创建订单以 `offerId + expectedOfferVersion` 为交易输入，Catalog 信息只用于冻结展示快照。

## 迁移顺序

1. 建立 Store Offer、Inventory ATP、WMS 库存表及订单 Saga 字段。
2. 切换消息翻译器和运行时装配。
3. 将现有 goods `inventory` 开发数据迁移为默认履约节点的 StockPosition：历史 `available_quantity` 已扣除预留量，因此 `onHand = availableQuantity + reservedQuantity`、`reserved = reservedQuantity`；只接受非负、整数且不超过 `INTEGER` 的数量，无法无损转换时迁移必须失败并保留源表。结构未被验证的 `goods_inventory*` 表只有为空时才可删除，非空时必须停止并由人工提供转换方案。
4. 将现有 `ON_SALE` 商品转换成默认 Store 的 ACTIVE Offer，`OFF_SALE` 转换成 SUSPENDED Offer。
5. 将 Catalog 状态映射：`DRAFT → DRAFT`，`ON_SALE/OFF_SALE → PUBLISHED`。
6. 删除 goods 中的库存和销售许可实现。

当前项目未上线，迁移脚本负责结构和可安全推导的数据转换；不能推导 store/offer 的数据必须由初始化任务补齐。

## 验证重点

- Catalog 状态机及不存在销售许可 API。
- Offer 授权、过期、幂等、下架竞态。
- ATP 公式、预留/释放幂等、WMS 事件版本去重。
- Order Saga 顺序、失败补偿以及支付时序。
- 消息契约、持久化转换、迁移和模块依赖。
