---
kind: logging_system
name: Custom Logger Abstraction over SLF4J with JSTORE Marker
category: logging_system
scope:
    - '**'
source_files:
    - j-store-common-core/src/main/kotlin/com/jstore/common/logging/Logger.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/logging/LoggerFactory.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/logging/LogException.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/logging/slf4j/Slf4jImpl.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/logging/slf4j/Slf4jLocationAwareLoggerImpl.kt
    - j-store-boot/src/main/resources/application-local.properties
---

The J-Store platform defines a thin, framework-agnostic logging abstraction in `j-store-common-core` that delegates to SLF4J at runtime. The system is organized around three core components:

**Abstraction layer**
- `com.jstore.common.logging.Logger` — a minimal interface exposing `debug`, `info`, `warn`, `error` (each with `msg`, `format+arg`, `format+throwable`, `format+varargs`) plus `isDebugEnabled()`.
- `com.jstore.common.logging.LoggerFactory` — an object that discovers and instantiates the concrete `Logger` implementation via reflection. It defaults to `Slf4jSimpleImpl` and throws `LogException` if no implementation can be set up.
- `com.jstore.common.logging.LogException` — a runtime exception used when logger initialization fails.

**SLF4J implementations**
- `Slf4jSimpleImpl` — resolves the underlying `org.slf4j.Logger` and dispatches all calls through either `Slf4jLoggerImpl` or `Slf4jLocationAwareLoggerImpl` depending on whether the underlying logger implements `LocationAwareLogger`.
- `Slf4jLocationAwareLoggerImpl` — uses the `LocationAwareLogger.log(Marker, FQCN, level, ...)` API so that file/line information is reported from the *caller* class rather than the adapter. It attaches a constant `Marker` named `"JSTORE"` (defined as `LoggerFactory.MARKER`) to every log entry, enabling downstream filtering by marker.
- `Slf4jLoggerImpl` — a fallback wrapper for non-`LocationAwareLogger` backends; it also handles Kotlin's array-vararg edge cases where a single `Array<*>` argument could otherwise be misinterpreted.

**Usage pattern across the codebase**
Every module obtains a logger via `LoggerFactory.getLogger(SomeClass::class)` or `LoggerFactory.getLogger(javaClass)`. This pattern is used consistently in common utilities (`AbstractFactory`, `JsonUtils`, `SnowFlakSequence`), Spring infrastructure (`OutboxPublisher`, `OutboxCleaner`, `SpringDomainEventMulticasterGuard`, `ChinaAddressProvider`), and application services (e.g., `InventoryConfirmEventHandler`). There is no direct use of `org.slf4j.Logger` in domain or application code — all logging goes through the custom `Logger` interface.

**Configuration and sinks**
- Log levels are configured through standard Spring Boot properties. The local profile sets `logging.level.root=info` (`application-local.properties`). No dedicated logback/log4j2 configuration files were found in this branch; the project relies on Spring Boot's default SLF4J binding.
- The `JSTORE` marker is available for filtering but is not referenced in any external configuration in this branch.

**Design decisions**
- The abstraction isolates the rest of the codebase from the concrete logging backend, allowing the implementation to be swapped without touching business code.
- Location-aware logging is preferred when the underlying SLF4J implementation supports it, ensuring stack traces point to the actual caller.
- A single static marker (`JSTORE`) is applied uniformly, providing a simple hook for structured filtering or enrichment in downstream log processors.