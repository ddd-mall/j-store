# Payment & Accounting Schema

<cite>
**Referenced Files in This Document**
- [LedgerAccount.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt)
- [JournalEntry.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt)
- [AccountingPeriod.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt)
- [SettlementStatement.kt](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt)
- [PaymentOrder.kt](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt)
- [PaymentOrderImpl.kt](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrderImpl.kt)
- [AccountingApplicationService.kt](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt)
- [LedgerAccountPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt)
- [JournalEntryPO.kt](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt)
- [PaymentOrderPO.kt](file://j-store-payment-infrastructure/src/main/kotlin/com/jstore/payment/domain/payment/persistence/PaymentOrderPO.kt)
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
10. Appendices

## Introduction
This document provides a comprehensive data model and process documentation for payment processing and accounting within the system. It covers:
- Payment order lifecycle including capture, refund, and reversal operations
- Double-entry bookkeeping with journal entries, ledger accounts, and settlement statements
- Merchant membership and account structures for unified account management
- Financial transaction auditing and reconciliation mechanisms
- Currency handling, exchange rate storage, and multi-currency support
- Compliance requirements for financial data retention and audit trails

The content is derived from domain models, application services, infrastructure persistence entities, and database migrations present in the repository.

## Project Structure
The relevant code spans three primary modules:
- Accounting Domain: defines ledger accounts, journal entries, accounting periods, and settlement statements
- Payment Domain: defines payment orders, captures, refunds, and their state transitions
- Application Layer: orchestrates accounting events into double-entry journal entries
- Infrastructure: JPA entities mapping to persistent tables for accounting and payments
- Database Migrations: define schema for orders, after-sales, and related projections

```mermaid
graph TB
subgraph "Accounting Domain"
A_Ledger["LedgerAccount"]
A_Journal["JournalEntry"]
A_Period["AccountingPeriod"]
A_Settle["SettlementStatement"]
end
subgraph "Payment Domain"
P_Order["PaymentOrder"]
end
subgraph "Application Service"
S_App["AccountingApplicationService"]
end
subgraph "Infrastructure (Persistence)"
I_Account["LedgerAccountPO"]
I_Journal["JournalEntryPO"]
I_Payment["PaymentOrderPO"]
end
subgraph "Database"
DB_Account["accounting_ledger_account"]
DB_Journal["accounting_journal_entry / accounting_journal_line"]
DB_Payment["payment_orders / payment_refunds / payment_refund_items"]
end
P_Order --> S_App
S_App --> A_Journal
S_App --> A_Period
S_App --> A_Ledger
A_Journal --> I_Journal
A_Ledger --> I_Account
I_Journal --> DB_Journal
I_Account --> DB_Account
P_Order --> I_Payment
I_Payment --> DB_Payment
```

**Diagram sources**
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)
- [SettlementStatement.kt:1-62](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt#L1-L62)
- [PaymentOrder.kt:1-94](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt#L1-L94)
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)
- [LedgerAccountPO.kt:1-47](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L1-L47)
- [JournalEntryPO.kt:1-69](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L1-L69)
- [PaymentOrderPO.kt:1-73](file://j-store-payment-infrastructure/src/main/kotlin/com/jstore/payment/domain/payment/persistence/PaymentOrderPO.kt#L1-L73)

**Section sources**
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)
- [SettlementStatement.kt:1-62](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt#L1-L62)
- [PaymentOrder.kt:1-94](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt#L1-L94)
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)
- [LedgerAccountPO.kt:1-47](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L1-L47)
- [JournalEntryPO.kt:1-69](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L1-L69)
- [PaymentOrderPO.kt:1-73](file://j-store-payment-infrastructure/src/main/kotlin/com/jstore/payment/domain/payment/persistence/PaymentOrderPO.kt#L1-L73)

## Core Components
- Ledger Account: Represents an accounting subject (platform, merchant, user, channel) with type (asset, liability, equity, revenue, expense), balance direction (debit/credit), and status (active/inactive). Supports activation/deactivation.
- Journal Entry: Double-entry record with lines (debit/credit per account), source document linkage, accounting date, status (draft/posted/reversed), and reversal relationships.
- Accounting Period: Time window for posting entries; supports open/close/reopen controls and containment checks.
- Settlement Statement: Aggregates per-order gross/refund/commission/net amounts over a period; supports confirm and mark paid states.
- Payment Order: Tracks payable amount, currency, capture details, and multiple refunds with statuses; enforces business rules on capture and refund flows.

Key behaviors:
- Capture transitions payment order to captured and records provider transaction details.
- Refund requests are validated against remaining payable; success marks refund succeeded or failed; order status updates accordingly.
- Accounting service translates business events into journal entries ensuring balanced debits and credits, period validation, and idempotency via source documents.

**Section sources**
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)
- [SettlementStatement.kt:1-62](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt#L1-L62)
- [PaymentOrder.kt:1-94](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt#L1-L94)
- [PaymentOrderImpl.kt:1-192](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrderImpl.kt#L1-L192)
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)

## Architecture Overview
The architecture follows a clear separation between domain, application, and infrastructure layers. Business events from payment operations drive accounting journal creation through the application service, which validates periods, resolves ledger accounts by code and subject, and persists double-entry records. Settlement statements summarize merchant payables and track confirmation and payment.

```mermaid
sequenceDiagram
participant Pay as "PaymentDomain.PaymentOrder"
participant App as "AccountingApplicationService"
participant Per as "AccountingPeriodRepository"
participant Acc as "LedgerAccountRepository"
participant Jou as "JournalEntryRepository"
Pay->>App : "recordOrderPaid(sourceDocument, accountingDate, paidAmount, merchantId)"
App->>Per : "requireOpenPeriod(accountingDate)"
Per-->>App : "AccountingPeriod"
App->>Acc : "findByCodeAndSubject('1010', CHANNEL, DEFAULT)"
Acc-->>App : "Clearing Account"
App->>Acc : "findByCodeAndSubject('2101', MERCHANT, merchantId)"
Acc-->>App : "Payable Account"
App->>App : "Create JournalEntry + Debit Clearing / Credit Payable"
App->>Per : "post(period)"
App->>Jou : "save(entry)"
Jou-->>App : "Saved JournalEntry"
App-->>Pay : "Success"
```

**Diagram sources**
- [AccountingApplicationService.kt:33-96](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L33-L96)
- [JournalEntry.kt:54-79](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L54-L79)
- [AccountingPeriod.kt:18-32](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L18-L32)
- [LedgerAccount.kt:51-63](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L51-L63)

**Section sources**
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)

## Detailed Component Analysis

### Payment Order Lifecycle
The payment order aggregate manages capture and refund operations with strict state transitions and validations:
- Capture: Validates pending state, provider transaction ID, currency, and amount; records capture details and emits a captured event.
- Refund Request: Ensures order is captured or partially refunded; validates total requested refund does not exceed payable; publishes refund requested event.
- Refund Retry: Allows retry when previous attempt failed; resets failure metadata and re-publishes request.
- Refund Success/Failure: Updates refund status, provider reference, timestamps; adjusts overall order status to partially refunded or fully refunded; emits appropriate events.

```mermaid
stateDiagram-v2
[*] --> Pending
Pending --> Captured : "capture(providerTxId, amount, currency)"
Captured --> PartiallyRefunded : "refund succeeded (partial)"
Captured --> Refunded : "refund succeeded (full)"
PartiallyRefunded --> Refunded : "refund succeeded (remaining)"
Captured --> Captured : "retryRefund(refundId)"
Captured --> Captured : "markRefundFailed(reason)"
PartiallyRefunded --> PartiallyRefunded : "retryRefund(refundId)"
PartiallyRefunded --> PartiallyRefunded : "markRefundFailed(reason)"
```

**Diagram sources**
- [PaymentOrder.kt:15-26](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt#L15-L26)
- [PaymentOrderImpl.kt:39-176](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrderImpl.kt#L39-L176)

**Section sources**
- [PaymentOrder.kt:1-94](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrder.kt#L1-L94)
- [PaymentOrderImpl.kt:1-192](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrderImpl.kt#L1-L192)

### Double-Entry Bookkeeping: Journal Entries and Ledger Accounts
Journal entries represent balanced debit/credit transactions linked to source documents (orders, refunds, settlements, adjustments). Each line references a ledger account and specifies side and amount. The accounting period must be open for posting. Reversals create new entries referencing original entries.

```mermaid
classDiagram
class LedgerAccount {
+id : LedgerAccountId
+code : LedgerAccountCode
+name : String
+type : LedgerAccountType
+direction : BalanceDirection
+subject : AccountingSubject
+status : LedgerAccountStatus
+activate() Result
+deactivate() Result
}
class JournalEntry {
+id : JournalEntryId
+entryNo : String
+type : JournalEntryType
+sourceDocument : SourceDocument
+accountingDate : LocalDate
+status : JournalEntryStatus
+lines : JournalLine[]
+createdAt : Instant
+postedAt : Instant?
+reversedBy : JournalEntryId?
+reversalOf : JournalEntryId?
+addLine(line) Result
+post(period) Result
+markReversed(id) Result
+createReversal(...) Result
}
class JournalLine {
+id : JournalLineId
+accountId : LedgerAccountId
+side : EntrySide
+amount : Price
+memo : String
}
class AccountingPeriod {
+id : AccountingPeriodId
+periodCode : String
+startDate : LocalDate
+endDate : LocalDate
+status : PeriodStatus
+contains(date) Boolean
+close(closedBy) Result
+reopen(reason) Result
}
JournalEntry --> JournalLine : "has many"
JournalEntry --> LedgerAccount : "references via lines"
JournalEntry --> AccountingPeriod : "posts within"
```

**Diagram sources**
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)

**Section sources**
- [JournalEntry.kt:1-93](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L1-L93)
- [LedgerAccount.kt:1-64](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/account/LedgerAccount.kt#L1-L64)
- [AccountingPeriod.kt:1-33](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L1-L33)

### Settlement Statements
Settlement statements aggregate per-order financials over a defined period, tracking gross, refund, commission, and net amounts. They progress through draft, confirmed, and paid states, enabling reconciliation and payout processes.

```mermaid
flowchart TD
Start(["Start"]) --> AddLines["Add Settlement Lines<br/>per order"]
AddLines --> ComputeNet["Compute Net Amount<br/>Gross - Refund - Commission"]
ComputeNet --> Confirm{"Confirm Statement?"}
Confirm --> |Yes| MarkConfirmed["Mark Confirmed"]
Confirm --> |No| End(["End"])
MarkConfirmed --> PaidCheck{"Paid?"}
PaidCheck --> |Yes| MarkPaid["Mark Paid with timestamp"]
PaidCheck --> |No| End
```

**Diagram sources**
- [SettlementStatement.kt:1-62](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt#L1-L62)

**Section sources**
- [SettlementStatement.kt:1-62](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/settlement/SettlementStatement.kt#L1-L62)

### Accounting Application Service Flows
The application service implements key accounting workflows:
- Record Order Paid: Creates a journal entry debiting clearing account and crediting merchant payable; ensures open period and idempotency via source document lookup.
- Record Order Completed: Recognizes platform commission by debiting merchant payable and crediting platform revenue.
- Record Order Refund Approved: Generates a reversal entry linking to original posted entry; debits merchant payable and credits clearing.
- Record Settlement Paid: Records bank payment by debiting merchant payable and crediting bank account.

```mermaid
sequenceDiagram
participant Client as "Caller"
participant App as "AccountingApplicationService"
participant Repo as "JournalEntryRepository"
participant Period as "AccountingPeriodRepository"
participant AccRepo as "LedgerAccountRepository"
Client->>App : "recordOrderRefundApproved(cmd)"
App->>Repo : "findBySourceDocument(original)"
Repo-->>App : "Original Entry"
App->>Period : "requireOpenPeriod(accountingDate)"
Period-->>App : "Open Period"
App->>AccRepo : "resolve payable/clearing accounts"
AccRepo-->>App : "Accounts"
App->>App : "Create reversal entry + lines"
App->>Period : "post(period)"
App->>Repo : "save(reversal)"
Repo-->>App : "Saved"
App-->>Client : "Result"
```

**Diagram sources**
- [AccountingApplicationService.kt:168-247](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L168-L247)
- [JournalEntry.kt:67-79](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/JournalEntry.kt#L67-L79)
- [AccountingPeriod.kt:27-32](file://j-store-accounting-domain/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingPeriod.kt#L27-L32)

**Section sources**
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)

### Persistence Models and Schema
Persistence entities map domain concepts to relational tables:
- LedgerAccountPO: Stores account code, name, type, balance direction, subject type/id, status, timestamps; unique constraint on code+subject.
- JournalEntryPO and JournalLinePO: Store entry metadata, source document linkage, status, reversal links, and lines with account, side, amount, memo; eager loading of lines.
- PaymentOrderPO, PaymentRefundPO, PaymentRefundItemPO: Store payment order details, refunds, and itemized refund components; versioning for concurrency control.

Database migrations include:
- Order status dimensions: Adds trade, payment, fulfillment, after-sale statuses with constraints and indexes.
- After-sale aggregate: Defines after_sales, after_sale_items, after_sale_capacities, order_refund_facts, and related constraints/indexes.

**Section sources**
- [LedgerAccountPO.kt:1-47](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L1-L47)
- [JournalEntryPO.kt:1-69](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L1-L69)
- [PaymentOrderPO.kt:1-73](file://j-store-payment-infrastructure/src/main/kotlin/com/jstore/payment/domain/payment/persistence/PaymentOrderPO.kt#L1-L73)
- [V20260731__order_status_dimensions.sql:1-33](file://j-store-boot/src/main/resources/db/migration/V20260731__order_status_dimensions.sql#L1-L33)
- [V20260803__order_after_sale_aggregate.sql:1-21](file://j-store-boot/src/main/resources/db/migration/V20260803__order_after_sale_aggregate.sql#L1-L21)

## Dependency Analysis
The accounting subsystem depends on:
- Repositories for ledger accounts, journal entries, and accounting periods
- Application commands for recording order paid, completed, refund approved, and settlement paid
- Domain aggregates enforcing business rules and state transitions
- Infrastructure repositories implementing persistence via JPA

```mermaid
graph LR
AppSvc["AccountingApplicationService"] --> RepoJE["JournalEntryRepository"]
AppSvc --> RepoAcc["LedgerAccountRepository"]
AppSvc --> RepoPer["AccountingPeriodRepository"]
RepoJE --> PO_JE["JournalEntryPO"]
RepoAcc --> PO_Acc["LedgerAccountPO"]
RepoPer --> PO_Per["AccountingPeriodPO"]
PayOrder["PaymentOrder"] --> Events["Domain Events"]
Events --> AppSvc
```

**Diagram sources**
- [AccountingApplicationService.kt:25-29](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L25-L29)
- [JournalEntryPO.kt:1-69](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L1-L69)
- [LedgerAccountPO.kt:1-47](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L1-L47)

**Section sources**
- [AccountingApplicationService.kt:1-337](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L1-L337)
- [JournalEntryPO.kt:1-69](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L1-L69)
- [LedgerAccountPO.kt:1-47](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L1-L47)

## Performance Considerations
- Idempotency: Journal entries are deduplicated by source document to prevent duplicate postings.
- Eager Loading: Journal lines are eagerly loaded to avoid N+1 queries during reporting.
- Versioning: Payment orders use optimistic locking to handle concurrent updates safely.
- Indexing: Database migrations add indexes on frequently queried columns (e.g., order statuses, after-sale relationships).
- Period Validation: Open period checks ensure efficient posting without scanning closed periods.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and resolutions:
- Invalid State Errors: Ensure payment order is in correct state before capture/refund operations; verify refund attempts only on captured or partially refunded orders.
- Refund Amount Exceeds Payable: Validate sum of refund items against payable amount; enforce ceiling constraints at application level.
- Journal Entry Not Found: When creating reversals, ensure original entry exists and is posted; check source document mappings.
- Account Resolution Failures: Verify ledger accounts exist for given codes and subjects; fallback to default accounts where applicable.
- Period Closed: Confirm accounting period is open for posting; reopen if necessary with proper authorization.

**Section sources**
- [PaymentOrderImpl.kt:75-176](file://j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentOrderImpl.kt#L75-L176)
- [AccountingApplicationService.kt:168-247](file://j-store-accounting-application/src/main/kotlin/com/jstore/accounting/service/AccountingApplicationService.kt#L168-L247)

## Conclusion
The payment and accounting schemas implement a robust, auditable system for financial operations. Payment orders manage capture and refund lifecycles with strict validations, while accounting entries provide double-entry bookkeeping with period controls and reversal capabilities. Settlement statements enable merchant payouts and reconciliation. The design emphasizes idempotency, consistency, and compliance through structured data models, explicit state transitions, and database constraints.

[No sources needed since this section summarizes without analyzing specific files]

## Appendices

### Data Model Diagram
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
varchar entry_no
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
bigint account_id
varchar side
bigint amount_fen
varchar memo
}
PAYMENT_ORDERS {
bigint id PK
bigint order_id UK
bigint merchant_id
decimal payable_amount
varchar currency
varchar status
varchar provider_transaction_id
decimal captured_amount
timestamp captured_at
bigint version
}
PAYMENT_REFUNDS {
bigint id PK
bigint payment_order_id FK
bigint after_sale_id UK
decimal amount
varchar status
varchar provider_refund_id
varchar failure_reason
timestamp requested_at
timestamp completed_at
}
PAYMENT_REFUND_ITEMS {
varchar id PK
bigint payment_refund_id FK
bigint order_item_id
bigint sku_id
int quantity
decimal amount
}
ACCOUNTING_LEDGER_ACCOUNT ||--o{ ACCOUNTING_JOURNAL_LINE : "referenced by"
ACCOUNTING_JOURNAL_ENTRY ||--o{ ACCOUNTING_JOURNAL_LINE : "has many"
PAYMENT_ORDERS ||--o{ PAYMENT_REFUNDS : "has many"
PAYMENT_REFUNDS ||--o{ PAYMENT_REFUND_ITEMS : "has many"
```

**Diagram sources**
- [LedgerAccountPO.kt:16-46](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/account/persistence/LedgerAccountPO.kt#L16-L46)
- [JournalEntryPO.kt:21-68](file://j-store-accounting-infrastructure/src/main/kotlin/com/jstore/accounting/domain/journal/persistence/JournalEntryPO.kt#L21-L68)
- [PaymentOrderPO.kt:19-72](file://j-store-payment-infrastructure/src/main/kotlin/com/jstore/payment/domain/payment/persistence/PaymentOrderPO.kt#L19-L72)