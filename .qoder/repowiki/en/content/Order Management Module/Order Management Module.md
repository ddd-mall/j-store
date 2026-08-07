# Order Management Module

<cite>
**Referenced Files in This Document**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderFactory.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt)
- [TradeStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/TradeStatus.kt)
- [PaymentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/PaymentStatus.kt)
- [FulfillmentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/FulfillmentStatus.kt)
- [AfterSaleStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleStatus.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [UserServiceImpl.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/UserServiceImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderStockEventHandler.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderStockEventHandler.kt)
- [OrderController.kt](file://j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)
- [SaleAuthorizationRef.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/SaleAuthorizationRef.kt)
- [OrderBuyerAuthorizationTest.kt](file://j-store-order-application/src/test/kotlin/com/jstore/order/service\OrderBuyerAuthorizationTest.kt)
- [design.md](file://docs/spec/order-status-dimensions/design.md)
</cite>

## Update Summary
**Changes Made**
- Enhanced order domain with user service integration through UserService ACL for buyer validation
- Added buyer authorization checks throughout order creation and cancellation workflows
- Improved integration with sale authorization system through SaleAuthorizationRef tracking
- Updated order creation flow to support proper authorization coordination with user service
- Added comprehensive buyer authorization testing coverage

## Table of Contents
1. Introduction
2. Project Structure
3. Core Components
4. Architecture Overview
5. Detailed Component Analysis
6. Dependency Analysis
7. Performance Considerations
8. Troubleshooting Guide
9. Conclusion

## Introduction
This document explains the Order Management module with a focus on the order aggregate design that uses multi-dimensional status tracking across trade, payment, fulfillment, and after-sale dimensions. It covers the order lifecycle from creation through completion, including stock reservation, payment integration, and fulfillment coordination. The module now includes enhanced buyer authorization checks through UserService ACL integration and improved sale authorization system coordination. It also documents command handlers, event processing, state transitions, database schema with status dimensions and versioning, and common issues such as concurrent access, optimistic locking, and data consistency. The content is designed to be accessible to beginners while providing sufficient technical depth for experienced developers.

## Project Structure
The Order Management module spans multiple layers with enhanced user service integration:
- Domain layer defines the Order aggregate, its four status dimensions, business behaviors, and UserService ACL interface for buyer validation.
- Application layer orchestrates use cases by loading aggregates, invoking domain methods, persisting changes, publishing events, and performing buyer authorization checks.
- Infrastructure layer persists orders and items using JPA entities, manages versioning, and provides UserService implementation for user profile queries.

```mermaid
graph TB
subgraph "Domain"
OI["Order Interface"]
OI_IMPL["OrderImpl"]
TS["TradeStatus"]
PS["PaymentStatus"]
FS["FulfillmentStatus"]
AS["AfterSaleStatus"]
US["UserService ACL"]
end
subgraph "Application"
OS["OrderService"]
OSEH["OrderStockConfirmedEventHandler"]
end
subgraph "Infrastructure"
PO["OrderPO / OrderItemPO"]
USI["UserServiceImpl"]
end
OI --> OI_IMPL
OI_IMPL --> TS
OI_IMPL --> PS
OI_IMPL --> FS
OI_IMPL --> AS
OS --> US
OS --> OI
OSEH --> OS
OS --> PO
USI --> US
```

**Diagram sources**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [UserServiceImpl.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/UserServiceImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderStockEventHandler.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderStockEventHandler.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)

**Section sources**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)

## Core Components
- Order aggregate interface exposes four status dimensions and key behaviors for stock confirmation, payment capture, fulfillment updates, refund projection, cancellation, and completion.
- OrderImpl implements these behaviors with strict preconditions and idempotency checks where applicable.
- OrderService orchestrates use cases with enhanced buyer authorization: load aggregate, validate buyer ownership, invoke behavior, save, publish pending events.
- UserService ACL provides buyer information validation and active user status checking.
- OrderStockConfirmedEventHandler integrates inventory confirmation into order flow by transitioning the order to paid-ready state.
- OrderPO models persistence with four status columns and versioning via @Version.

Key responsibilities:
- Trade dimension tracks order lifecycle stages (CREATED → ACTIVE → CLOSED/COMPLETED).
- Payment dimension records captured facts and partial/full refunds.
- Fulfillment dimension reflects shipping progress (UNFULFILLED → PENDING_SHIPMENT → SHIPPED → DELIVERED).
- After-sale dimension captures refund eligibility and successful refund projections.
- Buyer authorization ensures users can only access and modify their own orders.

**Section sources**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderStockEventHandler.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderStockEventHandler.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)

## Architecture Overview
The order workflow integrates inventory, payment, fulfillment, and user domains through application services and domain events. Stock confirmation transitions the order to active; payment capture marks it paid; fulfillment steps advance shipping states; refund success updates payment and trade statuses accordingly. Enhanced buyer authorization ensures secure access control throughout the order lifecycle.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Auth as "Authentication"
participant Service as "OrderService"
participant UserACL as "UserService"
participant Aggregate as "OrderImpl"
participant Repo as "OrderRepository"
participant Bus as "EventPublisher"
Client->>Auth : Login + Create Order Request
Auth-->>Client : Current User ID
Client->>Service : createOrder(cmd)
Service->>UserACL : findUserInfo(buyerUid)
UserACL-->>Service : UserInfo or null
alt Valid User
Service->>Aggregate : create(order, buyerInfo)
Aggregate-->>Service : Order
Service->>Repo : add(order)
Service->>Bus : publishPendingEvents()
Service-->>Client : Success
else Invalid User
Service-->>Client : BUYER_INVALID error
end
```

**Diagram sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [OrderFactory.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt)

**Section sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderFactory.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt)

## Detailed Component Analysis

### Order Aggregate Design and Multi-Dimensional Status Tracking
- Four independent status enums define orthogonal business facts:
  - TradeStatus: CREATED, ACTIVE, CLOSED, COMPLETED
  - PaymentStatus: UNPAID, PAID, PARTIALLY_REFUNDED, REFUNDED
  - FulfillmentStatus: UNFULFILLED, PENDING_SHIPMENT, SHIPPED, DELIVERED
  - AfterSaleStatus: REQUESTED, RETURN_REQUIRED, REFUND_PENDING, REFUND_FAILED, COMPLETED, REJECTED, CANCELLED
- The aggregate enforces cross-dimensional invariants during each operation, ensuring consistent state transitions.
- Refund eligibility and successful refund projection are modeled explicitly to avoid ambiguous states.
- Enhanced with sale authorization tracking through SaleAuthorizationRef for improved integration with store authorization systems.

```mermaid
classDiagram
class Order {
+id : OrderId
+merchantId : MerchantId
+buyerInfo : UserInfo
+items : List<OrderItem>
+recipientInfo : RecipientInfo
+tradeStatus : TradeStatus
+paymentStatus : PaymentStatus
+fulfillmentStatus : FulfillmentStatus
+commitmentStatus : CommitmentStatus
+saleAuthorizations : SaleAuthorizationRef[]
+refundedAmount : Price
+successfulRefundFacts : RefundFact[]
+amountSnapshot : OrderAmountSnapshot
+paidAmount : Price
+paymentReference : String?
+fulfillmentReference : String?
+createTime : LocalDateTime
+updateTime : LocalDateTime
+recordSaleAuthorized(authorizations) : Result~Unit, BusinessError~
+markSaleAuthorizationFailed(reason) : Result~Unit, BusinessError~
+confirmStock() : Result~Unit, BusinessError~
+markStockInsufficient(reason) : Result~Unit, BusinessError~
+recordPaymentCaptured(paymentReference, capturedAmount, currency, occurredAt) : Result~Boolean, BusinessError~
+recordFulfillmentPrepared(fulfillmentReference) : Result~Boolean, BusinessError~
+recordShipmentDispatched(fulfillmentReference) : Result~Boolean, BusinessError~
+recordShipmentDelivered(fulfillmentReference) : Result~Boolean, BusinessError~
+complete() : Result~Unit, BusinessError~
+cancel(reason) : Result~Unit, BusinessError~
+refundEligibility() : Result~RefundEligibility, BusinessError~
+recordRefundSucceeded(refundId, afterSaleId, items, occurredAt) : Result~RefundProjectionResult, BusinessError~
}
class OrderImpl {
-_items : MutableList~OrderItem~
-_tradeStatus : TradeStatus
-_paymentStatus : PaymentStatus
-_fulfillmentStatus : FulfillmentStatus
-_paidAmount : Price
-_refundedAmount : Price
-_paymentReference : String?
-_fulfillmentReference : String?
-refundFacts : MutableList~RefundFact~
+recordCreated()
+transition(valid, operation, action) : Result~Unit, BusinessError~
}
class TradeStatus
class PaymentStatus
class FulfillmentStatus
class AfterSaleStatus
class SaleAuthorizationRef
Order <|.. OrderImpl
OrderImpl --> TradeStatus : "uses"
OrderImpl --> PaymentStatus : "uses"
OrderImpl --> FulfillmentStatus : "uses"
OrderImpl --> AfterSaleStatus : "uses"
OrderImpl --> SaleAuthorizationRef : "tracks"
```

**Diagram sources**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [TradeStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/TradeStatus.kt)
- [PaymentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/PaymentStatus.kt)
- [FulfillmentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/FulfillmentStatus.kt)
- [AfterSaleStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleStatus.kt)
- [SaleAuthorizationRef.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/SaleAuthorizationRef.kt)

**Section sources**
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [TradeStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/TradeStatus.kt)
- [PaymentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/PaymentStatus.kt)
- [FulfillmentStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/FulfillmentStatus.kt)
- [AfterSaleStatus.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/aftersale/AfterSaleStatus.kt)
- [SaleAuthorizationRef.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/SaleAuthorizationRef.kt)

### Enhanced Buyer Authorization and User Service Integration
The order management system now includes comprehensive buyer authorization through UserService ACL integration:

- **UserService ACL**: Provides buyer information validation and active user status checking through a clean interface abstraction.
- **Buyer Validation**: Order creation validates buyer existence and active status before proceeding with order construction.
- **Access Control**: All order operations verify buyer ownership through explicit buyer ID checks in service layer methods.
- **Security Enforcement**: Unauthorized access attempts return appropriate business errors rather than exposing sensitive information.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Controller as "OrderController"
participant Service as "OrderService"
participant UserACL as "UserService"
participant Repo as "OrderRepository"
Note over Client,Service : Order Creation Flow
Client->>Controller : POST /api/orders
Controller->>Service : createOrder(OrderCreateCMD)
Service->>UserACL : findUserInfo(buyerUid)
UserACL-->>Service : UserInfo or null
alt Active User Found
Service->>Service : orderFactory.create(cmd, buyerInfo)
Service->>Repo : add(order)
Service-->>Client : 201 Created
else Invalid or Inactive User
Service-->>Client : 400 BUYER_INVALID
end
Note over Client,Service : Order Access Flow
Client->>Controller : GET /api/orders/{orderId}
Controller->>Service : getOrderById(userId, orderId)
Service->>Repo : findById(orderId)
Repo-->>Service : Order
alt Order belongs to buyer
Service-->>Client : 200 OK
else Unauthorized access
Service-->>Client : 404 ORDER_NOT_FOUND
end
```

**Diagram sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [OrderController.kt](file://j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt)

**Section sources**
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [UserServiceImpl.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/acl/UserServiceImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderController.kt](file://j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt)
- [OrderBuyerAuthorizationTest.kt](file://j-store-order-application/src/test/kotlin/com/jstore/order/service\OrderBuyerAuthorizationTest.kt)

### Order Lifecycle and State Transitions
The lifecycle progresses through well-defined transitions validated by the aggregate with enhanced authorization checks:
- Creation: initial state with CREATED trade, UNPAID payment, UNFULFILLED fulfillment, NONE after-sale, with buyer validation.
- Stock confirmation: transitions trade to ACTIVE after buyer authorization verification.
- Payment capture: marks payment PAID and records reference.
- Fulfillment preparation: moves to PENDING_SHIPMENT.
- Shipment dispatched: moves to SHIPPED.
- Delivery confirmed: moves to DELIVERED.
- Completion: finalizes trade to COMPLETED.
- Cancellation: closes order when unpaid and unfulfilled, with buyer ownership verification.
- Refund eligibility: computes eligible items and amounts.
- Successful refund: updates payment/trade statuses based on full or partial refund.

```mermaid
flowchart TD
Start(["Order Created<br/>Buyer Validated"]) --> ConfirmStock["Confirm Stock<br/>Trade: ACTIVE"]
ConfirmStock --> Pay["Record Payment Captured<br/>Payment: PAID"]
Pay --> PrepareFulfillment["Record Fulfillment Prepared<br/>Fulfillment: PENDING_SHIPMENT"]
PrepareFulfillment --> Ship["Record Shipment Dispatched<br/>Fulfillment: SHIPPED"]
Ship --> Deliver["Record Shipment Delivered<br/>Fulfillment: DELIVERED"]
Deliver --> Complete["Complete Order<br/>Trade: COMPLETED"]
ConfirmStock --> Cancel["Cancel Order<br/>Buyer Verified<br/>Trade: CLOSED"]
Pay --> RefundEligibility["Refund Eligibility Check"]
RefundEligibility --> RefundSuccess["Record Refund Succeeded<br/>Payment: PARTIALLY_REFUNDED or REFUNDED"]
RefundSuccess --> CloseOrPartial{"Full Refund?"}
CloseOrPartial --> |Yes| Close["Trade: CLOSED"]
CloseOrPartial --> |No| Partial["Trade: ACTIVE<br/>Payment: PARTIALLY_REFUNDED"]
```

**Diagram sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)

**Section sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)

### Command Handlers and Event Processing
- OrderService handles commands like create, confirm stock, mark insufficient stock, record payment capture, fulfillment updates, complete, cancel, and refund success with enhanced buyer authorization.
- Each handler loads the aggregate, validates buyer ownership, invokes the corresponding method, saves changes, and publishes pending events.
- OrderStockConfirmedEventHandler listens to inventory reserved events and triggers stock confirmation.
- Enhanced sale authorization handling through recordSaleAuthorized and markSaleAuthorizationFailed methods.

```mermaid
sequenceDiagram
participant Inventory as "Inventory Domain"
participant Handler as "OrderStockConfirmedEventHandler"
participant Service as "OrderService"
participant Repo as "OrderRepository"
participant Aggregate as "OrderImpl"
participant Bus as "EventPublisher"
Inventory-->>Handler : InventoryReservedIntegrationEvent
Handler->>Service : confirmStock(orderId)
Service->>Repo : findById(orderId)
Repo-->>Service : Order
Service->>Aggregate : confirmStock()
Aggregate-->>Service : Result
Service->>Repo : save(order)
Service->>Bus : publishPendingEvents()
Service-->>Handler : Success
```

**Diagram sources**
- [OrderStockEventHandler.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderStockEventHandler.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)

**Section sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderStockEventHandler.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderStockEventHandler.kt)

### Database Schema with Status Dimensions and Versioning
- OrderPO includes four status columns aligned with domain enums plus commitment status for sale authorization tracking.
- Versioning is implemented via @Version to support optimistic locking.
- Items and refund facts are persisted as related entities.
- Enhanced schema supports sale authorization references and buyer information.

```mermaid
erDiagram
ORDERS {
bigint id PK
bigint merchant_id
bigint buyer_uid
string buyer_phone
string buyer_name
jsonb recipient_info
varchar trade_status
varchar payment_status
varchar fulfillment_status
varchar commitment_status
string currency
decimal items_subtotal
decimal discount_amount
decimal shipping_amount
decimal tax_amount
decimal payable_amount
decimal paid_amount
decimal refunded_amount
string payment_reference UK
string fulfillment_reference UK
bigint version
timestamp create_time
timestamp update_time
}
ORDER_ITEMS {
bigint id PK
bigint order_id FK
bigint sku_id
bigint spu_id
string goods_name
string sku_description
int quantity
decimal unit_price
bigint snapshot_version
varchar status
int refunded_quantity
decimal refunded_amount
}
ORDER_REFUND_FACTS {
bigint id PK
bigint order_id FK
string refund_id
bigint after_sale_id
bigint order_item_id
int quantity
decimal amount
timestamp occurred_at
}
ORDERS ||--o{ ORDER_ITEMS : "contains"
ORDERS ||--o{ ORDER_REFUND_FACTS : "records"
```

**Diagram sources**
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)

**Section sources**
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)

