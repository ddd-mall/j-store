# 需求文档：订单状态多维化

## 简介

当前订单聚合使用单一 `OrderStatus` 同时表达库存确认、支付、履约、交易终结和售后处理。退款申请会把订单主状态覆盖为 `REFUNDING`，并依赖 `previousStatus` 在拒绝退款后恢复；这种模型无法稳定表达“已支付且退款处理中”“已签收且售后处理中”等并行事实，也会随着部分退款和后续履约能力增加而产生状态组合膨胀。

本特性将订单状态拆分为 `Trade_Status`、`Payment_Status`、`Fulfillment_Status` 和 `After_Sale_Status` 四个维度，由订单聚合统一维护各维度的不变量。现有正向流程、取消、按行项申请退款、批准退款和拒绝退款的业务语义保持不变。项目尚未上线，因此不保留旧状态模型或接口兼容层：API 的单一 `status` 字段直接由四个新状态字段替代，数据库通过新的 Flyway 迁移直接演进为四维状态结构。

本特性范围包括订单领域模型及状态转换规则、持久化映射、破坏性开发库结构迁移、查询响应契约更新、领域与基础设施测试。数据库变更必须通过项目规定的新 Flyway 迁移完成；允许丢弃或重建开发环境中的现有订单数据，不要求回填、审计或继续读取旧状态数据。

本特性不包括：抽取 `PaymentOrder`、`AfterSaleOrder` 或 `FulfillmentOrder` 独立聚合；新增部分支付、支付中、真实退款到账、退货物流、多包裹、部分发货或部分签收流程；改变订单金额模型；改变现有权限模型、URL、请求体或错误响应格式；改变与状态无关的现有领域事件契约；解决事件发布、支付校验及其他与状态拆分无直接关系的问题。旧 `OrderStatus`、`OrderStatusTransitionRules`、订单级 `previousStatus` 以及对应数据库列不属于需保留的兼容接口，后续设计可将其直接删除。

## 术语表

| 业务术语 | 英文标识符 | 定义 |
| --- | --- | --- |
| 订单聚合 | `Order_Aggregate` | 订单有界上下文中的聚合根及其订单行项，负责维护本特性的全部状态和转换不变量。 |
| 交易状态 | `Trade_Status` | 订单整体交易生命周期：`CREATED`（等待库存确认）、`ACTIVE`（库存已确认且交易进行中）、`CLOSED`（交易取消或全部行项退款获批）、`COMPLETED`（交易正常完成）。 |
| 支付状态 | `Payment_Status` | 订单款项生命周期：`UNPAID`（未支付）、`PAID`（已支付）、`PARTIALLY_REFUNDED`（部分行项退款获批）、`REFUNDED`（全部行项退款获批）。退款状态表示当前系统已批准退款，而非外部渠道已确认到账。 |
| 履约状态 | `Fulfillment_Status` | 订单发货生命周期：`UNFULFILLED`（尚未进入备货）、`PENDING_SHIPMENT`（待发货）、`SHIPPED`（已发货）、`DELIVERED`（已签收）。 |
| 售后状态 | `After_Sale_Status` | 订单售后处理摘要：`NONE`（无售后处理或所有申请均被拒绝）、`PROCESSING`（至少一个行项退款处理中且尚无已批准退款）、`PARTIALLY_COMPLETED`（至少一个行项退款已批准且订单仍有未退款行项，可同时仍有其他申请处理中）、`COMPLETED`（全部行项退款已批准）。 |
| 订单行项状态 | `Order_Item_Status` | 现有订单行项的 `NONE`、`WAIT_SHIPPING`、`SHIPPING`、`SHIPPING_ERROR`、`SHIPPING_FINISHED`、`REFUNDING`、`CANCELED` 状态，本特性不重新设计其状态集合。 |
| 正向订单操作 | `Forward_Order_Operation` | 现有的库存确认、支付、确认备货、发货、确认收货和完成订单操作。 |
| 取消操作 | `Cancellation_Operation` | 买家在未支付阶段主动取消，或库存不足导致订单取消的现有操作。 |
| 退款操作 | `Refund_Operation` | 现有的按订单行项申请退款、批准退款和拒绝退款操作。 |
| 开发库结构迁移 | `Development_Schema_Migration` | 通过新的 Flyway 迁移把订单表直接替换为四维状态结构；因系统尚未上线，该迁移不承担旧订单状态回填或数据保全职责。 |
| 领域事件 | `Domain_Event` | 现有订单创建、支付、取消、发货、完成及退款相关事件；本特性不改变其名称和载荷契约。 |

