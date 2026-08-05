# 实现计划：订单售后聚合拆分

## 概述

本计划以不兼容、无历史数据迁移的方式，将售后流程从 `Order` 聚合拆为同一订单有界上下文内的独立 `AfterSale` 聚合。实施遵循 TDD，按数据库结构、售后领域、订单退款事实、应用编排、持久化与并发、事件投影、接口与装配、删除审计的依赖顺序推进。每个实现切片先运行定向测试确认 RED，再做最小 GREEN，最后重构并复跑；工作区现有无关改动必须保留。

## Tasks

- [x] 1.1 建立直接替换式 PostgreSQL 结构
  - 负责文件：`j-store-boot/src/main/resources/db/migration/V20260803__order_after_sale_aggregate.sql`、`j-store-boot/src/test/kotlin/com/jstore/order/migration/OrderAfterSaleSchemaMigrationTest.kt`。
  - RED：先基于现有 Zonky embedded PostgreSQL/Flyway 测试编写元数据断言，覆盖 `after_sales`、`after_sale_items`、`after_sale_capacities`、`after_sale_command_receipts`、`order_refund_facts` 的列、外键、唯一键、检查约束与设计列出的索引；断言 `orders.after_sale_status`、`order_items.previous_item_status` 已删除，订单和行项新增退款累计及版本列；运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.migration.OrderAfterSaleSchemaMigrationTest"` 确认失败。
  - GREEN：新增迁移；先清空 `order_items/orders`，再删除旧列并创建新表、约束、索引；只修改新迁移，不修改 baseline、`V20260731` 或 `db/init`，不编写回填或兼容视图。
  - REFACTOR：复用元数据查询辅助函数，明确迁移仅适用于未上线开发库，再运行同一测试和 `git diff --check`。
  - _需求: 3.1, 3.2, 10.3, 10.4, 11.7_

- [x] 1.2 创建售后身份、状态、原因与快照值对象
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleId.kt`、`AfterSaleStatus.kt`、`AfterSaleValueObjects.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleValueObjectsTest.kt`。
  - RED：先用 Kotest 覆盖 `AfterSaleId`、`AfterSaleItemId`、`ApplicantActorId`、`MerchantActorId` 的类型化身份，`AfterSaleStatus` 四个精确枚举值，以及 `RefundReason`、`FulfillmentSnapshot`、`GoodsSnapshot`、`RefundEligibilitySnapshot`、`ReviewDecision` 的不可变性和边界校验。
  - GREEN：按设计建立值对象；`RefundCategory` 随 `RefundReason` 迁入售后包，原因说明 1..500，资格数量与金额为正，币种固定校验 `CNY`，退货事实与履约状态组合合法。
  - REFACTOR：保持领域层无 Spring/JPA 依赖，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleValueObjectsTest"`。
  - _需求: 1.1, 1.2, 1.3, 2.4, 4.1, 4.2, 4.3_

- [x] 1.3 创建售后命令、幂等类型与业务错误
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/command/AfterSaleCommands.kt`、`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleErrors.kt`、`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleCommandReceipt.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/command/AfterSaleCommandValidationPropertyTest.kt`。
  - RED：先用 Kotest property 覆盖创建/批准/拒绝/撤销命令，验证行项 1..100、无重复 ID、数量金额为正、仅 `CNY`、原因/拒绝原因 1..500、幂等键 trim 后 1..128；覆盖 `AfterSaleCommandType`、`AfterSaleCommandReceipt`、`AllocationAction`、`RefundCapacityCeiling`。
  - GREEN：实现设计签名的四类 CMD 及 `validate()`，定义完整 `AfterSaleErrors` 和订单侧 `REFUND_PROJECTION_INVALID`；命令只携带数据，不访问时间、仓储或框架。
  - REFACTOR：统一规范化方法与确定性错误优先级，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.command.AfterSaleCommandValidationPropertyTest"`。
  - _需求: 2.2, 2.3, 3.6, 5.2, 9.6_