### Concrete Workflows

#### Enhanced Order Creation with Buyer Authorization
- Create an order via OrderFactory with buyer validation through UserService ACL.
- Initial statuses: CREATED, UNPAID, UNFULFILLED, NONE.
- Validates buyer existence and active status before order construction.
- Publishes created event upon successful creation.

**Section sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderFactory.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderFactory.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)

#### Payment Processing
- Record payment capture with reference, amount, currency, and timestamp.
- Validates current state (ACTIVE, UNPAID), ensures idempotency, updates paid amount and payment status to PAID.
- Publishes paid event.

**Section sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)

#### Enhanced Cancellation with Buyer Verification
- Cancel an unpaid order (CREATED or ACTIVE, UNPAID, UNFULFILLED) with buyer ownership verification.
- Sets trade status to CLOSED and marks all items canceled.
- Publishes cancelled event.

**Section sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)

#### Refund Workflow
- Check refund eligibility based on payment and trade statuses and item-level refundable quantities/amounts.
- Record successful refund with refund ID, after-sale ID, and item details.
- Updates refunded amount, sets payment status to PARTIALLY_REFUNDED or REFUNDED, and adjusts trade status accordingly.

**Section sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)

## Dependency Analysis
- OrderService depends on OrderRepository, DomainEventPublisher, and UserService ACL for buyer validation.
- OrderImpl depends on domain enums and internal item implementations.
- OrderPO depends on JPA annotations and enum mappings.
- Integration handlers depend on message contracts and application use cases.
- Enhanced dependencies include UserService for buyer authorization and SaleAuthorizationRef for store authorization tracking.

