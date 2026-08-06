# Event Outbox & Messaging Schema

<cite>
**Referenced Files in This Document**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxDeadLetterOperationsRepository.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterOperationsRepository.kt)
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsDto.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsDto.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)
- [MessageConsumptionRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/messaging/MessageConsumptionRepository.kt)
- [MessageConsumptionRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/persistence/MessageConsumptionRepositoryImpl.kt)
- [TimerJobDeadQueueJpaPO.java](file://j-store-boot/src/main/java/com/jstore/order/expired/TimerJobDeadQueueJpaPO.java)
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
10. [Appendices](#appendices)

## Introduction
This document provides comprehensive data model and operational documentation for the event outbox and messaging infrastructure. It explains how domain events are persisted atomically with business transactions, delivered reliably to targets, retried on failures, and monitored for health. It also covers message serialization formats, type registries, dead letter handling, delivery target configuration, performance tuning, ordering guarantees, idempotency requirements, and operational procedures.

## Project Structure
The outbox and messaging subsystem spans common core abstractions, Spring-based runtime components, database schema migrations, and operational APIs:
- Core data model and repository interfaces reside in the common core module.
- Spring auto-configuration, scheduler, cleaner, monitor, and dead letter services live in the common spring module.
- Database schema is initialized via SQL migration scripts.
- Operational endpoints expose inspection and control capabilities.

```mermaid
graph TB
subgraph "Common Core"
OE["OutboxEntry.kt"]
OER["OutboxEntryRepository.kt"]
ODR["OutboxDeliveryRouter.kt"]
MCR["MessageConsumptionRepository.kt"]
end
subgraph "Common Spring"
OAP["OutboxAutoConfiguration.kt"]
OEP["OutboxEventPublisher.kt"]
OC["OutboxCleaner.kt"]
ODL["OutboxDeadLetterService.kt"]
ODLR["OutboxDeadLetterOperationsRepository.kt"]
OM["OutboxMonitor.kt"]
OH["OutboxOperationalHealth.kt"]
OP["OutboxProperties.kt"]
MCRI["MessageConsumptionRepositoryImpl.kt"]
end
subgraph "Boot (Operations)"
OOC["OutboxOperationsController.kt"]
OODT["OutboxOperationsDto.kt"]
OOP["OutboxOperationsProperties.kt"]
end
subgraph "Database"
DB["PostgreSQL<br/>outbox_entry + domain_event_consumption"]
end
OEP --> OER
OEP --> ODR
OC --> OER
ODL --> ODLR
OM --> OER
OH --> OER
OOC --> OER
OOC --> ODLR
MCRI --> DB
OER --> DB
ODLR --> DB
```

**Diagram sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxDeadLetterOperationsRepository.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterOperationsRepository.kt)
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsDto.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsDto.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)
- [MessageConsumptionRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/messaging/MessageConsumptionRepository.kt)
- [MessageConsumptionRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/persistence/MessageConsumptionRepositoryImpl.kt)

**Section sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

## Core Components
- Outbox entry model and status enumeration define the persistent representation of pending, in-progress, failed, and published messages.
- Repository interface abstracts persistence operations for outbox entries and consumption records.
- Delivery router determines where each event should be sent based on metadata such as delivery target and destination.
- Publisher integrates with transactional boundaries to persist outbox entries alongside business changes.
- Cleaner schedules periodic cleanup of successfully published entries.
- Dead letter service handles unrecoverable failures and persists them for manual intervention.
- Monitor and operational health provide metrics and readiness checks.
- Properties and auto-configuration wire up behavior and defaults.
- Operations controller exposes administrative endpoints for inspection and remediation.

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxDeadLetterOperationsRepository.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterOperationsRepository.kt)
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsDto.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsDto.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

## Architecture Overview
The outbox pattern ensures that domain events are first persisted into a dedicated table within the same transaction as business state changes. A background process claims eligible entries, serializes payloads, routes to delivery targets, updates status, and retries on transient failures. Unrecoverable errors are moved to a dead letter store for operator review. Consumption is tracked per listener to guarantee idempotency.

