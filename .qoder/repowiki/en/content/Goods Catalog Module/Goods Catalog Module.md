# Goods Catalog Module

<cite>
**Referenced Files in This Document**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [CommodityUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [InventoryUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryUseCase.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)
- [04-goods-spu-sku-snapshot.sql](file://docker/postgres/init/04-goods-spu-sku-snapshot.sql)
- [07-goods-style-sku-code.sql](file://docker/postgres/init/07-goods-style-sku-code.sql)
</cite>

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
This document explains the Goods Catalog module, focusing on:
- SPU/SKU hierarchy design and persistence model
- Draft workflow for editing live goods
- Snapshot generation to decouple product presentation from order history
- Inventory pre-reservation, confirmation, and release mechanisms
- Product publishing workflows and snapshot queries
- Relationship between goods and orders via snapshots
- Performance considerations for large catalogs and inventory synchronization

The content is designed to be accessible to beginners while providing sufficient technical depth for experienced developers.

## Project Structure
The Goods Catalog module spans three layers:
- API layer: defines query interfaces and data structures for snapshots
- Application layer: orchestrates use cases (commodity and inventory)
- Domain and infrastructure layers: encapsulate business logic and persistence

```mermaid
graph TB
subgraph "API Layer"
A["GoodsSnapshotQueryService"]
end
subgraph "Application Layer"
B["CommodityService"]
C["InventoryService"]
end
subgraph "Domain Layer"
D["Spu / Sku / GoodsStyle"]
E["Inventory / ReservationRecord"]
end
subgraph "Infrastructure Layer"
F["SPU/SKU/Snapshot Tables"]
G["GoodsStyle Table"]
end
A --> B
B --> D
B --> F
C --> E
C --> F
D --> F
E --> F
D --> G
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)
- [04-goods-spu-sku-snapshot.sql](file://docker/postgres/init/04-goods-spu-sku-snapshot.sql)
- [07-goods-style-sku-code.sql](file://docker/postgres/init/07-goods-style-sku-code.sql)

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)
- [04-goods-spu-sku-snapshot.sql](file://docker/postgres/init/04-goods-spu-sku-snapshot.sql)
- [07-goods-style-sku-code.sql](file://docker/postgres/init/07-goods-style-sku-code.sql)

## Core Components
- CommodityService: Implements commodity operations including create/update, SKU management, draft workflow, publish/publish-draft, put-on-sale with snapshot creation, and style management. It also exposes snapshot querying.
- InventoryService: Implements inventory operations including create, reserve, confirm, release, and add stock. Uses a lock abstraction to ensure concurrency safety.
- GoodsSnapshotQueryService: Defines the contract for querying latest snapshots by SPU IDs.

Key responsibilities:
- CommodityService coordinates domain objects and repositories, enforces state transitions, and generates immutable snapshots for order history.
- InventoryService manages stock availability through reservation records and atomic updates under locks.

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [CommodityUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [InventoryUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryUseCase.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

## Architecture Overview
The Goods Catalog module follows a layered architecture with clear separation between application orchestration, domain logic, and persistence. Snapshots are generated at key lifecycle points (publish and publish-draft) to ensure order history remains consistent even if product details change later.

```mermaid
classDiagram
class CommodityService {
+createOrUpdate(cmd)
+addSku(cmd)
+publish(spuId)
+putOnSale(spuId)
+takeOffSale(spuId)
+getDraft(spuId)
+publishDraft(draftSpuId)
+discardDraft(draftSpuId)
+saveGoodsStyle(cmd)
+queryLatestSnapshots(spuIds)
}
class InventoryService {
+create(cmd)
+reserve(bizCode, commodityCode, amount)
+confirm(bizCode)
+release(bizCode)
+add(commodityCode, quantity)
}
class GoodsSnapshotQueryService {
+queryLatestSnapshots(spuIds) GoodsSnapshotInfo[]
}
class SpuRepository
class SpuSnapshotRepository
class GoodsStyleRepository
class InventoryRepository
class ReservationRecordRepository
CommodityService --> SpuRepository : "uses"
CommodityService --> SpuSnapshotRepository : "uses"
CommodityService --> GoodsStyleRepository : "uses"
CommodityService ..|> GoodsSnapshotQueryService : "implements"
InventoryService --> InventoryRepository : "uses"
InventoryService --> ReservationRecordRepository : "uses"
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

## Detailed Component Analysis

### SPU/SKU Hierarchy Design
- SPU (Standard Product Unit): Represents a product concept with attributes like name, description, status, and version. Status transitions include DRAFT, OFF_SALE, ON_SALE.
- SKU (Stock Keeping Unit): Represents a specific variant of an SPU with attributes such as skuName, attributes (JSON), price, merchant_code, and barcode.
- Snapshot: Immutable record of SPU and SKUs at publish time, used for order history and consistency.

Persistence schema highlights:
- spu table stores core product info and status/version
- sku table stores variant-level details and indexes by spu_id
- spu_snapshot table stores immutable snapshots keyed by spu_id and snapshot_version

```mermaid
erDiagram
SPU {
bigint id PK
varchar name
varchar description
varchar status
bigint version
timestamp create_time
timestamp update_time
}
SKU {
bigint id PK
bigint spu_id FK
varchar sku_name
jsonb attributes
numeric price
varchar merchant_code
varchar barcode
}
SPU_SNAPSHOT {
bigint id PK
bigint spu_id FK
bigint snapshot_version
varchar spu_name
varchar description
jsonb sku_snapshots
timestamp created_at
}
SPU ||--o{ SKU : "has many"
SPU ||--o{ SPU_SNAPSHOT : "produces"
```

**Diagram sources**
- [04-goods-spu-sku-snapshot.sql](file://docker/postgres/init/04-goods-spu-sku-snapshot.sql)
- [07-goods-style-sku-code.sql](file://docker/postgres/init/07-goods-style-sku-code.sql)

**Section sources**
- [04-goods-spu-sku-snapshot.sql](file://docker/postgres/init/04-goods-spu-sku-snapshot.sql)
- [07-goods-style-sku-code.sql](file://docker/postgres/init/07-goods-style-sku-code.sql)

### Draft Workflow Implementation
The draft workflow enables safe editing of live products without disrupting existing orders:
- getDraft: For ON_SALE SPU, returns or creates a draft copy (idempotent).
- publishDraft: Merges draft into source SPU, increments version, generates new snapshot, deletes draft, publishes events.
- discardDraft: Deletes draft without affecting source.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Service as "CommodityService"
participant Repo as "SpuRepository"
participant SnapRepo as "SpuSnapshotRepository"
Client->>Service : getDraft(spuId)
Service->>Repo : findById(spuId)
Repo-->>Service : Spu (ON_SALE)
Service->>Repo : findDraftBySourceSpuId(spuId)
alt Draft exists
Repo-->>Service : Draft Spu
Service-->>Client : Draft Spu
else No draft
Service->>Repo : save(createDraftCopy(source))
Service-->>Client : New Draft Spu
end
Client->>Service : publishDraft(draftSpuId)
Service->>Repo : findById(draftSpuId)
Service->>Repo : findById(sourceSpuId)
Service->>Service : mergeFromDraft(source, draft)
Service->>SnapRepo : save(createSnapshot(source))
Service->>Repo : save(source)
Service->>Repo : delete(draft)
Service-->>Client : SpuSnapshot
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)

### Commodity Service Operations
Operations include:
- Create/Update SPU: Validates input, prevents direct edits for ON_SALE items, supports both create and update flows.
- Add SKU: Creates SKU within SPU context and persists changes.
- Publish: Transitions SPU from DRAFT to OFF_SALE and publishes pending events.
- Put On Sale: Transitions SPU from OFF_SALE to ON_SALE and generates a snapshot.
- Take Off Sale: Transitions SPU from ON_SALE to OFF_SALE.
- Save Goods Style: Updates main images, detail HTML, and SKU images mapping.

```mermaid
flowchart TD
Start(["Operation Entry"]) --> Validate["Validate Command"]
Validate --> Valid{"Valid?"}
Valid --> |No| ReturnError["Return Business Error"]
Valid --> |Yes| Branch{"Operation Type"}
Branch --> |Create/Update| HandleCreateUpdate["Load or Create SPU<br/>Enforce ON_SALE edit guard"]
Branch --> |Add SKU| HandleAddSku["Create SKU and attach to SPU"]
Branch --> |Publish| HandlePublish["Transition DRAFT -> OFF_SALE<br/>Publish events"]
Branch --> |Put On Sale| HandlePutOnSale["Transition OFF_SALE -> ON_SALE<br/>Generate Snapshot"]
Branch --> |Take Off Sale| HandleTakeOffSale["Transition ON_SALE -> OFF_SALE"]
Branch --> |Save Style| HandleStyle["Upsert GoodsStyle<br/>Update images and HTML"]
HandleCreateUpdate --> Persist["Persist Changes"]
HandleAddSku --> Persist
HandlePublish --> Persist
HandlePutOnSale --> Persist
HandleTakeOffSale --> Persist
HandleStyle --> Persist
Persist --> End(["Operation Exit"])
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [CommodityUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityUseCase.kt)

### Inventory Management System
Inventory operations ensure accurate stock levels with concurrency control:
- Reserve: Idempotency check via bizCode; acquires lock per commodity; deducts tentative stock; creates reservation record with expiry.
- Confirm: Loads reservation record; deducts actual stock; marks reservation confirmed.
- Release: Releases reserved stock; marks reservation released.
- Add: Adds stock with lock protection.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Service as "InventoryService"
participant Lock as "InventoryLock"
participant InvRepo as "InventoryRepository"
participant ResRepo as "ReservationRecordRepository"
Client->>Service : reserve(bizCode, commodityCode, amount)
Service->>ResRepo : findByBizCode(bizCode)
alt Already reserved
ResRepo-->>Service : ReservationRecord
Service-->>Client : Existing ReservationRecord
else Not reserved
Service->>Lock : lock(commodityCode, timeout)
Lock-->>Service : Lock handle
Service->>InvRepo : findById(commodityCode)
InvRepo-->>Service : Inventory
Service->>Service : reserve(amount)
Service->>InvRepo : save(inventory)
Service->>Service : createReservationRecord(bizCode, commodityCode, amount)
Service->>ResRepo : save(reservationRecord)
Service-->>Client : ReservationRecord
end
Client->>Service : confirm(bizCode)
Service->>ResRepo : findByBizCode(bizCode)
Service->>Service : confirm()
Service->>InvRepo : findById(reservation.commodityCode)
Service->>Service : deduct(reservation.amount)
Service->>InvRepo : save(inventory)
Service->>ResRepo : save(reservationRecord)
Service-->>Client : true
Client->>Service : release(bizCode)
Service->>ResRepo : findByBizCode(bizCode)
Service->>Service : release()
Service->>InvRepo : findById(reservation.commodityCode)
Service->>Service : release(reservation.amount)
Service->>InvRepo : save(inventory)
Service->>ResRepo : save(reservationRecord)
Service-->>Client : true
```

**Diagram sources**
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)

**Section sources**
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [InventoryUseCase.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryUseCase.kt)

### Snapshot Generation and Querying
- Snapshot creation occurs during putOnSale and publishDraft to capture current SPU and SKU state.
- Latest snapshot query returns structured data including SPU and SKU snapshots for display and order history.

```mermaid
sequenceDiagram
participant Client as "Client"
participant Service as "CommodityService"
participant SnapRepo as "SpuSnapshotRepository"
Client->>Service : queryLatestSnapshots([spuId1, spuId2])
loop For each distinct spuId
Service->>SnapRepo : findLatestBySpuId(spuId)
SnapRepo-->>Service : SpuSnapshot?
alt Found
Service-->>Client : GoodsSnapshotInfo
else Not found
Service-->>Client : Skip
end
end
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

### Goods and Orders Through Snapshots
- Orders reference snapshot versions to maintain historical accuracy of product details and pricing.
- Snapshot tables store immutable data that can be queried independently of live product changes.

```mermaid
flowchart TD
OrderItem["Order Item"] --> SnapshotRef["Snapshot Version Reference"]
SnapshotRef --> SpuSnapshot["SPU Snapshot"]
SpuSnapshot --> SkuSnapshots["SKU Snapshots"]
SkuSnapshots --> Price["Price at Time of Order"]
SkuSnapshots --> Attributes["Attributes at Time of Order"]
```

[No sources needed since this diagram shows conceptual workflow, not actual code structure]

## Dependency Analysis
The Goods Catalog module has clear dependencies:
- CommodityService depends on SpuRepository, SpuSnapshotRepository, GoodsStyleRepository, and domain factories.
- InventoryService depends on InventoryRepository, ReservationRecordRepository, and InventoryLock.
- GoodsSnapshotQueryService is implemented by CommodityService to expose snapshot queries.

```mermaid
graph TB
CommodityService --> SpuRepository
CommodityService --> SpuSnapshotRepository
CommodityService --> GoodsStyleRepository
InventoryService --> InventoryRepository
InventoryService --> ReservationRecordRepository
InventoryService --> InventoryLock
CommodityService ..|> GoodsSnapshotQueryService
```

**Diagram sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)
- [GoodsSnapshotQueryService.kt](file://j-store-goods-api/src/main/kotlin/com/jstore/goods/api/GoodsSnapshotQueryService.kt)

## Performance Considerations
- Large catalog queries: Use indexed lookups on spu_id and snapshot_version for efficient snapshot retrieval.
- Snapshot generation: Batch operations where possible to reduce database writes during publish workflows.
- Inventory concurrency: Leverage InventoryLock to prevent race conditions during reserve/confirm/release operations.
- Idempotency: Use bizCode-based checks to avoid duplicate reservations and ensure safe retries.
- Data partitioning: Consider sharding strategies for high-volume SKU and snapshot tables.
- Caching: Cache frequently accessed snapshots and inventory states with appropriate invalidation policies.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and resolutions:
- Direct editing of ON_SALE SPU: Prevented by business rules; use draft workflow instead.
- Missing snapshots: Ensure putOnSale or publishDraft is executed before order placement.
- Inventory conflicts: Check lock acquisition failures and retry with backoff; verify reservation records exist for confirm/release.
- Duplicate reservations: Verify bizCode uniqueness and idempotency handling.

**Section sources**
- [CommodityService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/CommodityService.kt)
- [InventoryService.kt](file://j-store-goods-application/src/main/kotlin/com/jstore/goods/service/InventoryService.kt)

## Conclusion
The Goods Catalog module provides a robust foundation for managing product hierarchies, drafts, snapshots, and inventory. By separating concerns across layers and leveraging immutable snapshots, it ensures consistency between product presentation and order history. The inventory system uses locking and idempotency to handle concurrent operations safely. These patterns scale well for large catalogs and complex e-commerce scenarios.

[No sources needed since this section summarizes without analyzing specific files]