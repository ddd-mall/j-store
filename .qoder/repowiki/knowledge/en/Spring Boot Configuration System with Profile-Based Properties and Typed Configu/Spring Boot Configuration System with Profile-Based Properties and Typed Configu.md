---
kind: configuration_system
name: Spring Boot Configuration System with Profile-Based Properties and Typed ConfigurationProperties
category: configuration_system
scope:
    - '**'
source_files:
    - j-store-boot/src/main/resources/application.properties
    - j-store-boot/src/main/resources/application-local.properties
    - j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt
    - j-store-boot/src/main/kotlin/com/jstore/order/config/OrderMerchantProperties.kt
    - j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxAutoConfiguration.kt
    - j-store-boot/src/main/kotlin/JStoreOrderBootApplication.kt
    - j-store-boot/src/main/kotlin/com/jstore/user/config/UserBootConfiguration.kt
---

The j-store platform uses Spring Boot's standard configuration system as its runtime configuration mechanism, organized around profile-based property files and strongly-typed `@ConfigurationProperties` classes. The application is a multi-module Gradle project where the boot module (`j-store-boot`) serves as the Spring Boot entry point and centralizes all externalized configuration.

**Configuration Loading and Profiles**
- The base `application.properties` sets `spring.application.name=j-store-order-boot`, enables graceful shutdown, activates the `local` profile via `spring.profiles.active=local`, and configures Flyway migration settings (locations, baseline version, validation).
- Environment-specific overrides live in `application-local.properties` (active by default) and an empty `application-dev.properties`. The local profile defines PostgreSQL datasource URLs, Hikari connection pool settings, Redis connection details, JWT secret, logging levels, and feature flags like `jstore.outbox.enabled=true` and `jstore.order.merchant-id=1`.
- Nacos integration is present but commented out in `application-local.properties`, showing the intended path for centralized configuration management (`spring.cloud.nacos.config.*` properties and `spring.config.import[0] = nacos:...`).

**Typed Configuration with @ConfigurationProperties**
The codebase follows a consistent pattern of defining typed configuration classes:
- `OutboxProperties` (`com.jstore.common.framework.event.outbox.OutboxProperties`) — a comprehensive data class with prefix `jstore.outbox` exposing polling intervals, batch sizes, retry policies, retention settings, and event type scan packages. It includes extensive `init { require(...) }` validation blocks enforcing constraints like positive values and ordering relationships.
- `OrderMerchantProperties` (`com.jstore.order.config.OrderMerchantProperties`) — a minimal data class with prefix `jstore.order` binding `merchantId` with validation requiring a positive number.
- These are registered via `@EnableConfigurationProperties` at the application level (`JStoreOrderBootApplication.kt`) and per-module configurations (e.g., `OrderBootConfiguration.kt` enables `OrderMerchantProperties`).

**Auto-Configuration Pattern**
- `OutboxAutoConfiguration` demonstrates the standard Spring Boot auto-configuration pattern: it uses `@ConditionalOnProperty(prefix = "jstore.outbox", name = ["enabled"], havingValue = "true")` to conditionally enable the entire outbox subsystem based on configuration.
- The auto-configuration wires beans for event serialization, persistence, scheduling, monitoring, and cleanup based on the bound `OutboxProperties`.

**Direct Property Injection**
- Some components use `@Value("\${jwt.secret}")` for simple scalar properties (e.g., `UserBootConfiguration.kt` injects the JWT secret directly into `JwtTokenProvider`).

**Configuration Structure Conventions**
- All custom application properties use the `jstore.*` namespace (e.g., `jstore.outbox.*`, `jstore.order.*`), providing clear separation from Spring Boot defaults.
- Boolean feature flags follow the pattern `jstore.<feature>.enabled=true/false`.
- Numeric IDs and sensitive values (JWT secrets) are injected via properties rather than hardcoded.
- Validation is performed inline in `init` blocks of `@ConfigurationProperties` classes using Kotlin's `require()` function.

**Externalized Configuration Sources**
- Primary source: `application.properties` + profile-specific `.properties` files under `src/main/resources/`
- Planned secondary source: Nacos Config Server (Spring Cloud Alibaba) — dependencies declared in `gradle/libs.versions.toml` (`spring-cloud-starter-alibaba-nacos-config`) and configuration templates exist but are currently disabled.
- No `.env` files or environment variable overrides are used in the current setup.

**Constraints and Enforced Rules**
- Outbox properties enforce strict validation: polling interval > 0, batch size > 0, max retry count > 0, lock timeout > 0, retention days ≥ 0, cleanup batch size > 0, event type scan packages must not be empty, and max retry delay must be ≥ initial retry delay.
- Merchant ID must be configured as a positive number.
- The outbox subsystem is completely disabled unless `jstore.outbox.enabled=true` is explicitly set.