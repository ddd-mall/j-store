---
inclusion: fileMatch
fileMatchPattern: ['**/*.kt', '**/*.kts']
---

# DDD Architecture Guidelines — j-store

This project is a Kotlin/Spring Boot e-commerce system following Domain-Driven Design. All code changes must conform to these rules.

## Tech Stack

- Kotlin 2.3, Java 25, Spring Boot 3.5, Spring Data JPA, PostgreSQL
- Build: Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`)
- Base package: `com.jstore`

## Project Module Layout

Each bounded context is split across Gradle modules:

| Module pattern | Layer | Example |
|---|---|---|
| `j-store-{context}` | Domain + Application | `j-store-order`, `j-store-goods` |
| `j-store-{context}-infrastructure` | Infrastructure | `j-store-order-infrastructure` |
| `j-store-{context}-boot` | Interface/API (controllers) | `j-store-order-boot` |
| `j-store-common-core` | Shared domain framework | Framework base types (no Spring) |
| `j-store-common-spring` | Shared Spring integration | Spring-specific utilities |

Dependency direction: `boot → infrastructure → domain module → common-core`. Domain modules must NOT depend on infrastructure or boot modules.

## Package Structure Within Modules

Domain module (`j-store-{context}/src/main/kotlin/com/jstore/{context}/`):

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
service/                    # Application services
config/                     # Bean configuration
```

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

- `Entity<I : Identify>` — base entity interface, requires `val id: I`
- `AgreeGate<I : Identify>` — aggregate root interface, extends `Entity`, provides `domainEventQueue`, `publishEvent()`, `getDomainEvent()`
- `Identify` — marker interface for identity types (extends `Properties`)
- `Repository<I : Identify, E : Entity<I>>` — base repository with `save(entity)` and `findById(id)`
- `Page<T>` / `SortedPage<T>` — pagination wrappers
- `Result<T, E>` — custom sealed result type with `Success<T>` / `Failure<E>`, supports `map`, `onSuccess`, `onFailure`, `fold`, `getOrThrow`
- `BusinessError` — error type with `message`, `errorCode`, `httpCode`; use `CommonBusinessError` constants or define context-specific error objects
- `DomainEvent` — marker interface with `val source: Any`

## Coding Rules

### Entities & Aggregates
- Entities are identified by a typed ID implementing `Identify` (e.g., `data class OrderId(val value: Long) : Identify`)
- Aggregate roots implement `AgreeGate<{Id}>` and carry a `domainEventQueue`
- Entities must encapsulate business behavior — no anemic models (data-only classes with external service logic)
- Aggregates reference other aggregates by ID only, never by direct object reference
- One transaction modifies one aggregate; cross-aggregate coordination uses domain events

### Value Objects
- Must be immutable — use `data class` or `val`-only properties
- Encapsulate validation in `init` blocks
- Prefer value objects over primitives for domain concepts (use `Price` not `BigDecimal`, `PhoneNumber` not `String`, `OrderId` not `Long`)

### Commands
- Name with verb phrase + `CMD` or `Command` suffix (e.g., `NormalOrderCreateCMD`, `SKUAppendCMD`)
- Place in `domain/{aggregate}/command/` package
- Commands are data carriers — no business logic inside

### Domain Events
- Name with past-tense verb + `Event` suffix (e.g., `OrderCreatedEvent`, `CommodityOnSaleEvent`)
- Place in `domain/{aggregate}/event/` package
- Implement `DomainEvent` interface
- Publish via `aggregateRoot.publishEvent(event)` — events are dispatched during repository `save()`
- Events should carry necessary data, not entire aggregate objects

### Repositories
- Interface in domain module, implementation in infrastructure module
- Extend `Repository<{Id}, {Entity}>` from common-core
- Method signatures use domain objects only — no PO types, no SQL, no Spring-specific types
- Each aggregate root gets exactly one repository

### Factories
- Use when aggregate creation is complex (multiple dependencies, external service calls)
- Annotate with `@Service` when using Spring DI
- Responsible for producing a valid initial aggregate state
- Do NOT perform business orchestration — that belongs in application services

### Application Services
- Located in `service/` package within the domain module
- Annotate with `@Service`
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
- Domain events are persisted and published within the repository `save()` method inside a `@Transactional` boundary

### Error Handling
- Use `Result<T, BusinessError>` (from `com.jstore.common.utils`) — not exceptions — for expected business failures
- Define context-specific error objects (e.g., `OrderErrors`, `StorageErrors`) following the `CommonBusinessError` pattern
- Use `onFailure { return Failure(it) }` for early-return error propagation

## Prohibited Patterns

1. Domain layer must NOT import Spring, JPA, Hibernate, or any infrastructure framework
2. No anemic models — entities must have behavior methods, not just data
3. No cross-aggregate mutation in a single transaction
4. No PO types in domain layer — POs exist only in infrastructure
5. No business logic in application services or controllers
6. No direct object references between aggregates — ID references only
7. No persistence details in repository interfaces (no PO, SQL, or framework pagination types)
8. No `var` on value objects — they must be immutable

## Naming Conventions

| Building Block | Pattern | Examples |
|---|---|---|
| Aggregate Root / Entity | Business noun | `Order`, `Spu`, `Inventory` |
| Entity Implementation | Noun + `Impl` | `NormalOrderImpl` |
| Value Object | Business noun | `OrderId`, `Money`, `Price`, `PhoneNumber` |
| Domain Event | Past-tense + `Event` | `OrderCreatedEvent`, `CommodityOnSaleEvent` |
| Command | Verb phrase + `CMD`/`Command` | `NormalOrderCreateCMD`, `CommodityCreateCmd` |
| Repository Interface | Root + `Repository` | `OrderRepository`, `SpuRepository` |
| Repository Impl | Root + `RepositoryImpl` | `OrderRepositoryImpl` |
| Application Service | Context + `Service` | `CommodityService`, `InventoryService` |
| Factory | Root + `Factory` | `NormalOrderFactory`, `SpuFactory` |
| ACL Interface | External context + `Service` | `GoodsService`, `GeoAddressService` |
| Persistence Object | Entity + `PO` | `OrderPO`, `SkuPO` |
| JPA Repository | Entity + `POJpaRepository` | `OrderPOJpaRepository` |
| Error Constants | Context + `Errors` | `OrderErrors`, `StorageErrors` |