- [x] 1.4 建立售后聚合与行项构造不变量
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSale.kt`、`AfterSaleImpl.kt`、`AfterSaleItem.kt`、`AfterSaleItemImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleInvariantsPropertyTest.kt`。
  - RED：先覆盖 1..100 行项、空行项、跨订单、重复订单行项、请求超过资格快照、币种不一，以及 `REQUESTED/APPROVED/REJECTED/CANCELLED` 与审核/撤销字段组合；验证 `items` 是不可变副本且仅引用订单和行项 ID。
  - GREEN：实现设计中的 `AfterSale`/`AfterSaleItem` 接口与实现，构造器显式接收完整持久化状态、`version` 和事件队列，并一次性验证全部不变量。
  - REFACTOR：抽取纯不变量快照，限制实现细节可见性，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleInvariantsPropertyTest"`。
  - _需求: 1.1, 1.2, 1.3, 1.4, 11.1, 11.2_

- [x] 1.5 定义四类显式售后领域事件
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/event/AfterSaleDomainEvent.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleDomainEventContractTest.kt`。
  - RED：先断言四类事件分别标注 `after-sale.requested/approved/rejected/cancelled` 版本 1，均实现 `ExplicitDomainEvent`，`aggregateType=AfterSale`、聚合 ID 正确、显式 UUID 事件 ID 唯一，并完整携带 actor、订单、行项、金额、原因、退货要求和发生时间。
  - GREEN：实现 `AfterSaleEventItem`、密封基类和四个事件；批准事件行项必须携带 `skuId/quantity/amount/currency`，以支持订单、库存、支付和财务订阅方独立幂等消费。
  - REFACTOR：避免事件持有聚合对象，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleDomainEventContractTest"`。
  - _需求: 8.1, 8.2, 8.3, 8.4, 8.6, 8.7, 11.8_

- [x] 1.6 实现售后批准、拒绝和撤销状态机
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleStateMachinePropertyTest.kt`。
  - RED：先穷举四个状态与 `approve/reject/cancel`，覆盖 actor 权限、空拒绝原因、审核/撤销时间、恰好一个事件，以及非法状态或失败时字段、更新时间和事件队列完全不变。
  - GREEN：实现设计签名；完整验证候选状态后一次提交字段，最后发布事件，`REQUESTED` 只允许转到三个终态。
  - REFACTOR：统一失败原子性快照和终态错误格式，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleStateMachinePropertyTest"`。
  - _需求: 5.1, 5.2, 5.4, 5.5, 6.1, 6.3, 11.1_

- [x] 1.7 实现从订单事实创建售后聚合的工厂
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleFactory.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleFactoryUnitTest.kt`。
  - RED：先覆盖买家、商家、商品展示、资格数量金额、履约状态与 `requireReturn` 的快照；覆盖目标不存在、重复、超数量/金额、无容量、非法币种/订单状态失败且不产生事件；验证 `SHIPPED/DELIVERED` 需要退货，其他两态不需要。
  - GREEN：实现 `AfterSaleFactoryImpl`，使用 `SnowFlakSequence` 生成根和行项 ID，从 `Order.refundEligibility()` 生成不可变快照，初始化 `REQUESTED` 并发布唯一 `AfterSaleRequestedEvent`；工厂不访问仓储。
  - REFACTOR：集中请求与资格匹配逻辑，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleFactoryUnitTest"`。
  - _需求: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 4.1, 4.4_

- [x] 1.8 为订单建立退款资格和累计退款行项模型
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/Order.kt`、`OrderImpl.kt`、`OrderItem.kt`、`OrderItemImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundEligibilityPropertyTest.kt`。
  - RED：先验证 `RefundEligibility`、`RefundableOrderItem`、`purchasedAmount/refundedQuantity/refundedAmount/refundable*` 派生关系，覆盖未支付、交易/履约不允许、零容量、金额数量边界及商品快照；验证订单累计等于行累计且不超过实付。
  - GREEN：新增设计类型、`Order.totalRefundedAmount` 与 `refundEligibility()`；订单行记录累计退款事实，不修改履约 `status`，更新订单不变量以移除售后摘要依赖。
  - REFACTOR：复用纯计算并防 `Price` 下溢，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderRefundEligibilityPropertyTest"`。
  - _需求: 2.1, 2.3, 7.1, 7.2, 7.8_

