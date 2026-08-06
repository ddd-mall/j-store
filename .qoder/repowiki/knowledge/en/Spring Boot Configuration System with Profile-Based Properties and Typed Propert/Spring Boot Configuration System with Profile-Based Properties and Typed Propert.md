---
kind: configuration_system
name: Spring Boot Configuration System with Profile-Based Properties and Typed Properties
category: configuration_system
scope:
    - '**'
source_files:
    - j-store-boot/src/main/resources/application.properties
    - j-store-boot/src/main/resources/application-local.properties
    - .env.example
    - j-store-boot/src/main/kotlin/JStoreOrderBootApplication.kt
    - j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt
    - j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/MessagingProperties.kt
    - j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt
---

## What system/approach is used
The J-Store platform uses Spring Boot's native configuration system as its primary mechanism for loading, layering, and managing runtime configuration. The application relies on `application.properties` files with profile-specific overrides (`application-local.properties`, `application-dev.properties`) and environment variable substitution via `${VAR_NAME:default}` syntax. Custom configuration is exposed through Kotlin data classes annotated with `@ConfigurationProperties`, providing type-safe access to nested configuration sections.

## Key files and packages
- **Root configuration**: `j-store-boot/src/main/resources/application.properties` - activates the `local` profile and configures Flyway migrations
- **Local development settings**: `j-store-boot/src/main/resources/application-local.properties` - contains database, Redis, JWT, outbox, and messaging configuration
- **Environment template**: `.env.example` - documents required environment variables for local development
- **Application entry point**: `j-store-boot/src/main/kotlin/JStoreOrderBootApplication.kt` - enables configuration properties scanning via `@EnableConfigurationProperties`
- **Typed properties classes**:
  - `j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxProperties.kt` - outbox pattern configuration
  - `j-store-common-spring/src/main/kotlin/com/jstore/common/framework/messaging/MessagingProperties.kt` - messaging mode configuration
  - `j-store-boot/src/main/kotlin/com/jstore/outbox/operations/OutboxOperationsProperties.kt` - operational outbox settings
  - `j-store-common-spring/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxOperationalHealth.kt` - observability configuration

## Architecture and conventions
Configuration follows a layered approach where base defaults are defined in `application.properties`, environment-specific overrides in `application-{profile}.properties`, and external values injected via environment variables. The application uses Spring Boot's automatic property binding to map configuration keys to strongly-typed Kotlin data classes with validation constraints defined in `init {}` blocks.

The configuration system supports multiple deployment modes through the `jstore.messaging.mode` property (local/broker/hybrid), allowing the same codebase to run in different environments without code changes. Database connections use HikariCP connection pooling with configurable pool sizes, and Redis connectivity is parameterized for different environments.

## Conventions and constraints
- **Profile-based configuration**: All environment-specific settings are isolated in separate `application-{profile}.properties` files, with `spring.profiles.active=local` set as default
- **Environment variable injection**: Sensitive configuration (database credentials, JWT secrets, Redis passwords) must be provided via environment variables using the `${VAR_NAME:default}` syntax
- **Type-safe properties**: All custom configuration uses `@ConfigurationProperties` with Kotlin data classes rather than `@Value` annotations for better maintainability
- **Validation constraints**: Configuration properties include runtime validation in `init {}` blocks ensuring valid ranges and non-empty values
- **Naming convention**: Custom properties use the `jstore.*` namespace prefix to avoid conflicts with Spring Boot defaults
- **Security**: Environment variables like `JSTORE_DB_PASSWORD`, `JSTORE_REDIS_PASSWORD`, and `JSTORE_JWT_SECRET` are never committed to version control
- **Database configuration**: Uses Flyway for schema management with baseline migration support and validation enabled
- **Future extensibility**: Nacos configuration discovery is commented out but available for cloud deployment scenarios