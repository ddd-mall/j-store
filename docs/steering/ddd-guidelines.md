---
inclusion: fileMatch
fileMatchPattern: ['**/*.kt', '**/*.kts']
---

# DDD Architecture Guidelines — j-store

This project is a Kotlin/Spring Boot e-commerce system following Domain-Driven Design. All code changes must conform to these rules.

当前有界上下文、权威事实和交易一致性协议以 [领域建模说明](../domain-modeling.md) 为事实总览；本文件规定实现这些模型时必须遵守的代码和分层约束。

## Tech Stack

- Kotlin 2.4, Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL
- Build: Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`)
- Base package: `com.jstore`

## Project Module Layout

Each bounded context is split across Gradle modules:

| Module pattern | Layer | Example |
|---|---|---|
| `j-store-{context}-domain` | Pure Domain | `j-store-order-domain` |
| `j-store-{context}-application` | Framework-free Application | `j-store-order-application` |
| `j-store-{context}-infrastructure` | Infrastructure | `j-store-order-infrastructure` |
| `j-store-{context}-boot` | Deployment/composition (controllers, transactions, wiring) | `j-store-order-boot` |
| `j-store-common-core` | Shared domain framework | Framework base types (no Spring) |
| `j-store-common-spring` | Shared Spring integration | 通用地理地址等非消息工具 |
| `j-store-messaging-core` | Messaging ports | Framework-neutral integration contracts and transport SPI |
| `j-store-outbox-core` | Reliable delivery core | Outbox model, repository and routing ports (no Spring/JPA) |
| `j-store-messaging-local-spring` | Local messaging adapter | In-process Spring domain/integration buses |
| `j-store-outbox-spring` | Outbox runtime adapter | Spring Boot/JPA relay, persistence, scheduling and observability |

Dependency direction: `boot → application → domain → common-core` and `boot → infrastructure → domain`. Application modules that publish or consume integration messages may depend on `messaging-core`; only composition/infrastructure modules may depend on `outbox-spring` or concrete transport adapters. `outbox-spring → outbox-core → messaging-core` is one-way. Domain/application modules must NOT depend on infrastructure or boot modules. 每个有业务实现的有界上下文统一使用四模块形态；公共发布语言可保留独立的 `-api` 模块。

## Package Structure Within Modules

Domain module (`j-store-{context}-domain/src/main/kotlin/com/jstore/{context}/`):

```
domain/
  {aggregate}/              # One folder per aggregate
    {AggregateRoot}.kt      # Aggregate root interface or class
    {AggregateRoot}Impl.kt  # Concrete implementation (if interface-based)
    {AggregateRoot}Factory.kt
    {AggregateRoot}Repository.kt  # Interface only
    command/                # Command objects (CMDs)
    event/                  # Domain events
acl/                        # Anti-corruption layer interfaces
```

Application module (`j-store-{context}-application`) contains use-case ports, orchestration services and integration-message handlers. It must remain free of Spring/Jakarta/Hibernate imports. Spring transactions and bean configuration belong to `j-store-{context}-boot`.

Infrastructure module (`j-store-{context}-infrastructure/src/main/kotlin/com/jstore/{context}/`):

```
domain/
  {aggregate}/
    {AggregateRoot}RepositoryImpl.kt  # Repository implementation
    persistence/
      {Entity}PO.kt                  # JPA persistence objects
      {Entity}POJpaRepository.kt     # Spring Data JPA repositories