- [x] 1.9 实现订单对已批准售后的幂等退款投影
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/Order.kt`、`OrderImpl.kt`、`OrderRefundFact.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderApprovedAfterSaleProjectionTest.kt`、`OrderRefundProjectionPropertyTest.kt`。
  - RED：先覆盖合法多行部分退款、全部退款、重复 `afterSaleId`、空/重复/未知行项、非正数、逐行和总额超限；断言非法载荷无部分更新，重复投递返回 `newlyRegistered=false`，履约状态和行项状态不变。
  - GREEN：实现 `ApprovedRefundItem`、`RefundProjectionResult`、`RefundFact` 和 `registerApprovedAfterSale()`；先完整校验再一次性累计并登记售后 ID，部分/全部退款更新支付状态，全部退款关闭交易但保持履约事实。
  - REFACTOR：统一候选累计计算和守恒校验，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderApprovedAfterSaleProjectionTest" --tests "com.jstore.order.domain.order.OrderRefundProjectionPropertyTest"`。
  - _需求: 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 11.5_

- [x] 1.10 定义售后仓储和商家解析领域契约
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepository.kt`、`j-store-order/src/main/kotlin/com/jstore/order/acl/AfterSaleMerchantResolver.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryContractTest.kt`。
  - RED：先用编译型 fake 验证 `createWithAllocation`、`findById`、`findByOrderId`、`saveDecision`、`findReceipt` 均只暴露领域类型，并验证 resolver 只接收 `Order`、返回 `MerchantActorId`。
  - GREEN：按设计创建接口；业务原子方法返回 `Result<AfterSale,BusinessError>`，不泄露 JPA 锁、PO、事务或 SQL 类型。
  - REFACTOR：统一命名与空值语义，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.aftersale.AfterSaleRepositoryContractTest"`。
  - _需求: 3.1, 3.2, 10.2_

- [x] 2. 检查点 — 数据结构与核心领域模型
  - 保留用户及其他工作区无关改动；如发现上游设计冲突请向用户确认。
  - 运行完整测试套件 `./gradlew.bat test`，要求全部通过。

- [x] 3.1 实现售后应用服务的创建与查询编排
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/service/AfterSaleApplicationService.kt`、`j-store-order/src/test/kotlin/com/jstore/order/service/AfterSaleApplicationServiceCreateTest.kt`。
  - RED：用 fake 工厂/仓储/订单仓储/resolver 覆盖命令校验、订单不存在、申请人非买家、商家解析失败、资格失败、工厂失败、幂等快速路径和成功创建；验证成功只调用 `createWithAllocation`，任何路径都不保存订单；查询仅允许申请人或商家，无权统一返回 `NOT_FOUND`。
  - GREEN：实现 `create/get/listByOrder`；创建顺序严格为校验、回执、订单、买家、商家、资格、工厂、容量仓储，规范化 JSON SHA-256 摘要不含时间。
  - REFACTOR：抽取回执摘要及查询权限辅助函数，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.service.AfterSaleApplicationServiceCreateTest"`。
  - _需求: 2.1, 2.2, 2.3, 2.6, 3.6, 7.9, 9.1, 11.4_

