# Trade / Checkout 边界演进需求

## 背景与目标

当前 Order 聚合既保存订单成交快照和支付、履约、退款投影，又持有销售授权、库存预留和失败补偿阶段。跨上下文流程由多个 Translator 与 Order handler 隐式串联，无法独立表达结账卡点、截止时间、补偿进度，也会阻碍后续购物车、动态计价、优惠券、跨商户拆单和对账能力接入。

目标是建立独立 Trade / Checkout 有界上下文，使 Trade 决定“一次购买如何达成”，Order 只记录“最终形成了什么订单及其后续事实”。本规格按可独立验收的迭代推进，不在缺少产品规则时预造购物车、促销或跨店模型。

## 领域边界

- Cart 负责可反复修改的购买意图，后续独立建设。
- Pricing / Promotion 负责算价、活动、优惠券生命周期与优惠分摊，后续独立建设。
- Trade 负责一次结账请求的持久化进度、销售授权、库存预留、支付准备、截止时间和补偿决策。
- Order 负责单商户订单的不可变成交快照、订单生命周期以及支付、履约和退款事实投影。
- Payment 负责支付机构交互、捕获、取消、退款和外部资金流水。
- Accounting / Reconciliation 负责账务、结算和差异发现；Trade 不成为账务事实源。

## 迭代 1：交易承诺编排迁移

### TC-R1 持久化 Trade Process

1. 系统必须为每个已创建但尚未形成销售承诺的订单建立且仅建立一个 Trade Process。
2. 第一迭代以 `orderId` 作为 Trade 的稳定相关键和过渡身份；未来跨商户拆单时再引入独立 `tradeId -> orderIds` 模型。
3. Trade Process 必须持久化商户、冻结行项、应付金额、币种、当前状态、销售授权、库存预留、期限、失败原因和更新时间。
4. Trade Process 状态至少包括 `AUTHORIZING`、`RESERVING`、`COMMITTED`、`PAID`、`FAILED`、`CLOSED`。

### TC-R2 销售授权与库存预留

1. Order 创建事实到达 Trade 时，Trade 必须幂等建立流程并发布一次销售授权命令。
2. 销售授权成功时，Trade 必须校验授权覆盖全部 Offer、持久化授权并发布库存预留命令。
3. 销售授权失败时，Trade 必须进入 `FAILED` 并通知 Order 关闭，不得请求库存。
4. 库存预留成功时，Trade 必须保存 reservation ID 和最早过期时间，进入 `COMMITTED` 并通知 Order 交易承诺已形成。
5. 库存预留失败时，Trade 必须进入 `FAILED`，通知 Order 关闭，并幂等释放已经取得的销售授权。

### TC-R3 Order 职责收缩

1. Order 不再保存 Trade Process 的阶段状态和销售授权集合。
2. Order 不再直接消费销售授权或库存预留结果。
3. Order 只消费 Trade 发布的“承诺已确认”或“承诺失败”结果，分别进入 `ACTIVE` 或 `CLOSED`。
4. Order 的成交快照、金额快照、支付/履约/退款投影、订单取消和完成规则保持不变。
5. 支付单仍由 Order 接受承诺确认后产生的稳定事实创建；支付准备迁入 Trade 属于后续迭代。

### TC-R4 取消与补偿

1. 未支付 Order 取消时，Trade 必须收到稳定取消事实。
2. 已取得授权或库存预留的 Trade 关闭时，必须发布库存释放和销售授权释放命令。
3. 重复取消、重复失败或重复外部结果不得重复改变 Trade 状态或产生不可幂等副作用。

### TC-R5 可靠性与分层

1. Trade domain/application 必须保持框架无关。
2. Trade 状态保存与下一条集成命令写入 Outbox 必须位于同一用例事务。
3. Trade handler 必须通过业务状态实现幂等，不能只依赖 Inbox message ID。
4. Translator 只做领域事件与集成消息的语言转换，不得决定下一步流程。
5. 重启后流程必须能够从数据库状态继续处理。

## 后续迭代

### 迭代 2：Checkout API 与订单创建迁移

- 引入独立 `checkoutRequestId` 和公开 Checkout API。
- Trade 在校验报价、授权和预留后创建 Order；Order 创建接口降为内部契约。
- OrderFactory 只接受可信成交快照，不再查询 Goods、Offer 或地址 ACL。
- 支持有限同步等待以及 `202 + checkoutId + statusUrl` 慢路径。

### 迭代 3：Pricing、Promotion 与购物车

- 建立版本化 PricingQuote、活动叠加/互斥与优惠券锁定、核销、释放协议。
- 将商品级、订单级、平台出资和商户出资优惠分摊冻结到订单行。
- 建立独立 Cart 上下文并支持从 Cart 发起 Checkout。

### 迭代 4：跨商户拆单与统一支付

- 引入独立 `tradeId` 和 `TradeOrderPlan`，一个 Trade 生成多个单商户 Order。
- Payment 从一单一支付演进为 Trade 应付及 Order 金额分配。
- 支持跨店优惠分摊、部分支付失败和统一关单。

### 迭代 5：对账与恢复

- 建立 Trade、Order、Promotion、Reservation、Payment 和 Accounting 差异查询。
- 对账只生成带审计的修复命令，不跨上下文直接写数据。
- 支付渠道对账归 Payment/Accounting/Reconciliation，不归 Trade。

## 质量目标

- 数据完整性：任何失败路径都不得遗留不可见的授权或库存承诺。
- 可恢复性：重复、乱序和进程重启不越级推进状态。
- 可维护性：Order 与 Trade 各自只有一个主要变化原因。
- 可演进性：后续购物车、优惠和拆单不再修改 Order 的承诺编排职责。
- 可观测性：每个流程的当前状态、失败原因和期限可查询。

## 本次非范围

- 不实现购物车、活动规则、优惠券、动态运费或税费。
- 不实现跨商户拆单或 Trade 级统一支付。
- 不接入真实支付渠道或 Broker。
- 不实现完整支付渠道对账。