## 需求

### 需求 1：建立四维订单状态

**用户故事：** 作为订单领域开发者，我希望交易、支付、履约和售后状态被独立表达，以便订单可以同时呈现不同业务维度的真实进展。

#### 验收标准

1. THE `Order_Aggregate` SHALL 分别暴露且持久维护一个 `Trade_Status`、一个 `Payment_Status`、一个 `Fulfillment_Status` 和一个 `After_Sale_Status`。
2. THE `Order_Aggregate` SHALL 使用四维状态作为业务规则的唯一事实来源。
3. THE `Trade_Status` SHALL 仅使用 `CREATED`、`ACTIVE`、`CLOSED` 和 `COMPLETED` 表达交易生命周期。
4. THE `Payment_Status` SHALL 仅使用 `UNPAID`、`PAID`、`PARTIALLY_REFUNDED` 和 `REFUNDED` 表达本特性支持的款项生命周期。
5. THE `Fulfillment_Status` SHALL 仅使用 `UNFULFILLED`、`PENDING_SHIPMENT`、`SHIPPED` 和 `DELIVERED` 表达本特性支持的履约生命周期。
6. THE `After_Sale_Status` SHALL 仅使用 `NONE`、`PROCESSING`、`PARTIALLY_COMPLETED` 和 `COMPLETED` 表达订单级售后摘要。
7. WHEN 创建新的 `Order_Aggregate`, THE `Order_Aggregate` SHALL 初始化为 `Trade_Status=CREATED`、`Payment_Status=UNPAID`、`Fulfillment_Status=UNFULFILLED`、`After_Sale_Status=NONE`。
8. THE `Order_Aggregate` SHALL 不再暴露或依赖订单级 `previousStatus` 恢复正向流程状态。

### 需求 2：保持正向订单流程

**用户故事：** 作为订单业务使用者，我希望状态模型拆分后原有下单至完成流程保持一致，以便现有业务可以无感继续运行。

#### 验收标准

1. WHILE `Order_Aggregate` IN `Trade_Status=CREATED`、`Payment_Status=UNPAID`, WHEN 库存确认成功, THE `Order_Aggregate` SHALL 转为 `Trade_Status=ACTIVE` 且保持其他状态维度不变。
2. WHILE `Order_Aggregate` IN `Trade_Status=ACTIVE`、`Payment_Status=UNPAID`, WHEN 支付成功, THE `Order_Aggregate` SHALL 转为 `Payment_Status=PAID` 并保持 `Trade_Status=ACTIVE`、`Fulfillment_Status=UNFULFILLED`、`After_Sale_Status=NONE`。
3. WHILE `Order_Aggregate` IN `Trade_Status=ACTIVE`、`Payment_Status=PAID`、`Fulfillment_Status=UNFULFILLED`, WHEN 确认备货, THE `Order_Aggregate` SHALL 转为 `Fulfillment_Status=PENDING_SHIPMENT`。
4. WHILE `Order_Aggregate` IN `Trade_Status=ACTIVE`、`Payment_Status=PAID`、`Fulfillment_Status=PENDING_SHIPMENT`, WHEN 发货, THE `Order_Aggregate` SHALL 转为 `Fulfillment_Status=SHIPPED`。
5. WHILE `Order_Aggregate` IN `Trade_Status=ACTIVE`、`Fulfillment_Status=SHIPPED`, WHEN 确认收货, THE `Order_Aggregate` SHALL 转为 `Fulfillment_Status=DELIVERED`。
6. WHILE `Order_Aggregate` IN `Trade_Status=ACTIVE`、`Payment_Status=PAID`、`Fulfillment_Status=DELIVERED`、`After_Sale_Status=NONE`, WHEN 完成订单, THE `Order_Aggregate` SHALL 转为 `Trade_Status=COMPLETED`。
7. WHEN 任一 `Forward_Order_Operation` 不满足其规定的四维前置状态, THE `Order_Aggregate` SHALL 返回现有非法状态类型的业务错误且不修改任何状态维度、金额、行项或 `Domain_Event` 队列。
8. WHEN 任一合法 `Forward_Order_Operation` 完成, THE `Order_Aggregate` SHALL 保持现有金额更新、`Order_Item_Status` 更新、更新时间更新和 `Domain_Event` 产生行为。