```

## Framework Base Types (j-store-common-core)

When creating domain objects, use these existing base types:

- `Entity<I : Identifier>` — base entity interface, requires `val id: I`
- `AggregateRoot<I : Identifier>` — aggregate consistency-boundary marker
- `RecordsDomainEvents` — pending-event snapshot and acknowledgement contract
- `EventRecordingAggregateRoot<I>` — aggregate base class with a private event collection and protected `raise()`
- `Identifier` — marker interface for typed identities
- `AggregateRepository<I : Identifier, A : AggregateRoot<I>>` — aggregate-only repository with `save(aggregate)` and `findById(id)`
- `Page<T>` / `SortedPage<T>` — query pagination wrappers under `com.jstore.common.query`
- `Result<T, E>` — custom sealed result type with `Success<T>` / `Failure<E>`, supports `map`, `onSuccess`, `onFailure`, `fold`, and explicit `getOrThrow(errorMapper)` at exception boundaries
- `BusinessError` — error type with `message`, `errorCode`, `httpCode`; use `CommonBusinessError` constants or define context-specific error objects
- `DomainEvent` — immutable event contract with stable ID, name, version, time and scalar aggregate reference metadata

## Coding Rules

### Single Responsibility Principle (SRP)
- Implementation code MUST follow the Single Responsibility Principle (SRP).
- Each module, class, function, or other cohesive unit MUST have one well-defined responsibility and one primary reason to change.
- Separate unrelated responsibilities instead of accumulating them in the same unit.

### Entities & Aggregates
- Entities are identified by a typed ID implementing `Identifier` (e.g., `data class OrderId(val value: Long) : Identifier`)
- Aggregate roots implement `AggregateRoot<{Id}>`; event-producing roots also implement `RecordsDomainEvents`, normally through `EventRecordingAggregateRoot`
- Pending event collections must remain private. Aggregate behavior records events through protected `raise()`; callers may only read snapshots and acknowledge stable event IDs after successful publication
- Entities must encapsulate business behavior — no anemic models (data-only classes with external service logic)
- Aggregates reference other aggregates by ID only, never by direct object reference
- Aggregates are consistency boundaries. Prefer one aggregate per write use case; when a local invariant requires multiple aggregates, declare and test the wider application transaction explicitly. Cross-context coordination uses integration messages rather than a distributed database transaction.

### Value Objects
- Must be immutable — use `data class` or `val`-only properties
- Encapsulate validation in `init` blocks
- Prefer value objects over primitives for domain concepts (use `Price` not `BigDecimal`, `PhoneNumber` not `String`, `OrderId` not `Long`)

### Commands
- Name with verb phrase + `CMD` or `Command` suffix (e.g., `NormalOrderCreateCMD`, `SKUAppendCMD`)
- Place in `domain/{aggregate}/command/` package
- Commands are data carriers — no business logic inside

### Domain Events
- Name with past-tense verb + `Event` suffix (e.g., `OrderCreatedEvent`, `CommodityPublishedEvent`)
- Place in `domain/{aggregate}/event/` package
- Implement `DomainEvent` directly and provide all stable envelope metadata at compile time
- Record inside the aggregate via protected `raise(event)`
- Application services persist the aggregate and then write a stable snapshot of pending events to the Outbox
- Clear the aggregate event queue only after every pending event was accepted by the publisher
- Events should carry necessary data, not entire aggregate objects

### Repositories
- Interface in domain module, implementation in infrastructure module
- Extend `AggregateRepository<{Id}, {AggregateRoot}>` from common-core
- Method signatures use domain objects only — no PO types, no SQL, no Spring-specific types
- Each aggregate root gets exactly one repository

### Factories
- Use when aggregate creation is complex (multiple dependencies, external service calls)
- Keep factories framework-free; construct and inject them from the boot module
- Responsible for producing a valid initial aggregate state
- Do NOT perform business orchestration — that belongs in application services

### Application Services
- Located in `service/` package within `j-store-{context}-application`
- Must remain framework-free and must not use Spring stereotypes or transaction annotations
- Expose inbound use-case interfaces; controllers and message handlers depend on those interfaces
- Orchestrate use cases: load aggregate → execute domain logic → save
- Return `Result<T, BusinessError>` for operations that can fail
- Do NOT contain business rules — delegate to domain objects

### Anti-Corruption Layer (ACL)
- Interfaces in `acl/` package of the consuming domain module
- Implementations in the infrastructure module
- Define context-local data types (e.g., `GoodsId`, `GoodsInfo` in order context)
- Convert external models to local domain models

### Infrastructure / Persistence
- PO classes use JPA annotations (`@Entity`, `@Table`, `@Column`)
- PO class names end with `PO` suffix (e.g., `OrderPO`, `OrderItemPO`)
- JPA repositories end with `POJpaRepository` suffix
- Repository implementations contain a `Converter` object for PO ↔ domain entity mapping
- Repository implementations persist aggregates only; they must not publish domain events or open a narrower independent transaction
- Mutating repository adapters should require an existing transaction (`MANDATORY`) where Spring is used

### Transactions And External Side Effects

- `j-store-{context}-boot` owns Spring transaction decorators around inbound use-case interfaces
- A write transaction spans aggregate persistence and Outbox writes; query use cases use read-only transactions
- Database rollback must preserve the aggregate's pending event queue for retry
- Redis, brokers, payment providers and other non-transactional resources are not part of the database atomic boundary; coordinate them through post-commit work, Outbox/inbox messages, or an explicitly designed compensation protocol

### Error Handling
- Use `Result<T, BusinessError>` (from `com.jstore.common.utils`) — not exceptions — for expected business failures
- Define context-specific error objects (e.g., `OrderErrors`, `InventoryErrors`) following the `CommonBusinessError` pattern
- Use `onFailure { return Failure(it) }` for early-return error propagation
- Do not unwrap `BusinessError` inside domain or application use cases. At an infrastructure boundary that must throw (for example, message redelivery), use `getOrThrow(::BusinessErrorException)` so the structured error remains available.

## Prohibited Patterns

1. Domain layer must NOT import Spring, JPA, Hibernate, or any infrastructure framework
2. No anemic models — entities must have behavior methods, not just data
3. No unbounded cross-aggregate mutation; when one use case must update multiple local aggregates, the application transaction and invariants must be explicit
4. No PO types in domain layer — POs exist only in infrastructure
5. No business logic in application services or controllers
6. No direct object references between aggregates — ID references only
7. No persistence details in repository interfaces (no PO, SQL, or framework pagination types)
8. No `var` on value objects — they must be immutable

## Naming Conventions

| Building Block | Pattern | Examples |
|---|---|---|
| Aggregate Root / Entity | Business noun | `Order`, `Spu`, `SalesOffer`, `StockPosition` |
| Entity Implementation | Noun + `Impl` | `OrderImpl`, `SpuImpl` |
| Value Object | Business noun | `OrderId`, `Money`, `Price`, `PhoneNumber` |
| Domain Event | Past-tense + `Event` | `OrderCreatedEvent`, `CommodityPublishedEvent`, `StockReservedEvent` |
| Command | Verb phrase + `CMD`/`Command` | `NormalOrderCreateCMD`, `CommodityCreateCmd` |
| Repository Interface | Root + `Repository` | `OrderRepository`, `SpuRepository` |
| Repository Impl | Root + `RepositoryImpl` | `OrderRepositoryImpl` |
| Application Service | Context + `Service` | `CommodityService`, `InventoryService` |
| Factory | Root + `Factory` | `OrderFactory`, `SpuFactory` |
| ACL Interface | External context + `Service` | `GoodsService`, `GeoAddressService` |
| Persistence Object | Entity + `PO` | `OrderPO`, `SkuPO` |
| JPA Repository | Entity + `POJpaRepository` | `OrderPOJpaRepository` |
| Error Constants | Context + `Errors` | `OrderErrors`, `InventoryErrors` |
