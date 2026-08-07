# Testing Strategy & Implementation

<cite>
**Referenced Files in This Document**
- [build.gradle.kts](file://build.gradle.kts)
- [libs.versions.toml](file://gradle/libs.versions.toml)
- [test_spotless_pre_push.py](file://tests/tooling/test_spotless_pre_push.py)
- [pre-push](file://scripts/git-hooks/pre-push)
- [AccountingApplicationServiceTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingApplicationServiceTest.kt)
- [AccountingEventHandlerTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingEventHandlerTest.kt)
- [FakeAccountingRepositories.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/FakeAccountingRepositories.kt)
- [LedgerAccountUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/account/LedgerAccountUnitTest.kt)
- [AccountingPeriodUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodUnitTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [SettlementStatementUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementUnitTest.kt)
- [JournalEntryRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImplTest.kt)
- [SettlementStatementRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementRepositoryImplTest.kt)
- [AccountingJpaTestConfig.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/AccountingJpaTestConfig.kt)
- [AuthenticatedUserContextPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/context/AuthenticatedUserContextPropertyTest.kt)
- [AuthenticationDecisionPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/AuthenticationDecisionPropertyTest.kt)
- [BearerTokenExtractionPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/BearerTokenExtractionPropertyTest.kt)
- [TokenValidationErrorPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/TokenValidationErrorPropertyTest.kt)
- [OutboxEventPublisherPropertyTest.kt](file://j-store-common-spring/src/test/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisherPropertyTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)
- [AfterSalePostgresConcurrencyTest.kt](file://j-store-order-infrastructure/src/test/kotlin/com/jstore/order/integration/AfterSalePostgresConcurrencyTest.kt)
- [OrderAfterSaleSchemaMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/order/migration/OrderAfterSaleSchemaMigrationTest.kt)
- [OutboxFlywayMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/OutboxFlywayMigrationTest.kt)
- [OutboxOperationsControllerTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/operations/OutboxOperationsControllerTest.kt)
</cite>

## Update Summary
**Changes Made**
- Added comprehensive section on pre-push hook testing framework with Python-based automated repository setup and behavioral validation
- Updated tooling testing section to include Git hook testing strategies
- Enhanced quality gates section with incremental Spotless functionality testing
- Added new diagrams illustrating the pre-push hook testing workflow

## Table of Contents
1. Introduction
2. Project Structure
3. Core Components
4. Architecture Overview
5. Detailed Component Analysis
6. Tooling and Quality Gates
7. Dependency Analysis
8. Performance Considerations
9. Troubleshooting Guide
10. Conclusion

## Introduction
This document explains the testing strategy and implementation for the J-Store platform using a testing pyramid approach: unit tests, integration tests, property-based tests with Kotest, and comprehensive tooling tests including Git hook validation. It covers strategies for domain models, application services, infrastructure components, and development tooling, including test fixtures, fake repositories, mock services, and automated repository setup for Git hooks. Concrete examples include order lifecycle, commodity workflows, user authentication flows, event-driven components, outbox pattern, asynchronous processing, and incremental code formatting validation through pre-push hooks. Integration testing leverages embedded PostgreSQL via zt-database and Testcontainers-like patterns. The document also outlines performance testing, load testing, chaos engineering approaches, and comprehensive test governance with quality gates.

## Project Structure
The repository is organized by modules (accounting, goods, order, payment, user, fulfillment, common, authentication SDK, boot) with dedicated testing directories for different test types:
- src/main: production code
- src/test: unit and integration tests
- src/testFixtures: shared test data builders and fixtures
- tests/tooling: Python-based tooling and Git hook tests
- tests/governance: governance and compliance tests
- tests/skills: specification development tests

Key testing-related build configuration:
- Kotlin JVM and Spring plugins are applied at the root level.
- Version catalog centralizes dependencies including Kotest, Mockito, JUnit 5, Spring Boot Test, Redisson, and PostgreSQL driver.
- Gradle tasks for installing and managing Git hooks with incremental Spotless support.

```mermaid
graph TB
Root["Root Build Config"] --> AccountingApp["Accounting Application Tests"]
Root --> AccountingDomain["Accounting Domain Tests"]
Root --> AccountingInfra["Accounting Infrastructure Tests"]
Root --> AuthSDK["Authentication SDK Tests"]
Root --> CommonSpring["Common Spring Tests"]
Root --> OrderDomain["Order Domain Tests"]
Root --> OrderInfra["Order Infrastructure Tests"]
Root --> Boot["Boot Integration Tests"]
Root --> ToolingTests["Tooling & Hook Tests"]
ToolingTests --> PrePushHook["Pre-push Hook Validation"]
ToolingTests --> SpotlessCheck["Incremental Spotless Testing"]
```

**Diagram sources**
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)
- [test_spotless_pre_push.py:1-85](file://tests/tooling/test_spotless_pre_push.py#L1-L85)

**Section sources**
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)
- [libs.versions.toml:1-111](file://gradle/libs.versions.toml#L1-L111)

## Core Components
- Unit tests validate pure domain logic and small units without external dependencies.
- Integration tests verify persistence, transactions, concurrency, and cross-component interactions.
- Property-based tests use Kotest to assert invariants across randomized inputs.
- Test fixtures provide deterministic builders for complex aggregates.
- Fake repositories isolate application services from infrastructure.
- Mock services simulate external integrations and events.
- **New**: Git hook tests automate repository setup and validate pre-push behavior with incremental Spotless functionality.

Examples:
- Domain unit tests for accounting entities and journals.
- Application service tests using fake repositories.
- Property tests for authentication context isolation and outbox serialization.
- Concurrency tests against embedded PostgreSQL for after-sale capacity locking.
- **New**: Automated Git repository creation and pre-push hook execution testing.

**Section sources**
- [LedgerAccountUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/account/LedgerAccountUnitTest.kt)
- [AccountingPeriodUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodUnitTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [SettlementStatementUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementUnitTest.kt)
- [AccountingApplicationServiceTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingApplicationServiceTest.kt)
- [AccountingEventHandlerTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingEventHandlerTest.kt)
- [FakeAccountingRepositories.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/FakeAccountingRepositories.kt)
- [AuthenticatedUserContextPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/context/AuthenticatedUserContextPropertyTest.kt)
- [OutboxEventPublisherPropertyTest.kt](file://j-store-common-spring/src/test/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisherPropertyTest.kt)
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)
- [AfterSalePostgresConcurrencyTest.kt](file://j-store-order-infrastructure/src/test/kotlin/com/jstore/order/integration/AfterSalePostgresConcurrencyTest.kt)
- [test_spotless_pre_push.py:13-85](file://tests/tooling/test_spotless_pre_push.py#L13-L85)

## Architecture Overview
The testing architecture aligns with DDD and modular design with enhanced tooling integration:
- Domain layer tests ensure business rules and invariants.
- Application layer tests orchestrate use cases with fakes/mocks.
- Infrastructure layer tests validate persistence and transactional behavior.
- Boot-level tests cover migrations, controllers, and end-to-end scenarios.
- **New**: Tooling layer tests validate Git hooks, code formatting, and development workflow automation.

```mermaid
graph TB
subgraph "Domain Layer"
D1["Accounting Domain Tests"]
D2["Order Domain Tests"]
D3["Goods Domain Tests"]
end
subgraph "Application Layer"
A1["Accounting App Service Tests"]
A2["Order App Service Tests"]
A3["Goods App Service Tests"]
end
subgraph "Infrastructure Layer"
I1["Accounting JPA Tests"]
I2["Order Postgres Concurrency Tests"]
I3["Common Outbox Tests"]
end
subgraph "Boot Layer"
B1["Migration Tests"]
B2["Controller Contract Tests"]
B3["Outbox Operations Tests"]
end
subgraph "Tooling Layer"
T1["Git Hook Tests"]
T2["Spotless Integration Tests"]
T3["Repository Setup Automation"]
end
D1 --> A1
D2 --> A2
D3 --> A3
A1 --> I1
A2 --> I2
A3 --> I3
I1 --> B1
I2 --> B2
I3 --> B3
B1 --> T1
B2 --> T2
B3 --> T3
```

[No sources needed since this diagram shows conceptual workflow, not actual code structure]

## Detailed Component Analysis

### Accounting Domain Unit Tests
Focus on ledger accounts, journal entries, periods, and settlement statements. These tests assert state transitions, balance calculations, and period constraints.

```mermaid
classDiagram
class LedgerAccount {
+id
+code
+type
+direction
+subject
+status
+debit(amount) void
+credit(amount) void
+balance() Price
}
class JournalEntry {
+id
+lines
+sourceDocument
+post() void
}
class AccountingPeriod {
+id
+periodCode
+startDate
+endDate
+isOpen() bool
+lock() void
}
class SettlementStatement {
+id
+entries
+calculate() SettlementResult
}
LedgerAccount --> JournalEntry : "used by"
AccountingPeriod --> JournalEntry : "constrains posting"
SettlementStatement --> JournalEntry : "aggregates"
```

**Diagram sources**
- [LedgerAccountUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/account/LedgerAccountUnitTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [AccountingPeriodUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodUnitTest.kt)
- [SettlementStatementUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementUnitTest.kt)

**Section sources**
- [LedgerAccountUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/account/LedgerAccountUnitTest.kt)
- [JournalEntryUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryUnitTest.kt)
- [AccountingPeriodUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/journal/AccountingPeriodUnitTest.kt)
- [SettlementStatementUnitTest.kt](file://j-store-accounting-domain/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementUnitTest.kt)

### Accounting Application Service Tests with Fakes
Application services are tested with fake repositories to isolate orchestration logic and event handling.

```mermaid
sequenceDiagram
participant Test as "AccountingApplicationServiceTest"
participant Service as "AccountingApplicationService"
participant Repo as "FakeAccountingRepositories"
participant Handler as "AccountingEventHandler"
Test->>Service : executeUseCase(command)
Service->>Repo : save(JournalEntry)
Repo-->>Service : saved entity
Service->>Handler : publish event
Handler-->>Service : handle event
Service-->>Test : result verified
```

**Diagram sources**
- [AccountingApplicationServiceTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingApplicationServiceTest.kt)
- [AccountingEventHandlerTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingEventHandlerTest.kt)
- [FakeAccountingRepositories.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/FakeAccountingRepositories.kt)

**Section sources**
- [AccountingApplicationServiceTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingApplicationServiceTest.kt)
- [AccountingEventHandlerTest.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/AccountingEventHandlerTest.kt)
- [FakeAccountingRepositories.kt](file://j-store-accounting-application/src/test/kotlin/com/jstore/accounting/service/FakeAccountingRepositories.kt)

### Authentication Context Property Tests
Kotest property tests validate thread isolation and round-trip behavior of authenticated user context.

```mermaid
flowchart TD
Start(["Property Test Entry"]) --> GenerateUserId["Generate UserId via Arb"]
GenerateUserId --> SetContext["Set AuthenticatedUserContext"]
SetContext --> AssertRead["Assert getCurrentUserId matches"]
AssertRead --> ClearContext["Clear context"]
ClearContext --> AssertNull["Assert getCurrentUserIdOrNull is null"]
AssertNull --> End(["Exit"])
```

**Diagram sources**
- [AuthenticatedUserContextPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/context/AuthenticatedUserContextPropertyTest.kt)

**Section sources**
- [AuthenticatedUserContextPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/context/AuthenticatedUserContextPropertyTest.kt)
- [AuthenticationDecisionPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/AuthenticationDecisionPropertyTest.kt)
- [BearerTokenExtractionPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/BearerTokenExtractionPropertyTest.kt)
- [TokenValidationErrorPropertyTest.kt](file://j-store-authentication-spring-sdk/src/test/kotlin/com/jstore/authentication/spring/TokenValidationErrorPropertyTest.kt)

### Outbox Event Publisher Property Tests
Property tests assert that events are persisted with PENDING status and stable envelope fields.

```mermaid
sequenceDiagram
participant PropTest as "OutboxEventPublisherPropertyTest"
participant Publisher as "OutboxEventPublisher"
participant Serializer as "JacksonEventSerializer"
participant Repo as "OutboxEntryRepository (mock)"
participant Registry as "EventTypeRegistry"
PropTest->>PropTest : generate random DomainEvent
PropTest->>Publisher : publishEvent(event)
Publisher->>Serializer : serialize(event)
Publisher->>Repo : save(OutboxEntry with PENDING)
Repo-->>Publisher : saved entry
Publisher-->>PropTest : verify status and metadata
```

**Diagram sources**
- [OutboxEventPublisherPropertyTest.kt](file://j-store-common-spring/src/test/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisherPropertyTest.kt)

**Section sources**
- [OutboxEventPublisherPropertyTest.kt](file://j-store-common-spring/src/test/kotlin/com/jstore/common/framework/event/outbox/OutboxEventPublisherPropertyPropertyTest.kt)

### Order Test Fixtures
Shared fixtures construct valid orders with consistent item lists, addresses, and amounts for deterministic tests.

```mermaid
classDiagram
class OrderTestFixtures {
+testOrder(trade, payment, fulfillment, itemStatuses) OrderImpl
}
class OrderImpl {
+id
+merchantId
+buyerInfo
+items
+recipientInfo
+amountSnapshot
+tradeStatus
+paymentStatus
+fulfillmentStatus
}
OrderTestFixtures --> OrderImpl : "creates"
```

**Diagram sources**
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)

**Section sources**
- [OrderTestFixtures.kt](file://j-store-order-domain/src/testFixtures/kotlin/com/jstore/order/domain/order/OrderTestFixtures.kt)

### After-Sale Concurrency Integration Tests
Embedded PostgreSQL validates capacity locking, deadlock avoidance, optimistic versioning, idempotency keys, and rollback semantics.

```mermaid
flowchart TD
Start(["Concurrency Test Entry"]) --> SeedCapacity["Seed capacities"]
SeedCapacity --> ReserveA["Thread A reserves items sorted"]
SeedCapacity --> ReserveB["Thread B reserves items reverse sorted"]
ReserveA --> CheckAvailable{"Available?"}
ReserveB --> CheckAvailable
CheckAvailable --> |Yes| UpdateRequested["Update requested_quantity"]
CheckAvailable --> |No| FailReserve["Fail reservation"]
UpdateRequested --> Commit["Commit transaction"]
FailReserve --> Rollback["Rollback"]
Commit --> VerifyState["Verify final state"]
Rollback --> VerifyState
VerifyState --> End(["Exit"])
```

**Diagram sources**
- [AfterSalePostgresConcurrencyTest.kt](file://j-store-order-infrastructure/src/test/kotlin/com/jstore/order/integration/AfterSalePostgresConcurrencyTest.kt)

**Section sources**
- [AfterSalePostgresConcurrencyTest.kt](file://j-store-order-infrastructure/src/test/kotlin/com/jstore/order/integration/AfterSalePostgresConcurrencyTest.kt)

### Infrastructure Repository Tests
JPA repository implementations are validated with Spring Boot test configuration scanning entities and repositories.

```mermaid
classDiagram
class AccountingJpaTestConfig {
+@SpringBootConfiguration
+@EnableAutoConfiguration
+@EntityScan("com.jstore.accounting")
+@EnableJpaRepositories("com.jstore.accounting")
}
class JournalEntryRepositoryImplTest
class SettlementStatementRepositoryImplTest
AccountingJpaTestConfig <.. JournalEntryRepositoryImplTest : "uses"
AccountingJpaTestConfig <.. SettlementStatementRepositoryImplTest : "uses"
```

**Diagram sources**
- [AccountingJpaTestConfig.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/AccountingJpaTestConfig.kt)
- [JournalEntryRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImplTest.kt)
- [SettlementStatementRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementRepositoryImplTest.kt)

**Section sources**
- [AccountingJpaTestConfig.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/AccountingJpaTestConfig.kt)
- [JournalEntryRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/journal/JournalEntryRepositoryImplTest.kt)
- [SettlementStatementRepositoryImplTest.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/domain/settlement/SettlementStatementRepositoryImplTest.kt)

### Boot Integration Tests
Migration and controller contract tests ensure schema correctness and API stability.

```mermaid
sequenceDiagram
participant Test as "OutboxOperationsControllerTest"
participant Controller as "OutboxOperationsController"
participant DB as "Embedded Postgres / Flyway"
participant Migration as "OutboxFlywayMigrationTest"
Test->>Controller : POST /outbox/operations
Controller->>DB : query outbox entries
DB-->>Controller : list entries
Controller-->>Test : response validated
Migration->>DB : apply migration scripts
DB-->>Migration : schema verified
```

**Diagram sources**
- [OutboxOperationsControllerTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/operations/OutboxOperationsControllerTest.kt)
- [OutboxFlywayMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/OutboxFlywayMigrationTest.kt)
- [OrderAfterSaleSchemaMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/order/migration/OrderAfterSaleSchemaMigrationTest.kt)

**Section sources**
- [OutboxOperationsControllerTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/operations/OutboxOperationsControllerTest.kt)
- [OutboxFlywayMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/OutboxFlywayMigrationTest.kt)
- [OrderAfterSaleSchemaMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/order/migration/OrderAfterSaleSchemaMigrationTest.kt)

### Pre-push Hook Testing Framework
**New Section**: Comprehensive Python-based testing framework for Git pre-push hooks with automated repository setup and incremental Spotless validation.

```mermaid
sequenceDiagram
participant Test as "SpotlessPrePushHookTest"
participant TempRepo as "Temporary Git Repository"
participant Hook as "pre-push Script"
participant Spotless as "Gradle Spotless Plugin"
Test->>TempRepo : Create temp directory
Test->>TempRepo : Initialize git repo
Test->>TempRepo : Configure user settings
Test->>TempRepo : Add sample files
Test->>TempRepo : Create initial commit
Test->>Hook : Execute with diff input
Hook->>Hook : Analyze file changes
Hook->>Spotless : Run incremental check
Spotless-->>Hook : Format validation result
Hook-->>Test : Return exit code and output
Test->>Test : Validate expected behavior
```

**Diagram sources**
- [test_spotless_pre_push.py:13-85](file://tests/tooling/test_spotless_pre_push.py#L13-L85)
- [pre-push:1-86](file://scripts/git-hooks/pre-push#L1-86)
- [build.gradle.kts:85-98](file://build.gradle.kts#L85-L98)

**Section sources**
- [test_spotless_pre_push.py:1-85](file://tests/tooling/test_spotless_pre_push.py#L1-85)
- [pre-push:1-86](file://scripts/git-hooks/pre-push#L1-86)
- [build.gradle.kts:85-98](file://build.gradle.kts#L85-L98)

## Tooling and Quality Gates
**Updated Section**: Enhanced tooling testing with comprehensive Git hook validation and automated quality assurance.

### Git Hook Testing Strategy
- Automated repository creation with temporary directories
- POSIX shell compatibility validation
- Incremental file change detection for Java, Kotlin, and Gradle files
- Dry-run mode support for safe testing environments
- Behavioral validation for documentation-only pushes vs source code changes

### Incremental Spotless Integration
- File-specific formatting checks based on git diff analysis
- Automatic fallback to full repository scan when no specific files detected
- Support for both Unix and Windows environments
- Configurable Gradle executor selection (gradlew, gradlew.bat, or custom)
- Exit code validation and error message verification

### Quality Gate Integration
- Pre-push hook installation via Gradle tasks
- Automated executable permission setting
- Integration with existing spotlessInstallGitPrePushHook task
- Cross-platform compatibility testing

**Section sources**
- [test_spotless_pre_push.py:13-85](file://tests/tooling/test_spotless_pre_push.py#L13-L85)
- [pre-push:1-86](file://scripts/git-hooks/pre-push#L1-86)
- [build.gradle.kts:85-98](file://build.gradle.kts#L85-L98)

## Dependency Analysis
Testing dependencies are centralized in the version catalog with additional tooling requirements:
- Kotest runner, assertions, and property libraries.
- Mockito and Mockito-Kotlin for mocking.
- Spring Boot Test starter for integration tests.
- PostgreSQL driver for embedded database usage.
- Redisson for Redis integration where applicable.
- **New**: Python unittest framework for Git hook testing.
- **New**: subprocess and pathlib modules for automated repository management.

```mermaid
graph TB
Versions["libs.versions.toml"] --> Kotest["kotest-runner-junit5<br/>kotest-assertions-core<br/>kotest-property"]
Versions --> Mockito["mockito-core<br/>mockito-kotlin"]
Versions --> SpringTest["spring-boot-starter-test"]
Versions --> Postgres["postgresql"]
Versions --> Redisson["redisson-spring-boot-starter"]
Tooling["Python Dependencies"] --> Unittest["unittest<br/>subprocess<br/>pathlib<br/>tempfile"]
Tooling --> Shell["POSIX shell compatibility"]
```

**Diagram sources**
- [libs.versions.toml:1-111](file://gradle/libs.versions.toml#L1-L111)
- [test_spotless_pre_push.py:1-6](file://tests/tooling/test_spotless_pre_push.py#L1-L6)

**Section sources**
- [libs.versions.toml:1-111](file://gradle/libs.versions.toml#L1-L111)
- [test_spotless_pre_push.py:1-6](file://tests/tooling/test_spotless_pre_push.py#L1-L6)

## Performance Considerations
- Use embedded databases for fast, deterministic integration tests; avoid heavy setup in unit tests.
- Limit test iterations in property-based tests to reasonable bounds to keep CI times low.
- Parallelize independent tests but be cautious with shared resources like embedded databases.
- Profile critical paths with dedicated benchmarks outside the main test suite.
- For load testing, consider external tools (e.g., k6, Gatling) targeting API endpoints exposed by boot modules.
- Chaos engineering can be introduced via fault injection in integration tests (network failures, timeouts) to validate resilience.
- **New**: Git hook tests run in isolated temporary directories to prevent interference with working repositories.
- **New**: Incremental Spotless checks minimize formatting overhead by analyzing only changed files.

[No sources needed since this section provides general guidance]

## Troubleshooting Guide
- Ensure test configurations scan correct packages for entities and repositories.
- Validate Flyway migrations run in order and match expected schema changes.
- When using embedded Postgres, confirm connection parameters and schema initialization.
- For property tests, adjust iteration counts and generators if flakiness occurs.
- For async or event-driven tests, verify event publication and consumption sequencing.
- **New**: Verify POSIX shell availability for Git hook tests on different platforms.
- **New**: Check temporary directory permissions and cleanup procedures for automated repository setup.
- **New**: Validate git command availability and proper repository initialization in test environments.

**Section sources**
- [AccountingJpaTestConfig.kt](file://j-store-accounting-infrastructure/src/test/kotlin/com/jstore/accounting/AccountingJpaTestConfig.kt)
- [OutboxFlywayMigrationTest.kt](file://j-store-boot/src/test/kotlin/com/jstore/outbox/OutboxFlywayMigrationTest.kt)
- [AfterSalePostgresConcurrencyTest.kt](file://j-store-order-infrastructure/src/test/kotlin/com/jstore/order/integration/AfterSalePostgresConcurrencyTest.kt)
- [test_spotless_pre_push.py:14-16](file://tests/tooling/test_spotless_pre_push.py#L14-L16)

## Conclusion
The J-Store platform employs a robust testing strategy combining unit, integration, property-based tests, and comprehensive tooling validation. Domain logic is thoroughly validated, application services are isolated with fakes and mocks, and infrastructure behaviors are verified against embedded databases. Property-based testing ensures invariants under randomized inputs, while concurrency and transactional integrity are explicitly covered. The addition of automated Git hook testing with incremental Spotless functionality enhances development workflow reliability and code quality enforcement. The approach scales across modules and supports continuous integration with clear quality gates, ensuring consistent code formatting and preventing problematic commits from reaching shared repositories.

[No sources needed since this section summarizes without analyzing specific files]