---
kind: business_term
name: Business Glossary
category: business_term
scope:
    - '**'
---

### Outbox Pattern
- Definition：A design pattern ensuring reliable message delivery by writing business data and events in the same database transaction. The outbox table stores pending events that are later published asynchronously, guaranteeing at-least-once delivery without distributed transactions.
- Aliases：outbox、transactional outbox

### Domain Event
- Definition：An immutable record of something that happened in a domain, implemented through ExplicitDomainEvent interface with stable event contracts including eventId, eventName, eventVersion, and aggregate information. Events are published within business transactions and processed asynchronously.
- Aliases：domain event、ExplicitDomainEvent

### Saga Compensation
- Definition：A choreography-based approach to maintaining consistency across domains using compensating events. When a step fails, compensating events trigger rollback actions in other domains (e.g., OrderStockInsufficientEvent triggers order cancellation). Each positive action must have a corresponding compensation action.
- Aliases：compensation、saga、compensating event

### Consumer Idempotency
- Definition：Guarantee that processing the same event multiple times produces the same result. Implemented through domain_event_consumption table with (listener_id, event_id) primary key, ensuring each listener processes each event exactly once even with at-least-once delivery.
- Aliases：idempotency、consumer idempotency、event deduplication

### Dead Letter Queue
- Definition：Storage for events that failed processing after exhausting retry attempts. Events move to DEAD_LETTER status when retry_count reaches maximum, requiring manual intervention or automated reprocessing. Must have monitoring and alerting configured.
- Aliases：dead letter、DEAD_LETTER、DLQ

### Event Versioning
- Definition：Mechanism for handling evolution of event schemas over time. Each event has an eventVersion field, and EventUpcaster chain transforms older payload formats to target versions during deserialization. Prevents breaking changes when event structures evolve.
- Aliases：event version、versioning、upcasting

### ACL Event
- Definition：Anti-Corruption Layer events that translate between different domain models across service boundaries. Examples include OrderStockConfirmedEvent, OrderStockInsufficientEvent, and AfterSaleStockRestoreRequestedEvent that maintain domain isolation while enabling cross-domain communication.
- Aliases：ACL、anti-corruption layer、boundary event

### Relay
- Definition：The outbox relay process that polls outbox_entry table, claims pending events, delivers them to listeners within a transaction, and updates consumption records. Handles retry logic, locking, and dead letter routing with IN_PROGRESS state and lock timeout recovery.
- Aliases：outbox relay、delivery relay

### Aggregate
- Definition：A consistency boundary in DDD containing related domain objects and business rules. Transactions operate within aggregate boundaries, and events carry aggregateType and aggregateId for correlation and ordering guarantees.
- Aliases：aggregate root、consistency boundary

### Final Consistency
- Definition：Consistency model where systems eventually converge to the correct state through reliable message delivery, consumer idempotency, and compensation mechanisms. Trade-off for scalability and availability in distributed systems.
- Aliases：eventual consistency、最终一致
