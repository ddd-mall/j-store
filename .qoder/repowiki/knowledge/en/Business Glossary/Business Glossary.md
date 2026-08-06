---
kind: business_term
name: Business Glossary
category: business_term
scope:
    - '**'
---

### Outbox Pattern
- Definition：A design pattern for ensuring reliable message delivery in distributed systems by writing business data and events in the same database transaction. In this project, implemented via outbox_entry table with retry mechanisms, dead letter handling, and consumer idempotency.
- Aliases：outbox、transactional-outbox

### Saga Pattern
- Definition：A choreography-based approach to maintaining data consistency across multiple domains through compensating events. Each business action has a corresponding compensation action that reverses its effects if subsequent steps fail.
- Aliases：saga、compensation-events

### Domain Event Consumption
- Definition：The mechanism for tracking which consumers have processed which events to ensure idempotency. Uses domain_event_consumption table with composite primary key (listener_id, event_id) to prevent duplicate processing.
- Aliases：event-consumption、consumption-tracking

### Dead Letter Queue
- Definition：A holding area for messages that have failed processing after maximum retry attempts. Requires monitoring and manual intervention to prevent silent data inconsistencies.
- Aliases：dead-letter、dlq

### Bounded Context
- Definition：DDD concept defining explicit boundaries between different business domains (Order, Goods, User, Payment, Fulfillment, Accounting). Each context has its own model, language, and data store.
- Aliases：context、bounded-context

### Aggregate Root
- Definition：DDD entity that encapsulates business logic and ensures consistency within its boundary. Implements AggregateRoot interface and typically RecordsDomainEvents for generating domain events.
- Aliases：aggregate、aggregate-root

### ACL (Anti-Corruption Layer)
- Definition：Adapter layer that translates between different domain models when contexts interact. Prevents external domain models from polluting internal domain boundaries.
- Aliases：acl、anti-corruption-layer

### Integration Contracts
- Definition：Versioned command/event contracts shared between bounded contexts. Defined in j-store-integration-contracts module to maintain stable APIs across context boundaries.
- Aliases：contracts、integration-contracts