```mermaid
sequenceDiagram
participant App as "Application Service"
participant Pub as "OutboxEventPublisher"
participant Repo as "OutboxEntryRepository"
participant DB as "PostgreSQL"
participant Router as "OutboxDeliveryRouter"
participant Clean as "OutboxCleaner"
participant DL as "OutboxDeadLetterService"
App->>Pub : "Publish domain event"
Pub->>Repo : "Persist outbox entry (status=PENDING)"
Repo->>DB : "INSERT outbox_entry"
Note over Pub,DB : "Transaction commits with business state"
Clean->>Repo : "Claim PENDING/FAILED/IN_PROGRESS rows"
Repo-->>Clean : "Rows with locks"
Clean->>Router : "Resolve delivery target and destination"
Router-->>Clean : "Target config"
Clean->>DB : "Update status=IN_PROGRESS, set lock"
Clean->>Clean : "Serialize payload"
Clean-->>DB : "Mark PUBLISHED or FAILED"
alt "Unrecoverable error"
Clean->>DL : "Move to dead letter"
DL->>DB : "Persist dead letter record"
end
```

**Diagram sources**
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)

## Detailed Component Analysis

### Data Model: Outbox Entry and Consumption Records
- The outbox entry table stores all fields required for reliable delivery: identifiers, routing metadata, payload, aggregate context, timestamps, status, retry counters, locking information, and last error details.
- Indexes support efficient polling by status and creation time, claiming with next attempt scheduling, lock expiration recovery, cleanup of published entries, and filtering by delivery target.
- A separate consumption tracking table records per-listener idempotency keys to prevent duplicate processing.

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

### Outbox Entry Entity and Status
- The entity models the row structure and behaviors associated with an outbox entry, including status transitions and validation rules.
- Status values include PENDING, IN_PROGRESS, FAILED, and PUBLISHED, enabling robust lifecycle management.

```mermaid
classDiagram
class OutboxEntry {
+string id
+string eventType
+string eventId
+string eventClassName
+int eventVersion
+string messageKind
+string deliveryTarget
+string destination
+string partitionKey
+string correlationId
+string causationId
+string tenantId
+string payload
+string aggregateType
+string aggregateId
+datetime occurredAt
+string status
+datetime createdAt
+datetime updatedAt
+int retryCount
+datetime nextAttemptAt
+string lockedBy
+datetime lockedAt
+datetime lockedUntil
+string lastError
}
class OutboxEntryStatus {
<<enumeration>>
+PENDING
+IN_PROGRESS
+FAILED
+PUBLISHED
}
OutboxEntry --> OutboxEntryStatus : "uses"
```

**Diagram sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)

### Repository Abstraction
- The repository defines methods to persist, claim, update, and query outbox entries and consumption records.
- It encapsulates transactional semantics and indexing strategies used by the publisher and cleaner.

```mermaid
classDiagram
class OutboxEntryRepository {
+save(entry) void
+claimEligible(limit) OutboxEntry[]
+markInProgress(id, locker) bool
+markPublished(id) void
+markFailed(id, error) void
+cleanupPublished(before) int
+recordConsumption(listenerId, eventId) void
+isConsumed(listenerId, eventId) bool
}
```

**Diagram sources**
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)

**Section sources**
- [OutboxEntryRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntryRepository.kt)

### Delivery Routing
- The router resolves the appropriate delivery target and destination using metadata such as delivery target and destination fields.
- It supports multi-target routing and can be extended for custom routing logic.

```mermaid
flowchart TD
Start(["Route Event"]) --> ReadMeta["Read delivery_target and destination"]
ReadMeta --> Resolve{"Target configured?"}
Resolve --> |Yes| BuildConfig["Build delivery config"]
Resolve --> |No| Fallback["Use default local delivery"]
BuildConfig --> Return["Return target config"]
Fallback --> Return
```

**Diagram sources**
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)

**Section sources**
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)

### Publisher Integration
- The publisher integrates with application transactions to persist outbox entries atomically with business state changes.
- It sets initial status to PENDING and assigns identifiers and metadata.

```mermaid
sequenceDiagram
participant App as "Application Service"
participant Pub as "OutboxEventPublisher"
participant Repo as "OutboxEntryRepository"
participant DB as "PostgreSQL"
App->>Pub : "publish(event)"
Pub->>Repo : "persist(PENDING)"
Repo->>DB : "INSERT outbox_entry"
DB-->>Repo : "OK"
Repo-->>Pub : "entry saved"
Note over Pub,DB : "Transaction commits together with business changes"
```

**Diagram sources**
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)

**Section sources**
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)

### Cleaner and Retry Mechanism
- The cleaner periodically claims eligible entries based on status and next attempt scheduling.
- It applies optimistic locking and sets short-lived locks to avoid stuck processing.
- On success, it marks entries as PUBLISHED; on failure, it increments retry count and schedules next attempt.

