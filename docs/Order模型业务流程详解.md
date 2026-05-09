# Order 模型业务流程详解

## 一、订单状态总览

```
OrderStatus:
  PENDING_STOCK     待库存确认（初始状态）
  PENDING_PAYMENT   待支付
  PAID              已支付
  PENDING_SHIPMENT  待发货
  SHIPPED           已发货
  DELIVERED         已签收
  COMPLETED         已完成（终态）
  CANCELLED         已取消（终态）
  REFUNDING         退款中
```

```
OrderItemStatus:
  NONE              初始
  WAIT_SHIPPING     待发货
  SHIPPING          运输中
  SHIPPING_ERROR    运输异常
  SHIPPING_FINISHED 已签收
  REFUNDING         退款中
  CANCELED          已取消
```

---

## 二、正向流程

### 2.1 状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING_STOCK : 创建订单
    PENDING_STOCK --> PENDING_PAYMENT : 库存预扣成功
    PENDING_STOCK --> CANCELLED : 库存不足
    PENDING_PAYMENT --> PAID : 买家支付
    PENDING_PAYMENT --> CANCELLED : 买家取消 / 支付超时
    PAID --> PENDING_SHIPMENT : 确认备货
    PENDING_SHIPMENT --> SHIPPED : 卖家发货
    SHIPPED --> DELIVERED : 买家确认收货
    DELIVERED --> COMPLETED : 订单完成
    COMPLETED --> [*]
    CANCELLED --> [*]
```

### 2.2 流程时序

```mermaid
sequenceDiagram
    participant Buyer as 买家
    participant OrderSvc as OrderService
    participant OrderAgg as Order 聚合根
    participant Translator as 事件翻译器(boot层)
    participant StockCtx as 库存上下文

    Note over Buyer, StockCtx: ① 创建订单

    Buyer->>OrderSvc: createOrder(OrderCreateCMD)
    OrderSvc->>OrderAgg: OrderFactory.create(cmd)
    Note right of OrderAgg: 状态 = PENDING_STOCK
    OrderAgg-->>OrderSvc: 发布 OrderCreatedEvent
    OrderSvc-->>Translator: OrderCreatedEvent
    Translator->>StockCtx: StockReservationRequestedEvent（预扣库存）

    Note over Buyer, StockCtx: ② 库存预扣结果

    alt 库存充足
        StockCtx-->>Translator: StockReservedEvent
        Translator-->>OrderSvc: OrderStockConfirmedEvent
        OrderSvc->>OrderAgg: confirmStock()
        Note right of OrderAgg: 状态 → PENDING_PAYMENT
    else 库存不足
        StockCtx-->>Translator: StockReservationFailedEvent
        Translator-->>OrderSvc: OrderStockInsufficientEvent
        OrderSvc->>OrderAgg: markStockInsufficient(reason)
        Note right of OrderAgg: 状态 → CANCELLED
        OrderAgg-->>Translator: OrderCancelledEvent
        Translator->>StockCtx: StockReleaseRequestedEvent（释放库存）
    end

    Note over Buyer, StockCtx: ③ 买家支付

    Buyer->>OrderSvc: payOrder(OrderPayCMD)
    OrderSvc->>OrderAgg: pay(paidAmount)
    Note right of OrderAgg: 状态 → PAID，记录实付金额
    OrderAgg-->>Translator: OrderPaidEvent
    Translator->>StockCtx: StockConfirmRequestedEvent（预扣 → 真正扣减）

    Note over Buyer, StockCtx: ④ 确认备货

    OrderSvc->>OrderAgg: confirmForShipment()
    Note right of OrderAgg: 状态 → PENDING_SHIPMENT
    Note right of OrderAgg: （预留节点：风控审核/仓储确认/卖家手动）

    Note over Buyer, StockCtx: ⑤ 卖家发货

    OrderSvc->>OrderAgg: ship()
    Note right of OrderAgg: 状态 → SHIPPED
    Note right of OrderAgg: 行项状态 → SHIPPING
    OrderAgg-->>Translator: OrderShippedEvent

    Note over Buyer, StockCtx: ⑥ 买家确认收货

    Buyer->>OrderSvc: confirmDelivery()
    OrderSvc->>OrderAgg: confirmDelivery()
    Note right of OrderAgg: 状态 → DELIVERED
    Note right of OrderAgg: 行项状态 → SHIPPING_FINISHED

    Note over Buyer, StockCtx: ⑦ 订单完成

    OrderSvc->>OrderAgg: complete()
    Note right of OrderAgg: 状态 → COMPLETED（终态）
    OrderAgg-->>Translator: OrderCompletedEvent