### 需求 3：保持取消语义并约束终态

**用户故事：** 作为买家或库存协作者，我希望未支付订单仍可按原规则取消，以便状态拆分不改变库存失败和主动取消流程。

#### 验收标准

1. WHILE `Order_Aggregate` IN `Trade_Status=CREATED` 或 `Trade_Status=ACTIVE` 且 `Payment_Status=UNPAID`, WHEN 执行合法 `Cancellation_Operation`, THE `Order_Aggregate` SHALL 转为 `Trade_Status=CLOSED` 并保持 `Payment_Status=UNPAID`。
2. WHEN 买家主动执行合法 `Cancellation_Operation`, THE `Order_Aggregate` SHALL 将全部 `Order_Item_Status` 更新为 `CANCELED` 并产生现有取消 `Domain_Event`。
3. WHEN 库存不足触发合法 `Cancellation_Operation`, THE `Order_Aggregate` SHALL 产生现有取消 `Domain_Event` 并保持现有行项更新行为。
4. WHILE `Order_Aggregate` IN `Trade_Status=CLOSED` 或 `Trade_Status=COMPLETED`, WHEN 执行 `Forward_Order_Operation` 或 `Cancellation_Operation`, THE `Order_Aggregate` SHALL 返回非法状态业务错误且不改变聚合。
5. IF `Order_Aggregate` IN `Trade_Status=CLOSED` 且 `Payment_Status=UNPAID`, THEN THE `Order_Aggregate` SHALL 保持 `After_Sale_Status=NONE`。

### 需求 4：售后状态不得覆盖正向状态

**用户故事：** 作为售后处理人员，我希望退款进度独立于支付和履约事实，以便准确判断退款是否需要退货并安全处理拒绝或部分批准。

#### 验收标准

1. WHILE `Order_Aggregate` IN `Payment_Status=PAID` 或 `Payment_Status=PARTIALLY_REFUNDED`，且处于现有允许申请退款的未发货或已签收组合, WHEN 对尚未退款且满足行项规则的行项执行退款申请, THE `Order_Aggregate` SHALL 更新相应 `Order_Item_Status` 并将 `After_Sale_Status` 更新为 `PROCESSING` 或在已有批准退款时保持 `PARTIALLY_COMPLETED`，且不覆盖 `Trade_Status`、`Payment_Status` 和 `Fulfillment_Status` 所表达的既有事实。
2. WHEN `Refund_Operation` 判断是否需要退货, THE `Order_Aggregate` SHALL 直接依据 `Fulfillment_Status` 判断，并在 `Fulfillment_Status=SHIPPED` 或 `Fulfillment_Status=DELIVERED` 时标记需要退货。
3. WHEN 部分行项退款获批且仍存在未退款行项, THE `Order_Aggregate` SHALL 更新为 `Payment_Status=PARTIALLY_REFUNDED`、`After_Sale_Status=PARTIALLY_COMPLETED` 并保持 `Trade_Status=ACTIVE`。
4. WHEN 全部 `Order_Item_Status` 均因退款获批成为 `CANCELED`, THE `Order_Aggregate` SHALL 更新为 `Payment_Status=REFUNDED`、`After_Sale_Status=COMPLETED`、`Trade_Status=CLOSED`，保持退款批准前的 `Fulfillment_Status` 不变，且不得因状态更新顺序丢失退款是否需要退货的信息。
5. WHEN 一次退款拒绝后仍有其他行项处于 `REFUNDING`, THE `Order_Aggregate` SHALL 保持 `After_Sale_Status=PROCESSING` 或在已有批准退款时保持 `After_Sale_Status=PARTIALLY_COMPLETED`。
6. WHEN 所有待处理退款均被拒绝且没有行项退款获批, THE `Order_Aggregate` SHALL 更新为 `After_Sale_Status=NONE`，且保持申请前的 `Trade_Status`、`Payment_Status` 和 `Fulfillment_Status` 不变。
7. WHEN 所有待处理退款均被拒绝但已有部分行项退款获批, THE `Order_Aggregate` SHALL 保持 `Payment_Status=PARTIALLY_REFUNDED` 和 `After_Sale_Status=PARTIALLY_COMPLETED`。
8. WHEN 任一 `Refund_Operation` 的行项集合为空、包含非本订单行项或包含不合法 `Order_Item_Status`, THE `Order_Aggregate` SHALL 返回现有对应业务错误且不产生部分状态更新。
9. WHEN 任一合法 `Refund_Operation` 完成, THE `Order_Aggregate` SHALL 保持现有退款 `Domain_Event` 的类型、金额、原因、行项标识及拒绝原因契约。
10. WHILE `Order_Aggregate` IN `Payment_Status=PARTIALLY_REFUNDED`、`After_Sale_Status=PARTIALLY_COMPLETED`, WHEN 对剩余尚未退款且满足行项规则的行项提交后续退款申请, THE `Order_Aggregate` SHALL 接受该申请并保持 `Payment_Status=PARTIALLY_REFUNDED`、`After_Sale_Status=PARTIALLY_COMPLETED`，直至后续申请处理结果触发其他合法状态变化。

