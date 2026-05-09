# 需求文档：订单逆向业务流程

## 简介

为订单领域模型实现完整的逆向业务流程，包括买家主动取消、退款申请、退款审批、退货退款等场景。当前系统已实现正向流程（PENDING_STOCK → ... → COMPLETED），并在状态机中预留了逆向分支（CANCELLED、REFUNDING），但仅有库存不足时的被动取消逻辑。本需求旨在补全所有逆向场景，使订单生命周期管理完整。

退款/退货操作支持行项级别粒度：买家可以选择部分 OrderItem 进行退款，退款金额按选中行项的小计计算。全单退款是部分退款的特例（选中所有行项）。

## 术语表

- **Order**：订单聚合根，封装订单全生命周期的状态转移和业务规则
- **OrderItem**：订单行项实体，生命周期依附于 Order 聚合根，拥有独立的 OrderItemStatus
- **OrderStatus**：订单状态枚举，包含正向状态和逆向状态（CANCELLED、REFUNDING）
- **OrderItemStatus**：订单行项状态枚举，包含 REFUNDING 和 CANCELED
- **OrderStatusTransitionRules**：订单状态转移规则对象，定义所有合法的状态转移
- **Buyer**：买家，发起订单和逆向操作的用户角色
- **Seller**：卖家/运营方，审批退款、处理退货的角色
- **RefundReason**：退款原因值对象，封装退款原因分类和描述
- **CancellationReason**：取消原因值对象，封装取消原因分类和描述
- **OrderToStockEventTranslator**：订单→库存事件翻译器，将订单领域事件转换为库存 ACL 集成事件
- **DomainEvent**：领域事件标记接口，用于跨上下文异步通信
- **部分退款**：买家选择订单中的部分行项进行退款，未选中的行项保持原状态
- **全单退款**：买家选择订单中的所有行项进行退款，是部分退款的特例

## 需求

### 需求 1：买家主动取消订单（未支付）

**用户故事：** 作为买家，我希望在支付前能主动取消订单，以便在改变购买意愿时及时止损。

#### 验收标准

1. WHILE Order 处于 PENDING_STOCK 状态, WHEN Buyer 发起取消请求, THE Order SHALL 将状态转移为 CANCELLED 并记录取消原因为"买家主动取消"
2. WHILE Order 处于 PENDING_PAYMENT 状态, WHEN Buyer 发起取消请求, THE Order SHALL 将状态转移为 CANCELLED 并记录取消原因为"买家主动取消"
3. WHEN Order 状态转移为 CANCELLED, THE Order SHALL 发布 OrderCancelledEvent 领域事件，事件中携带 orderId 和取消原因
4. WHEN OrderCancelledEvent 被发布, THE OrderToStockEventTranslator SHALL 将该事件翻译为 StockReleaseRequestedEvent 以释放预扣库存
5. IF Buyer 对处于 PAID、PENDING_SHIPMENT、SHIPPED、DELIVERED、COMPLETED 状态的 Order 发起取消请求, THEN THE Order SHALL 拒绝操作并返回状态不合法的业务错误
6. IF Buyer 对已处于 CANCELLED 状态的 Order 发起取消请求, THEN THE Order SHALL 拒绝操作并返回状态不合法的业务错误

### 需求 2：支付超时自动取消

**用户故事：** 作为系统运营方，我希望待支付订单在超时后自动取消，以便及时释放被预扣的库存资源。

#### 验收标准

1. WHILE Order 处于 PENDING_PAYMENT 状态, WHEN 支付超时时间到达, THE Order SHALL 将状态转移为 CANCELLED 并记录取消原因为"支付超时"
2. WHEN Order 因支付超时被取消, THE Order SHALL 发布 OrderCancelledEvent 领域事件，事件中携带 orderId 和取消原因"支付超时"
3. THE Order SHALL 将所有关联 OrderItem 的状态设置为 CANCELED

### 需求 3：买家申请退款（已支付未发货）— 支持行项级别粒度

**用户故事：** 作为买家，我希望在已支付但尚未发货时能选择部分或全部行项申请退款，以便在不需要某些商品时精确取回对应货款。

#### 验收标准

