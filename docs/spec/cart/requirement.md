# 购物车上下文需求

## 背景与目标

j-store 已由 Trade / Checkout 统一承担成交快照、销售授权、库存预留、拆单和 Order 创建，但当前只支持客户端直接提交购买行。系统需要新增独立 Cart 有界上下文，用于保存认证买家可反复修改的购买意图，并向 Checkout 提供一次可验证、可追溯的输入。

购物车中的金额、可售性和库存只代表某次刷新时的试算结果，不构成价格锁定、库存承诺或成交事实。Trade 受理后必须继续通过 Store / Offer、Inventory / ATP 等权威上下文形成真正的销售与库存承诺。

## 领域术语

- **Cart**：一个认证买家的当前购物车，拥有可变的购买意图和内容版本。
- **Cart Line**：对一个确定 `SalesOffer` 所销售 SKU 的购买意图；同一 SKU 的不同 Offer 是不同购物车行。
- **Selection**：买家当前选择参与下一次 Checkout 的购物车行集合。
- **Settlement Scope**：一个 Cart 固定的 `market + channelId + currency` 组合；第一条成功加购确定该组合。
- **Cart Refresh**：针对一个确定 Cart 内容版本重新读取商品、Offer 和 ATP 事实并尝试生成试算结果。
- **Cart Assessment**：某个 Cart 版本的派生试算结果，包含每一行是否纳入结算、排除原因、观察价格和预估金额。
- **Eligible Line**：已选择、商品资料可用、Offer 当前有效且 ATP 足以覆盖请求数量的行。

## 范围

本功能包括：

1. 认证买家将一个由 `skuId + offerId` 确定的销售目标加入自己的购物车。
2. 同一买家只有一个当前活动购物车；相同 Offer 再次加购时增加数量，不产生重复行。
3. 购物车内容或选择发生变化后触发购物车刷新；系统也允许显式请求刷新。
4. 刷新时按当前商品发布状态、Store / Offer 销售状态、有效期和 Inventory ATP 计算基础结算金额。
5. 商品或 Offer 已下架、失效，或者 ATP 不能满足购物车请求数量时，该行保留在购物车中，但不纳入试算和 Checkout。
6. 买家可以重新选择购物车行，并从当前选择创建 Checkout。
7. Trade 冻结来源 Cart 的 ID、内容版本和摘要；后续 Cart 变化不能修改已受理 Trade。
8. 一个 Cart 可以包含多个商户的商品；买家可在一次 Checkout 中提交多个商户的已选择商品，由 Trade 拆分订单计划。

## 明确不在本次范围

- 游客购物车、登录时合并购物车和跨账号共享购物车。
- 优惠券、红包、活动叠加、税费、运费和优惠分摊；第一版试算仅使用当前 Offer 单价计算商品基础金额。
- 购物车占用或预留库存。
- 自动降低购物车数量以适配剩余 ATP。
- 秒杀、选座、预售等需要短期资源 Hold 的特殊购物车。
- Checkout 成功后自动删除、扣减或清空购物车行。
- 跨市场、跨渠道或跨币种商品合并为同一个 Cart / Trade；第一版 Cart 固定一个 Settlement Scope。
- 某个商户失败后仍允许其他商户部分成交；第一版采用整笔 Trade 失败并补偿的原子承诺策略。
- 主动扫描所有购物车并响应全站库存或 Offer 变化；刷新采用购物车变化、显式刷新和 Checkout 前刷新驱动。

## 需求与验收标准

### CART-R1 加购绑定 Offer 的 SKU

1. 加购请求必须包含认证买家、`skuId`、`offerId`、正整数数量和请求幂等键；买家身份只能来自认证上下文。
2. Store / Offer 必须确认 `offerId` 当前引用该 `skuId`；客户端不能仅凭 SKU 指定价格、商户或履约节点。
3. 加购只确认 SKU 与 Offer 的身份关系，不锁价、不锁库存，也不因暂时下架或售罄删除购买意图；是否可结算由该版本的刷新结果决定。
4. Given 买家的活动购物车中不存在该 Offer，When 加购成功，Then 创建一条已选择的 Cart Line、增加 Cart 内容版本并触发刷新事件。
5. Given 已存在相同 Offer 的 Cart Line，When 再次加购，Then 在领域数量上限内累加数量、保持原行身份、增加 Cart 内容版本并触发刷新事件。
6. Given 相同 `requestId` 和相同请求重复到达，Then 返回第一次结果且不重复增加数量；相同 `requestId` 携带冲突内容时返回冲突。
7. 不同 Offer 即使引用同一 SKU 也不得合并。

