# Domain Model Testing

<cite>
**Referenced Files in This Document**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [libs.versions.toml](file://gradle/libs.versions.toml)
- [build.gradle.kts (goods domain)](file://j-store-goods-domain/build.gradle.kts)
- [build.gradle.kts (user domain)](file://j-store-user-domain/build.gradle.kts)
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
This document explains how to test domain models in the J-Store platform using Kotest with property-based testing. It focuses on aggregates, entities, and value objects across order lifecycle states, commodity draft workflows, user account operations, and accounting ledger operations. You will learn strategies for validating business rules, state transitions, invariants, error conditions, and edge cases, along with guidance on test fixtures, data builders, and assertion libraries tailored for domain testing.

## Project Structure
The repository is organized by bounded contexts (modules). Each module contains:
- Domain layer with aggregates, entities, and value objects
- Application layer use cases and services
- Infrastructure layer persistence implementations
- Tests that exercise domain behavior directly, often with property-based tests

Key testing-related modules:
- j-store-order-domain: Order aggregate lifecycle tests and fixtures
- j-store-goods-domain: Commodity draft workflow property tests
- j-store-user-domain: User account status transition property tests
- j-store-accounting-domain: Journal entry unit tests for accounting invariants

```mermaid
graph TB
subgraph "Order Domain"
ODT["OrderLifecycleRegressionTest.kt"]
OFX["OrderTestFixtures.kt"]
end
subgraph "Goods Domain"
GCDI["CreateDraftCopyDataIntegrityPropertyTest.kt"]
GMFD["MergeFromDraftPropertyTest.kt"]
end
subgraph "User Domain"
UST["UserAccountStatusTransitionPropertyTest.kt"]
end
subgraph "Accounting Domain"
JE["JournalEntryUnitTest.kt"]
end
ODT --> OFX
GCDI --> GMFD
```

**Diagram sources**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)

**Section sources**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)

## Core Components
This section highlights the core domain components under test and their testing patterns:
- Order aggregate: lifecycle transitions, event emission, idempotency, and atomicity
- Commodity SPU/SKU: draft copy integrity and merge semantics
- User account: state transitions and illegal state handling
- Accounting journal: balancing invariant, immutability after posting, and reversal correctness

Testing techniques used:
- Kotest FunSpec for structured tests
- Kotest Property for randomized inputs and invariant assertions
- Test fixtures and builders for consistent object creation
- Result types (Success/Failure) for deterministic assertions

**Section sources**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)

## Architecture Overview
The testing architecture centers around pure domain tests that validate business rules without infrastructure concerns. Property-based tests generate valid and invalid inputs to ensure invariants hold across many scenarios. Unit tests verify specific sequences and outcomes, including event emissions and state changes.

```mermaid
graph TB
subgraph "Tests"
OT["Order Lifecycle Tests"]
GT["Commodity Draft Tests"]
UT["User Account Tests"]
AT["Accounting Journal Tests"]
end
subgraph "Domain Models"
OA["Order Aggregate"]
SP["SPU/SKU Entities"]
UA["User Account Entity"]
JE["Journal Entry Entity"]
end
subgraph "Utilities"
FIX["Test Fixtures & Builders"]
RES["Result Types (Success/Failure)"]
end
OT --> OA
GT --> SP
UT --> UA
AT --> JE
OT --> FIX
GT --> FIX
UT --> FIX
AT --> FIX
OT --> RES
GT --> RES
UT --> RES
AT --> RES
```

[No sources needed since this diagram shows conceptual workflow, not actual code structure]

## Detailed Component Analysis

### Order Lifecycle Testing
Focus areas:
- Stock confirmation opens trade and emits a payment creation gate event
- Stock failure closes order and emits cancellation event
- Idempotent stock confirmation prevents duplicate events
- Fulfillment sequence preserves item statuses through delivery and completion
- Cancellation behavior differs for unpaid vs paid orders
- Invalid payment recording does not mutate state partially

```mermaid
sequenceDiagram
participant T as "Test"
participant O as "OrderImpl"
participant E as "Pending Events"
T->>O : confirmStock()
O-->>T : Success
O->>E : emit OrderStockConfirmedEvent
T->>O : markStockInsufficient(reason)
O-->>T : Success
O->>E : emit OrderCancelledEvent
T->>O : confirmStock() again
O-->>T : Failure
T->>O : recordFulfillmentPrepared(ref)
O-->>T : Success
T->>O : recordShipmentDispatched(ref)
O-->>T : Success
T->>O : recordShipmentDelivered(ref)
O-->>T : Success
T->>O : complete()
O-->>T : Success
```

**Diagram sources**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)

**Section sources**
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)

### Commodity Draft Workflow Testing
Focus areas:
- createDraftCopy preserves source data integrity (name, description, SKU list, version, status, sourceSpuId)
- mergeFromDraft merges draft into ON_SALE source and increments version while keeping status unchanged
- Property-based generation ensures robust coverage over varied SKUs and attributes