- [x] 3.2 实现售后应用服务的审核与撤销编排
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/service/AfterSaleApplicationService.kt`、`j-store-order/src/test/kotlin/com/jstore/order/service/AfterSaleApplicationServiceDecisionTest.kt`。
  - RED：覆盖批准/拒绝/撤销的校验、回执快速路径、售后不存在、商家/申请人无权、聚合行为失败、保存冲突和成功；断言批准使用 `APPROVE`，拒绝/撤销使用 `RELEASE`，且不加载或保存订单。
  - GREEN：按“校验 → 回执 → 加载售后 → actor 校验 → 领域行为 → saveDecision”实现三个方法，同键同摘要返回当前聚合，同键异摘要冲突。
  - REFACTOR：共享决策模板但不把状态规则移入服务，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.service.AfterSaleApplicationServiceDecisionTest"`。
  - _需求: 3.4, 3.5, 3.6, 5.3, 6.2, 7.9, 9.6, 11.4_

- [x] 3.3 创建售后根与行项 JPA 映射和往返转换
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/persistence/AfterSalePO.kt`、`AfterSaleItemPO.kt`、`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSalePORoundTripPropertyTest.kt`。
  - RED：先对四个合法状态、1..100 行项、快照、审核/撤销信息、时间和版本执行 `AfterSale → PO → AfterSale` 属性测试，并验证非法 PO 组合被领域构造器拒绝。
  - GREEN：实现 `@Entity/@Table`、根 `@Version`、EAGER 行项映射和 `Converter.toPO/toDomain`；所有金额按分值 `numeric(19,0)` 往返，恢复对象事件队列为空。
  - REFACTOR：复用 Price/时间转换并运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.aftersale.AfterSalePORoundTripPropertyTest"`。
  - _需求: 10.1, 11.2_

- [x] 3.4 创建容量与命令回执 JPA 访问层
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/persistence/AfterSaleCapacityPO.kt`、`AfterSaleCommandReceiptPO.kt`、`AfterSalePOJpaRepository.kt`、`AfterSaleCapacityPOJpaRepository.kt`、`AfterSaleCommandReceiptPOJpaRepository.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSalePersistenceMappingTest.kt`。
  - RED：先验证容量 ceiling/requested/approved 字段、回执唯一业务键、根按订单查询排序，以及容量仓储具备 `INSERT ... ON CONFLICT DO NOTHING` 和按 `order_item_id` 升序悲观锁查询。
  - GREEN：实现 PO 与 Spring Data repository；锁查询使用 `PESSIMISTIC_WRITE` 或等价原生 `FOR UPDATE`，领域接口不暴露锁细节。
  - REFACTOR：收敛列名与枚举字符串长度，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.aftersale.AfterSalePersistenceMappingTest"`。
  - _需求: 3.1, 3.2, 3.3, 3.6, 10.1, 10.2_

- [x] 3.5 实现创建售后、容量占用、回执与 Outbox 的单事务原子操作
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryPostgresTest.kt`。
  - RED：用真实 PostgreSQL 覆盖容量行初始化上限一致、升序锁、数量/金额超限、售后/行项/容量/回执/Outbox 同时提交；模拟 publisher 失败验证全部回滚且聚合事件不被误清空。
  - GREEN：给 `createWithAllocation` 加 `@Transactional`，锁定并校验全部容量后写根、占用、回执，再逐个调用实际 `DomainEventPublisher.publishEvent`；仅在全部成功后通过现有 `getDomainEvent()` 出队，避免扩展公共框架 API。
  - REFACTOR：固定锁排序并翻译可识别约束异常，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.aftersale.AfterSaleRepositoryPostgresTest"`。
  - _需求: 2.6, 3.1, 3.2, 3.3, 8.5, 10.1, 11.3_

- [x] 3.6 实现审核决定、容量转换、回执与 Outbox 的单事务原子操作
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleRepositoryImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleDecisionPostgresTest.kt`。
  - RED：覆盖批准将 requested 转 approved、拒绝/撤销释放 requested、终态不二次改变、根版本冲突、Outbox/回执失败全回滚；验证锁顺序和事件仅一次。
  - GREEN：实现 `saveDecision` 的根行/版本及容量升序锁，原子保存状态、额度、回执、Outbox；将 `ObjectOptimisticLockingFailureException` 和约束竞争映射为设计业务错误。
  - REFACTOR：共享事务内保存/发布逻辑，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.aftersale.AfterSaleDecisionPostgresTest"`。
  - _需求: 3.4, 3.5, 5.4, 6.3, 8.5, 9.6, 11.3_

