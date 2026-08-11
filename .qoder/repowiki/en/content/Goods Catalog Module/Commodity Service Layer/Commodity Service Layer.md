# Commodity Service Layer

<cite>
**Referenced Files in This Document**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [CommodityUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt)
- [TransactionalCommodityUseCase.kt](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt)
- [SpuRepositoryImpl.kt](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt)
- [GoodsStyleRepositoryImpl.kt](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt)
- [CommodityArchivedEvent.kt](file://j-store-goods-domain/src/main/kotlin/com/jstore/goods/domain/commodity/event/CommodityArchivedEvent.kt)
- [SpuImpl.kt](file://j-store-goods-domain/src/main/kotlin/com/jstore/goods/domain/commodity/SpuImpl.kt)
- [InventoryService.kt](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt)
</cite>

## Update Summary
**Changes Made**
- Updated event handling to reflect renaming of CommodityOffSaleEvent to CommodityArchivedEvent
- Added documentation for new archive workflow and ARCHIVED commodity status
- Updated integration patterns to show separation between goods domain and dedicated inventory module
- Enhanced transactional boundary documentation to include archive operations

## Table of Contents
1. [Introduction](#introduction)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Architecture Overview](#architecture-overview)
5. [Detailed Component Analysis](#detailed-component-analysis)
6. [Dependency Analysis](#dependency-analysis)
7. [Performance Considerations](#performance-considerations)
8. [Troubleshooting Guide](#troubleshooting-guide)
9. [Conclusion](#conclusion)

## Introduction
This document explains the commodity service layer implementation that orchestrates product lifecycle operations: creating/updating products (SPU), managing styles, handling SKU operations, executing publishing workflows with snapshots and domain events, and managing product archival. It details how application services interact with domain aggregates through use case interfaces, how transactions are configured, and how errors and business rules are enforced. The system now features a clear separation between goods domain operations and inventory management, with inventory functionality migrated to a dedicated inventory module.

## Project Structure
The commodity service layer spans three layers with clear separation from inventory management:
- Application service and use case interface define orchestration and contracts including archive operations.
- Infrastructure repositories implement persistence for SPU and GoodsStyle with mandatory transaction boundaries.
- Boot configuration wraps use cases with explicit read/write transactions and delegates snapshot queries to a read-only path.
- Dedicated inventory module handles stock reservations and physical stock management independently.

```mermaid
graph TB
subgraph "Application"
UC["CommodityUseCase"]
SVC["CommodityService"]
end
subgraph "Infrastructure"
SRepo["SpuRepositoryImpl"]
GRepo["GoodsStyleRepositoryImpl"]
end
subgraph "Boot Config"
TX["TransactionalCommodityUseCase"]
end
subgraph "Inventory Module"
INV["InventoryService"]
end
UC --> SVC
SVC --> SRepo
SVC --> GRepo
TX --> SVC
TX --> UC
SVC -.-> INV
```

**Diagram sources**
- [CommodityUseCase.kt:1-33](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt#L1-L33)
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)

**Section sources**
- [CommodityUseCase.kt:1-33](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt#L1-L33)
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)

## Core Components
- CommodityUseCase: Declares all commodity operations as application-level use cases, returning typed results for success or business errors, including archive operations.
- CommodityService: Implements orchestration across SPU creation/update, SKU addition, status transitions, draft workflows, style management, archival, and snapshot queries.
- TransactionalCommodityUseCase: Wraps each operation in an explicit Spring transaction template; read operations are marked read-only.
- SpuRepositoryImpl and GoodsStyleRepositoryImpl: Persist domain entities with mandatory transaction propagation, ensuring writes occur within an existing transaction.
- InventoryService: Dedicated service for inventory management, handling stock reservations, confirmations, releases, and physical stock updates.

Key responsibilities:
- Enforce business rules (e.g., preventing direct edits on PUBLISHED items).
- Manage state transitions (publish, archive, draft workflows).
- Handle drafts (getDraft, publishDraft, discardDraft).
- Maintain presentation styles (main images, detail HTML, per-SKU images).
- Publish domain events after successful mutations, including archive events.
- Coordinate with inventory module for stock-related operations.

**Updated** Added archive workflow and updated status transition logic to reflect the new ARCHIVED status and CommodityArchivedEvent.

**Section sources**
- [CommodityUseCase.kt:1-33](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt#L1-L33)
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)

## Architecture Overview
The commodity service layer follows a clear separation with distinct boundaries from inventory management:
- Use case interface defines stable contracts including archive operations.
- Application service composes domain factories, repositories, and event publishing.
- Infrastructure repositories enforce mandatory transactions and convert between domain and persistence models.
- Boot configuration centralizes transactional behavior and read-only query semantics.
- Dedicated inventory module handles stock reservations and physical stock management independently.

```mermaid
classDiagram
class CommodityUseCase {
+createOrUpdate(cmd) Result~Spu,BusinessError~
+addSku(cmd) Result~Spu,BusinessError~
+publish(spuId) Result~SpuSnapshot,BusinessError~
+archive(spuId) Result~Unit,BusinessError~
+getDraft(spuId) Result~Spu,BusinessError~
+publishDraft(draftSpuId) Result~SpuSnapshot,BusinessError~
+discardDraft(draftSpuId) Result~Unit,BusinessError~
+saveGoodsStyle(cmd) Result~GoodsStyle,BusinessError~
}
class CommodityService {
-spuFactory
-spuRepository
-domainEventPublisher
-snapshotFactory
-snapshotRepository
-goodsStyleRepository
-goodsStyleFactory
+createOrUpdate(cmd)
+addSku(cmd)
+publish(spuId)
+archive(spuId)
+getDraft(spuId)
+publishDraft(draftSpuId)
+discardDraft(draftSpuId)
+saveGoodsStyle(cmd)
+queryLatestSnapshots(spuIds)
}
class TransactionalCommodityUseCase {
-delegate : CommodityUseCase
-snapshotQueries : GoodsSnapshotQueryService
-write : TransactionTemplate
-read : TransactionTemplate
+createOrUpdate(cmd)
+addSku(cmd)
+publish(spuId)
+archive(spuId)
+getDraft(spuId)
+publishDraft(draftSpuId)
+discardDraft(draftSpuId)
+saveGoodsStyle(cmd)
+queryLatestSnapshots(spuIds)
}
class SpuRepositoryImpl {
+save(entity) Spu
+findById(id) Spu?
+findDraftBySourceSpuId(sourceSpuId) Spu?
+delete(spu) void
}
class GoodsStyleRepositoryImpl {
+save(entity) GoodsStyle
+findById(id) GoodsStyle?
+findBySpuId(spuId) GoodsStyle?
}
class InventoryService {
-reserve(command) Result~StockReservationResult,BusinessError~
-confirm(orderId) Result~Unit,BusinessError~
-release(orderId) Result~Unit,BusinessError~
-applyPhysicalStock(message) Result~Boolean,BusinessError~
}
CommodityUseCase <|.. CommodityService
CommodityUseCase <|.. TransactionalCommodityUseCase
CommodityService --> SpuRepositoryImpl : "uses"
CommodityService --> GoodsStyleRepositoryImpl : "uses"
CommodityService ..> InventoryService : "coordinates via events"
```

**Diagram sources**
- [CommodityUseCase.kt:1-33](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt#L1-L33)
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)

## Detailed Component Analysis

### CommodityService Orchestration
- Product creation/update: Validates command, guards PUBLISHED direct edits, creates or updates SPU via factory, persists, and returns result.
- SKU operations: Loads SPU, creates SKU via factory, adds to aggregate, persists, and returns result.
- Publishing workflow: Transitions SPU state, persists, publishes pending domain events.
- Archival workflow: Transitions to ARCHIVED status, persists, publishes CommodityArchivedEvent.
- Draft workflow:
  - getDraft: Ensures only PUBLISHED items can have drafts; returns existing draft or creates a new one idempotently.
  - publishDraft: Merges draft into source, increments version, creates snapshot, deletes draft, publishes events.
  - discardDraft: Deletes draft copy without affecting source.
- Style management: Validates command, loads or creates GoodsStyle, updates main images, detail HTML, and per-SKU images, persists.
- Snapshot query: Returns latest snapshots for given SPU IDs with mapped DTOs.

**Updated** Added archive workflow that transitions PUBLISHED items to ARCHIVED status and publishes CommodityArchivedEvent.

```mermaid
sequenceDiagram
participant Client as "Caller"
participant TX as "TransactionalCommodityUseCase"
participant SVC as "CommodityService"
participant SRepo as "SpuRepository"
participant Pub as "DomainEventPublisher"
Client->>TX : archive(spuId)
TX->>SVC : archive(spuId)
SVC->>SRepo : findById(spuId)
SRepo-->>SVC : Spu
SVC->>SVC : spu.archive()
SVC->>SRepo : save(spu)
SVC->>Pub : publishPendingEvents(spu)
SVC-->>TX : Success(Unit)
TX-->>Client : Unit
```

**Diagram sources**
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [CommodityService.kt:80-88](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L80-L88)

**Section sources**
- [CommodityService.kt:33-201](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L33-L201)

### Transactional Boundaries
- Write operations: All write methods delegate to a write TransactionTemplate, ensuring atomicity and consistent rollback on failures.
- Read operations: Snapshot queries use a read-only TransactionTemplate to optimize read paths.
- Repository mandates: Persistence methods require an existing transaction (MANDATORY), preventing accidental writes outside a transaction boundary.

```mermaid
flowchart TD
Start(["Method Entry"]) --> CheckType{"Write or Read?"}
CheckType --> |Write| TxWrite["Execute in write TransactionTemplate"]
CheckType --> |Read| TxRead["Execute in read-only TransactionTemplate"]
TxWrite --> Delegate["Delegate to CommodityService"]
TxRead --> Query["Delegate to snapshot query"]
Delegate --> RepoSave["Repository.save(...)<br/>mandatory transaction"]
Query --> RepoFind["Repository.find(...)"]
RepoSave --> End(["Return Result"])
RepoFind --> End
```

**Diagram sources**
- [TransactionalCommodityUseCase.kt:12-45](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L12-L45)
- [SpuRepositoryImpl.kt:16-22](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L16-L22)
- [GoodsStyleRepositoryImpl.kt:15-21](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L15-L21)

**Section sources**
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)

### Domain Integration and Aggregates
- SPU aggregate: State transitions and validations are delegated to domain methods (e.g., publish, archive, mergeFromDraft).
- Snapshot creation: A dedicated factory builds immutable snapshots from current SPU state for consistent reads.
- Event publishing: Pending domain events are published after successful mutations to ensure consistency, including CommodityArchivedEvent for archival.
- Inventory coordination: Goods domain coordinates with dedicated inventory module through domain events rather than direct coupling.

**Updated** Enhanced domain integration to include archive workflow and improved separation from inventory management.

```mermaid
sequenceDiagram
participant App as "CommodityService"
participant SAgg as "Spu Aggregate"
participant SRepo as "SpuRepository"
participant SnapF as "SpuSnapshotFactory"
participant SnapRepo as "SpuSnapshotRepository"
participant Pub as "DomainEventPublisher"
App->>SAgg : archive()
SAgg-->>App : success/failure
App->>SRepo : save(Spu)
App->>Pub : publishPendingEvents(Spu)
```

**Diagram sources**
- [CommodityService.kt:80-88](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L80-L88)
- [SpuImpl.kt:68-77](file://j-store-goods-domain/src/main/kotlin/com/jstore/goods/domain/commodity/SpuImpl.kt#L68-L77)

**Section sources**
- [CommodityService.kt:68-88](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L68-L88)
- [SpuImpl.kt:68-77](file://j-store-goods-domain/src/main/kotlin/com/jstore/goods/domain/commodity/SpuImpl.kt#L68-L77)

### Common Operations Examples
- Create new product (SPU): Validate command, create via factory, persist, return result.
- Update styles: Validate command, load or create GoodsStyle, update main/detail images and per-SKU images, persist.
- Add SKU: Load SPU, create SKU via factory, add to aggregate, persist.
- Publish workflow: Transition states, create snapshot, persist, publish events.
- Archive workflow: Transition to ARCHIVED status, persist, publish CommodityArchivedEvent.

These flows are implemented by the corresponding methods in the service and validated by business rules enforced in the service and domain.

**Updated** Added archive workflow example showing the new archival process.

**Section sources**
- [CommodityService.kt:33-201](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L33-L201)

## Dependency Analysis
- CommodityService depends on:
  - SpuFactory and SpuRepository for SPU lifecycle.
  - GoodsStyleFactory and GoodsStyleRepository for style management.
  - SpuSnapshotFactory and SpuSnapshotRepository for snapshot creation and persistence.
  - DomainEventPublisher for event emission.
- TransactionalCommodityUseCase depends on:
  - CommodityUseCase implementation (delegation).
  - GoodsSnapshotQueryService for read-only snapshot queries.
  - PlatformTransactionManager for explicit transaction control.
- Repositories depend on JPA repositories and converters for domain-persistence mapping.
- Inventory coordination: Goods domain coordinates with dedicated inventory module through domain events rather than direct dependencies.

**Updated** Enhanced dependency analysis to show the separation between goods domain and inventory module.

```mermaid
graph LR
TX["TransactionalCommodityUseCase"] --> UC["CommodityUseCase"]
UC --> SVC["CommodityService"]
SVC --> SRepo["SpuRepository"]
SVC --> GRepo["GoodsStyleRepository"]
SVC --> SnapRepo["SpuSnapshotRepository"]
SVC --> Pub["DomainEventPublisher"]
SRepo --> JPA["JpaRepository"]
GRepo --> JPA
SnapRepo --> JPA
SVC -.-> INV["InventoryService<br/>(via events)"]
```

**Diagram sources**
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)

**Section sources**
- [CommodityService.kt:1-203](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L1-L203)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)
- [SpuRepositoryImpl.kt:1-95](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/SpuRepositoryImpl.kt#L1-L95)
- [GoodsStyleRepositoryImpl.kt:1-71](file://j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/commodity/GoodsStyleRepositoryImpl.kt#L1-L71)
- [InventoryService.kt:1-240](file://j-store-inventory-application/src/main/kotlin/com/jstore/inventory/service/InventoryService.kt#L1-L240)

## Performance Considerations
- Read-only snapshot queries are executed in read-only transactions to leverage database optimizations.
- Mandatory transaction propagation at repository level prevents accidental non-transactional writes.
- Snapshot creation occurs once per publish transition to minimize repeated heavy computations.
- Draft workflows avoid unnecessary merges by reusing existing drafts idempotently.
- Archive operations are lightweight, focusing on status transition and event publication.
- Inventory operations are handled in dedicated module to reduce goods domain complexity.

**Updated** Added considerations for archive operations and inventory module separation.

## Troubleshooting Guide
Common issues and strategies:
- Business rule violations: Methods return typed failure results indicating specific business errors (e.g., SPU not found, PUBLISHED direct edit rejected, draft-related constraints, invalid status transitions).
- Transaction rollbacks: Any exception thrown during write operations will cause the entire transaction to roll back due to explicit TransactionTemplate usage.
- Event publishing failures: If event publishing fails, it occurs within the same transaction; failures will trigger rollback to maintain consistency.
- Validation errors: Command verification steps fail fast with descriptive errors before invoking domain logic.
- Archive validation: Archive operations validate that only PUBLISHED items can be archived.

Operational tips:
- Inspect returned Result types to handle success vs. failure branches explicitly.
- Ensure callers invoke methods through TransactionalCommodityUseCase to guarantee transactional boundaries.
- For draft operations, verify source relationships and status constraints before merging or discarding.
- Monitor CommodityArchivedEvent publications for audit trails of archived products.

**Updated** Added troubleshooting guidance for archive operations and event monitoring.

**Section sources**
- [CommodityService.kt:33-201](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt#L33-L201)
- [TransactionalCommodityUseCase.kt:1-46](file://j-store-goods-boot/src/main/kotlin/com/jstore/goods/config/TransactionalCommodityUseCase.kt#L1-L46)

## Conclusion
The commodity service layer provides a robust orchestration of product lifecycle operations with clear transactional boundaries, strong validation, and consistent integration with domain aggregates. The design ensures data integrity through mandatory transactions, enforces business rules at the application and domain levels, and supports scalable snapshot-based reads. The recent refactoring has successfully separated inventory management concerns into a dedicated module while maintaining clean event-driven communication between domains. Error handling is explicit and predictable, enabling reliable operations across product creation, style management, SKU operations, publishing workflows, and archival processes.

**Updated** Enhanced conclusion to reflect the successful completion of goods domain refactoring and inventory module separation.