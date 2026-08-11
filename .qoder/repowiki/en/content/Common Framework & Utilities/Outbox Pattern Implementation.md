# Outbox Pattern Implementation

<cite>
**Referenced Files in This Document**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxEntryPO.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryPO.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxPublisher.kt)
- [EventSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventSerializer.kt)
- [EventTypeRegistry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventTypeRegistry.kt)
- [IntegrationMessageSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/IntegrationMessageSerializer.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)
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
This document explains the Outbox Pattern implementation used to ensure reliable message delivery by combining database transactions with asynchronous message publishing. It covers the OutboxEntry model, repository interfaces and JPA persistence, Spring auto-configuration, publisher mechanism, event serialization strategies, consumption tracking, dead-letter handling, configuration examples, broker integrations, deployment considerations, performance optimization, scaling strategies, and troubleshooting guidance.

## Project Structure
The outbox implementation spans core domain abstractions, Spring-based persistence and wiring, and operational boot modules:
- Core abstractions define the OutboxEntry model, repository interface, serializers, type registries, and delivery routing.
- Spring persistence provides JPA entities, repository implementation with advanced SQL for claiming and leasing, and Spring Boot auto-configuration.
- Boot module exposes operational endpoints and properties for monitoring and management.

```mermaid
graph TB
subgraph "Core"
A["OutboxEntry"]
B["OutboxEntryRepository"]
C["EventSerializer / EventTypeRegistry"]
D["IntegrationMessageSerializer / IntegrationMessageTypeRegistry"]
E["OutboxDeliveryChannel / OutboxDeliveryRouter"]
end
subgraph "Spring Persistence"
F["OutboxEntryPO (JPA Entity)"]
G["OutboxEntryRepositoryImpl"]
end
subgraph "Boot Ops"
H["OutboxOperationsController"]
I["OutboxOperationsProperties"]
end
A --> B
C --> A
D --> A
E --> A
F --> G
B --> G
G --> E
H --> G
I --> H
```

**Diagram sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxEntryPO.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryPO.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxEntryPO.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryPO.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

## Core Components
- OutboxEntry: Domain model representing a pending or in-flight message with metadata such as aggregate identity, correlation/causation IDs, tenant context, delivery target, destination, partition key, retry counters, lease fields, and timestamps. Includes validation rules for required fields and consistency between status and lease state.
- OutboxEntryRepository: Repository interface defining save, claim, lease renewal, mark published/failed, dead-letter operations, counts, and health queries.
- JPA Entity and Repository Impl: OutboxEntryPO maps to the outbox_entry table; OutboxEntryRepositoryImpl implements optimistic locking via lock_token, atomic claim with SKIP LOCKED, lease renewal, and robust failure transitions.
- Serializers and Registries: EventSerializer and EventTypeRegistry for domain events; IntegrationMessageSerializer and IntegrationMessageTypeRegistry for integration messages. Supports versioned deserialization and upcasting.
- Delivery Routing: OutboxDeliveryChannel and OutboxDeliveryRouter route entries to local domain bus, local integration bus, or external brokers based on deliveryTarget.

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxEntryPO.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryPO.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [EventSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventSerializer.kt)
- [EventTypeRegistry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventTypeRegistry.kt)
- [IntegrationMessageSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/IntegrationMessageSerializer.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)

## Architecture Overview
The outbox pattern ensures reliability by persisting an event record within the same transaction that updates application state. A background scheduler polls the outbox table, claims rows atomically, delivers them through a router to the appropriate channel, and marks them published or failed with exponential backoff. Dead letters are quarantined for manual inspection and requeue.

```mermaid
sequenceDiagram
participant App as "Application Service"
participant TX as "DB Transaction"
participant Repo as "OutboxEntryRepository"
participant DB as "outbox_entry"
participant Sched as "OutboxScheduler"
participant Pub as "OutboxPublisher"
participant Router as "OutboxDeliveryRouter"
participant Channel as "OutboxDeliveryChannel"
participant Broker as "Broker / Local Bus"
App->>TX : Begin transaction
App->>Repo : save(OutboxEntry)
Repo->>DB : INSERT row (PENDING)
TX-->>App : Commit (state + outbox persisted together)
loop Scheduled poll
Sched->>Pub : pollAndPublish()
Pub->>Repo : claimPendingAndRetryable(...)
Repo->>DB : UPDATE ... FOR UPDATE SKIP LOCKED
DB-->>Repo : Claimed rows (IN_PROGRESS, leased)
Pub->>Router : deliver(entry)
Router->>Channel : deliver(entry)
Channel->>Broker : Publish message
alt Success
Pub->>Repo : markPublished(...)
Repo->>DB : UPDATE PUBLISHED
else Failure
Pub->>Repo : markFailed(...)
Repo->>DB : UPDATE FAILED/DEAD_LETTER
end
end
```