- [x] 3.7 验证并发容量竞争和幂等命令回执
  - 负责文件：`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/aftersale/AfterSaleAllocationConcurrencyPostgresTest.kt`、`AfterSaleIdempotencyPostgresTest.kt`；仅在测试揭示问题时修改 `AfterSaleRepositoryImpl.kt` 和容量/回执 repository。
  - RED：使用真实并发事务竞争同一及交叉多行项，断言成功总量永不超 ceiling 且无死锁；并发同 actor/type/key/摘要只产生一个聚合、占用和事件，同键异摘要稳定冲突。
  - GREEN：补足唯一键冲突后的新事务回执重读、固定锁顺序和异常翻译，不在应用服务做无限重试。
  - REFACTOR：稳定线程屏障与超时，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "*AfterSaleAllocationConcurrencyPostgresTest" --tests "*AfterSaleIdempotencyPostgresTest"`。
  - _需求: 3.3, 3.6, 5.4, 9.6, 11.3_

- [x] 3.8 更新订单 JPA 模型、退款事实映射与悲观锁访问
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt`、`OrderRefundFactPO.kt`、`OrderPOJpaRepository.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/OrderRefundPersistenceMappingTest.kt`。
  - RED：先验证 `OrderPO.@Version`、订单累计退款、行项累计退款、无 `afterSaleStatus/previousItemStatus`，以及 `(order,afterSale,item)` 唯一退款事实；验证 `findByIdForUpdate` 对根使用悲观写锁，普通查询不加锁。
  - GREEN：新增字段、退款事实关联和锁定查询，删除旧售后列映射；保留 `OrderItemStatus.CANCELED` 供交易取消使用。
  - REFACTOR：整理构造参数与 JPA 默认值，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.order.OrderRefundPersistenceMappingTest"`。
  - _需求: 7.1, 7.3, 10.3_

- [x] 3.9 更新订单仓储转换并验证退款事实往返
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/OrderRepositoryImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/OrderRefundFactsPORoundTripPropertyTest.kt`。
  - RED：先覆盖多行累计退款和多售后事实的 `Order → PO → Order` 往返、总额守恒、非法 PO 拒绝，以及版本保真。
  - GREEN：更新 Converter 映射 `version/totalRefundedAmount/refundedQuantity/refundedAmount/refundFacts`，删除 after-sale/previous-item 字段；新增基础设施内部锁定加载/保存投影路径。
  - REFACTOR：复用现有订单转换辅助，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.order.OrderRefundFactsPORoundTripPropertyTest"`。
  - _需求: 7.1, 7.2, 7.3, 10.3, 11.3_

- [x] 3.10 实现订单批准事件投影服务及监听器契约
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/service/OrderRefundProjectionService.kt`、`OrderRefundProjectionHandler.kt`、`j-store-order/src/test/kotlin/com/jstore/order/service/OrderRefundProjectionHandlerTest.kt`。
  - RED：先验证监听器 ID 固定为 `order.after-sale-approved.refund-projection.v1`、订单 ID/行项映射正确、重复事件成功无副作用、订单缺失或非法载荷抛投影异常；验证领域模块内服务接口无 Spring 依赖。
  - GREEN：监听 `AfterSaleApprovedEvent` 并委托投影端口；端口负责明确失败语义，处理器不吞异常，保证 Outbox 可重试/死信。
  - REFACTOR：将事件到 `ApprovedRefundItem` 的转换集中一处，运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.service.OrderRefundProjectionHandlerTest"`。
  - _需求: 7.3, 7.4, 7.5, 8.6, 11.8_