```

---

## 三、逆向流程

### 3.1 取消订单

买家在 `PENDING_STOCK` 或 `PENDING_PAYMENT` 阶段可主动取消。

```mermaid
stateDiagram-v2
    PENDING_STOCK --> CANCELLED : 买家取消
    PENDING_PAYMENT --> CANCELLED : 买家取消 / 支付超时
```

```mermaid
sequenceDiagram
    participant Buyer as 买家
    participant OrderSvc as OrderService
    participant OrderAgg as Order 聚合根
    participant Translator as 事件翻译器
    participant StockCtx as 库存上下文

    Buyer->>OrderSvc: cancelOrder(OrderCancelCMD)
    OrderSvc->>OrderAgg: cancel(CancellationReason)
    Note right of OrderAgg: 状态 → CANCELLED
    Note right of OrderAgg: 所有行项 → CANCELED
    OrderAgg-->>Translator: OrderCancelledEvent
    Translator->>StockCtx: StockReleaseRequestedEvent（释放预扣库存）
```

### 3.2 退款流程（已支付后）

可发起退款的状态：`PAID`、`PENDING_SHIPMENT`、`DELIVERED`

```mermaid
stateDiagram-v2
    PAID --> REFUNDING : 申请退款
    PENDING_SHIPMENT --> REFUNDING : 申请退款
    DELIVERED --> REFUNDING : 申请退款（需退货）

    REFUNDING --> CANCELLED : 退款批准（所有行项终态）
    REFUNDING --> PAID : 退款拒绝（恢复）
    REFUNDING --> PENDING_SHIPMENT : 退款拒绝（恢复）
    REFUNDING --> DELIVERED : 退款拒绝（恢复）
```

#### 3.2.1 申请退款

```mermaid
sequenceDiagram
    participant Buyer as 买家
    participant OrderSvc as OrderService
    participant OrderAgg as Order 聚合根

    Buyer->>OrderSvc: requestRefund(OrderRequestRefundCMD)
    OrderSvc->>OrderAgg: requestRefund(reason, itemIds)

    Note right of OrderAgg: 校验 Order 状态可转 REFUNDING
    Note right of OrderAgg: 校验 itemIds 非空且属于本订单
    Note right of OrderAgg: 校验行项状态（非 REFUNDING/CANCELED）
    Note right of OrderAgg: 记录 previousStatus = 当前状态
    Note right of OrderAgg: Order 状态 → REFUNDING
    Note right of OrderAgg: 选中行项记录 previousItemStatus
    Note right of OrderAgg: 选中行项状态 → REFUNDING
    Note right of OrderAgg: 计算退款金额 = Σ 行项小计

    OrderAgg-->>OrderSvc: 发布 OrderRefundRequestedEvent
    Note right of OrderAgg: requireReturn = (之前是 SHIPPED/DELIVERED)
```

#### 3.2.2 卖家批准退款

```mermaid
sequenceDiagram
    participant Seller as 卖家
    participant OrderSvc as OrderService
    participant OrderAgg as Order 聚合根
    participant Translator as 事件翻译器
    participant StockCtx as 库存上下文

    Seller->>OrderSvc: approveRefund(OrderApproveRefundCMD)
    OrderSvc->>OrderAgg: approveRefund(itemIds)

    Note right of OrderAgg: 选中行项 → CANCELED
    Note right of OrderAgg: 若所有行项都是 CANCELED → Order 状态 → CANCELLED

    OrderAgg-->>Translator: OrderRefundApprovedEvent

    alt requireReturn = false（未发货，可直接释放库存）
        Translator->>StockCtx: StockReleaseRequestedEvent
    else requireReturn = true（已发货，需等退货入库）
        Note right of Translator: 不释放库存，等退货流程
    end