```mermaid
flowchart TD
Start(["Claim Cycle"]) --> Claim["Claim eligible rows"]
Claim --> Lock["Acquire lock per row"]
Lock --> Serialize["Serialize payload"]
Serialize --> Deliver["Deliver to target"]
Deliver --> Success{"Success?"}
Success --> |Yes| MarkPublished["Mark PUBLISHED"]
Success --> |No| RetryCheck{"Retry limit reached?"}
RetryCheck --> |No| ScheduleNext["Update next_attempt_at"]
RetryCheck --> |Yes| DeadLetter["Move to dead letter"]
MarkPublished --> End(["Done"])
ScheduleNext --> End
DeadLetter --> End
```

**Diagram sources**
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)

**Section sources**
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)

### Dead Letter Handling
- Unrecoverable failures are persisted to a dead letter store for manual inspection and reprocessing.
- The dead letter service provides operations to list, inspect, and replay dead letter entries.

```mermaid
classDiagram
class OutboxDeadLetterService {
+moveToDeadLetter(entry, error) void
+listDeadLetters(filter) DeadLetterRecord[]
+replay(deadLetterId) void
}
class OutboxDeadLetterOperationsRepository {
+save(record) void
+findByFilter(filter) DeadLetterRecord[]
+deleteById(id) void
}
OutboxDeadLetterService --> OutboxDeadLetterOperationsRepository : "persists/replays"
```

**Diagram sources**
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxDeadLetterOperationsRepository.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterOperationsRepository.kt)

**Section sources**
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxDeadLetterOperationsRepository.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterOperationsRepository.kt)

### Monitoring and Health
- Monitor exposes metrics such as counts by status, retry rates, and latency indicators.
- Operational health aggregates key signals to determine pipeline health and readiness.

```mermaid
classDiagram
class OutboxMonitor {
+countByStatus() Map~String,int~
+retryRate() double
+avgProcessingTime() double
}
class OutboxOperationalHealth {
+checkHealth() HealthStatus
+getMetrics() Map~String,Object~
}
OutboxOperationalHealth --> OutboxMonitor : "aggregates"
```

**Diagram sources**
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)

**Section sources**
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)

### Configuration and Auto-Configuration
- Properties define defaults for batch sizes, intervals, retry limits, and timeouts.
- Auto-configuration wires up the cleaner, monitor, and other components with sensible defaults.

```mermaid
classDiagram
class OutboxProperties {
+int batchSize
+long pollIntervalMs
+int maxRetries
+long lockTimeoutMs
+string defaultDeliveryTarget
}
class OutboxAutoConfiguration {
+configureCleaner() void
+configureMonitor() void
+registerDefaults() void
}
OutboxAutoConfiguration --> OutboxProperties : "reads"
```

**Diagram sources**
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)

**Section sources**
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)
- [OutboxAutoConfiguration.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt)

### Operational API
- The operations controller exposes endpoints for inspecting outbox entries, viewing dead letters, and triggering maintenance tasks.
- DTOs define request/response shapes for clarity and stability.

```mermaid
classDiagram
class OutboxOperationsController {
+listEntries(query) OutboxEntryDTO[]
+listDeadLetters(filter) DeadLetterDTO[]
+replayDeadLetter(id) void
+triggerCleanup() void
}
class OutboxOperationsDto {
<<data transfer objects>>
}
class OutboxOperationsProperties {
<<admin settings>>
}
OutboxOperationsController --> OutboxOperationsDto : "uses"
```

**Diagram sources**
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsDto.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsDto.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

**Section sources**
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxOperationsDto.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsDto.kt)
- [OutboxOperationsProperties.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt)

### Message Consumption Tracking
- Consumption records ensure idempotent processing per listener and event identity.
- The repository abstraction supports recording and checking consumption status.

```mermaid
classDiagram
class MessageConsumptionRepository {
+recordConsumption(listenerId, eventId) void
+isConsumed(listenerId, eventId) bool
}
class MessageConsumptionRepositoryImpl {
+recordConsumption(listenerId, eventId) void
+isConsumed(listenerId, eventId) bool
}
MessageConsumptionRepository <|.. MessageConsumptionRepositoryImpl : "implements"
```

**Diagram sources**
- [MessageConsumptionRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/messaging/MessageConsumptionRepository.kt)
- [MessageConsumptionRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/persistence/MessageConsumptionRepositoryImpl.kt)

**Section sources**
- [MessageConsumptionRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/messaging/MessageConsumptionRepository.kt)
- [MessageConsumptionRepositoryImpl.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/persistence/MessageConsumptionRepositoryImpl.kt)