### CART-R2 购物车刷新与金额试算

1. Cart 内容或 Selection 成功变化后必须产生携带 `cartId + cartVersion + reason` 的 `CartRefreshRequestedEvent`。
2. 收到刷新事件时，系统必须读取事件指定版本的 Cart，查询当前 Catalog、Store / Offer 和 ATP 事实，并尝试生成 Cart Assessment。
3. 试算金额必须等于所有 Eligible Line 的 `当前 Offer 单价 × 请求数量` 之和，使用 `Price` 的最小货币单位运算。
4. Cart Assessment 必须记录来源 Cart 版本、计算时间、币种、总额和每一行的纳入状态、排除原因及观察版本。
5. 重复处理同一 `cartId + cartVersion` 的刷新事件不得产生不同的持久化身份或重复副作用。
6. 旧版本刷新晚于新版本完成时，不得覆盖新版本 Assessment；查询只能将与当前 Cart 版本相同的 Assessment 标记为 CURRENT。

### CART-R3 下架、售罄和不足库存不纳入结算

刷新时按以下规则处理：

| 条件 | 行结果 | 是否计入金额 |
|---|---|---:|
| 行未被买家选择 | `UNSELECTED` | 否 |
| 当前 Catalog 不再发布该 SKU | `CATALOG_UNAVAILABLE` | 否 |
| Store 或 Offer 非活动、未生效、已过期或引用不匹配 | `OFFER_UNAVAILABLE` | 否 |
| ATP 为 0 | `OUT_OF_STOCK` | 否 |
| ATP 大于 0 但小于请求数量 | `INSUFFICIENT_STOCK` | 否 |
| 上述条件均满足 | `ELIGIBLE` | 是 |

1. 被排除的行必须保留在 Cart 中，以便买家删除、调整或等待重新上架；刷新不能静默删除购买意图。
2. 第一版不得进行部分数量结算：库存不足以满足整行数量时整行排除。
3. 查询依赖超时或不可用属于技术失败，不得被伪装成“下架”或“售罄”；失败刷新不得用较小金额覆盖最近一次成功 Assessment。

### CART-R4 重新选择与 Checkout

1. 买家可以提交期望 Cart 版本和目标 Cart Line ID 集合，原子替换当前 Selection。
2. 选择集合只能包含该买家 Cart 中存在的行；空集合合法，但不能发起 Checkout。
3. Selection 实际变化时增加 Cart 内容版本并触发刷新；提交相同 Selection 不增加版本。
4. 从 Cart 发起 Checkout 时必须携带 `checkoutRequestId`、`cartId` 和 `expectedCartVersion`；收货信息继续遵循 Trade / Checkout 现有契约。
5. Checkout 前必须同步计算当前版本的新鲜 Cart Assessment 候选，只向 Trade 提交 `ELIGIBLE` 行；下架、售罄和库存不足行不得进入 Trade。Checkout 不得依赖异步刷新是否已完成或复用旧 Assessment。
6. 若没有 Eligible Line，Checkout 必须明确拒绝且不得创建 Trade。
7. 若 Cart 版本与请求不一致，必须返回冲突，不得使用调用方未确认的新内容创建 Trade。
8. Trade 受理后必须冻结 `cartId + cartVersion + cartDigest`；之后修改 Cart 不得改变该 Trade。
9. 相同 `checkoutRequestId` 和相同 Cart 摘要重复提交必须返回同一 Trade；摘要不同必须冲突。
10. Checkout 时重新验证与库存预留之间发生变化，仍以 Store 的 SaleAuthorization 和 Inventory 的 StockReservation 结果为最终裁决。

### CART-R5 买家隔离与访问控制

1. 所有 Cart 写入、查询、刷新和 Checkout 操作必须使用认证上下文中的买家 ID。
2. 访问其他买家的 Cart 按不存在处理，不泄露 Cart、行项、价格或选择状态。
3. 客户端提交的商户、价格、Offer 版本、Catalog 版本、ATP 或排除原因均不得成为可信输入。

### CART-R6 SRP 与 DDD 边界