```mermaid
flowchart TD
Start(["Start"]) --> GenSource["Generate ON_SALE SPU"]
GenSource --> Copy["createDraftCopy(source)"]
Copy --> AssertCopy["Assert draft fields match source<br/>status=DRAFT<br/>sourceSpuId=source.id<br/>id!=source.id"]
AssertCopy --> GenDraft["Generate DRAFT SPU with SKUs"]
GenDraft --> Merge["source.mergeFromDraft(draft)"]
Merge --> AssertMerge["Assert name/description/skus updated<br/>version incremented<br/>status remains ON_SALE"]
AssertMerge --> End(["End"])
```

**Diagram sources**
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)

**Section sources**
- [CreateDraftCopyDataIntegrityPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/CreateDraftCopyDataIntegrityPropertyTest.kt)
- [MergeFromDraftPropertyTest.kt](file://j-store-goods-domain/src/test/kotlin/com/jstore/goods/domain/commodity/MergeFromDraftPropertyTest.kt)

### User Account Operations Testing
Focus areas:
- State transitions: ACTIVE <-> DISABLED via enable/disable
- Illegal state transitions return Failure with ILLEGAL_STATE
- Property-based tests cover random IDs and consistent nickname/password hashing

```mermaid
stateDiagram-v2
[*] --> ACTIVE
ACTIVE --> DISABLED : disable()
DISABLED --> ACTIVE : enable()
ACTIVE --> ACTIVE : enable() -> Failure(ILLEGAL_STATE)
DISABLED --> DISABLED : disable() -> Failure(ILLEGAL_STATE)
```

**Diagram sources**
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)

**Section sources**
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)

### Accounting Ledger Operations Testing
Focus areas:
- Balanced entries can be posted; unbalanced entries fail
- Posted entries cannot be modified; reversal creates mirrored lines
- Original lines remain immutable after posting

```mermaid
flowchart TD
Start(["Start"]) --> BuildDraft["Build draft JournalEntry with lines"]
BuildDraft --> CheckBalance{"Debits == Credits?"}
CheckBalance --> |Yes| Post["post(openPeriod)"]
CheckBalance --> |No| Fail["Return Failure(JOURNAL_ENTRY_UNBALANCED)"]
Post --> AssertPosted["Assert status=POSTED"]
AssertPosted --> TryModify["Try addLine()"]
TryModify --> ModifyFail["Return Failure(JOURNAL_ENTRY_ALREADY_POSTED)"]
AssertPosted --> Reversal["createReversal(...)"]
Reversal --> AssertReversal["Assert sides flipped<br/>original lines unchanged"]
Fail --> End(["End"])
ModifyFail --> End
AssertReversal --> End
```

**Diagram sources**
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)

**Section sources**
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)

## Dependency Analysis
Kotest and related libraries are centrally managed via Gradle version catalog. The domain modules include Kotest runner, assertions, and property testing dependencies. Tests run on JUnit Platform.

```mermaid
graph TB
LVT["libs.versions.toml"] --> KTR["kotest-runner-junit5"]
LVT --> KTA["kotest-assertions-core"]
LVT --> KTP["kotest-property"]
GD["j-store-goods-domain build.gradle.kts"] --> KTR
GD --> KTA
GD --> KTP
UD["j-store-user-domain build.gradle.kts"] --> KTR
UD --> KTA
UD --> KTP
```

**Diagram sources**
- [libs.versions.toml](file://gradle/libs.versions.toml)
- [build.gradle.kts (goods domain)](file://j-store-goods-domain/build.gradle.kts)
- [build.gradle.kts (user domain)](file://j-store-user-domain/build.gradle.kts)

**Section sources**
- [libs.versions.toml](file://gradle/libs.versions.toml)
- [build.gradle.kts (goods domain)](file://j-store-goods-domain/build.gradle.kts)
- [build.gradle.kts (user domain)](file://j-store-user-domain/build.gradle.kts)

## Performance Considerations
- Property-based tests should limit sample sizes to balance coverage and runtime (e.g., 100 samples per checkAll).
- Avoid heavy I/O in domain tests; keep them fast and deterministic.
- Use lightweight generators for complex structures (e.g., SKU lists) to reduce test execution time.
- Prefer immutable domain objects and pure functions to avoid hidden state costs.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and resolutions:
- Illegal state transitions: Ensure initial state matches expected preconditions before invoking operations; assert Failure with ILLEGAL_STATE when appropriate.
- Unbalanced journal entries: Validate debit/credit sums before posting; assert Failure with JOURNAL_ENTRY_UNBALANCED.
- Duplicate operations: Confirm idempotency checks prevent repeated side effects; assert no additional events or state mutations.
- Partial mutations on failures: Verify that failed operations leave the domain model unchanged; compare snapshots before and after.

**Section sources**
- [UserAccountStatusTransitionPropertyTest.kt](file://j-store-user-domain/src/test/kotlin/com/jstore/user/UserAccountStatusTransitionPropertyTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [OrderLifecycleRegressionTest.kt](file://j-store-order-domain/src/test/kotlin/com/jstore/order/domain/order/OrderLifecycleRegressionTest.kt)

## Conclusion
The J-Store platform employs robust domain testing practices using Kotest and property-based testing to validate business rules, state transitions, and invariants across order, commodity, user, and accounting domains. By combining focused unit tests with comprehensive property tests, the team ensures correctness, resilience, and maintainability of domain logic. Adopting similar patterns—fixtures, builders, Result types, and clear assertions—will help teams scale testing efforts effectively.

[No sources needed since this section summarizes without analyzing specific files]