- [x] 4. 检查点 — 应用编排、持久化与并发边界
  - 核对售后事务从未保存订单，容量/回执/Outbox 在真实 PostgreSQL 中原子提交，保留工作区无关改动。
  - 运行完整测试套件 `./gradlew.bat test`，要求全部通过。

- [x] 5.1 实现订单投影的基础设施事务适配器
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/service/OrderRefundProjectionServiceImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/service/OrderRefundProjectionPostgresTest.kt`。
  - RED：用真实 PostgreSQL/Outbox 投递事务覆盖锁定订单、退款事实与订单累计同事务、重复批准事件幂等、并发不同售后批准不丢失、非法投影全部回滚；断言 `domain_event_consumption` 回执与订单修改同一外层事务。
  - GREEN：实现 `@Transactional` 投影适配器，使用 `findByIdForUpdate`、调用 `registerApprovedAfterSale` 并保存；将业务不变量失败抛为不可忽略的 `NonRetryableRefundProjectionException`，不提前登记消费成功。
  - REFACTOR：日志仅记录 eventId/afterSaleId/orderId，运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.service.OrderRefundProjectionPostgresTest"`。
  - _需求: 7.3, 7.4, 7.5, 7.9, 8.5, 11.5_

- [x] 5.2 实现单店商家解析器与配置校验
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/ConfiguredAfterSaleMerchantResolver.kt`、`j-store-boot/src/main/kotlin/com/jstore/order/config/OrderMerchantProperties.kt`、`j-store-boot/src/test/kotlin/com/jstore/order/config/OrderMerchantConfigurationTest.kt`。
  - RED：先验证 `jstore.order.merchant-id` 正数时返回固定 `MerchantActorId`，缺失、零或负数时 Spring 启动失败，请求体无法覆盖商家身份。
  - GREEN：实现 resolver 和 `@ConfigurationProperties` 校验；配置仅作为当前单店 ACL 适配器，不进入领域聚合规则。
  - REFACTOR：错误信息明确缺失键，运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.config.OrderMerchantConfigurationTest"`。
  - _需求: 5.3, 9.5_

- [x] 5.3 装配售后工厂、应用服务、投影与 resolver
  - 负责文件：`j-store-boot/src/main/kotlin/com/jstore/order/config/OrderBootConfiguration.kt`、`j-store-boot/src/test/kotlin/com/jstore/order/config/AfterSaleBootWiringTest.kt`。
  - RED：先启动最窄 Spring 上下文，断言 `AfterSaleFactory`、`AfterSaleApplicationService`、`AfterSaleRepository`、resolver、`OrderRefundProjectionHandler` 和事务投影适配器唯一可注入，并验证事件类型注册扫描到四类售后事件。
  - GREEN：在现有配置中装配无 Spring 注解的领域/应用对象，基础设施 repository 继续由组件扫描提供；保持现有事件总线/Outbox 配置不变。
  - REFACTOR：修正现有配置包命名引用但不扩大范围，运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.config.AfterSaleBootWiringTest"`。
  - _需求: 8.5, 9.5, 11.8_

- [x] 5.4 提供独立售后资源 API
  - 负责文件：`j-store-boot/src/main/kotlin/com/jstore/order/controller/AfterSaleController.kt`、`j-store-boot/src/main/kotlin/com/jstore/order/controller/ControllerResponses.kt`、`j-store-boot/src/test/kotlin/com/jstore/order/controller/AfterSaleControllerContractTest.kt`。
  - RED：先用 MockMvc/现有认证测试六个路由、`@CurrentUserId`、`Idempotency-Key`、参数校验、权限/冲突错误和详情/列表 JSON；金额以分输出，时间沿用 `LocalDateTime`，请求不含 applicant/merchant 可伪造字段。
  - GREEN：实现设计路由、请求/响应 DTO 和共享 `{message,errorCode}`/`Result.toResponse` 转换，完整返回原因、快照、行项、审核/撤销和时间字段。
  - REFACTOR：所有响应复用单一映射函数，运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.controller.AfterSaleControllerContractTest"`。
  - _需求: 9.1, 9.2, 9.5, 9.6, 11.6_