```

#### 3.2.3 卖家拒绝退款

```mermaid
sequenceDiagram
    participant Seller as 卖家
    participant OrderSvc as OrderService
    participant OrderAgg as Order 聚合根

    Seller->>OrderSvc: rejectRefund(OrderRejectRefundCMD)
    OrderSvc->>OrderAgg: rejectRefund(rejectReason, itemIds)

    Note right of OrderAgg: 选中行项恢复到 previousItemStatus
    Note right of OrderAgg: 若无行项处于 REFUNDING
    Note right of OrderAgg: → Order 恢复到 previousStatus（PAID/PENDING_SHIPMENT/DELIVERED）
    Note right of OrderAgg: → 清空 previousStatus

    OrderAgg-->>OrderSvc: 发布 OrderRefundRejectedEvent
```

---

## 四、跨上下文事件协作

```mermaid
flowchart LR
    subgraph 订单上下文
        OC[OrderCreatedEvent]
        OP[OrderPaidEvent]
        OX[OrderCancelledEvent]
        ORA[OrderRefundApprovedEvent]
    end

    subgraph boot 组装层
        T1[OrderToStockEventTranslator]
        T2[StockToOrderEventTranslator]
    end

    subgraph 库存上下文
        SR[StockReservationRequestedEvent]
        SC[StockConfirmRequestedEvent]
        SRL[StockReleaseRequestedEvent]
        SRE[StockReservedEvent]
        SRF[StockReservationFailedEvent]
    end

    OC --> T1 --> SR
    OP --> T1 --> SC
    OX --> T1 --> SRL
    ORA --> T1 -->|requireReturn=false| SRL

    SRE --> T2 -->|OrderStockConfirmedEvent| 订单上下文
    SRF --> T2 -->|OrderStockInsufficientEvent| 订单上下文
```

| 订单事件 | 翻译为 | 库存动作 |
|---|---|---|
| `OrderCreatedEvent` | `StockReservationRequestedEvent` | 预扣库存 |
| `OrderPaidEvent` | `StockConfirmRequestedEvent` | 预扣 → 真正扣减 |
| `OrderCancelledEvent` | `StockReleaseRequestedEvent` | 释放预扣库存 |
| `OrderRefundApprovedEvent` | `StockReleaseRequestedEvent`（仅未发货） | 释放已扣减库存 |

| 库存事件 | 翻译为 | 订单动作 |
|---|---|---|
| `StockReservedEvent` | `OrderStockConfirmedEvent` | `confirmStock()` → PENDING_PAYMENT |
| `StockReservationFailedEvent` | `OrderStockInsufficientEvent` | `markStockInsufficient()` → CANCELLED |

---

## 五、Command 与方法对照表

| 应用服务方法 | Command | 触发方式 | 发布事件 |
|---|---|---|---|
| `createOrder` | `OrderCreateCMD` | 买家提交 | `OrderCreatedEvent` |
| `confirmStock` | — (仅 orderId) | 库存事件回调 | — |
| `markStockInsufficient` | — (orderId + reason) | 库存事件回调 | `OrderCancelledEvent` |
| `payOrder` | `OrderPayCMD` | 支付回调 | `OrderPaidEvent` |
| `confirmForShipment` | — (仅 orderId) | 预留（风控/仓储/卖家手动） | — |
| `shipOrder` | — (仅 orderId) | 卖家操作 | `OrderShippedEvent` |
| `confirmDelivery` | — (仅 orderId) | 买家操作 | — |
| `completeOrder` | — (仅 orderId) | 系统/买家操作 | `OrderCompletedEvent` |
| `cancelOrder` | `OrderCancelCMD` | 买家操作 | `OrderCancelledEvent` |
| `requestRefund` | `OrderRequestRefundCMD` | 买家操作 | `OrderRefundRequestedEvent` |
| `approveRefund` | `OrderApproveRefundCMD` | 卖家操作 | `OrderRefundApprovedEvent` |
| `rejectRefund` | `OrderRejectRefundCMD` | 卖家操作 | `OrderRefundRejectedEvent` |
