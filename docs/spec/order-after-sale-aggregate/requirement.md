# 需求文档：订单售后聚合拆分

## 简介

当前订单聚合同时承担交易、支付、履约事实以及退款申请、审核、拒绝和售后摘要维护。退款处理中间态写入订单及订单行项，导致订单聚合需要保存售后流程状态、退款前行项状态并处理长事务生命周期；同一订单的多次或并发售后申请也缺少独立身份和可审计边界。对标主流电商领域划分，售后单具有独立标识、独立生命周期、独立审核决策以及未来承载退货物流和退款执行的演进需求，应从订单聚合中拆分。

本特性在订单有界上下文内新增独立的 `After_Sale_Aggregate`，每次售后申请对应一个聚合实例。该拆分是聚合边界调整，不要求拆成新的 Gradle 模块、微服务或独立限界上下文。`Order_Aggregate` 继续作为交易状态、支付状态、履约状态、原始购买数量与金额、累计已退款数量与金额的权威来源；`After_Sale_Aggregate` 负责申请、商家批准、商家拒绝和申请人撤销售后申请的流程。两个聚合只通过标识和不可变快照关联，应用服务负责加载事实、校验容量和编排单聚合事务，跨聚合结果通过领域事件最终一致地投影回订单。

本特性支持同一订单多笔顺序或并发售后单，以及一笔售后单包含多个订单行项。申请时按订单行项冻结售后占用数量和金额，所有处于处理中或已批准的售后单对同一订单行项的占用总和不得超过订单当前可退款数量和金额；拒绝或允许撤销的申请释放处理中占用，批准后的占用转为已退款事实，不得再次使用。是否需要退货在申请创建时依据不可变履约快照确定，后续订单履约变化不得改写既有售后单。

本次重构不考虑既有数据迁移和兼容过渡：允许直接替换开发数据库结构、删除旧列和重建相关表；删除订单上的 `AfterSaleStatus`、退款申请/批准/拒绝行为、退款中行项状态及退款前状态恢复数据；旧售后接口、旧请求体、旧响应字段和旧退款领域事件不作为兼容契约保留。

本特性范围包括售后领域模型、仓储接口与持久化、应用服务编排、领域事件、订单退款事实投影、售后接口、订单接口清理以及测试。本特性不包括真实支付渠道退款到账、退货物流单、换货、维修、平台仲裁、举证、自动审核、超时任务、运费退款、优惠重算、退款手续费、跨币种退款、拆分为新服务，以及历史数据回填。

## 术语表

| 业务术语 | 英文标识符 | 定义 |
| --- | --- | --- |
| 订单聚合 | `Order_Aggregate` | 订单有界上下文中的交易聚合根，是交易、支付、履约、购买数量与金额以及累计已退款事实的权威来源。 |
| 订单标识 | `Order_Id` | 唯一标识一个订单聚合的类型化标识。 |
| 订单行项标识 | `Order_Item_Id` | 在订单领域内唯一标识一个订单行项的类型化标识。 |
| 售后聚合 | `After_Sale_Aggregate` | 每次售后申请对应的独立聚合根，封装申请内容、处理状态、审核决策和状态转换。 |
| 售后标识 | `After_Sale_Id` | 唯一标识一个售后聚合的类型化标识。 |
| 售后行项 | `After_Sale_Item` | 售后聚合内的不可变申请行项，引用订单及订单行项标识并保存申请时快照、申请数量和申请金额。 |
| 售后状态 | `After_Sale_Status` | 售后聚合的生命周期状态：`REQUESTED`、`APPROVED`、`REJECTED` 或 `CANCELLED`；不再是订单状态维度。 |
| 退款原因 | `Refund_Reason` | 申请人提交的结构化退款类别和说明快照。 |
| 履约快照 | `Fulfillment_Snapshot` | 创建售后单时从订单读取并固化的履约状态及是否需要退货事实。 |
| 退款资格快照 | `Refund_Eligibility_Snapshot` | 创建售后单时从订单读取并固化的每个目标行项可退款数量、可退款金额、币种及必要商品展示信息。 |
| 售后占用 | `After_Sale_Allocation` | 为防止多笔售后超额申请而对订单行项退款数量和金额建立的容量占用；处理中占用可释放，批准占用不可重复使用。 |
| 退款事实 | `Refund_Fact` | 售后批准后由订单聚合幂等接收的已退款数量和金额事实。 |
| 领域事件 | `Domain_Event` | 聚合行为成功后发布、用于跨聚合和外部订阅方最终一致处理的不可变业务事实。 |
| 售后仓储 | `After_Sale_Repository` | 以售后领域对象为契约，负责售后聚合存取及同一订单行项售后占用约束的仓储。 |
| 售后应用服务 | `After_Sale_Application_Service` | 编排订单事实读取、售后聚合创建或加载、单聚合保存和领域事件发布的应用服务。 |
| 商家 | `Merchant_Actor` | 有权批准或拒绝所属订单售后申请的业务参与者。 |
| 申请人 | `Applicant_Actor` | 发起售后申请并可在允许状态撤销本人申请的业务参与者。 |
| 售后接口 | `After_Sale_API` | 创建、查询、批准、拒绝和撤销售后单的接口契约。 |