1. WHILE Order 处于 PAID 状态, WHEN Buyer 提交退款申请并提供 RefundReason 和目标 OrderItemId 列表, THE Order SHALL 将指定行项的状态转移为 REFUNDING
2. WHILE Order 处于 PENDING_SHIPMENT 状态, WHEN Buyer 提交退款申请并提供 RefundReason 和目标 OrderItemId 列表, THE Order SHALL 将指定行项的状态转移为 REFUNDING
3. WHEN 退款申请被接受, THE Order SHALL 计算退款金额为所有选中行项的 subtotal() 之和
4. WHEN 退款申请被接受, THE Order SHALL 在每个选中的 OrderItem 上记录其进入 REFUNDING 前的 previousItemStatus，以便退款拒绝时恢复
5. WHEN 退款申请被接受且所有行项均被选中, THE Order 状态 SHALL 转移为 REFUNDING
6. WHEN 退款申请被接受且仅部分行项被选中, THE Order 状态 SHALL 转移为 REFUNDING（订单整体处于退款处理中）
7. WHEN Order 进入 REFUNDING 状态, THE Order SHALL 记录 previousStatus 为进入 REFUNDING 前的状态
8. WHEN 退款申请被接受, THE Order SHALL 发布 OrderRefundRequestedEvent 领域事件，事件中携带 orderId、退款金额、退款原因、退货标记和退款行项 OrderItemId 列表
9. IF 目标 OrderItemId 列表为空, THEN THE Order SHALL 拒绝操作并返回业务错误
10. IF 目标 OrderItemId 列表中包含不属于该订单的 OrderItemId, THEN THE Order SHALL 拒绝操作并返回业务错误
11. IF 目标 OrderItemId 列表中包含已处于 REFUNDING 或 CANCELED 状态的行项, THEN THE Order SHALL 拒绝操作并返回业务错误
12. IF Buyer 对处于 PENDING_STOCK、PENDING_PAYMENT、SHIPPED、COMPLETED、CANCELLED 状态的 Order 提交退款申请, THEN THE Order SHALL 拒绝操作并返回状态不合法的业务错误

### 需求 4：买家申请退货退款（已签收）— 支持行项级别粒度

**用户故事：** 作为买家，我希望在签收商品后发现问题时能选择部分或全部行项申请退货退款，以便精确保障自身消费权益。

#### 验收标准

1. WHILE Order 处于 DELIVERED 状态, WHEN Buyer 提交退货退款申请并提供 RefundReason 和目标 OrderItemId 列表, THE Order SHALL 将指定行项的状态转移为 REFUNDING
2. WHEN Order 因退货退款进入 REFUNDING 状态, THE Order SHALL 发布 OrderRefundRequestedEvent 领域事件，事件中携带 orderId、退款金额、退款原因、退货标记（true）和退款行项 OrderItemId 列表
3. WHEN Order 因退货退款进入 REFUNDING 状态, THE Order SHALL 仅将选中的 OrderItem 状态设置为 REFUNDING，未选中的行项保持原状态

### 需求 5：卖家审批退款 — 支持行项级别粒度

**用户故事：** 作为卖家，我希望能按行项粒度审批买家的退款申请，以便精确处理每个行项的退款请求。

#### 验收标准

1. WHILE Order 处于 REFUNDING 状态, WHEN Seller 批准退款并指定目标 OrderItemId 列表, THE Order SHALL 将指定行项的状态转移为 CANCELED
2. WHEN 退款被批准, THE Order SHALL 计算退款金额为所有被批准行项的 subtotal() 之和
3. WHEN 退款被批准且所有行项均已进入 CANCELED 状态, THE Order 状态 SHALL 转移为 CANCELLED
4. WHEN 退款被批准但仍有行项处于非终态, THE Order 状态 SHALL 保持 REFUNDING
5. WHEN 退款被批准, THE Order SHALL 发布 OrderRefundApprovedEvent 领域事件，事件中携带 orderId、退款金额和被批准的行项 OrderItemId 列表
6. WHEN OrderRefundApprovedEvent 被发布, THE OrderToStockEventTranslator SHALL 将该事件翻译为 StockReleaseRequestedEvent，仅释放被批准行项的库存
7. WHILE Order 处于 REFUNDING 状态, WHEN Seller 拒绝退款并指定目标 OrderItemId 列表和拒绝原因, THE Order SHALL 将指定行项的状态从 REFUNDING 恢复为进入退款前的 previousItemStatus
8. WHEN 退款被拒绝且所有行项均已脱离 REFUNDING 状态（无行项处于 REFUNDING）, THE Order 状态 SHALL 恢复为 previousStatus
9. WHEN 退款被拒绝但仍有行项处于 REFUNDING 状态, THE Order 状态 SHALL 保持 REFUNDING
10. WHEN 退款被拒绝, THE Order SHALL 发布 OrderRefundRejectedEvent 领域事件，事件中携带 orderId、拒绝原因和被拒绝的行项 OrderItemId 列表
11. IF Seller 对非 REFUNDING 状态的 Order 执行退款审批操作, THEN THE Order SHALL 拒绝操作并返回状态不合法的业务错误
12. IF 目标 OrderItemId 列表中包含未处于 REFUNDING 状态的行项（对于批准/拒绝操作）, THEN THE Order SHALL 拒绝操作并返回业务错误