**Diagram sources**
- [OutboxPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxPublisher.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)

## Detailed Component Analysis

### OutboxEntry Model and Status Flow
OutboxEntry encapsulates all metadata needed for reliable delivery and observability. It enforces constraints such as non-blank identifiers, positive versions, consistent lease fields when IN_PROGRESS, and correct pairing of messageKind and deliveryTarget.

```mermaid
classDiagram
class OutboxEntry {
+string id
+string eventType
+string payload
+string aggregateType
+string aggregateId
+OutboxEntryStatus status
+Instant createdAt
+Instant updatedAt
+int retryCount
+Instant nextAttemptAt
+string lockedBy
+Instant lockedAt
+Instant lockedUntil
+long lockToken
+string lastError
+string eventId
+string eventClassName
+int eventVersion
+Instant occurredAt
+OutboxMessageKind messageKind
+OutboxDeliveryTarget deliveryTarget
+string destination
+string partitionKey
+string correlationId
+string causationId
+string tenantId
}
class OutboxEntryStatus
class OutboxMessageKind
class OutboxDeliveryTarget
OutboxEntry --> OutboxEntryStatus : "has"
OutboxEntry --> OutboxMessageKind : "has"
OutboxEntry --> OutboxDeliveryTarget : "has"
```

**Diagram sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)

### Repository Interface and JPA Implementation
The repository abstracts persistence operations. The implementation uses native SQL for high-performance claiming with SKIP LOCKED, lease renewal, and conditional updates guarded by lock_token to prevent lost updates.

```mermaid
flowchart TD
Start(["Claim Cycle"]) --> CheckExpired["Mark expired locks as DEAD_LETTER if retry exhausted"]
CheckExpired --> SelectCandidates["Select candidates: PENDING or FAILED ready or IN_PROGRESS expired<br/>Skip predecessors per aggregate"]
SelectCandidates --> ForUpdate["FOR UPDATE SKIP LOCKED LIMIT batch"]
ForUpdate --> UpdateState["UPDATE to IN_PROGRESS, increment retry_count,<br/>set locked_by/at/until, lock_token++"]
UpdateState --> ReturnRows["Return claimed rows"]
ReturnRows --> End(["Done"])
```

**Diagram sources**
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)

**Section sources**
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)

### Database Schema and Indexes
The schema defines the outbox_entry table and supporting indexes for polling, claiming, cleanup, and diagnostics. A separate domain_event_consumption table tracks listener-level idempotency for domain events.

```mermaid
erDiagram
OUTBOX_ENTRY {
varchar id PK
varchar event_type
varchar event_id
varchar event_class_name
int event_version
varchar message_kind
varchar delivery_target
varchar destination
varchar partition_key
varchar correlation_id
varchar causation_id
varchar tenant_id
text payload
varchar aggregate_type
varchar aggregate_id
timestamptz occurred_at
varchar status
timestamptz created_at
timestamptz updated_at
int retry_count
timestamptz next_attempt_at
varchar locked_by
timestamptz locked_at
timestamptz locked_until
text last_error
}
DOMAIN_EVENT_CONSUMPTION {
varchar listener_id PK
varchar event_id PK
varchar event_name
int event_version
timestamptz consumed_at
}
OUTBOX_ENTRY ||--o{ DOMAIN_EVENT_CONSUMPTION : "consumed by listeners"
```

**Diagram sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

**Section sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

### Spring Auto-Configuration
OutboxAutoConfiguration wires serializers, registries, repositories, publishers, channels, routers, schedulers, monitors, and operational health under a feature flag. It also configures local and broker delivery channels conditionally.

```mermaid
graph LR
AC["OutboxAutoConfiguration"]
ER["EventTypeRegistry"]
IR["IntegrationMessageTypeRegistry"]
ES["EventSerializer"]
IMS["IntegrationMessageSerializer"]
OER["OutboxEntryRepository"]
MCR["MessageConsumptionRepository"]
DEP["DomainEventPublisher"]
IMP["IntegrationMessagePublisher"]
LDC["LocalIntegrationMessageDeliveryChannel"]
BDC["BrokerIntegrationMessageDeliveryChannel"]
RTR["OutboxDeliveryRouter"]
PUB["OutboxPublisher"]
SCH["OutboxScheduler"]
MON["OutboxMonitor"]
HEALTH["OutboxOperationalHealth"]
AC --> ER
AC --> IR
AC --> ES
AC --> IMS
AC --> OER
AC --> MCR
AC --> DEP
AC --> IMP
AC --> LDC
AC --> BDC
AC --> RTR
AC --> PUB
AC --> SCH
AC --> MON
AC --> HEALTH
```