## 需求

### 需求 1：建立独立售后聚合边界

**用户故事：** 作为领域维护者，我希望每次售后申请拥有独立聚合和身份，以便售后流程可独立演进并避免污染订单交易模型。

#### 验收标准

1. THE `After_Sale_Aggregate` SHALL 以 `After_Sale_Id` 作为聚合身份，并且每次合法申请创建一个新的聚合实例。
2. THE `After_Sale_Aggregate` SHALL 仅通过 `Order_Id` 和 `Order_Item_Id` 引用 `Order_Aggregate`，不得持有订单聚合或订单行项对象引用。
3. THE `After_Sale_Aggregate` SHALL 包含至少一个 `After_Sale_Item`、一个 `Refund_Reason`、一个 `Fulfillment_Snapshot`、一个 `After_Sale_Status`、申请人与商家标识、创建时间和更新时间。
4. FOR ALL `After_Sale_Item`, THE `After_Sale_Aggregate` SHALL 保证其 `Order_Id` 与聚合引用的 `Order_Id` 一致，且 `Order_Item_Id` 在聚合内不重复。
5. THE `Order_Aggregate` SHALL 不再包含 `After_Sale_Status`、售后申请原因、审核决定、退款中状态或退款前状态恢复信息。
6. THE `Order_Aggregate` SHALL 不再暴露申请退款、批准退款或拒绝退款的领域行为。

### 需求 2：从订单权威事实创建售后申请

**用户故事：** 作为申请人，我希望基于订单当前可退款事实提交指定数量和金额的售后申请，以便获得准确且可追溯的处理单据。

#### 验收标准

1. WHEN `Applicant_Actor` 提交售后申请, THE `After_Sale_Application_Service` SHALL 加载对应 `Order_Aggregate` 并以其当前事实生成 `Refund_Eligibility_Snapshot` 和 `Fulfillment_Snapshot`。
2. WHEN 任一目标订单不存在、目标行项不属于该订单、申请行项为空、行项重复、数量非正数、金额非正数或币种不一致, THE `After_Sale_Application_Service` SHALL 返回明确业务错误且不创建 `After_Sale_Aggregate`。
3. WHEN 订单未支付、已退款容量为零或当前交易与履约事实不允许售后, THE `After_Sale_Application_Service` SHALL 返回明确业务错误且不创建 `After_Sale_Aggregate`。
4. FOR ALL `After_Sale_Item`, THE `After_Sale_Aggregate` SHALL 固化 `Order_Item_Id`、申请数量、申请金额、币种及申请时必要商品展示快照。
5. WHEN 合法售后申请创建, THE `After_Sale_Aggregate` SHALL 初始化为 `After_Sale_Status=REQUESTED` 并产生售后已申请 `Domain_Event`。
6. WHEN 售后申请创建失败, THE `After_Sale_Aggregate` SHALL 不产生持久化记录、售后占用或 `Domain_Event`。

### 需求 3：安全约束多笔售后数量与金额

**用户故事：** 作为订单业务负责人，我希望同一订单能够顺序或并发创建多笔售后单且永不超额，以便支持部分退款和多次申请。

#### 验收标准