### 需求 5：保证跨维度状态不变量

**用户故事：** 作为订单领域维护者，我希望所有状态组合满足明确不变量，以便阻止不可能的订单状态进入持久化层。

#### 验收标准

1. IF `Order_Aggregate` IN `Trade_Status=CREATED`, THEN THE `Order_Aggregate` SHALL 同时处于 `Payment_Status=UNPAID`、`Fulfillment_Status=UNFULFILLED`、`After_Sale_Status=NONE`。
2. IF `Order_Aggregate` IN `Payment_Status=UNPAID`, THEN THE `Order_Aggregate` SHALL 不处于 `Fulfillment_Status=PENDING_SHIPMENT`、`SHIPPED` 或 `DELIVERED`。
3. IF `Order_Aggregate` IN `Fulfillment_Status=PENDING_SHIPMENT`、`SHIPPED` 或 `DELIVERED`, THEN THE `Order_Aggregate` SHALL 处于 `Payment_Status=PAID`、`Payment_Status=PARTIALLY_REFUNDED` 或 `Payment_Status=REFUNDED`；退款不得把已经发生的履约事实重置为 `Fulfillment_Status=UNFULFILLED`。
4. IF `Order_Aggregate` IN `Trade_Status=COMPLETED`, THEN THE `Order_Aggregate` SHALL 同时处于 `Payment_Status=PAID`、`Fulfillment_Status=DELIVERED`、`After_Sale_Status=NONE`。
5. IF `Order_Aggregate` IN `Payment_Status=PARTIALLY_REFUNDED`, THEN THE `Order_Aggregate` SHALL 同时处于 `After_Sale_Status=PARTIALLY_COMPLETED` 且至少有一个未取消行项。
6. IF `Order_Aggregate` IN `Payment_Status=REFUNDED`, THEN THE `Order_Aggregate` SHALL 同时处于 `Trade_Status=CLOSED`、`After_Sale_Status=COMPLETED` 且所有 `Order_Item_Status` 均为 `CANCELED`。
7. IF `Order_Aggregate` IN `After_Sale_Status=PROCESSING`, THEN THE `Order_Aggregate` SHALL 至少有一个 `Order_Item_Status=REFUNDING`。
8. IF `Order_Aggregate` IN `After_Sale_Status=COMPLETED`, THEN THE `Order_Aggregate` SHALL 不允许新的 `Forward_Order_Operation`、`Cancellation_Operation` 或 `Refund_Operation`。
9. FOR ALL 可由公开领域行为产生或由持久化恢复的状态组合, THE `Order_Aggregate` SHALL 满足本需求定义的全部跨维度不变量。

### 需求 6：以四维状态替换 API 单一状态

**用户故事：** 作为 API 调用方，我希望订单响应直接提供四个含义明确的状态字段，以便分别判断交易、支付、履约和售后进度。

#### 验收标准

