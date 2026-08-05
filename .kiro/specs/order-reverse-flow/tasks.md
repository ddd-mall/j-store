# 实现计划：订单逆向业务流程

## 概述

基于现有 DDD 分层架构，为订单领域模型实现完整的逆向业务流程。实现顺序遵循领域驱动设计的由内而外原则：先实现值对象和领域事件等基础构件，再扩展聚合根和实体的领域行为，然后扩展状态转移规则，接着扩展应用服务编排，最后扩展事件翻译器完成跨上下文集成。

## Tasks

- [x] 1. 新增值对象与错误常量
  - [x] 1.1 创建 RefundReason 值对象和 RefundCategory 枚举
    - 在 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/` 下创建 `RefundReason.kt`
    - 包含 `RefundCategory` 枚举（NO_LONGER_NEEDED, NOT_AS_DESCRIBED, QUALITY_ISSUE, OTHER）
    - `RefundReason` 为 data class，包含 `category: RefundCategory` 和 `description: String`，init 块校验 description 非空白
    - _需求：3.1, 4.1, 7.1_

  - [x] 1.2 创建 CancellationReason 值对象和 CancellationCategory 枚举
    - 在 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/` 下创建 `CancellationReason.kt`
    - 包含 `CancellationCategory` 枚举（BUYER_CANCELLED, PAYMENT_TIMEOUT, STOCK_INSUFFICIENT）
    - `CancellationReason` 为 data class，包含 `category: CancellationCategory` 和 `description: String`，init 块校验 description 非空白
    - _需求：1.1, 1.2, 2.1_

  - [x] 1.3 扩展 OrderErrors 新增退款相关错误常量
    - 在 `OrderErrors` 对象中新增 `REFUND_ITEMS_EMPTY`、`REFUND_ITEM_NOT_FOUND`、`REFUND_ITEM_INVALID_STATE` 三个 BusinessError 常量
    - _需求：3.9, 3.10, 3.11, 5.12_