### 需求 6：逆向流程状态转移规则

**用户故事：** 作为开发者，我希望状态转移规则完整覆盖逆向场景，以便确保所有逆向操作的合法性和一致性。

#### 验收标准

1. THE OrderStatusTransitionRules SHALL 支持从 REFUNDING 到 CANCELLED 的状态转移（退款批准，所有行项终态）
2. THE OrderStatusTransitionRules SHALL 支持从 REFUNDING 到 PAID 的状态转移（退款拒绝，恢复已支付状态）
3. THE OrderStatusTransitionRules SHALL 支持从 REFUNDING 到 PENDING_SHIPMENT 的状态转移（退款拒绝，恢复待发货状态）
4. THE OrderStatusTransitionRules SHALL 支持从 REFUNDING 到 DELIVERED 的状态转移（退款拒绝，恢复已签收状态）
5. FOR ALL 合法的状态转移, THE OrderStatusTransitionRules 的 isValidTransition 方法 SHALL 返回 true；FOR ALL 非法的状态转移, 该方法 SHALL 返回 false
6. THE Order SHALL 在执行逆向状态转移前记录转移前的状态（Order 级别 previousStatus），以便退款拒绝时恢复
7. THE OrderItem SHALL 在进入 REFUNDING 状态前记录转移前的状态（OrderItem 级别 previousItemStatus），以便退款拒绝时恢复

### 需求 7：逆向流程领域事件定义

**用户故事：** 作为开发者，我希望逆向流程产生完整的领域事件，以便其他限界上下文能正确响应订单逆向操作。

#### 验收标准

1. THE OrderRefundRequestedEvent SHALL 携带 orderId、退款金额、退款原因、是否需要退货的标记和退款行项 OrderItemId 列表
2. THE OrderRefundApprovedEvent SHALL 携带 orderId、退款金额和被批准的行项 OrderItemId 列表
3. THE OrderRefundRejectedEvent SHALL 携带 orderId、拒绝原因和被拒绝的行项 OrderItemId 列表
4. THE OrderCancelledEvent SHALL 继续携带 orderId 和取消原因（已有，保持兼容）
5. FOR ALL 逆向领域事件, THE 事件 SHALL 继承 OrderDomainEvent 基类并实现 DomainEvent 接口

### 需求 8：逆向流程应用服务编排

**用户故事：** 作为开发者，我希望应用服务层提供完整的逆向操作用例编排，以便控制器层能调用统一的入口执行逆向业务。

#### 验收标准

1. THE OrderService SHALL 提供 cancelOrder 方法，接受 orderId 和 CancellationReason 参数，编排买家主动取消用例
2. THE OrderService SHALL 提供 requestRefund 方法，接受 orderId、RefundReason 和 List&lt;OrderItemId&gt; 参数，编排退款申请用例
3. THE OrderService SHALL 提供 approveRefund 方法，接受 orderId 和 List&lt;OrderItemId&gt; 参数，编排退款批准用例
4. THE OrderService SHALL 提供 rejectRefund 方法，接受 orderId、拒绝原因和 List&lt;OrderItemId&gt; 参数，编排退款拒绝用例
5. FOR ALL 逆向操作方法, THE OrderService SHALL 遵循"加载聚合 → 执行领域行为 → 保存 → 发布事件"的编排模式
6. IF 指定 orderId 对应的 Order 不存在, THEN THE OrderService SHALL 返回 ORDER_NOT_FOUND 业务错误
