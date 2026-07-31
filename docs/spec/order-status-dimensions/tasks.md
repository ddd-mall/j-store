# 实现计划：订单状态多维化

## 概述

本计划按“破坏性数据库结构迁移 → 领域模型与不变量 → 正向/取消/退款行为 → 工厂与测试夹具 → 持久化 → API → 旧模型清理与回归”的依赖顺序实施。每个任务都遵循 TDD：先增加或调整测试并运行指定最小命令确认红灯，再完成最小实现使其转绿，最后在同一测试保护下重构。项目尚未上线，不实现旧 `OrderStatus`、订单级 `previousStatus`、数据库旧列或 API `status` 的兼容逻辑。

## Tasks

- [x] 1.1 新增破坏性 Flyway 迁移及数据库结构验证
  - 负责文件：`j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql`、`j-store-boot/src/test/kotlin/com/jstore/order/migration/OrderStatusDimensionsMigrationTest.kt`。
  - RED：先编写 PostgreSQL/Flyway 集成测试，从当前 baseline 迁移后查询 `information_schema.columns`、约束和 `pg_indexes`，断言四列的 `VARCHAR(32)`、默认值、非空、CHECK 取值、四个 `(状态列, create_time DESC)` 索引，以及旧 `status`、`previous_status`、`idx_orders_status_create_time` 均不存在；运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.migration.OrderStatusDimensionsMigrationTest"` 并确认因迁移缺失失败。
  - GREEN：新增迁移，严格按 `DELETE order_items`、`DELETE orders`、删除旧索引/旧列、新增四列与约束、新增四索引的顺序执行；不得修改 baseline 或 `docker/postgres/init` 快照。
  - REFACTOR：复用测试中的元数据查询辅助函数并再次运行同一命令，确保迁移注释明确“仅适用于未上线开发环境”。
  - _需求: 7.1, 7.2, 7.3, 7.4, 7.6, 8.4_

- [x] 1.2 建立四个状态枚举与订单聚合公开契约
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/TradeStatus.kt`、`PaymentStatus.kt`、`FulfillmentStatus.kt`、`AfterSaleStatus.kt`、`Order.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderStatusDimensionsUnitTest.kt`。
  - RED：先用 Kotest `FunSpec` 穷举断言四个枚举仅包含设计规定值，并让测试引用 `Order.tradeStatus/paymentStatus/fulfillmentStatus/afterSaleStatus`；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderStatusDimensionsUnitTest"` 确认编译或断言失败。
  - GREEN：新增四个枚举；在 `Order` 删除订单级 `status`、`previousStatus`，新增四个只读属性，保持所有行为签名不变。
  - REFACTOR：整理领域包导入并再次运行同一命令，不给恢复路径提供默认状态或旧状态投影。
  - _需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8_

- [x] 1.3 实现统一状态快照与跨维度不变量引擎
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderStateInvariantsPropertyTest.kt`。
  - RED：先用 Kotest property/受控枚举笛卡尔积覆盖 `OrderStateSnapshot` 合法和非法组合、空行项、退款中/已取消行项约束，并断言 `violations` 收集全部违规、`requireValid` 抛出包含全部 violation 的 `IllegalArgumentException`；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderStateInvariantsPropertyTest"`。
  - GREEN：在 `OrderImpl.kt` 内新增 `internal data class OrderStateSnapshot` 与 `internal object OrderStateInvariants`，实现设计列出的全部规则；将 `OrderImpl` 构造器改为显式接收四个私有可变状态并在 `init` 调用 `requireValid`，公开四个只读 getter。
  - REFACTOR：抽取无副作用的快照构造辅助方法，保持领域层无 Spring/JPA 依赖；再次运行同一命令。
  - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 8.2_

- [x] 1.4 实现库存确认、支付与备货的逐维正向转换
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderStatusDimensionsUnitTest.kt`。
  - RED：先覆盖 `confirmStock()`、`pay(Price)`、`confirmForShipment()` 的合法前后快照、金额/时间和 `OrderPaidEvent` 关键载荷，以及每个不完整前置组合返回 `OrderErrors.ILLEGAL_STATE` 且聚合快照和事件队列不变；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderStatusDimensionsUnitTest"`。
  - GREEN：按“完整前置验证 → 候选快照不变量 → 一次性提交 → 最后发事件”实现三项行为，仅改变指定维度；非法消息列出操作名和当前四个枚举名。
  - REFACTOR：复用行为级候选校验辅助函数，但不恢复通用状态图；再次运行同一命令。
  - _需求: 2.1, 2.2, 2.3, 2.7, 2.8, 5.8, 8.1, 8.3, 8.7_

