The module is a pure Kotlin/Java library built with Gradle (`build.gradle.kts`) targeting JVM 25. It exposes a flat package `com.jstore.common` organized into cohesive sub-packages:
- `framework`: core DDD primitives — `Entity<I:Identify>`, `Repository`, `Page`, `Properties`, and the `AgreeGate` marker.
- `framework/event`: in-process domain event infrastructure centered on `DomainEventBus` (publish/register/unregister) plus `DomainEventPublisher` for transactional publishing, listener registration via `DomainEventListenerRegistry`, and an outbox subsystem (`OutboxEntry`, `OutboxEntryRepository`, `EventSerializer`, `EventTypeRegistry`, `EventUpcaster`) for reliable async delivery.
- `geo`: immutable address value objects (`I18nGeoAddress`, `AddressComponent`, `CountryCode`, `DivisionLevel`) with Jackson serialization support and a `CountryAddressProvider` SPI; Chinese-specific helpers live under `chinese/`.
- `logging`: abstract `Logger` interface with `Slf4jImpl` and `Slf4jLocationAwareLoggerImpl` implementations behind `LoggerFactory`, decoupling callers from SLF4J.
- `persistent`: Snowflake ID generation (`SnowFlakeId` annotation + `SnowFlakSequence`).
- `properties`: typed value types `Id`, `PhoneNumber`, `Price`.
- `utils`: functional `Result<T,E>` sealed class (Rust-style combinators), concurrent `ListenableFuture` family, JSON/string helpers, caching, and factories.

Dependency direction is one-way: higher-level packages (event, geo) depend only on framework and utils; nothing depends back on them. Tests mirror the source layout under `src/test/kotlin` and `src/test/java`, using Kotest property tests alongside JUnit 5.