- [x] 5.5 清理订单 API 并公开退款累计摘要
  - 负责文件：`j-store-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt`、`j-store-boot/src/test/kotlin/com/jstore/order/controller/OrderControllerRefundSummaryContractTest.kt`。
  - RED：先断言详情/列表包含 `totalRefundedAmount`、行项 `refundedQuantity/refundedAmount`，不含 `afterSaleStatus` 或审核信息；旧 `/refund`、`/approve-refund`、`/reject-refund` 路由均不存在。
  - GREEN：删除旧退款 DTO/方法，更新 `OrderResponse` 与行项映射；保留支付、交易、履约和行项履约状态。
  - REFACTOR：共享控制器响应辅助，运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.controller.OrderControllerRefundSummaryContractTest"`。
  - _需求: 9.3, 9.4, 10.5, 11.6_

- [x] 5.6 建立数量感知的售后库存恢复事件翻译器
  - 负责文件：`j-store-boot/src/main/kotlin/com/jstore/translator/OrderToStockEventTranslator.kt`、商品上下文售后库存恢复事件及处理器、对应翻译器与处理器测试。
  - RED：先验证 `AfterSaleApprovedToStockRestoreTranslator` 订阅新批准事件，`requireReturn=true` 不恢复库存，false 时按事件 SKU/数量发布 `AfterSaleStockRestoreRequestedEvent`；验证不查询订单、不复用整笔预占释放事件且 listenerId 稳定。
  - GREEN：直接使用批准事件快照生成数量感知的恢复行项；商品侧调用按数量增加可售库存的行为，不依赖订单预占记录。
  - REFACTOR：保持翻译纯格式转换，并验证部分退款只恢复申请数量。
  - _需求: 8.2, 8.6, 8.7, 11.8_

- [x] 5.7 删除订单聚合旧售后状态、行为和退款中模型
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/Order.kt`、`OrderImpl.kt`、`OrderItem.kt`、`OrderItemImpl.kt`、`OrderItemStatus.kt`；删除 `domain/order/AfterSaleStatus.kt`、`domain/order/RefundReason.kt` 和三个 `domain/order/command/Order*RefundCMD.kt`。
  - RED：先运行 `rg -n "AfterSaleStatus|requestRefund|approveRefund|rejectRefund|REFUNDING|previousItemStatus|enterRefunding|restoreFromRefunding" j-store-order/src/main` 并把命中旧订单流程视为失败；新增/更新订单不变量测试，确保取消仍可使用 `CANCELED`。
  - GREEN：删除订单售后维度、退款行为、退款中状态和恢复字段；同步简化 `OrderStateSnapshot/OrderStateInvariants`，退款累计事实取代原售后摘要。
  - REFACTOR：运行 `./gradlew.bat :j-store-order:test` 和上述审计，允许新 `domain/aftersale` 类型命中但不允许订单聚合旧引用。
  - _需求: 1.5, 1.6, 7.8, 10.6_

- [x] 5.8 删除旧订单退款服务与事件契约
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/service/OrderService.kt`、`j-store-order/src/main/kotlin/com/jstore/order/domain/order/event/OrderDomainEvent.kt`；删除仅验证旧流程的 `j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundApprovalPropertyTest.kt`、`OrderRefundRejectionPropertyTest.kt`、`OrderRefundStatusUnitTest.kt`、`OrderRefundValidationPropertyTest.kt`。
  - RED：先让新的事件契约测试断言不存在 `order.refund-requested/approved/rejected` 注册类型和 `OrderService` 三个旧方法，同时保留其他订单事件契约。
  - GREEN：删除旧服务方法、导入和三个旧事件，只保留新售后事件与订单既有非退款事件；迁移仍有价值的守恒场景到新投影测试后再删除旧夹具。
  - REFACTOR：运行 `./gradlew.bat :j-store-order:test` 以及 `rg -n "OrderRefund(Requested|Approved|Rejected)Event|Order(Request|Approve|Reject)RefundCMD" j-store-order/src/main`。
  - _需求: 1.6, 8.7, 10.6, 11.8_

