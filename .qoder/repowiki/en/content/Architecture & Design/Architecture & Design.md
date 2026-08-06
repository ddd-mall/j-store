# Architecture & Design

<cite>
**Referenced Files in This Document**
- [settings.gradle.kts](file://settings.gradle.kts)
- [build.gradle.kts](file://build.gradle.kts)
- [AggregateRoot.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/AggregateRoot.kt)
- [Entity.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/Entity.kt)
- [DomainEvent.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEvent.kt)
- [DomainEventPublisher.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventPublisher.kt)
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [AggregateRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/AggregateRepository.kt)
- [Order.kt](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [CommerceIntegrationMessages.kt](file://j-store-integration-contracts/src/main/kotlin/com/jstore/contracts/commerce/CommerceIntegrationMessages.kt)
- [OrderService.kt](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [V20260731__order_status_dimensions.sql](file://j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql)
- [V20260803__order_after_sale_aggregate.sql](file://j-store-boot/src/main/resources/db/migration/V20260803__order_after_sale_aggregate.sql)
</cite>

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
This document explains the J-Store platform’s Domain-Driven Design implementation, focusing on bounded contexts, aggregates, entities, value objects, repositories, and the event-driven architecture with outbox-based delivery and cross-context integration contracts. It also documents the layered architecture (boot, application, domain, infrastructure), component interactions, data flows, technical decisions, trade-offs, constraints, and module dependency structure that enforces DDD boundaries.

## Project Structure
J-Store is a multi-module Gradle project organized by bounded contexts and layers:
- Common core provides foundational DDD primitives, events, messaging, and utilities.
- Each business context (order, goods, payment, fulfillment, accounting, user, shop, warehouse) is split into domain, application, infrastructure, and boot modules.
- Integration contracts are shared across contexts to define stable messages for cross-context communication.
- Boot modules assemble Spring configurations and expose APIs.

```mermaid
graph TB
subgraph "Common"
CC["j-store-common-core"]
CS["j-store-common-spring"]
IC["j-store-integration-contracts"]
end
subgraph "Contexts"
O_D["j-store-order-domain"]
O_A["j-store-order-application"]
O_I["j-store-order-infrastructure"]
O_B["j-store-order-boot"]
G_D["j-store-goods-domain"]
G_A["j-store-goods-application"]
G_I["j-store-goods-infrastructure"]
G_B["j-store-goods-boot"]
P_D["j-store-payment-domain"]
P_A["j-store-payment-application"]
P_I["j-store-payment-infrastructure"]
P_B["j-store-payment-boot"]
F_D["j-store-fulfillment-domain"]
F_A["j-store-fulfillment-application"]
F_I["j-store-fulfillment-infrastructure"]
F_B["j-store-fulfillment-boot"]
A_D["j-store-accounting-domain"]
A_A["j-store-accounting-application"]
A_I["j-store-accounting-infrastructure"]
A_B["j-store-accounting-boot"]
U_D["j-store-user-domain"]
U_A["j-store-user-application"]
U_I["j-store-user-infrastructure"]
U_B["j-store-user-boot"]
end
CC --> O_D
CC --> G_D
CC --> P_D
CC --> F_D
CC --> A_D
CC --> U_D
IC --> O_A
IC --> P_A
IC --> F_A
IC --> A_A
O_D --> O_A --> O_I --> O_B
G_D --> G_A --> G_I --> G_B
P_D --> P_A --> P_I --> P_B
F_D --> F_A --> F_I --> F_B
A_D --> A_A --> A_I --> A_B
U_D --> U_A --> U_I --> U_B
```

**Diagram sources**
- [settings.gradle.kts:1-83](file://settings.gradle.kts#L1-L83)

**Section sources**
- [settings.gradle.kts:12-83](file://settings.gradle.kts#L12-L83)
- [build.gradle.kts:1-64](file://build.gradle.kts#L1-L64)

## Core Components
The foundation of the DDD implementation resides in common-core:
- AggregateRoot and Entity define identity and consistency boundaries.
- RecordsDomainEvents enables aggregates to record and acknowledge pending domain events.
- DomainEvent defines immutable facts with stable metadata for idempotency and tracing.
- DomainEventPublisher abstracts transactional outbox persistence for reliable event emission.
- OutboxEntry models durable outbox records with locking, retry, and routing metadata.
- AggregateRepository defines persistence ports for aggregates.

These components enforce strict separation between domain logic and infrastructure concerns, while providing robust eventing guarantees.

**Section sources**
- [AggregateRoot.kt:1-40](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/AggregateRoot.kt#L1-L40)
- [Entity.kt:1-6](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/Entity.kt#L1-L6)
- [DomainEvent.kt:1-46](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEvent.kt#L1-L46)
- [DomainEventPublisher.kt:1-11](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventPublisher.kt#L1-L11)
- [OutboxEntry.kt:1-86](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt#L1-L86)
- [AggregateRepository.kt:1-9](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/AggregateRepository.kt#L1-L9)

## Architecture Overview
J-Store follows a layered architecture per bounded context:
- Boot: Spring configuration, controllers, and wiring.
- Application: Use cases and orchestration; no business rules, delegates to domain.
- Domain: Aggregates, entities, value objects, domain services, and repository interfaces.
- Infrastructure: Repository implementations, persistence, external integrations, and event publishing.

Cross-context communication uses integration contracts (commands/events) published via outbox and consumed by handlers in other contexts.

```mermaid
graph TB
subgraph "Boot Layer"
OB["Order Boot"]
AB["Accounting Boot"]
PB["Payment Boot"]
FB["Fulfillment Boot"]
end
subgraph "Application Layer"
OA["Order Application Service"]
AA["Accounting Application Service"]
PA["Payment Application Service"]
FA["Fulfillment Application Service"]
end
subgraph "Domain Layer"
OD["Order Domain (Aggregates)"]
AD["Accounting Domain (Aggregates)"]
PD["Payment Domain (Aggregates)"]
FD["Fulfillment Domain (Aggregates)"]
end
subgraph "Infrastructure Layer"
OI["Order Infrastructure"]
AI["Accounting Infrastructure"]
PI["Payment Infrastructure"]
FI["Fulfillment Infrastructure"]
end
subgraph "Contracts"
IC["Integration Contracts"]
end
OB --> OA --> OD --> OI
AB --> AA --> AD --> AI
PB --> PA --> PD --> PI
FB --> FA --> FD --> FI
OA --> IC
AA --> IC
PA --> IC
FA --> IC
```

**Diagram sources**
- [OrderService.kt:1-186](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt#L1-L186)
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)
- [CommerceIntegrationMessages.kt:1-382](file://j-store-integration-contracts/src/main/kotlin/com/jstore/contracts/commerce/CommerceIntegrationMessages.kt#L1-L382)

## Detailed Component Analysis

### Order Bounded Context
- Aggregate: Order encapsulates trade, payment, fulfillment, and after-sale dimensions as parallel state facets.
- Application: OrderService orchestrates use cases, persists changes, and publishes pending domain events.
- Infrastructure: Repository implementations persist Order and related projections.

```mermaid
classDiagram
class Order {
+OrderId id
+MerchantId merchantId
+UserInfo buyerInfo
+OrderItem[] items
+RecipientInfo recipientInfo
+TradeStatus tradeStatus
+PaymentStatus paymentStatus
+FulfillmentStatus fulfillmentStatus
+Price refundedAmount
+RefundFact[] successfulRefundFacts
+OrderAmountSnapshot amountSnapshot
+Price paidAmount
+String? paymentReference
+String? fulfillmentReference
+LocalDateTime createTime
+LocalDateTime updateTime
+confirmStock() Result
+markStockInsufficient(reason) Result
+recordPaymentCaptured(paymentReference, capturedAmount, currency, occurredAt) Result
+recordFulfillmentPrepared(fulfillmentReference) Result
+recordShipmentDispatched(fulfillmentReference) Result
+recordShipmentDelivered(fulfillmentReference) Result
+complete() Result
+cancel(reason) Result
+refundEligibility() Result
+recordRefundSucceeded(refundId, afterSaleId, items, occurredAt) Result
}
```

**Diagram sources**
- [Order.kt:1-90](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt#L1-L90)

```mermaid
sequenceDiagram
participant Client as "Client"
participant OrderSvc as "OrderService"
participant Repo as "OrderRepository"
participant Pub as "DomainEventPublisher"
participant Outbox as "Outbox"
Client->>OrderSvc : createOrder(cmd)
OrderSvc->>Repo : add(order)
OrderSvc->>OrderSvc : publishPendingEvents(Pub)
OrderSvc->>Pub : publishEvent(event)
Pub->>Outbox : write OutboxEntry
Note over OrderSvc,Outbox : Event persisted in same DB transaction as order
```

**Diagram sources**
- [OrderService.kt:43-51](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt#L43-L51)
- [DomainEventPublisher.kt:1-11](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventPublisher.kt#L1-L11)
- [OutboxEntry.kt:1-86](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt#L1-L86)

**Section sources**
- [Order.kt:1-90](file://j-store-order-domain/src/main/kotlin/com/jstore/order/domain/order/Order.kt#L1-L90)
- [OrderService.kt:1-186](file://j-store-order-application/src/main/kotlin/com/jstore/order/service/OrderService.kt#L1-L186)

### Accounting Bounded Context
- Aggregate: JournalEntry models double-entry bookkeeping with lines, posting, and reversal semantics.
- Application: AccountingApplicationService composes journal entries based on commands, validates periods, and resolves ledger accounts.

```mermaid
classDiagram
class JournalEntry {
+JournalEntryId id
+String entryNo
+JournalEntryType type
+SourceDocument sourceDocument
+LocalDate accountingDate
+JournalEntryStatus status
+JournalLine[] lines
+Instant createdAt
+Instant? postedAt
+JournalEntryId? reversedBy
+JournalEntryId? reversalOf
+addLine(line) Result
+post(period) Result
+markReversed(reversalEntryId) Result
+createReversal(reversalEntryId, reversalEntryNo, accountingDate, reason) Result
}
class JournalLine {
+JournalLineId id
+LedgerAccountId accountId
+EntrySide side
+Price amount
+String memo
}
```

**Diagram sources**
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)

```mermaid
flowchart TD
Start(["Record Order Paid"]) --> CheckExisting["Check existing journal by source document"]
CheckExisting --> Exists{"Exists?"}
Exists --> |Yes| ReturnExisting["Return existing entry"]
Exists --> |No| OpenPeriod["Require open accounting period"]
OpenPeriod --> ResolveAccounts["Resolve clearing and payable accounts"]
ResolveAccounts --> BuildEntry["Build JournalEntry with lines"]
BuildEntry --> Post["Post entry to period"]
Post --> Save["Save journal entry"]
Save --> End(["Done"])
```

**Diagram sources**
- [AccountingApplicationService.kt:33-96](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L33-L96)

**Section sources**
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)

### Cross-Context Integration Contracts
Integration contracts define stable commands and events exchanged between contexts:
- Inventory commands: reserve, confirm, release, restore-after-refund.
- Payment commands/events: create-for-order, request-refund, captured, refund succeeded/failed.
- Fulfillment commands/events: create-for-order, prepared, dispatched, delivered.
- Order completion event triggers accounting.

```mermaid
sequenceDiagram
participant Order as "Order Context"
participant Contracts as "Integration Contracts"
participant Inventory as "Inventory Context"
participant Payment as "Payment Context"
participant Fulfillment as "Fulfillment Context"
participant Accounting as "Accounting Context"
Order->>Contracts : ReserveInventoryCommand
Contracts-->>Inventory : inventory.reserve
Inventory-->>Contracts : InventoryReservedIntegrationEvent
Contracts-->>Order : order.events
Order->>Contracts : CreatePaymentForOrderCommand
Contracts-->>Payment : payment.create-for-order
Payment-->>Contracts : PaymentCapturedIntegrationEvent
Contracts-->>Order : commerce.events
Order->>Contracts : CreateFulfillmentForOrderCommand
Contracts-->>Fulfillment : fulfillment.create-for-order
Fulfillment-->>Contracts : FulfillmentPreparedIntegrationEvent
Contracts-->>Order : order.events
Order-->>Contracts : OrderCompletedIntegrationEvent
Contracts-->>Accounting : accounting.events
```

**Diagram sources**
- [CommerceIntegrationMessages.kt:1-382](file://j-store-integration-contracts/src/main/kotlin/com/jstore/contracts/commerce/CommerceIntegrationMessages.kt#L1-L382)

**Section sources**
- [CommerceIntegrationMessages.kt:1-382](file://j-store-integration-contracts/src/main/kotlin/com/jstore/contracts/commerce/CommerceIntegrationMessages.kt#L1-L382)

### Outbox Pattern and Event Delivery
Outbox ensures reliable event publication within the same database transaction as business writes:
- Domain events are recorded by aggregates.
- Application layer publishes pending events via DomainEventPublisher.
- OutboxEntry captures message kind, delivery target, destination, partition key, correlation/causation IDs, and tenant context.
- Consumers pick up entries with locking and retry semantics.

```mermaid
flowchart TD
A["Aggregate raises DomainEvent"] --> B["Records pending events"]
B --> C["Application calls publishPendingEvents"]
C --> D["DomainEventPublisher.publishEvent"]
D --> E["Write OutboxEntry (same DB tx)"]
E --> F["Outbox worker picks IN_PROGRESS with lock"]
F --> G{"Delivery success?"}
G --> |Yes| H["Acknowledge events"]
G --> |No| I["Retry with backoff"]
```

**Diagram sources**
- [DomainEvent.kt:1-46](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEvent.kt#L1-L46)
- [DomainEventPublisher.kt:1-11](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventPublisher.kt#L1-L11)
- [OutboxEntry.kt:1-86](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt#L1-L86)

**Section sources**
- [DomainEventPublisher.kt:1-11](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/DomainEventPublisher.kt#L1-L11)
- [OutboxEntry.kt:1-86](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt#L1-L86)

### Data Model Evolution and Constraints
Schema migrations demonstrate evolving order state modeling and after-sale capabilities:
- Multi-dimensional order statuses enforced via constraints and indexes.
- After-sale tables include command receipts and refund facts for idempotency and auditability.

```mermaid
erDiagram
ORDERS {
bigint id PK
varchar trade_status
varchar payment_status
varchar fulfillment_status
varchar after_sale_status
timestamp create_time
timestamp update_time
}
AFTER_SALES {
bigint id PK
bigint order_id FK
bigint applicant_id
bigint merchant_id
varchar status
timestamp create_time
}
AFTER_SALE_ITEMS {
bigint id PK
bigint after_sale_id FK
bigint order_item_id
int quantity
}
ORDER_REFUND_FACTS {
bigint id PK
bigint order_id FK
bigint after_sale_id FK
bigint order_item_id
int quantity
numeric amount
timestamp occurred_at
}
ORDERS ||--o{ AFTER_SALES : "has"
AFTER_SALES ||--o{ AFTER_SALE_ITEMS : "contains"
ORDERS ||--o{ ORDER_REFUND_FACTS : "records"
```

**Diagram sources**
- [V20260731__order_status_dimensions.sql:1-33](file://j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql#L1-L33)
- [V20260803__order_after_sale_aggregate.sql:14-21](file://j-store-boot/src/main/resources/db/migration/V20260803__order_after_sale_aggregate.sql#L14-L21)

**Section sources**
- [V20260731__order_status_dimensions.sql:1-33](file://j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql#L1-L33)
- [V20260803__order_after_sale_aggregate.sql:14-21](file://j-store-boot/src/main/resources/db/migration/V20260803__order_after_sale_aggregate.sql#L14-L21)

## Dependency Analysis
Module dependencies enforce DDD boundaries:
- Domain modules depend only on common-core.
- Application modules depend on domain and common-core.
- Infrastructure modules implement repository interfaces from domain.
- Boot modules wire application and infrastructure.
- Integration contracts are shared across application modules.

```mermaid
graph LR
CC["common-core"] --> OD["order-domain"]
CC --> AD["accounting-domain"]
CC --> PD["payment-domain"]
CC --> FD["fulfillment-domain"]
OD --> OA["order-application"]
AD --> AA["accounting-application"]
PD --> PA["payment-application"]
FD --> FA["fulfillment-application"]
OA --> OI["order-infrastructure"]
AA --> AI["accounting-infrastructure"]
PA --> PI["payment-infrastructure"]
FA --> FI["fulfillment-infrastructure"]
OA --> IC["integration-contracts"]
AA --> IC
PA --> IC
FA --> IC
OI --> OB["order-boot"]
AI --> AB["accounting-boot"]
PI --> PB["payment-boot"]
FI --> FB["fulfillment-boot"]
```

**Diagram sources**
- [settings.gradle.kts:12-83](file://settings.gradle.kts#L12-L83)

**Section sources**
- [settings.gradle.kts:12-83](file://settings.gradle.kts#L12-L83)

## Performance Considerations
- Outbox locking and retries ensure eventual consistency without blocking domain transactions.
- Partition keys and correlation/causation IDs enable ordered processing and traceability.
- Multi-dimensional order statuses reduce complex joins and support efficient queries via targeted indexes.
- Idempotent consumers rely on stable message IDs and unique constraints (e.g., command receipts).

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
- Duplicate pending domain events are prevented by aggregate-level checks; verify event ID uniqueness when raising events.
- Outbox validation enforces consistent fields (message kind vs delivery target, lease completeness); inspect OutboxEntry construction errors.
- Accounting operations require open periods and valid ledger accounts; validate period availability and account codes before posting.
- Schema constraints (order statuses, after-sale facts) will reject invalid state transitions or inconsistent data; review migration definitions and constraint violations.

**Section sources**
- [AggregateRoot.kt:21-39](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/AggregateRoot.kt#L21-L39)
- [OutboxEntry.kt:35-72](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt#L35-L72)
- [AccountingApplicationService.kt:33-96](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L33-L96)
- [V20260731__order_status_dimensions.sql:1-33](file://j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql#L1-L33)

## Conclusion
J-Store’s DDD implementation leverages clear bounded contexts, strong aggregate boundaries, and an event-driven architecture backed by the outbox pattern. The layered design isolates concerns across boot, application, domain, and infrastructure, while integration contracts provide stable cross-context communication. Technical choices emphasize reliability, idempotency, and maintainability, enabling scalable evolution of each context independently.