- [x] 1.5 实现发货、收货与完成的逐维正向转换
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderStatusDimensionsUnitTest.kt`。
  - RED：先覆盖 `ship()`、`confirmDelivery()`、`complete()` 每步只改变规定维度、行项变为 `SHIPPING/SHIPPING_FINISHED`、事件关键载荷保持不变，以及退款中或终态调用完全原子失败；运行同一 `OrderStatusDimensionsUnitTest` 定向命令。
  - GREEN：实现 `PENDING_SHIPMENT → SHIPPED → DELIVERED` 与 `ACTIVE → COMPLETED`，完整校验支付及售后前置，最后发布既有 `OrderShippedEvent`、`OrderCompletedEvent`。
  - REFACTOR：消除快照/更新时间重复且保持事件类、`@DomainEventType` 名称、版本、字段不变；再次运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderStatusDimensionsUnitTest"`。
  - _需求: 2.4, 2.5, 2.6, 2.7, 2.8, 5.8, 8.1, 8.3, 8.7_

- [x] 1.6 实现未支付取消与库存不足关闭
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderCancellationUnitTest.kt`。
  - RED：先覆盖 `CREATED/ACTIVE + UNPAID/UNFULFILLED/NONE` 的 `cancel(CancellationReason)`、`CREATED` 的 `markStockInsufficient(String)`，断言均关闭交易且保持其他维度；买家取消全部行项，库存不足保持行项；终态、已支付或售后中调用均无副作用失败；运行对应定向测试。
  - GREEN：实现两个取消路径及候选不变量校验，保持既有 `OrderCancelledEvent` 类型、原因、ID 等载荷契约。
  - REFACTOR：统一非法状态错误格式和提交时序；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderCancellationUnitTest"`。
  - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 5.8, 8.3, 8.7_

- [x] 1.7 实现退款目标集合的确定性预校验
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundAtomicityPropertyTest.kt`。
  - RED：先为请求、批准、拒绝建立空集合、重复 ID、外部 ID、非法行项状态属性测试，断言错误优先级依次为 `REFUND_ITEMS_EMPTY`、`REFUND_ITEM_INVALID_STATE`、`REFUND_ITEM_NOT_FOUND`、`REFUND_ITEM_INVALID_STATE`，且四维状态、行项、金额、时间、事件队列完全不变；运行定向测试。
  - GREEN：用 `associateBy` 一次解析目标行，所有验证在首次写入前完成，并为三个退款行为复用私有目标解析结果。
  - REFACTOR：保持错误常量和公开签名不变，限制辅助类型可见性；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderRefundAtomicityPropertyTest"`。
  - _需求: 4.8, 5.8, 8.3_

- [x] 1.8 实现退款申请与售后摘要推导
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundStatusUnitTest.kt`。
  - RED：先覆盖支付为 `PAID/PARTIALLY_REFUNDED`、履约为 `UNFULFILLED/PENDING_SHIPMENT/DELIVERED` 的首次及后续合法申请，断言目标行进入 `REFUNDING`、三个正向维度不变、摘要为 `PROCESSING` 或 `PARTIALLY_COMPLETED`、`requireReturn` 只由履约状态决定；明确断言 `SHIPPED` 不可申请；运行定向测试。
  - GREEN：实现 `deriveAfterSaleStatus(paymentStatus, itemStatuses)` 的固定优先级，并实现 `requestRefund` 的完整四维前置、候选行项快照、原子提交和 `OrderRefundRequestedEvent`。
  - REFACTOR：确保摘要只能经纯函数重算，且部分批准后继续申请剩余行项；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderRefundStatusUnitTest"`。
  - _需求: 4.1, 4.2, 4.9, 4.10, 5.5, 5.6, 8.1, 8.7_