- [x] 5.9 迁移受影响订单与基础设施测试夹具
  - 负责文件：仅限 `j-store-order/src/test/kotlin/com/jstore/order/`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/` 中经搜索确认仍构造旧售后字段或前状态的既有测试与 `OrderTestFixtures.kt`。
  - RED：依次运行 `./gradlew.bat :j-store-order:test`、`./gradlew.bat :j-store-order-infrastructure:test`，记录由构造签名、四维状态降为三维状态和 PO 字段删除造成的编译/断言失败。
  - GREEN：将夹具改为三维订单状态、退款累计和退款事实输入；保留原收货、商品快照、交易/支付/履约及取消测试覆盖，不通过放宽领域不变量方便测试。
  - REFACTOR：再次运行两个模块测试并审计 `rg -n "afterSaleStatus|previousItemStatus|REFUNDING" j-store-order/src/test j-store-order-infrastructure/src/test`。
  - _需求: 1.5, 7.1, 10.3, 11.9_

- [x] 5.10 完成跨模块删除审计与集成回归
  - 负责文件：`j-store-boot/src/test/kotlin/com/jstore/order/integration/AfterSaleApprovedIntegrationEventTest.kt`；仅按审计结果清理 `j-store-order/src/main/`、`j-store-order-infrastructure/src/main/`、`j-store-boot/src/main/` 的旧引用。
  - RED：先建立集成测试验证新批准事件经 Outbox 分别驱动订单投影与库存翻译、重复投递不重复退款；运行 `rg -n "OrderRefundRequestedEvent|OrderRefundApprovedEvent|OrderRefundRejectedEvent|OrderRequestRefundCMD|OrderApproveRefundCMD|OrderRejectRefundCMD|afterSaleStatus|previous_item_status|REFUNDING|approve-refund|reject-refund|/refund" j-store-order/src/main j-store-order-infrastructure/src/main j-store-boot/src/main`，任何旧生产契约命中均失败。
  - GREEN：清除残余旧导入、装配、DTO、路由和映射，不新增 typealias、适配器、兼容字段或旧事件转发；保留新售后语义及交易取消的 `CANCELED`。
  - REFACTOR：运行 `./gradlew.bat :j-store-order:test :j-store-order-infrastructure:test :j-store-boot:test`、上述 `rg` 审计及 `git diff --check`。
  - _需求: 8.5, 8.7, 10.5, 10.6, 11.8, 11.9_

- [x] 6. 检查点 — 最终验证
  - 核对设计 15 项正确性属性、需求 1–11、真实 PostgreSQL 结构与并发、Transactional Outbox、订单投影、库存翻译、新 API 和删除清单均有自动化覆盖；使用 `git status --short` 确认未回退用户无关改动。
  - 运行完整测试套件 `./gradlew.bat test`，要求全部通过。

## 备注

- 所有复选框在实现前保持未勾选；后续实现应严格按顺序逐项执行，并在测试与评审通过后勾选。
- 本计划不启用 `manifest.json`，不修改既有迁移和初始化快照，不提供数据回填、兼容 API、兼容事件或兼容字段。
- `DomainEventPublisher.publishEvent` 的生产实现要求已有事务；售后生命周期事件必须在 `AfterSaleRepositoryImpl` 原子仓储事务中写 Outbox。订单批准投影依赖 Outbox 投递外层事务与 `domain_event_consumption` 去重，不在售后事务同步保存订单。
- PostgreSQL 集成测试使用项目已引入的 Zonky embedded PostgreSQL；若环境阻止启动，必须记录具体命令与原因，不得将未运行视为通过。
- `OrderItemStatus.CANCELED` 继续表达整笔交易取消形成的行项事实；删除的是 `REFUNDING` 和退款拒绝恢复所需的 `previousItemStatus`。



