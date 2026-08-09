---
kind: build_system
name: Gradle Multi-Project Build System with Spring Boot & Docker
category: build_system
scope:
    - '**'
source_files:
    - build.gradle.kts
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - gradle.properties
    - j-store-boot/build.gradle.kts
    - j-store-boot/Dockerfile
    - docker-compose.postgres.yml
    - docker/postgres/Dockerfile
    - scripts/quality-gate.sh
    - qodana.yaml
---

## Build System Overview

J-Store uses **Gradle** as the primary build system for a multi-project Kotlin/Spring Boot monorepo. The build orchestrates ~20 modules organized around DDD bounded contexts (order, goods, user, payment, fulfillment, accounting) plus shared libraries and boot applications.

### Core Build Architecture

**Centralized Version Management**: All dependency versions are declared in `gradle/libs.versions.toml` using Gradle's Version Catalog feature. This includes Spring Boot 3.5.16, Kotlin 2.3.0, PostgreSQL driver, and all third-party libraries. Each module references dependencies via `libs.<name>` instead of direct version strings.

**Multi-Project Structure**: `settings.gradle.kts` declares all modules with explicit `include()` statements. The root project `j-store` acts as an aggregator, while `j-store-boot` serves as the main Spring Boot application that wires together all domain services.

**Java/Kotlin Toolchain**: The project targets Java 25 via `java.toolchain.languageVersion = 25` at both root and module level, enforced through Gradle toolchains with `org.gradle.toolchains.foojay-resolver-convention` plugin for automatic JDK provisioning.

### Key Build Conventions

**Module Pattern**: Each DDD context follows a consistent four-module pattern:
- `<context>-domain`: Pure domain logic with no framework dependencies
- `<context>-application`: Application use cases and orchestration
- `<context>-infrastructure`: JPA repositories and external integrations
- `<context>-boot`: Spring Boot configuration and HTTP controllers

**Dependency Management**:
- Root `build.gradle.kts` applies common plugins (Kotlin JVM, Spotless code formatting, CycloneDX BOM generation)
- All projects inherit group `com.jstore` and version from `gradle.properties`
- Shared libraries (`j-store-common-core`, `j-store-common-spring`) use `api()` to expose transitive dependencies

**Testing Strategy**: JUnit 5 with Kotest property-based testing for domain modules. Test configuration is centralized with `useJUnitPlatform()` and `failOnNoDiscoveredTests = false`.

### Containerization & Deployment

**Docker Images**: `j-store-boot/Dockerfile` builds container images using Amazon Corretto 25 (Alpine Linux), running as non-root user (UID 10001). The image copies the built JAR and sets it as the entrypoint.

**Local Development**: `docker-compose.postgres.yml` provides PostgreSQL 16 and Redis 7 containers with health checks, persistent volumes, and environment variable configuration.

**Database Migrations**: Flyway-managed migrations under `docker/postgres/init/` with numbered SQL scripts (01-init.sql through 09-goods-spu-source-spu-id.sql) for schema evolution.

### Quality Gates & CI

**Code Formatting**: Spotless plugin enforces Google Java Format for Java files and ktfmt for Kotlin, ratcheting against `origin/master` branch to avoid blocking PRs on unrelated changes.

**Quality Gate Script**: `scripts/quality-gate.sh` runs three stages: repository governance checks, Python-based spec validation tests, and Gradle regression tests.

**Static Analysis**: Qodana configuration for JetBrains IDE static analysis with JVM community linter.

**Security Scanning**: `.gitleaksignore` file for secret detection, with separate `requirements-security.txt` for security tooling dependencies.

### Build Performance

**Gradle Optimization**: Configured with `-Xmx3072m -XX:MaxMetaspaceSize=768m` JVM args, `org.gradle.workers.max=2` for parallel execution, and `kotlin.compiler.execution.strategy=in-process` for faster compilation.

**Artifact Publishing**: CycloneDX BOM generation enabled for runtime dependency tracking across all modules.
