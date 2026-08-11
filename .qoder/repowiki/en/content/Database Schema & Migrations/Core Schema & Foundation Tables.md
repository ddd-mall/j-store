# Core Schema & Foundation Tables

<cite>
**Referenced Files in This Document**
- [01-init.sql](file://docker/postgres/init/01-init.sql)
- [init_j_store_boot_schema.sql](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql)
- [V20260507__baseline_j_store_boot_schema.sql](file://j-store-boot/src/main/resources/db/migration/V20260507__baseline_j_store_boot_schema.sql)
- [V20260805__order_payment_fulfillment_boundaries.sql](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql)
- [V20260806__unified_account_merchant_membership.sql](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql)
- [V20260807__event_delivery_targets.sql](file://j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql)
- [UserAccountPO.kt](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/persistence/UserAccountPO.kt)
- [RedisTokenStore.kt](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/RedisTokenStore.kt)
- [MerchantPO.kt](file://j-store-shop-infrastructure/src/main/kotlin/com/jstore/shop/domain/merchant/persistence/MerchantPO.kt)
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
10. Appendices

## Introduction
This document describes the core foundation data model for J-Store, focusing on base schema tables that underpin user accounts, authentication tokens, merchant information, and system configuration. It details primary and foreign keys, indexes, constraints, validation rules enforced at the database level, entity relationships, data access patterns, performance considerations, and lifecycle/retention strategies for foundational entities.

## Project Structure
The schema is defined through:
- A development schema bootstrap script
- An idempotent full initialization script
- Versioned Flyway migrations that evolve the schema over time

```mermaid
graph TB
A["Schema Bootstrap<br/>01-init.sql"] --> B["Full Init Script<br/>init_j_store_boot_schema.sql"]
B --> C["Baseline Schema Migration<br/>V20260507__baseline_j_store_boot_schema.sql"]
C --> D["Order/Payment/Fulfillment Boundaries<br/>V20260805__order_payment_fulfillment_boundaries.sql"]
D --> E["Unified Account & Merchant Membership<br/>V20260806__unified_account_merchant_membership.sql"]
E --> F["Event Delivery Targets<br/>V20260807__event_delivery_targets.sql"]
```

**Diagram sources**
- [01-init.sql:1-5](file://docker/postgres/init/01-init.sql#L1-L5)
- [init_j_store_boot_schema.sql:1-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L1-L309)
- [V20260507__baseline_j_store_boot_schema.sql:1-318](file://j-store-boot/src/main/resources/db/migration/V20260507__baseline_j_store_boot_schema.sql#L1-L318)
- [V20260805__order_payment_fulfillment_boundaries.sql:1-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L1-L153)
- [V20260806__unified_account_merchant_membership.sql:1-48](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L1-L48)
- [V20260807__event_delivery_targets.sql:1-28](file://j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql#L1-L28)

**Section sources**
- [01-init.sql:1-5](file://docker/postgres/init/01-init.sql#L1-L5)
- [init_j_store_boot_schema.sql:1-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L1-L309)
- [V20260507__baseline_j_store_boot_schema.sql:1-318](file://j-store-boot/src/main/resources/db/migration/V20260507__baseline_j_store_boot_schema.sql#L1-L318)

## Core Components
This section outlines the core foundation tables and their roles:
- User Accounts: identity and authentication baseline
- Merchants and Memberships: multi-tenant ownership and role-based membership
- Orders, Items, After-sales: commerce core with payment and fulfillment boundaries
- Accounting: ledger, journal entries, periods, settlement statements
- Outbox and Event Consumption: reliable event delivery and idempotency
- Timer Jobs: background job scheduling and dead-letter handling

Key characteristics:
- Primary keys are either BIGSERIAL or BIGINT (for external IDs)
- Foreign keys enforce referential integrity across aggregates
- CHECK constraints enforce business rules at the database level
- Indexes optimize frequent queries by status, timestamps, and tenant/merchant scoping

**Section sources**
- [init_j_store_boot_schema.sql:13-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L13-L309)
- [V20260805__order_payment_fulfillment_boundaries.sql:14-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L14-L153)
- [V20260806__unified_account_merchant_membership.sql:1-48](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L1-L48)

## Architecture Overview
The foundation schema supports a modular architecture where each domain (user, shop, order, payment, fulfillment, accounting) persists its state in dedicated tables while sharing common infrastructure tables (outbox, timer jobs).

```mermaid
graph TB
subgraph "Identity & Access"
UA["user_accounts"]
M["merchants"]
MM["merchant_memberships"]
MR["merchant_membership_roles"]
end
subgraph "Commerce"
O["orders"]
OI["order_items"]
AS["after_sales"]
ORF["order_refund_facts"]
end
subgraph "Payment"
PO["payment_orders"]
PR["payment_refunds"]
PRI["payment_refund_items"]
end
subgraph "Fulfillment"
FO["fulfillment_orders"]
FI["fulfillment_items"]
end
subgraph "Accounting"
ALA["accounting_ledger_account"]
AJE["accounting_journal_entry"]
AJL["accounting_journal_line"]
AP["accounting_period"]
ASS["accounting_settlement_statement"]
ASL["accounting_settlement_line"]
end
subgraph "Infrastructure"
OE["outbox_entry"]
DEC["domain_event_consumption"]
TJ["timer_job"]
HTJ["handled_timer_job"]
TDQ["timer_job_dead_queue"]
end
UA --> O
M --> O
M --> PO
M --> FO
O --> OI
O --> AS
O --> ORF
PO --> PR
PR --> PRI
FO --> FI
AJE --> AJL
ASS --> ASL
```

**Diagram sources**
- [init_j_store_boot_schema.sql:13-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L13-L309)
- [V20260805__order_payment_fulfillment_boundaries.sql:14-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L14-L153)
- [V20260806__unified_account_merchant_membership.sql:1-48](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L1-L48)

## Detailed Component Analysis

### User Accounts
- Purpose: Stores user identity and authentication baseline
- Key fields: id (PK), phone_number (unique), nickname, password_hash, status, create_time, update_time
- Constraints: Unique phone number; status enum enforced via application layer; timestamps default to now
- Persistence mapping: JPA entity maps directly to table columns

```mermaid
classDiagram
class UserAccountPO {
+Long id
+String phoneNumber
+String nickname
+String passwordHash
+String status
+LocalDateTime createTime
+LocalDateTime updateTime
}
```

**Diagram sources**
- [UserAccountPO.kt:14-29](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/persistence/UserAccountPO.kt#L14-L29)

**Section sources**
- [init_j_store_boot_schema.sql:13-22](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L13-L22)
- [UserAccountPO.kt:14-29](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/persistence/UserAccountPO.kt#L14-L29)

### Authentication Tokens
- Storage: Redis-backed token store for refresh tokens and access token blacklist
- Keys:
  - refresh_token:{userId} -> refreshToken with TTL
  - token_blacklist:{jti} -> marker with TTL
- Operations: store, get, remove refresh tokens; blacklist and check access tokens

```mermaid
flowchart TD
Start(["Login Flow"]) --> StoreRefresh["Store Refresh Token in Redis"]
StoreRefresh --> IssueAccessToken["Issue Access Token"]
IssueAccessToken --> CheckBlacklist{"Access Token Blacklisted?"}
CheckBlacklist --> |Yes| Deny["Deny Request"]
CheckBlacklist --> |No| Proceed["Proceed with Request"]
Proceed --> End(["Response"])
Deny --> End
```

**Diagram sources**
- [RedisTokenStore.kt:12-43](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/RedisTokenStore.kt#L12-L43)

**Section sources**
- [RedisTokenStore.kt:12-43](file://j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/RedisTokenStore.kt#L12-L43)

### Merchants and Memberships
- Merchants: id (PK), name, status (ACTIVE/DISABLED), timestamps
- Merchant Memberships: links users to merchants with unique (merchant_id, user_id); status ACTIVE/DISABLED; positive user_id constraint
- Roles: composite key (membership_id, role) with allowed roles enumerated
- Integration: FKs added to spu, spu_snapshot, orders, after_sales, payment_orders, fulfillment_orders to link to merchants

```mermaid
erDiagram
MERCHANTS {
bigint id PK
varchar name
varchar status
timestamp create_time
timestamp update_time
}
MERCHANT_MEMBERSHIPS {
bigint id PK
bigint merchant_id FK
bigint user_id
varchar status
timestamp create_time
timestamp update_time
}
MERCHANT_MEMBERSHIP_ROLES {
bigint membership_id FK
varchar role
}
MERCHANTS ||--o{ MERCHANT_MEMBERSHIPS : "has members"
MERCHANT_MEMBERSHIPS ||--o{ MERCHANT_MEMBERSHIP_ROLES : "has roles"
```

**Diagram sources**
- [V20260806__unified_account_merchant_membership.sql:3-34](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L3-L34)

**Section sources**
- [V20260806__unified_account_merchant_membership.sql:3-34](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L3-L34)
- [MerchantPO.kt:12-24](file://j-store-shop-infrastructure/src/main/kotlin/com/jstore/shop/domain/merchant/persistence/MerchantPO.kt#L12-L24)

### Orders, Items, and After-sales
- Orders: buyer info, recipient_info JSONB, status tracking, amount composition fields, references to payment and fulfillment
- Order Items: snapshot of goods at order time, quantity, unit price, status
- After-sales: refund lifecycle with statuses and reasons
- Refund Facts: per-order-item refund records with uniqueness constraints

```mermaid
erDiagram
ORDERS {
bigint id PK
bigint buyer_uid
varchar buyer_phone
varchar buyer_name
jsonb recipient_info
varchar status
varchar previous_status
numeric items_subtotal
numeric discount_amount
numeric shipping_amount
numeric tax_amount
numeric payable_amount
numeric paid_amount
numeric refunded_amount
varchar payment_reference
varchar fulfillment_reference
timestamp create_time
timestamp update_time
}
ORDER_ITEMS {
bigint id PK
bigint order_id FK
bigint sku_id
bigint spu_id
varchar goods_name
varchar sku_description
int quantity
numeric unit_price
bigint snapshot_version
varchar status
varchar previous_item_status
}
AFTER_SALES {
bigint id PK
bigint order_id FK
varchar status
timestamp return_received_at
varchar refund_id
varchar refund_failure_reason
}
ORDER_REFUND_FACTS {
bigint id PK
bigint order_id FK
varchar refund_id
bigint after_sale_id
bigint order_item_id
int quantity
numeric amount
timestamptz occurred_at
}
ORDERS ||--o{ ORDER_ITEMS : "contains"
ORDERS ||--o{ AFTER_SALES : "generates"
ORDERS ||--o{ ORDER_REFUND_FACTS : "records refunds"
```

**Diagram sources**
- [init_j_store_boot_schema.sql:82-116](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L82-L116)
- [V20260805__order_payment_fulfillment_boundaries.sql:20-76](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L20-L76)

**Section sources**
- [init_j_store_boot_schema.sql:82-116](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L82-L116)
- [V20260805__order_payment_fulfillment_boundaries.sql:20-76](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L20-L76)

### Payment and Fulfillment Boundaries
- Payment Orders: one-to-one with orders; captures provider transaction ids and amounts; strict status transitions enforced via CHECK
- Payment Refunds: linked to after-sales; provider refund ids; status transitions
- Fulfillment Orders: one-to-one with orders; address and carrier/tracking fields; status-dependent nullability enforced via CHECK

```mermaid
erDiagram
PAYMENT_ORDERS {
bigint id PK
bigint order_id UK
bigint merchant_id FK
numeric payable_amount
varchar currency
varchar status
varchar provider_transaction_id UK
numeric captured_amount
timestamptz captured_at
bigint version
}
PAYMENT_REFUNDS {
bigint id PK
bigint payment_order_id FK
bigint after_sale_id UK
numeric amount
varchar status
varchar provider_refund_id UK
varchar failure_reason
timestamptz requested_at
timestamptz completed_at
}
PAYMENT_REFUND_ITEMS {
varchar id PK
bigint payment_refund_id FK
bigint order_item_id
bigint sku_id
int quantity
numeric amount
}
FULFILLMENT_ORDERS {
bigint id PK
bigint order_id UK
bigint merchant_id FK
varchar status
varchar recipient_name
varchar recipient_phone
varchar recipient_email
varchar country_code
varchar district_code
varchar detail_address
varchar carrier_code
varchar tracking_number
bigint version
}
FULFILLMENT_ITEMS {
bigint id PK
bigint fulfillment_order_id FK
bigint order_item_id
bigint sku_id
int quantity
}
PAYMENT_ORDERS ||--o{ PAYMENT_REFUNDS : "has refunds"
PAYMENT_REFUNDS ||--o{ PAYMENT_REFUND_ITEMS : "details"
FULFILLMENT_ORDERS ||--o{ FULFILLMENT_ITEMS : "contains"
```

**Diagram sources**
- [V20260805__order_payment_fulfillment_boundaries.sql:79-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L79-L153)

**Section sources**
- [V20260805__order_payment_fulfillment_boundaries.sql:79-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L79-L153)

### Accounting Foundation
- Ledger Accounts: code/name/type/direction/subject linkage; unique code+subject
- Journal Entries: unique entry_no; source deduplication; reversal support
- Journal Lines: positive amount constraint; FK to entry and account
- Periods: period_code unique; closed state tracking
- Settlement Statements: merchant+period uniqueness; payable amounts and lifecycle timestamps
- Settlement Lines: per-statement breakdown with FK to statement

```mermaid
erDiagram
ACCOUNTING_LEDGER_ACCOUNT {
bigint id PK
varchar code
varchar name
varchar account_type
varchar balance_direction
varchar subject_type
varchar subject_id
varchar status
timestamp created_at
timestamp updated_at
}
ACCOUNTING_JOURNAL_ENTRY {
bigint id PK
varchar entry_no UK
varchar entry_type
varchar source_type
varchar source_id
varchar source_event_type
date accounting_date
varchar status
bigint reversed_by
bigint reversal_of
timestamp created_at
timestamp posted_at
}
ACCOUNTING_JOURNAL_LINE {
bigint id PK
bigint entry_id FK
bigint account_id FK
varchar side
bigint amount_fen
varchar memo
}
ACCOUNTING_PERIOD {
bigint id PK
varchar period_code UK
date start_date
date end_date
varchar status
timestamp closed_at
varchar closed_by
}
ACCOUNTING_SETTLEMENT_STATEMENT {
bigint id PK
varchar statement_no UK
varchar merchant_id
date period_start
date period_end
varchar status
bigint payable_amount_fen
timestamp confirmed_at
timestamp paid_at
timestamp created_at
}
ACCOUNTING_SETTLEMENT_LINE {
bigint id PK
bigint statement_id FK
varchar order_id
bigint gross_amount_fen
bigint refund_amount_fen
bigint commission_amount_fen
bigint net_amount_fen
}
ACCOUNTING_JOURNAL_ENTRY ||--o{ ACCOUNTING_JOURNAL_LINE : "has lines"
ACCOUNTING_LEDGER_ACCOUNT ||--o{ ACCOUNTING_JOURNAL_LINE : "referenced by"
ACCOUNTING_SETTLEMENT_STATEMENT ||--o{ ACCOUNTING_SETTLEMENT_LINE : "contains"
```

**Diagram sources**
- [init_j_store_boot_schema.sql:232-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L232-L309)

**Section sources**
- [init_j_store_boot_schema.sql:232-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L232-L309)

### Outbox and Event Consumption
- Outbox Entry: reliable event persistence with status-driven indexing; enriched message metadata (kind, target, destination, partition_key, correlation/causation, tenant)
- Domain Event Consumption: listener-level idempotency keyed by listener_id and event_id

```mermaid
sequenceDiagram
participant App as "Application"
participant DB as "Database"
participant Outbox as "OutboxEntry"
participant Consumer as "EventConsumer"
App->>DB : Begin Transaction
App->>DB : Insert OutboxEntry(status=PENDING)
App->>DB : Commit
Note over App,DB : Event persisted atomically with business transaction
Consumer->>DB : Select next eligible rows (status, next_attempt_at)
DB-->>Consumer : Rows locked for processing
Consumer->>DB : Update status=IN_PROGRESS, set lock fields
Consumer->>Consumer : Deliver message
alt Success
Consumer->>DB : Update status=PUBLISHED, updated_at
else Failure
Consumer->>DB : Update status=FAILED, retry_count++, last_error
end
Consumer->>DB : Commit
```

**Diagram sources**
- [init_j_store_boot_schema.sql:121-183](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L121-L183)
- [V20260807__event_delivery_targets.sql:1-28](file://j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql#L1-L28)

**Section sources**
- [init_j_store_boot_schema.sql:121-183](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L121-L183)
- [V20260807__event_delivery_targets.sql:1-28](file://j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql#L1-L28)

### Timer Jobs
- Active Jobs: topic/content/status/execute_time; indexed by execute_time and status
- Handled Jobs: archived processed jobs with remind_ttl and execute_time
- Dead Queue: failed jobs with dead_time; prevents reprocessing loops

```mermaid
flowchart TD
Start(["Job Scheduler"]) --> Pick["Pick Next Job by execute_time,status"]
Pick --> Execute["Execute Handler"]
Execute --> Success{"Success?"}
Success --> |Yes| Archive["Move to handled_timer_job"]
Success --> |No| Retry{"Retry Limit Reached?"}
Retry --> |No| Reschedule["Update next attempt"]
Retry --> |Yes| Dead["Move to timer_job_dead_queue"]
Archive --> End(["Done"])
Reschedule --> End
Dead --> End
```

**Diagram sources**
- [init_j_store_boot_schema.sql:188-227](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L188-L227)

**Section sources**
- [init_j_store_boot_schema.sql:188-227](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L188-L227)

## Dependency Analysis
Core dependencies and relationships:
- user_accounts referenced by orders.buyer_uid
- merchants referenced by spu, spu_snapshot, orders, after_sales, payment_orders, fulfillment_orders
- orders referenced by order_items, after_sales, order_refund_facts, payment_orders, fulfillment_orders
- payment_orders referenced by payment_refunds
- payment_refunds referenced by payment_refund_items
- accounting_journal_entry referenced by accounting_journal_line
- accounting_settlement_statement referenced by accounting_settlement_line

```mermaid
graph LR
UA["user_accounts"] --> O["orders"]
M["merchants"] --> SPU["spu"]
M --> SPU_SNAP["spu_snapshot"]
M --> O
M --> PO["payment_orders"]
M --> FO["fulfillment_orders"]
O --> OI["order_items"]
O --> AS["after_sales"]
O --> ORF["order_refund_facts"]
PO --> PR["payment_refunds"]
PR --> PRI["payment_refund_items"]
FO --> FI["fulfillment_items"]
AJE["accounting_journal_entry"] --> AJL["accounting_journal_line"]
ASS["accounting_settlement_statement"] --> ASL["accounting_settlement_line"]
```

**Diagram sources**
- [init_j_store_boot_schema.sql:82-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L82-L309)
- [V20260805__order_payment_fulfillment_boundaries.sql:14-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L14-L153)
- [V20260806__unified_account_merchant_membership.sql:36-48](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L36-L48)

**Section sources**
- [init_j_store_boot_schema.sql:82-309](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L82-L309)
- [V20260805__order_payment_fulfillment_boundaries.sql:14-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L14-L153)
- [V20260806__unified_account_merchant_membership.sql:36-48](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L36-L48)

## Performance Considerations
- Indexing strategy:
  - Status+time composite indexes for high-throughput scans (orders.status+create_time, outbox_entry.status+created_at)
  - GIN index on JSONB recipient_info for fast address queries
  - Merchant-scoped indexes (merchant_id+status, merchant_id+create_time) for multi-tenant filtering
  - Conditional indexes for outbox claim and cleanup paths
- Locking and contention:
  - Outbox uses row-level locking fields (locked_by, locked_until) to avoid duplicate processing
  - Idempotency via unique constraints (e.g., payment_orders.order_id, payment_refunds.after_sale_id)
- Data volume management:
  - Outbox and timer job histories should be partitioned or archived periodically
  - Use conditional indexes to reduce bloat on large tables

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and mitigations:
- Duplicate events: Ensure outbox_entry.event_id uniqueness checks and consumer idempotency via domain_event_consumption
- Stuck outbox entries: Inspect status IN ('PENDING','FAILED','IN_PROGRESS') and lock_expired index; clear stale locks if necessary
- Payment state inconsistencies: Validate CHECK constraints on payment_orders and fulfillment_orders; verify provider_transaction_id and captured_amount consistency
- Merchant membership conflicts: Enforce unique (merchant_id, user_id) and role enumeration; audit membership roles for unauthorized access

**Section sources**
- [init_j_store_boot_schema.sql:121-183](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L121-L183)
- [V20260805__order_payment_fulfillment_boundaries.sql:79-153](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L79-L153)
- [V20260806__unified_account_merchant_membership.sql:12-34](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L12-L34)

## Conclusion
The J-Store foundation schema establishes a robust, constraint-rich data model supporting user identity, merchant multi-tenancy, order lifecycle, payment and fulfillment boundaries, accounting integrity, and reliable event delivery. Carefully designed indexes and CHECK constraints ensure performance and correctness at scale. Lifecycle policies for outbox and timer jobs, along with idempotency mechanisms, provide resilience against failures and duplicates.

[No sources needed since this section summarizes without analyzing specific files]

## Appendices

### Data Validation Rules and Business Constraints
- Amount composition and non-negativity enforced via CHECK constraints on orders
- Payment and fulfillment state transitions validated by CHECK constraints
- Positive amount constraints on journal lines and refund items
- Role enumeration and membership status enforcement via CHECK constraints

**Section sources**
- [V20260805__order_payment_fulfillment_boundaries.sql:39-50](file://j-store-boot/src/main/resources/db/migration/V20260805__order_payment_fulfillment_boundaries.sql#L39-L50)
- [init_j_store_boot_schema.sql:269-272](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L269-L272)
- [V20260806__unified_account_merchant_membership.sql:19-33](file://j-store-boot/src/main/resources/db/migration/V20260806__unified_account_merchant_membership.sql#L19-L33)

### Data Lifecycle Policies and Retention Strategies
- Outbox entries: transition from PENDING to IN_PROGRESS to PUBLISHED/FAILED; archive published entries periodically using conditional indexes
- Domain event consumption: retain consumed markers for idempotency; consider retention windows per listener
- Timer jobs: move handled jobs to handled_timer_job; route persistent failures to timer_job_dead_queue for manual inspection
- Accounting periods: close periods and prevent further postings post-close; settle statements with lifecycle timestamps

**Section sources**
- [init_j_store_boot_schema.sql:121-183](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L121-L183)
- [V20260807__event_delivery_targets.sql:1-28](file://j-store-boot/src/main/resources/db/migration/V20260807__event_delivery_targets.sql#L1-L28)
- [init_j_store_boot_schema.sql:188-227](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L188-L227)
- [init_j_store_boot_schema.sql:274-296](file://j-store-boot/src/main/resources/db/init/init_j_store_boot_schema.sql#L274-L296)