1. FOR ALL 同一 `Order_Item_Id` 的 `After_Sale_Allocation`, THE `After_Sale_Repository` SHALL 保证处理中占用与批准占用的数量总和不超过 `Order_Aggregate` 给出的可退款数量上限。
2. FOR ALL 同一 `Order_Item_Id` 的 `After_Sale_Allocation`, THE `After_Sale_Repository` SHALL 保证处理中占用与批准占用的金额总和不超过 `Order_Aggregate` 给出的可退款金额上限。
3. WHEN 两笔或多笔售后申请并发竞争同一退款容量, THE `After_Sale_Repository` SHALL 原子地接受不超额的申请并拒绝会导致超额的申请。
4. WHEN `After_Sale_Aggregate` 转为 `REJECTED` 或 `CANCELLED`, THE `After_Sale_Allocation` SHALL 释放该售后单的处理中数量和金额。
5. WHEN `After_Sale_Aggregate` 转为 `APPROVED`, THE `After_Sale_Allocation` SHALL 将该售后单的占用固化为不可重复使用的批准占用。
6. WHEN 同一申请命令被重复提交且具有相同幂等标识, THE `After_Sale_Application_Service` SHALL 返回同一业务结果且不得重复创建售后聚合或重复占用容量。

### 需求 4：固化退货要求与申请快照

**用户故事：** 作为售后处理人员，我希望售后单保存申请时的履约事实，以便订单后续履约变化不会改变已经受理的处理规则。

#### 验收标准

1. WHEN 创建 `After_Sale_Aggregate`, THE `Fulfillment_Snapshot` SHALL 固化申请时的订单履约状态和 `requireReturn` 布尔事实。
2. WHEN 申请时订单商品已经发出或签收, THE `Fulfillment_Snapshot` SHALL 将 `requireReturn` 固化为真。
3. WHEN 申请时订单商品尚未发出, THE `Fulfillment_Snapshot` SHALL 将 `requireReturn` 固化为假。
4. WHEN `Order_Aggregate` 在售后申请后发生履约状态变化, THE `After_Sale_Aggregate` SHALL 保持既有 `Fulfillment_Snapshot`、`Refund_Eligibility_Snapshot` 和 `After_Sale_Item` 不变。

### 需求 5：商家批准或拒绝售后申请

**用户故事：** 作为商家，我希望对待处理售后单进行一次明确审核，以便形成可审计且不可歧义的处理结果。

#### 验收标准

1. WHILE `After_Sale_Aggregate` IN `After_Sale_Status=REQUESTED`, WHEN 有权 `Merchant_Actor` 批准申请, THE `After_Sale_Aggregate` SHALL 原子地转为 `APPROVED`、记录审核人和审核时间并产生售后已批准 `Domain_Event`。
2. WHILE `After_Sale_Aggregate` IN `After_Sale_Status=REQUESTED`, WHEN 有权 `Merchant_Actor` 拒绝申请且提供非空拒绝原因, THE `After_Sale_Aggregate` SHALL 原子地转为 `REJECTED`、记录审核人、拒绝原因和审核时间并产生售后已拒绝 `Domain_Event`。
3. WHEN 无权 `Merchant_Actor` 尝试审核不属于其订单的售后单, THE `After_Sale_Application_Service` SHALL 返回授权业务错误且不修改 `After_Sale_Aggregate`。
4. WHILE `After_Sale_Aggregate` IN `APPROVED`、`REJECTED` 或 `CANCELLED`, WHEN 再次批准或拒绝, THE `After_Sale_Aggregate` SHALL 返回非法状态业务错误且不修改状态、审核信息、占用或 `Domain_Event` 队列。
5. WHEN 批准或拒绝命令失败, THE `After_Sale_Aggregate` SHALL 保持失败前全部字段和 `Domain_Event` 队列不变。

### 需求 6：允许申请人在审核前撤销

**用户故事：** 作为申请人，我希望在商家作出决定前撤销自己的申请，以便纠正误操作并释放退款容量。

#### 验收标准

1. WHILE `After_Sale_Aggregate` IN `After_Sale_Status=REQUESTED`, WHEN 原 `Applicant_Actor` 撤销申请, THE `After_Sale_Aggregate` SHALL 转为 `CANCELLED`、记录撤销时间并产生售后已撤销 `Domain_Event`。
2. WHEN 非原 `Applicant_Actor` 尝试撤销申请, THE `After_Sale_Application_Service` SHALL 返回授权业务错误且不修改 `After_Sale_Aggregate`。
3. WHILE `After_Sale_Aggregate` IN `APPROVED`、`REJECTED` 或 `CANCELLED`, WHEN `Applicant_Actor` 尝试撤销申请, THE `After_Sale_Aggregate` SHALL 返回非法状态业务错误且不改变售后占用或产生新 `Domain_Event`。