1. Cart 聚合只负责购买意图、行唯一性、数量、Selection、内容版本及领域事件。
2. Cart Assessment 独立负责某个 Cart 版本的派生试算结果；外部事实查询和试算不得塞入 Cart 聚合。
3. Catalog、Store / Offer 和 Inventory 保持各自权威；Cart 只能通过稳定查询契约和消费方 ACL 读取标量事实。
4. Trade 保持唯一用户 Checkout 入口；Cart 不创建 Order、不签发 SaleAuthorization、不预留库存、不创建 Payment。
5. Controller 只处理认证、请求映射和响应映射；应用服务只编排；领域规则必须位于聚合、值对象或纯领域服务中。
6. Domain 和 Application 模块不得依赖 Spring、JPA、Redis 或其它上下文的基础设施模块。

### CART-R7 多商户合并交易

1. 一个 Cart 必须允许包含不同 `merchantId` 的 Cart Line，不得以商户作为 Cart 聚合或 Selection 的隔离边界。
2. Cart 创建时由第一条成功加购的可信 Offer 事实确定 `SettlementScope(market, channelId, currency)`；后续加购的 Offer 必须属于同一 Settlement Scope，否则明确拒绝且不得改变 Cart。
3. 买家可以在一次 Selection 中选择多个商户的商品，并通过一个 `checkoutRequestId` 发起一次 Checkout。
4. Trade 必须把 Eligible Line 按 `merchantId + fulfillmentNodeId` 分组，每组形成独立 `TradeOrderPlan`；同一商户存在多个履约节点时允许形成多个计划。
5. Trade 的商品总金额必须等于所有 `TradeOrderPlan.payableAmount` 之和，每个计划金额必须等于其行项目金额之和，且全部使用 Cart Settlement Scope 的币种。
6. 第一版采用原子承诺策略：任一计划的商户销售授权或库存预留失败时，整笔 Trade 失败；已经成功取得的销售授权和库存预留必须通过现有 Saga 补偿释放，不得创建部分成功交易。
7. 所有计划完成销售授权和库存预留之前不得开始创建 Order；全部承诺成功后，才按计划分别创建商户订单。
8. Given 商户 A 的两个商品使用同一履约节点、商户 B 的一个商品使用另一履约节点，When 买家一次选择三个商品并 Checkout，Then 系统创建一个 Trade、两个 TradeOrderPlan，并最终创建两个商户订单；Trade 总金额等于两个计划金额之和。

## 质量目标

- **正确性**：同一 Cart 版本的试算可重复；被排除行不计入金额；金额全部使用整数最小货币单位。
- **跨商户原子性**：一次 Checkout 的所有商户计划必须全部承诺成功后才能创建订单；任一授权或预留失败均触发整笔失败及补偿。
- **并发安全**：通过业务 Cart 版本和 JPA 乐观锁检测多设备更新；旧刷新不能覆盖新结果。
- **可靠性**：Cart 保存与刷新事件写入同一事务；刷新处理幂等并可重试。
- **安全性**：买家隔离，所有价格、状态和 ATP 来自可信服务。
- **可维护性**：Cart Intent、Assessment、外部事实采集和 Checkout 解析分别只有一个主要变化原因。
- **性能**：一次刷新必须按去重后的批量 ID 查询上游，禁止逐行 N+1 调用；第一版最多支持 100 条活动 Cart Line。
- **可观测性**：记录刷新成功、失败、过期丢弃、排除原因数量和耗时；日志不得包含地址、手机号等非必要个人信息。

## 依赖与假设

1. 一个 SKU 可存在多个 SalesOffer，因此第一版加购必须绑定具体 `offerId`；自动选择默认 Offer 属于后续业务能力。
2. “库存足够”定义为指定 Offer 履约节点的 `availableToPromise >= line.quantity`。
3. 即使 Offer 声明允许 backorder，当前 Inventory Reservation 仍要求 ATP 足额；因此第一版试算不把 backorder 当作可结算库存。
4. 当前 Pricing / Promotion 尚未实现；第一版 Cart Assessment 与现有 Trade 一样只计算 Offer 基础金额。
5. 本功能扩展 `docs/spec/trade-checkout-boundary/` 的迭代 4，不改变 Trade、Order、Payment 的既有权威边界。
6. 项目处于内部开发期，无需迁移或兼容既有购物车数据。
7. 第一版 Settlement Scope 的币种为受信任销售上下文给出的单一币种；当前系统默认使用 `CNY`，不得接受客户端自行指定或换算币种。