- [x] 1.9 实现部分与全量退款批准并保留履约事实
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundApprovalPropertyTest.kt`。
  - RED：先用 2–8 个行项和合法非空子集覆盖部分批准得到 `ACTIVE/PARTIALLY_REFUNDED/原履约/PARTIALLY_COMPLETED`、最后一项批准得到 `CLOSED/REFUNDED/原履约/COMPLETED`，穷举四个履约状态保持原值，并断言 `requireReturn` 不受提交顺序影响；运行定向测试。
  - GREEN：在任何提交前缓存当前履约及 `requireReturn`，将目标行候选设为 `CANCELED`，根据是否全部取消确定支付/交易/售后候选状态，统一校验后提交并发布既有批准事件。
  - REFACTOR：把“全部取消/仍有未取消”计算集中到纯函数，保证 `actualPay` 不因退款批准改变；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderRefundApprovalPropertyTest"`。
  - _需求: 4.3, 4.4, 4.9, 5.3, 5.5, 5.6, 8.7, 8.9_

- [x] 1.10 实现退款拒绝与行项级恢复
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderRefundRejectionPropertyTest.kt`。
  - RED：先覆盖拒绝合法子集时仅目标行恢复 `previousItemStatus`，仍有处理中行保持摘要，无处理中且无批准回到 `NONE`、已有批准保持 `PARTIALLY_COMPLETED`，并断言交易/支付/履约不被覆盖；运行定向测试。
  - GREEN：实现 `rejectRefund` 的候选恢复、摘要重算、不变量校验、一次性提交和既有拒绝事件；只保留 `OrderItem.previousItemStatus`，不引入订单级前序状态。
  - REFACTOR：复用退款集合预校验及摘要函数；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderRefundRejectionPropertyTest"`。
  - _需求: 4.5, 4.6, 4.7, 4.9, 5.7, 8.7_

- [x] 2. 检查点 — 迁移与核心领域行为
  - 保留工作区中既有 JDK 25 及其他无关改动，不执行 reset、checkout 或回退操作；若发现冲突请向用户确认。
  - 运行完整测试套件 `./gradlew.bat test`，要求全部通过。

- [x] 3.1 更新订单工厂的四维初始状态
  - 负责文件：`j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderFactoryUnitTest.kt`、`OrderFactoryShippingInfoPropertyTest.kt`。
  - RED：先调整工厂测试，断言所有合法创建结果唯一为 `CREATED/UNPAID/UNFULFILLED/NONE`、无订单级前序状态且创建事件载荷不变；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderFactoryUnitTest" --tests "com.jstore.order.domain.order.OrderFactoryShippingInfoPropertyTest"`。
  - GREEN：构造 `OrderImpl` 时显式传入四个初始状态，保持金额、商品/地址快照及事件行为不变。
  - REFACTOR：删除工厂内所有旧状态引用并再次运行同一命令。
  - _需求: 1.7, 1.8, 8.7_

- [x] 3.2 迁移领域测试夹具与构造调用
  - 负责文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderShippingInfoUnitTest.kt`、`SnapshotVersionMismatchPropertyTest.kt`、`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt`。
  - RED：先新增集中式 `OrderTestFixtures`，让现有测试改用显式四维合法组合，并运行 `./gradlew.bat :j-store-order:test` 暴露残余旧构造参数和旧状态断言。
  - GREEN：迁移受影响测试夹具及 `OrderImpl` 调用，保持收货信息、快照版本等非状态断言不变；不得通过放宽构造不变量方便测试。
  - REFACTOR：用具名参数/合法场景构造器减少重复，再运行 `./gradlew.bat :j-store-order:test`。
  - _需求: 8.2, 8.8_

- [x] 3.3 回归应用服务失败传播和保存边界
  - 负责文件：`j-store-order/src/test/kotlin/com/jstore/order/service/OrderServiceStatusDimensionsTest.kt`；仅在测试揭示必要时修改 `j-store-order/src/main/kotlin/com/jstore/order/service/OrderService.kt`、`OrderStockEventHandler.kt`、`OrderStockInsufficientEventHandler.kt`。
  - RED：用 fake/mock repository 覆盖四维非法操作原样返回 `ILLEGAL_STATE` 且不调用 `save`，合法库存确认、库存不足、支付、履约、取消、退款仍按现有路径保存；运行定向测试。
  - GREEN：仅修正因接口字段替换导致的编译/编排问题，不在服务或事件处理器添加业务状态判断，不改变 command 公共签名。
  - REFACTOR：保持“加载 → 聚合行为 → 成功保存”的结构；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.service.OrderServiceStatusDimensionsTest"`。
  - _需求: 2.7, 3.4, 4.8, 8.1, 8.3, 8.8_

