---
kind: logging_system
name: Custom Logger Facade over SLF4J with Marker-Based Structured Logging
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

The j-store project implements a thin, pluggable logging facade built around SLF4J rather than using SLF4J directly throughout the codebase. The system is centered in `j-store-common-core` under `com.jstore.common.logging` and provides a consistent logging API across all modules.

**Core architecture:**
- `Logger` interface defines the canonical logging API with debug/info/warn/error methods supporting message, formatted string, throwable, and vararg variants, plus an `isDebugEnabled()` check for performance-sensitive callers.
- `LoggerFactory` is a singleton that auto-discovers and instantiates the concrete logger implementation via reflection. On startup it attempts to use SLF4J through `Slf4jSimpleImpl`, falling back gracefully if unavailable.
- Two SLF4J-backed implementations exist: `Slf4jLoggerImpl` (wraps standard `org.slf4j.Logger`) and `Slf4jLocationAwareLoggerImpl` (uses `LocationAwareLogger` when available). The factory detects `LocationAwareLogger` support at runtime and selects the appropriate implementation automatically.
- `LogException` is a custom runtime exception thrown when logger initialization or instantiation fails.

**Structured logging conventions:**
- A constant marker `JSTORE` is defined in `LoggerFactory.MARKER` and used by the location-aware implementation to tag all log entries, enabling downstream filtering and enrichment of structured logs.
- The location-aware path uses `MarkerFactory.getMarker(LoggerFactory.MARKER)` as a static field, ensuring consistent marker reuse across all log calls.
- Log levels follow the standard SLF4J hierarchy: debug, info, warn, error — no custom levels are defined.

**Usage pattern across the codebase:**
- Every module imports `com.jstore.common.logging.LoggerFactory` and obtains a logger via `LoggerFactory.getLogger(this::class)` or `LoggerFactory.getLogger(javaClass)`, never calling SLF4J directly.
- This pattern appears consistently across domain services, application services, infrastructure components, and utility classes (e.g., `SnowFlakSequence`, `AbstractFactory`, `JsonUtils`, event handlers in goods/order modules).
- The convention is to declare a private `val log = LoggerFactory.getLogger(...)` companion object or instance field and use it throughout the class.

**Configuration and sinks:**
- Log level configuration is delegated entirely to Spring Boot's SLF4J integration. The only logging-related property observed is `logging.level.root=info` in `application-local.properties`.
- No custom log appenders, formatters, or structured JSON output is configured within the project; the actual sink behavior depends on whatever SLF4J binding (logback/log4j2) is present at runtime.
- The `Slf4jSimpleImpl` constructor name references suggest there may have been an intention to support a simple console-only fallback (`Slf4jSimpleImpl`), but the current code only wires `Slf4jSimpleImpl` which wraps SLF4J — this appears to be a naming artifact rather than a separate implementation.

**Design decisions:**
- The facade abstraction allows swapping logging backends without changing business code, though only SLF4J is currently supported.
- Location-aware logging is preferred when available to capture accurate source file/line information in structured outputs.
- The marker-based approach enables centralized log enrichment and filtering at the infrastructure layer without requiring every caller to pass contextual fields.