### 需求 7：订单保留退款事实并通过事件最终一致更新

**用户故事：** 作为交易领域维护者，我希望订单只保存已批准退款事实而不承载售后工作流，以便支付状态和可退款容量仍有单一权威来源。

#### 验收标准

1. THE `Order_Aggregate` SHALL 按订单行项维护原始购买数量与金额、累计已退款数量与金额，并维护订单累计已退款金额。
2. THE `Order_Aggregate` SHALL 以原始购买事实减去累计 `Refund_Fact` 计算每个订单行项的可退款数量和可退款金额。
3. WHEN 售后已批准 `Domain_Event` 被订单事件处理器接收, THE `Order_Aggregate` SHALL 以 `After_Sale_Id` 幂等登记每个行项的 `Refund_Fact` 并更新累计退款事实。
4. WHEN 同一售后已批准 `Domain_Event` 被重复投递, THE `Order_Aggregate` SHALL 不重复累计退款数量、金额或改变已经登记的结果。
5. WHEN 售后已批准 `Domain_Event` 的数量、金额、订单标识或行项标识违反订单退款不变量, THE `Order_Aggregate` SHALL 拒绝该 `Refund_Fact` 且不产生部分更新。
6. WHEN 累计已退款金额小于订单实付金额, THE `Order_Aggregate` SHALL 将 `Payment_Status` 表达为部分退款。
7. WHEN 累计已退款金额等于订单实付金额, THE `Order_Aggregate` SHALL 将 `Payment_Status` 表达为全部退款并按现有交易终结规则更新交易事实，同时保持既有履约事实不变。
8. THE `Order_Aggregate` SHALL 不因存在 `REQUESTED`、`REJECTED` 或 `CANCELLED` 售后单而修改交易、支付、履约状态或订单行项履约状态。
9. THE `After_Sale_Application_Service` SHALL 不在创建、批准、拒绝或撤销售后单的同一本地事务中修改 `Order_Aggregate`。

### 需求 8：发布稳定且可幂等消费的领域事件

**用户故事：** 作为跨模块集成开发者，我希望售后生命周期通过明确事件对外发布，以便订单、支付和财务模块可靠处理后续动作。

#### 验收标准

1. WHEN `After_Sale_Aggregate` 创建成功, THE `Domain_Event` SHALL 携带唯一事件标识、`After_Sale_Id`、`Order_Id`、申请人标识、行项申请数量与金额、退款原因、是否需要退货和发生时间。
2. WHEN `After_Sale_Aggregate` 批准成功, THE `Domain_Event` SHALL 携带唯一事件标识、`After_Sale_Id`、`Order_Id`、商家标识、批准行项数量与金额、是否需要退货和发生时间。
3. WHEN `After_Sale_Aggregate` 拒绝成功, THE `Domain_Event` SHALL 携带唯一事件标识、`After_Sale_Id`、`Order_Id`、商家标识、拒绝原因和发生时间。
4. WHEN `After_Sale_Aggregate` 撤销成功, THE `Domain_Event` SHALL 携带唯一事件标识、`After_Sale_Id`、`Order_Id`、申请人标识和发生时间。
5. THE `After_Sale_Repository` SHALL 在保存 `After_Sale_Aggregate` 的同一本地事务中可靠保存或发布其 `Domain_Event`，以符合项目现有领域事件机制。
6. THE `Domain_Event` SHALL 允许订单、支付和财务订阅方使用事件标识或 `After_Sale_Id` 幂等消费。
7. THE `Domain_Event` SHALL 以售后语义命名，不再沿用表示订单聚合行为的旧退款事件契约。
8. WHEN 批准事件的 `requireReturn=false`, THE 商品库存订阅方 SHALL 按事件中的 SKU 与批准数量增加可售库存且不得释放整笔订单预占；WHEN `requireReturn=true`, THE 商品库存订阅方 SHALL 等待范围外的退货收货事实而不立即恢复库存。