- [x] 3.4 更新 `OrderPO` 的四维枚举映射
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/OrderPOStatusMappingTest.kt`。
  - RED：先用反射/JPA 元模型测试四个属性均为 `@Enumerated(EnumType.STRING)`，列名、非空和长度为设计值，且不存在订单级 `status/previousStatus`；运行定向测试。
  - GREEN：删除旧两个 PO 字段及 `OrderStatus` 导入，新增四个非空枚举字段和设计默认值；保留 `OrderItemPO.previousItemStatus`。
  - REFACTOR：整理构造参数顺序并运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.order.OrderPOStatusMappingTest"`。
  - _需求: 7.5, 7.7, 8.5_

- [x] 3.5 更新仓储转换器并验证四维往返
  - 负责文件：`j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/OrderRepositoryImpl.kt`、`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/OrderStatusDimensionsPORoundTripPropertyTest.kt`。
  - RED：先对全部可持久化合法四维组合及金额、时间、收货信息、行项/行项前序状态执行 `Order → OrderPO → Order` 属性测试，并覆盖非法 PO 恢复被构造器拒绝；运行定向测试。
  - GREEN：`Converter.toPO/toDomain` 原样映射四列，移除旧字段读写，把四维状态显式传给 `OrderImpl`；其他字段保持不变。
  - REFACTOR：复用合法状态生成器并运行 `./gradlew.bat :j-store-order-infrastructure:test --tests "com.jstore.order.domain.order.OrderStatusDimensionsPORoundTripPropertyTest"`。
  - _需求: 5.9, 7.5, 7.7, 8.2, 8.5_

- [x] 3.6 迁移既有基础设施转换测试夹具
  - 负责文件：`j-store-order-infrastructure/src/test/kotlin/com/jstore/order/domain/order/RecipientInfoPORoundTripPropertyTest.kt`、`RecipientInfoPOBackwardCompatPropertyTest.kt`。
  - RED：先将现有 `OrderImpl/OrderPO` 构造改为四维合法输入，运行 `./gradlew.bat :j-store-order-infrastructure:test` 定位残余旧字段和构造参数。
  - GREEN：迁移夹具且保留收货信息 JSON、金额、时间和行项前序状态的原有断言；不为订单状态保留向后兼容测试。
  - REFACTOR：复用新的状态往返 fixture，运行 `./gradlew.bat :j-store-order-infrastructure:test`。
  - _需求: 7.5, 7.7, 8.5, 8.8_

- [x] 3.7 更新 API 四维响应契约
  - 负责文件：`j-store-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt`、`j-store-boot/src/test/kotlin/com/jstore/order/controller/OrderControllerStatusContractTest.kt`。
  - RED：先用现有 Spring Boot/JUnit Platform 栈的 Jackson DTO 或最窄 MockMvc 测试覆盖创建、详情、分页及操作后查询映射：JSON 含四个枚举名称字段、不含订单级 `status`，但 `items[].status` 仍存在，错误 DTO 不变；运行定向测试。
  - GREEN：将 `OrderResponse.status` 替换为四个非空字符串字段，`toOrderResponse()` 使用各枚举 `.name`；保持 URL、请求 DTO、HTTP 方法、认证注解和错误映射不变。
  - REFACTOR：确保所有响应路径继续复用同一转换函数；运行 `./gradlew.bat :j-store-boot:test --tests "com.jstore.order.controller.OrderControllerStatusContractTest"`。
  - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 8.6_