- [x] 2. 新增逆向流程领域事件
  - [x] 2.1 创建 OrderRefundRequestedEvent、OrderRefundApprovedEvent、OrderRefundRejectedEvent
    - 在 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/event/OrderDomainEvent.kt` 中追加三个事件 data class
    - `OrderRefundRequestedEvent`：携带 orderId、refundAmount（Price）、reason（RefundReason）、requireReturn（Boolean）、refundItemIds（List<OrderItemId>）
    - `OrderRefundApprovedEvent`：携带 orderId、refundAmount（Price）、approvedItemIds（List<OrderItemId>）
    - `OrderRefundRejectedEvent`：携带 orderId、rejectReason（String）、rejectedItemIds（List<OrderItemId>）
    - 三个事件均继承 `OrderDomainEvent` 基类
    - _需求：7.1, 7.2, 7.3, 7.5_

- [x] 3. 扩展 OrderItem 实体支持逆向状态管理
  - [x] 3.1 扩展 OrderItem 接口新增 previousItemStatus 属性
    - 在 `OrderItem` 接口中新增 `val previousItemStatus: OrderItemStatus?` 属性
    - _需求：3.4, 6.7_

  - [x] 3.2 扩展 OrderItemImpl 实现 previousItemStatus 及逆向方法
    - 新增 `_previousItemStatus: OrderItemStatus?` 构造参数（默认 null）
    - 实现 `enterRefunding()` 方法：记录 previousItemStatus 并将 status 设为 REFUNDING
    - 实现 `markCanceled()` 方法：清除 previousItemStatus 并将 status 设为 CANCELED
    - 实现 `restoreFromRefunding()` 方法：从 previousItemStatus 恢复 status 并清除 previousItemStatus
    - _需求：3.4, 5.1, 5.7, 6.7_

- [x] 4. 扩展 OrderStatusTransitionRules 支持逆向状态转移
  - [x] 4.1 在 validTransitions 中新增 REFUNDING 的合法转移规则
    - 新增 `OrderStatus.REFUNDING to setOf(OrderStatus.CANCELLED, OrderStatus.PAID, OrderStatus.PENDING_SHIPMENT, OrderStatus.DELIVERED)`
    - _需求：6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 5. 扩展 Order 聚合根支持逆向操作
  - [x] 5.1 扩展 Order 接口新增逆向方法签名
    - 新增 `val previousStatus: OrderStatus?` 属性
    - 新增 `cancel(reason: CancellationReason): Result<Unit, BusinessError>` 方法
    - 新增 `requestRefund(reason: RefundReason, itemIds: List<OrderItemId>): Result<Unit, BusinessError>` 方法
    - 新增 `approveRefund(itemIds: List<OrderItemId>): Result<Unit, BusinessError>` 方法
    - 新增 `rejectRefund(rejectReason: String, itemIds: List<OrderItemId>): Result<Unit, BusinessError>` 方法
    - _需求：1.1, 1.2, 3.1, 3.2, 4.1, 5.1, 5.7, 6.6_

  - [x] 5.2 实现 OrderImpl.cancel 方法
    - 新增 `_previousStatus: OrderStatus?` 构造参数（默认 null）
    - 校验状态转移合法性（仅 PENDING_STOCK、PENDING_PAYMENT 可取消）
    - 将所有 OrderItem 状态设为 CANCELED
    - 发布 OrderCancelledEvent（携带 orderId 和取消原因描述）
    - _需求：1.1, 1.2, 1.3, 1.5, 1.6, 2.1, 2.2, 2.3_

  - [x] 5.3 实现 OrderImpl.requestRefund 方法
    - 校验 Order 状态可转移到 REFUNDING（PAID、PENDING_SHIPMENT、DELIVERED）
    - 校验 itemIds 非空、属于本订单、状态非 REFUNDING/CANCELED
    - 记录 previousStatus（仅首次进入 REFUNDING 时）
    - 将选中行项调用 enterRefunding()
    - 计算退款金额为选中行项 subtotal() 之和
    - 发布 OrderRefundRequestedEvent（requireReturn 根据 previousStatus == DELIVERED 判断）
    - _需求：3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12, 4.1, 4.2, 4.3_

  - [x] 5.4 实现 OrderImpl.approveRefund 方法
    - 校验 Order 状态为 REFUNDING
    - 校验 itemIds 非空、属于本订单、状态为 REFUNDING
    - 将选中行项调用 markCanceled()
    - 计算退款金额为选中行项 subtotal() 之和
    - 判断所有行项是否均为 CANCELED：是则 Order 状态转为 CANCELLED，否则保持 REFUNDING
    - 发布 OrderRefundApprovedEvent
    - _需求：5.1, 5.2, 5.3, 5.4, 5.5, 5.12_

  - [x] 5.5 实现 OrderImpl.rejectRefund 方法
    - 校验 Order 状态为 REFUNDING
    - 校验 itemIds 非空、属于本订单、状态为 REFUNDING
    - 将选中行项调用 restoreFromRefunding()
    - 判断是否还有行项处于 REFUNDING：无则恢复 Order 状态为 previousStatus，有则保持 REFUNDING
    - 发布 OrderRefundRejectedEvent
    - _需求：5.7, 5.8, 5.9, 5.10, 5.11, 5.12_

  - [ ]* 5.6 编写属性测试：取消操作的状态转移与副作用
    - **属性 1：取消操作的状态转移与副作用**
    - 生成处于 PENDING_STOCK/PENDING_PAYMENT 状态的订单，执行 cancel 后验证状态、行项状态、事件
    - **验证需求：1.1, 1.2, 1.3, 2.1, 2.2, 2.3**

  - [ ]* 5.7 编写属性测试：非法状态下取消操作被拒绝
    - **属性 2：非法状态下取消操作被拒绝**
    - 生成处于不可取消状态的订单，执行 cancel 后验证返回 Failure 且状态不变
    - **验证需求：1.5, 1.6**

  - [ ]* 5.8 编写属性测试：部分退款申请的状态转移与副作用
    - **属性 3：部分退款申请的状态转移与副作用**
    - 生成处于 PAID/PENDING_SHIPMENT/DELIVERED 状态的订单，选取合法行项子集执行 requestRefund，验证选中/未选中行项状态、previousItemStatus、previousStatus、事件内容
    - **验证需求：3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 4.1, 4.2, 4.3, 6.6, 6.7, 7.1**

  - [ ]* 5.9 编写属性测试：非法状态下退款申请被拒绝
    - **属性 4：非法状态下退款申请被拒绝**
    - 生成处于不可退款状态的订单，执行 requestRefund 后验证返回 Failure 且状态不变
    - **验证需求：3.12**

  - [ ]* 5.10 编写属性测试：无效行项选择下退款申请被拒绝
    - **属性 5：无效行项选择下退款申请被拒绝**
    - 测试空列表、不属于订单的 ID、已处于 REFUNDING/CANCELED 的行项，验证返回 Failure 且状态不变
    - **验证需求：3.9, 3.10, 3.11**

  - [ ]* 5.11 编写属性测试：批准退款的行项状态转移与 Order 状态推导
    - **属性 6：批准退款的行项状态转移与 Order 状态推导**
    - 生成处于 REFUNDING 状态且有 REFUNDING 行项的订单，执行 approveRefund，验证行项状态、退款金额、Order 状态推导逻辑、事件
    - **验证需求：5.1, 5.2, 5.3, 5.4, 5.5, 7.2**

  - [ ]* 5.12 编写属性测试：拒绝退款的行项状态恢复（往返属性）
    - **属性 7：拒绝退款的行项状态恢复（往返属性）**
    - 生成可退款状态订单，先 requestRefund 再 rejectRefund 同一组行项，验证状态完全恢复
    - **验证需求：5.7, 5.8, 5.9, 5.10, 6.6, 6.7, 7.3**

  - [ ]* 5.13 编写属性测试：非 REFUNDING 状态下审批操作被拒绝
    - **属性 8：非 REFUNDING 状态下审批操作被拒绝**
    - 生成处于非 REFUNDING 状态的订单，执行 approveRefund/rejectRefund 后验证返回 Failure 且状态不变
    - **验证需求：5.11**

- [x] 6. 检查点 — 确保领域层编译通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 7. 扩展 OrderStatusTransitionRules 属性测试
  - [ ]* 7.1 编写属性测试：状态转移规则完备性
    - **属性 9：状态转移规则完备性**
    - 遍历所有 OrderStatus 组合，验证 isValidTransition 返回值与预定义合法转移集合一致
    - **验证需求：6.1, 6.2, 6.3, 6.4, 6.5**

- [x] 8. 扩展 OrderService 应用服务
  - [x] 8.1 实现 OrderService.cancelOrder 方法
    - 接受 orderId 和 CancellationReason 参数
    - 遵循"加载聚合 → 执行 cancel → 保存 → 发布事件"编排模式
    - 订单不存在时返回 ORDER_NOT_FOUND
    - _需求：8.1, 8.5, 8.6_

  - [x] 8.2 实现 OrderService.requestRefund 方法
    - 接受 orderId、RefundReason 和 List<OrderItemId> 参数
    - 遵循"加载聚合 → 执行 requestRefund → 保存 → 发布事件"编排模式
    - 订单不存在时返回 ORDER_NOT_FOUND
    - _需求：8.2, 8.5, 8.6_

  - [x] 8.3 实现 OrderService.approveRefund 方法
    - 接受 orderId 和 List<OrderItemId> 参数
    - 遵循"加载聚合 → 执行 approveRefund → 保存 → 发布事件"编排模式
    - 订单不存在时返回 ORDER_NOT_FOUND
    - _需求：8.3, 8.5, 8.6_

  - [x] 8.4 实现 OrderService.rejectRefund 方法
    - 接受 orderId、拒绝原因和 List<OrderItemId> 参数
    - 遵循"加载聚合 → 执行 rejectRefund → 保存 → 发布事件"编排模式
    - 订单不存在时返回 ORDER_NOT_FOUND
    - _需求：8.4, 8.5, 8.6_

  - [ ]* 8.5 编写属性测试：不存在的订单返回 ORDER_NOT_FOUND
    - **属性 10：不存在的订单返回 ORDER_NOT_FOUND**
    - 使用 mock OrderRepository（findById 返回 null），验证 cancelOrder/requestRefund/approveRefund/rejectRefund 均返回 ORDER_NOT_FOUND
    - **验证需求：8.6**

- [x] 9. 扩展 OrderToStockEventTranslator 事件翻译器
  - [x] 9.1 新增 onOrderRefundApproved 事件监听方法
    - 在 `j-store-boot/src/main/kotlin/com/jstore/translator/OrderToStockEventTranslator.kt` 中新增 `@EventListener` 方法
    - 监听 `OrderRefundApprovedEvent`，从订单中筛选被批准行项，翻译为 `StockReleaseRequestedEvent`（仅包含被批准行项的 skuId）
    - _需求：5.6, 7.2_

- [x] 10. 配置测试依赖
  - [x] 10.1 在 j-store-order 的 build.gradle.kts 中添加 Kotest 属性测试依赖
    - 添加 `testImplementation(libs.kotest.runner.junit5)`、`testImplementation(libs.kotest.assertions.core)`、`testImplementation(libs.kotest.property)`
    - _需求：属性测试基础设施_

- [x] 11. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点确保增量验证
- 属性测试验证设计文档中定义的 10 个正确性属性
- 单元测试验证具体示例和边界场景
- 持久化层（OrderPO/OrderItemPO 的 previousStatus/previousItemStatus 字段映射）和数据库迁移脚本将在基础设施模块实现时补充