### 需求 9：提供独立售后接口并清理订单接口

**用户故事：** 作为接口调用方，我希望通过售后资源完成申请和审核，并从订单响应读取退款结果摘要，以便接口边界与领域边界一致。

#### 验收标准

1. THE `After_Sale_API` SHALL 提供创建售后申请、按 `After_Sale_Id` 查询详情、按订单查询售后列表、商家批准、商家拒绝和申请人撤销能力。
2. WHEN `After_Sale_API` 返回售后详情或列表, THE `After_Sale_Aggregate` SHALL 返回售后标识、订单标识、申请人、商家、状态、原因、履约快照、售后行项、审核或撤销信息以及时间字段。
3. WHEN 订单接口返回订单详情或列表, THE `Order_Aggregate` SHALL 返回支付状态、订单累计已退款金额以及各订单行项累计已退款数量和金额。
4. WHEN 订单接口返回订单详情或列表, THE `Order_Aggregate` SHALL 不再返回订单级 `afterSaleStatus`、退款中行项状态或售后审核信息。
5. THE `After_Sale_API` SHALL 使用项目现有认证、授权、参数校验、业务错误和响应包装约定。
6. THE `After_Sale_API` SHALL 对创建和状态变更命令支持幂等处理，并对并发状态变更返回确定的成功结果或业务冲突错误。

### 需求 10：直接替换持久化结构和旧模型

**用户故事：** 作为项目维护者，我希望直接采用新的售后聚合结构，以便避免为尚未上线的数据和接口维护临时兼容复杂度。

#### 验收标准

1. THE `After_Sale_Aggregate` SHALL 具有独立持久化结构，能够往返保存聚合状态、行项快照、审核信息、撤销信息、时间和并发版本。
2. THE `After_Sale_Repository` SHALL 提供新增、按 `After_Sale_Id` 查询、按 `Order_Id` 查询以及保存售后聚合所需的领域接口。
3. THE `Order_Aggregate` SHALL 从持久化结构中删除订单级售后状态、退款中状态和退款前状态恢复字段，并增加累计退款事实及幂等登记所需结构。
4. THE `After_Sale_Aggregate` SHALL 通过项目规定的数据库结构脚本直接建立新表、约束和索引，且不要求迁移、回填或读取既有售后与退款数据。
5. THE `After_Sale_API` SHALL 不提供旧退款 URL、请求体、响应字段、状态值或事件的兼容适配层。
6. THE `Order_Aggregate` SHALL 删除旧售后类型、命令、行为、事件和仅服务于旧售后流程的测试夹具。

### 需求 11：以测试驱动方式完成重构

**用户故事：** 作为项目维护者，我希望聚合拆分由分层自动化测试保护，以便验证业务边界、并发约束和跨聚合一致性。

#### 验收标准

1. THE `After_Sale_Aggregate` SHALL 具有先于实现新增的领域单元测试，覆盖创建、批准、拒绝、撤销、重复操作、非法状态、空行项、重复行项和失败原子性。
2. THE `After_Sale_Aggregate` SHALL 具有属性测试，覆盖多行项数量与金额边界、状态机非法转换及快照持久化往返不变量。
3. THE `After_Sale_Repository` SHALL 具有基础设施集成测试，验证聚合往返、乐观并发、幂等创建以及同一订单行项并发申请不超额。
4. THE `After_Sale_Application_Service` SHALL 使用替身仓储进行应用服务测试，验证订单事实读取、权限、单聚合保存、错误传播和事件发布编排。
5. THE `Order_Aggregate` SHALL 具有事件投影测试，验证批准事件的幂等累计、超额拒绝、部分退款、全部退款和履约事实保持不变。
6. THE `After_Sale_API` SHALL 具有接口契约测试，验证新资源、授权、参数校验、状态响应和旧订单售后字段删除。
7. THE `After_Sale_Aggregate` SHALL 具有数据库结构测试，验证新售后表、容量约束、订单退款事实字段以及旧售后列删除。
8. FOR ALL 受本次重构影响的模块, THE `Domain_Event` SHALL 具有回归测试，验证订单、支付与财务订阅方消费新的售后批准事件且不依赖旧订单退款事件。
9. THE `After_Sale_Aggregate` SHALL 在相关模块测试及全仓测试全部通过后才视为完成。