1. WHEN API 返回单个或分页 `Order_Aggregate`, THE `Order_Aggregate` SHALL 通过 `tradeStatus`、`paymentStatus`、`fulfillmentStatus` 和 `afterSaleStatus` 字符串字段返回四维状态的枚举名称。
2. WHEN API 返回单个或分页 `Order_Aggregate`, THE `Order_Aggregate` SHALL 不再返回单一 `status` 字段。
3. THE `Order_Aggregate` SHALL 保持现有 API URL、HTTP 方法、请求字段、错误响应字段和 `Order_Item_Status` 响应字段不变。
4. WHEN `Forward_Order_Operation`、`Cancellation_Operation` 或 `Refund_Operation` 成功后查询订单, THE `Order_Aggregate` SHALL 返回与需求 2、需求 3 和需求 4 所定义结果一致的四维状态。
5. THE `Order_Aggregate` SHALL 不提供从四维状态派生旧单一订单状态的运行时投影。

### 需求 7：直接替换持久化状态结构

**用户故事：** 作为开发环境维护人员，我希望数据库直接采用新的四维状态结构，以便避免为尚未上线的旧模型承担兼容复杂度。

#### 验收标准

1. THE `Development_Schema_Migration` SHALL 通过新的 Flyway 迁移为订单表建立非空的 `Trade_Status`、`Payment_Status`、`Fulfillment_Status` 和 `After_Sale_Status` 持久化列。
2. THE `Development_Schema_Migration` SHALL 为新建订单配置与 `Trade_Status=CREATED`、`Payment_Status=UNPAID`、`Fulfillment_Status=UNFULFILLED`、`After_Sale_Status=NONE` 一致的数据库默认值。
3. THE `Development_Schema_Migration` SHALL 移除旧单一订单状态列和订单级前序状态列，且不提供旧状态回填、历史数据审计或双写过渡机制。
4. WHEN 现有开发数据阻碍破坏性结构替换, THE `Development_Schema_Migration` SHALL 允许清除或重建订单及其依赖的开发数据，而不保证旧订单可恢复。
5. THE `Order_Aggregate` SHALL 在持久化往返后保持四维状态、金额、时间、收货信息、`Order_Item_Status` 和行项退款恢复信息不变。
6. THE `Development_Schema_Migration` SHALL 为按每个业务状态维度与创建时间进行查询提供与实际查询需求匹配的索引支持。
7. THE `Order_Aggregate` SHALL 不再通过持久化对象读写旧单一订单状态或订单级前序状态。

### 需求 8：以 TDD 验证行为与新契约

**用户故事：** 作为订单模块维护者，我希望状态拆分由分层自动化测试保护，以便确认业务行为、新持久化结构和 API 契约正确。

#### 验收标准

1. THE `Order_Aggregate` SHALL 具有先于实现新增的领域单元测试，覆盖每个合法 `Forward_Order_Operation`、每个非法转换、取消、首次退款申请、部分退款后的后续申请、部分批准、全部批准、部分拒绝和全部拒绝。
2. FOR ALL 不满足跨维度不变量的状态组合, THE `Order_Aggregate` SHALL 具有拒绝构造、拒绝恢复或拒绝操作的自动化测试。
3. THE `Order_Aggregate` SHALL 具有验证失败操作原子性的测试，确认四维状态、行项、金额、更新时间和 `Domain_Event` 队列均未发生部分修改。
4. THE `Development_Schema_Migration` SHALL 具有验证四维列、默认值、非空约束以及旧状态列已移除的基础设施测试。
5. THE `Order_Aggregate` SHALL 具有持久化对象与领域对象四维状态往返一致性的基础设施测试。
6. THE `Order_Aggregate` SHALL 具有单订单查询、分页查询和各业务操作后四维响应字段的接口契约测试，并验证单一 `status` 字段不再出现。
7. THE `Domain_Event` SHALL 具有回归测试，证明状态拆分未改变与状态无关的现有业务事件类型和关键载荷。
8. THE `Order_Aggregate` SHALL 通过订单领域模块、订单基础设施模块及受影响 Boot 模块的相关测试套件。
9. THE `Order_Aggregate` SHALL 具有全额退款后仍保持 `Fulfillment_Status=PENDING_SHIPMENT`、`SHIPPED` 或 `DELIVERED` 原值的领域回归测试。