### Conceptual Overview
The outbox pattern decouples event publishing from delivery, ensuring durability and eventual consistency. Events are first stored in the outbox table within the same transaction as business state changes. A background process claims and delivers events, updating status and handling retries. Unrecoverable failures are isolated in a dead letter store. Consumption tracking prevents duplicate processing.

```mermaid
flowchart TD
Start(["Business Transaction"]) --> PersistOutbox["Persist outbox entry"]
PersistOutbox --> CommitTx["Commit transaction"]
CommitTx --> BackgroundClaim["Background claims entries"]
BackgroundClaim --> Deliver["Deliver to target"]
Deliver --> UpdateStatus["Update status"]
UpdateStatus --> Idempotency["Record consumption"]
Idempotency --> End(["Consistent State"])
```

[No sources needed since this diagram shows conceptual workflow, not actual code structure]

## Dependency Analysis
The outbox system composes several modules with clear responsibilities:
- Core abstractions define data models and repository contracts.
- Spring components implement runtime behavior, scheduling, and monitoring.
- Boot module exposes operational endpoints.
- Database schema provides durable storage and indexes for performance.

```mermaid
graph TB
Core["Common Core"] --> Spring["Common Spring"]
Spring --> Boot["Boot Operations"]
Core --> DB["PostgreSQL"]
Spring --> DB
Boot --> DB
```

**Diagram sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

**Section sources**
- [OutboxEntry.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEntry.kt)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)

## Performance Considerations
- Use targeted indexes for claiming and cleanup queries to minimize full table scans.
- Tune batch size and poll interval to balance throughput and resource usage.
- Set appropriate lock timeouts to recover from crashed workers quickly.
- Partition large tables if necessary and archive old published entries periodically.
- Monitor retry rates and adjust max retries and backoff strategies accordingly.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
- Inspect outbox entries by status to identify stuck or failing messages.
- Review last error fields for diagnostic clues.
- Use dead letter operations to replay or analyze unrecoverable failures.
- Check consumption records to verify idempotency and detect duplicates.
- Validate configuration properties for correct defaults and runtime overrides.

**Section sources**
- [OutboxOperationsController.kt](file://j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsController.kt)
- [OutboxDeadLetterService.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeadLetterService.kt)
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)

## Conclusion
The event outbox and messaging infrastructure provides a robust foundation for reliable, scalable, and observable event-driven communication across services. By persisting events atomically, delivering them with retries, isolating failures, and tracking consumption, the system ensures strong consistency guarantees while maintaining high availability and operational clarity.

[No sources needed since this section summarizes without analyzing specific files]

## Appendices

### Message Serialization Formats and Type Registries
- Payloads are serialized as strings within the outbox entry payload field.
- Event type metadata includes event class name and version to support evolution and compatibility.
- Type registries can be implemented around the publisher and router to map event types to serializers and handlers.

**Section sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)
- [OutboxEventPublisher.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisher.kt)

### Delivery Target Configuration and Routing
- Delivery target and destination fields guide routing decisions.
- Default local delivery is supported when no explicit target is configured.
- Custom routers can extend routing logic for advanced scenarios.

**Section sources**
- [OutboxDeliveryRouter.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxDeliveryRouter.kt)
- [OutboxProperties.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt)

### Ordering Guarantees and Idempotency
- Ordering can be enforced using partition keys correlated with aggregate identifiers.
- Idempotency is ensured through event IDs and per-listener consumption records.
- Consumers should validate idempotency keys before processing.

**Section sources**
- [06-outbox-entry.sql](file://docker/postgres/init/06-outbox-entry.sql)
- [MessageConsumptionRepository.kt](file://j-store-common-core/src/main/kotlin/com/jstore/common/framework/messaging/MessageConsumptionRepository.kt)

### Monitoring and Operational Procedures
- Use operational health checks to assess pipeline status.
- Monitor metrics for counts by status, retry rates, and processing times.
- Perform routine cleanup of published entries and review dead letters regularly.

**Section sources**
- [OutboxOperationalHealth.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt)
- [OutboxMonitor.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxMonitor.kt)
- [OutboxCleaner.kt](file://j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxCleaner.kt)

### Related Dead Queue Patterns
- A separate timer job dead queue exists for scheduled jobs, demonstrating a similar pattern for handling failures outside the outbox flow.

**Section sources**
- [TimerJobDeadQueueJpaPO.java](file://j-store-boot/src/main/java/com/jstore/order/expired/TimerJobDeadQueueJpaPO.java)