# 变更需求：原子可售库存预留

> 已被 `commerce-sellability-boundaries` 取代。本文件保留为上一阶段决策记录，不再描述目标架构。

## 背景

订单创建前同步查询商品快照只能提前发现陈旧请求，无法阻止商品下架与跨进程下单之间的竞态。最终销售许可必须由商品上下文在处理库存预留时作出，并与库存预留处于同一事务边界。

本变更修订现有“订单创建后预留库存”流程，不改变 HTTP 下单请求和订单四维状态枚举。现有 `TradeStatus.CREATED` 继续表示等待商品侧确认销售预留；只有预留成功后订单才进入 `ACTIVE`，也只有此后才允许创建支付单。

## 验收标准

1. WHEN 订单创建事件被翻译为销售预留命令, THE command SHALL 携带每个行项的 `spuId`、`skuId`、数量、成交单价和期望商品快照版本，并携带订单商户标识。
2. WHEN 商品上下文处理销售预留命令, THE goods service SHALL 在一个本地数据库事务中，以稳定顺序锁定涉及的商品记录，然后校验商品为 `ON_SALE`、归属目标商户、版本等于期望版本、SKU 存在且价格等于期望价格。
3. IF 任一商品校验失败, THEN THE goods service SHALL 不保留任何新库存预留，并发布库存预留失败事实。
4. IF 全部商品校验通过且库存充足, THEN THE goods service SHALL 幂等地预留全部库存，并发布库存预留成功事实。
5. WHEN 商品下架与销售预留并发, THE database lock and transaction boundary SHALL 以商品记录上的提交顺序决定结果：下架先提交则预留失败，预留先提交则该订单被接受。
6. WHILE 订单仍为 `TradeStatus.CREATED`, THE system SHALL NOT 创建支付单；WHEN 库存预留成功使订单进入 `ACTIVE`, THEN THE system SHALL 发布独立的库存确认领域事实并据此创建支付单。
7. THE synchronous goods snapshot query SHALL 继续作为用户体验和陈旧请求预校验，不作为最终销售许可的事实来源。
8. THE changed domain and integration contracts SHALL 具有序列化、翻译、商品应用编排和订单状态回归测试。

## 范围外

- 不引入跨数据库两阶段提交或通用分布式锁。
- 不在本变更中实现商户停用与商品销售资格的跨服务原子化。
- 不改变订单 HTTP 请求或四维状态响应字段。
- 不实现跨商户购物车或父子订单拆分。