```mermaid
graph LR
OS["OrderService"] --> OR["OrderRepository"]
OS --> EP["DomainEventPublisher"]
OS --> US["UserService ACL"]
OS --> OI["Order Interface"]
OI_IMPL["OrderImpl"] --> TS["TradeStatus"]
OI_IMPL --> PS["PaymentStatus"]
OI_IMPL --> FS["FulfillmentStatus"]
OI_IMPL --> AS["AfterSaleStatus"]
OI_IMPL --> SAR["SaleAuthorizationRef"]
PO["OrderPO"] --> DB["Database"]
```

**Diagram sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [SaleAuthorizationRef.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/SaleAuthorizationRef.kt)

**Section sources**
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderPO.kt](file://j-store-order-infrastructure/src/main/kotlin/com/jstore/order/domain/order/persistence/OrderPO.kt)
- [UserService.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/acl/UserService.kt)
- [SaleAuthorizationRef.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/SaleAuthorizationRef.kt)

## Performance Considerations
- Use optimistic locking via @Version to detect concurrent modifications.
- Minimize object graph traversal by fetching only necessary fields.
- Ensure event publishing is efficient and does not block critical paths.
- Index frequently queried status columns for faster filtering and sorting.
- Cache user profile lookups through UserService to reduce external API calls.
- Implement connection pooling for user service integration to handle high concurrency scenarios.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and resolutions:
- Concurrent access conflicts: Detected by optimistic locking; retry logic should handle transient failures.
- Invalid state transitions: Validate preconditions before invoking aggregate methods; log detailed error messages.
- Idempotency failures: Ensure operations like payment capture and fulfillment updates check existing references and states.
- Data consistency errors: Verify invariant checks in aggregate constructors and recovery paths.
- Buyer authorization failures: Check buyer ID validation in service layer methods and ensure proper user context propagation.
- User service integration issues: Verify UserService implementation configuration and network connectivity to user service endpoints.

**Section sources**
- [OrderImpl.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/OrderImpl.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [OrderBuyerAuthorizationTest.kt](file://j-store-order-application/src/test/kotlin/com/jstore/order/service\OrderBuyerAuthorizationTest.kt)

## Conclusion
The Order Management module implements a robust, multi-dimensional status model that clearly separates trade, payment, fulfillment, and after-sale concerns. The enhanced buyer authorization system through UserService ACL integration ensures secure access control throughout the order lifecycle. The aggregate enforces strict invariants and supports idempotent operations. Application services orchestrate workflows seamlessly with proper authorization coordination, while infrastructure ensures persistence with versioning. This design promotes clarity, maintainability, and scalability for complex order lifecycles with comprehensive security controls.

[No sources needed since this section summarizes without analyzing specific files]