**Diagram sources**
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)

**Section sources**
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)

### Publisher Mechanism and Retry Strategy
OutboxPublisher performs scheduled polling, claims entries, renews leases, delivers via router, and commits success/failure atomically. It applies exponential backoff capped at a maximum delay and moves entries to DEAD_LETTER after exceeding max retries.

```mermaid
sequenceDiagram
participant S as "Scheduler"
participant P as "OutboxPublisher"
participant R as "OutboxEntryRepository"
participant D as "OutboxDeliveryRouter"
participant C as "OutboxDeliveryChannel"
S->>P : pollAndPublish()
P->>R : claimPendingAndRetryable(maxRetryCount, batchSize, workerId, lockedUntil)
loop For each entry
P->>R : renewLease(id, workerId, lockToken, lockedUntil)
P->>D : deliver(entry)
D->>C : deliver(entry)
alt Delivery succeeds
P->>R : markPublished(entry, workerId)
else Delivery fails
P->>R : markFailed(entry, workerId)
opt retryCount >= maxRetryCount
P->>P : set status=DEAD_LETTER
end
end
end
P-->>S : metrics and logs
```

**Diagram sources**
- [OutboxPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxPublisher.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)

**Section sources**
- [OutboxPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxPublisher.kt)

### Event Serialization Strategies
- Domain Events: EventSerializer serializes/deserializes using ObjectMapper with EventTypeRegistry to resolve classes by name and version. Supports upcasting via EventUpcaster registry.
- Integration Messages: IntegrationMessageSerializer handles serialization with IntegrationMessageTypeRegistry. Both support versioned evolution and safe upgrades.

**Section sources**
- [EventSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventSerializer.kt)
- [EventTypeRegistry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/EventTypeRegistry.kt)
- [IntegrationMessageSerializer.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/IntegrationMessageSerializer.kt)

### Consumption Tracking and Idempotency
Domain event consumption is tracked in domain_event_consumption keyed by listener_id and event_id to ensure exactly-once semantics per listener. Integration consumers can use similar patterns via MessageConsumptionRepository.

**Section sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

### Operational Controls and Health
- OutboxOperationsController exposes operational endpoints for querying and managing outbox entries and dead letters.
- OutboxOperationsProperties centralizes configuration for operational features.
- OutboxOperationalHealth aggregates metrics and thresholds for readiness/liveness checks.

**Section sources**
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

## Dependency Analysis
The system exhibits clear layering:
- Core abstractions depend only on domain types and minimal utilities.
- Spring persistence depends on JPA and Spring Data.
- Auto-configuration wires components conditionally based on available beans and properties.
- Boot module adds operational APIs and properties.

```mermaid
graph TB
Core["Core Abstractions"]
Spring["Spring Persistence & Wiring"]
Boot["Boot Operations"]
Core --> Spring
Spring --> Boot
```

**Diagram sources**
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)

**Section sources**
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)

## Performance Considerations
- Use SKIP LOCKED and targeted indexes to minimize contention during claim cycles.
- Tune batchSize and maxInFlightPerPoll to balance throughput and memory usage.
- Configure lockTimeoutMillis appropriately to avoid premature lease expiry while allowing recovery from stuck workers.
- Enable batching for deletePublishedBefore to reclaim space without long-running transactions.
- Monitor retry backoff parameters to reduce load during transient failures.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
Common issues and remedies:
- Stuck locks: Expired locks are converted to DEAD_LETTER when retry count is exhausted; otherwise they become eligible again.
- Duplicate deliveries: Ensure consumer-side idempotency using event_id and consumption tables.
- Serialization errors: Verify EventTypeRegistry and IntegrationMessageTypeRegistry registrations and versions.
- High dead-letter volume: Investigate lastError fields and requeue selectively after fixes.
- Slow polling: Review indexes and adjust batchSize/maxInFlightPerPoll.

**Section sources**
- [OutboxEntryRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/persistence/OutboxEntryRepositoryImpl.kt)
- [OutboxPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxPublisher.kt)
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

## Conclusion
The outbox implementation combines transactional persistence with robust asynchronous delivery, providing strong guarantees against data loss and ensuring eventual consistency across services. With configurable retry/backoff, dead-letter handling, and operational tooling, it supports scalable and observable event-driven architectures across diverse brokers and environments.

[No sources needed since this section summarizes without analyzing specific files]