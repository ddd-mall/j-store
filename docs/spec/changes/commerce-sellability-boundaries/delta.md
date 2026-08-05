# 变更需求：销售资格、ATP 与仓储边界重构

## 背景

现有实现把商品资料、店铺上下架、价格和库存预留共同放在 goods 上下文，并依赖同一数据库事务锁定 SPU 与库存。这只能在单库部署中成立，也把“商品是什么”“店铺愿不愿意卖”和“平台能否承诺库存”混成了一个事实。

本变更由用户批准，取代 `atomic-sellable-inventory-reservation` 中“SPU 与库存同属商品上下文”的设计假设。

## 目标边界

1. Catalog 拥有 Product/SPU、SKU、规格、图片和资料生命周期。
2. Store/Offer 拥有 Store、SalesOffer、价格、渠道、销售周期、购买限制和履约策略。
3. Inventory/ATP 拥有 StockPosition、StockReservation、履约节点库存镜像、安全库存和可承诺数量。
4. WMS 拥有实物库存、库位以及入库、盘点、损耗、出库等真实库存事实。
5. Order 拥有订单快照和“销售授权 → 库存预留 → 交易确认”的 Saga 状态。

## 验收标准

1. Catalog SPU 的生命周期 SHALL 为 `DRAFT → PUBLISHED → ARCHIVED`，且 SHALL NOT 包含 `ON_SALE/OFF_SALE` 或最终销售许可校验。
2. SalesOffer SHALL 由 `offerId`、`storeId`、`merchantId`、`skuId`、渠道/市场、价格、有效期、限购规则、履约策略和版本构成，并封装 `ACTIVE/SUSPENDED/ENDED` 转换。
3. WHEN 店铺处理销售授权命令, THE Store service SHALL 在本地事务中串行化 Offer 与上下架操作，校验版本、价格、数量、有效期和状态，并幂等签发带失效时间的 `SaleAuthorization`。
4. WHEN 普通下架与授权并发, THEN 提交顺序 SHALL 决定结果；已签发且未失效的授权不因后续普通下架被隐式撤销。
5. Inventory SHALL 独立于 Catalog，按 SKU 与履约节点维护 `onHand`、`reserved`、`safetyStock` 和渠道配额，并以 `ATP = onHand - reserved - safetyStock - 隔离配额` 作为即时预留依据。
6. WHEN Inventory 收到库存预留命令, THE service SHALL 通过稳定业务键幂等创建 `StockReservation`；只有预留事实成功才代表库存承诺成立。
7. WMS SHALL 通过带来源版本的实物库存调整事件更新 Inventory 镜像；重复或旧版本事件 SHALL NOT 重复改变库存。
8. WHEN 订单创建, THE Order SHALL 冻结 `offerId`、Offer 版本、SKU、Catalog 快照版本、成交价、数量和履约策略，不把 `canSell` 持久化为权威字段。
9. Order Saga SHALL 先请求销售授权；授权成功后才请求 ATP 预留；两者成功后订单才进入 `ACTIVE` 并创建支付单。
10. IF 销售授权失败, THEN 订单 SHALL 关闭且 SHALL NOT 请求库存；IF 库存预留失败, THEN 订单 SHALL 关闭并请求释放销售授权。
11. 页面使用的可售查询 MAY 最终一致，但 SHALL NOT 代替 `SaleAuthorization` 与 `StockReservation` 两个持久化承诺。
12. 变更 SHALL 具有领域状态机、应用编排、消息序列化/翻译、持久化映射、数据库并发和迁移验证。

## 范围外

- 不实现完整 WMS 的库位、批次、波次和拣货算法，仅建立实物库存权威及版本化同步边界。
- 不使用跨数据库两阶段提交、分布式锁或跨服务线程锁。
- 不实现监管召回；未来应以单独的强制撤销政策处理。
- 不承诺页面库存展示与下单瞬间强一致。

## 兼容与迁移

项目尚未上线。本变更允许演进开发期消息版本和数据库结构，不提供旧 Outbox 消息双读；升级前应清理开发环境未投递旧消息并执行新迁移。