- [x] 3.8 删除旧订单状态模型与全部生产引用
  - 负责文件：删除 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/OrderStatus.kt`、`OrderStatusTransitionRules.kt`；按搜索结果清理 `j-store-order/src/main/`、`j-store-order-infrastructure/src/main/`、`j-store-boot/src/main/` 中残余引用。
  - RED：运行 `rg -n "OrderStatus|OrderStatusTransitionRules|previousStatus|_previousStatus|_status" j-store-order/src/main j-store-order-infrastructure/src/main j-store-boot/src/main`，把任何命中视为失败（`OrderItem.previousItemStatus`/`_previousItemStatus` 除外），并运行三个受影响模块编译。
  - GREEN：删除两个旧类型文件及订单级引用，不删除行项恢复字段，不增加 typealias、适配器或旧 API 投影。
  - REFACTOR：运行 `./gradlew.bat :j-store-order:compileKotlin :j-store-order-infrastructure:compileKotlin :j-store-boot:compileKotlin`，再执行上述 `rg` 审计。
  - _需求: 1.2, 1.8, 6.4, 7.2, 7.7_

- [x] 3.9 完成领域事件契约与终态原子性回归
  - 负责文件：`j-store-order/src/test/kotlin/com/jstore/order/domain/order/OrderDomainEventContractTest.kt`、`OrderTerminalStateAtomicityPropertyTest.kt`；生产事件文件 `j-store-order/src/main/kotlin/com/jstore/order/domain/order/event/OrderDomainEvent.kt` 只允许在编译适配确有必要时修改且不得改变公共结构。
  - RED：先回归创建、支付、取消、发货、完成、退款申请/批准/拒绝的事件类型、`eventName`、版本和关键载荷；属性测试 `CLOSED/COMPLETED` 或售后 `COMPLETED` 下所有公开修改行为均返回失败且完整快照不变；运行两项定向测试。
  - GREEN：修正遗漏的事件提交时序或状态前置，但保持事件类和载荷 API 不变。
  - REFACTOR：统一测试快照捕获辅助函数；运行 `./gradlew.bat :j-store-order:test --tests "com.jstore.order.domain.order.OrderDomainEventContractTest" --tests "com.jstore.order.domain.order.OrderTerminalStateAtomicityPropertyTest"`。
  - _需求: 2.8, 3.2, 3.3, 3.4, 4.9, 5.8, 8.3, 8.7_

- [x] 3.10 执行受影响模块回归并修复夹具残留
  - 负责文件：仅限 `j-store-order/src/test/`、`j-store-order-infrastructure/src/test/`、`j-store-boot/src/test/` 中仍引用旧订单状态或旧响应字段的测试/夹具；不得改动无关 JDK 25 配置。
  - RED：依次运行 `./gradlew.bat :j-store-order:test`、`./gradlew.bat :j-store-order-infrastructure:test`、`./gradlew.bat :j-store-boot:test`，记录所有由四维替换造成的失败。
  - GREEN：逐一迁移残余测试数据和断言，保持非状态业务覆盖不变；用 `rg -n "OrderStatus|previousStatus|\bstatus\s*=\s*order\.status" j-store-order/src/test j-store-order-infrastructure/src/test j-store-boot/src/test` 确认无订单级旧模型引用。
  - REFACTOR：再次依序运行三个模块测试，确保全部通过并保留用户/其他 agent 的无关工作区改动。
  - _需求: 8.8_

- [x] 4. 检查点 — 最终验证
  - 核对需求 1–8、设计中的四个状态组件、13 项 correctness properties、迁移、仓储转换和 API 契约均有实现与测试覆盖；如有问题请向用户确认。
  - 运行完整测试套件 `./gradlew.bat test`，要求全部通过；随后运行 `git status --short`，确认只包含本特性文件及原有无关改动，且未回退既有 JDK 25 变更。

## 备注

- 所有复选框在实现前保持未勾选；审批通过后由 `spec-generator` 严格按顺序一次实施一个任务，并在评审通过后再勾选。
- 本计划不创建 `manifest.json`；当前未启用可选 manifest 模式。
- 迁移允许清空开发环境 `orders`/`order_items`，但不得用于生产升级，也不承担回填、审计或旧数据读取职责。
- `OrderItem.previousItemStatus` 是退款拒绝恢复所需的行项事实，必须保留；仅删除订单级 `previousStatus`。
- 若测试依赖外部 PostgreSQL 且当前环境不可用，应记录具体未验证命令和原因，不得把未运行视为通过。
