---
kind: build_system
name: Gradle Multi-Module Build System with Spring Boot & Docker
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
---

## Build System Overview

The J-Store project uses **Gradle Kotlin DSL** as its primary build system, organized as a multi-module project following Domain-Driven Design principles. The build is configured through a centralized version catalog approach with consistent toolchain management across all modules.

## Core Build Architecture

### Gradle Configuration Structure
- **Root build.gradle.kts**: Defines global plugins (Kotlin JVM, Spring), Java toolchain (Java 25), and repository configuration including Maven Central, Aliyun mirror, and local Maven
- **settings.gradle.kts**: Declares all 17 modules and plugin management for Lombok
- **gradle/libs.versions.toml**: Centralized version catalog managing all dependency versions including Spring Boot 3.5.16, Kotlin 2.3.0, PostgreSQL 42.7.4, and testing frameworks
- **gradle.properties**: Global properties for project group (com.jstore) and version (0.0.1-SNAPSHOT)

### Module Organization Pattern
Each domain module follows a consistent pattern:
- **Domain modules** (`j-store-order`, `j-store-goods`, `j-store-user`, `j-store-accounting`): Pure Kotlin DDD modules with business logic
- **Infrastructure modules** (`*-infrastructure`): Spring Boot implementations providing JPA repositories and external service integrations
- **Boot applications** (`j-store-boot`, `j-store-admin-boot`): Spring Boot applications that wire together domain and infrastructure modules
- **Shared libraries** (`j-store-common-core`, `j-store-common-spring`): Reusable framework components

### Dependency Management Strategy
- Uses Spring Boot BOM for dependency version alignment
- Leverages Gradle's version catalog for centralized dependency management
- Employs `api()` vs `implementation()` keywords to control transitive dependencies
- Test dependencies are isolated using `testImplementation` scope

## Build Tasks and Conventions

### Compilation and Testing
- All modules use Kotlin JVM toolchain targeting Java 25
- JUnit 5 platform with Kotest property-based testing framework
- Embedded PostgreSQL for integration tests via `io.zonky.test:embedded-postgres`
- Consistent test configuration across modules with `useJUnitPlatform()`

### Artifact Generation
- Spring Boot bootJar task generates executable JARs for application modules
- Standard JAR artifacts for library modules
- Tar tasks configured with duplicate strategy exclusion
- Docker images built from generated JAR files

## Containerization and Deployment

### Docker Configuration
- **Application containers**: Single-stage Dockerfile using Amazon Corretto 21 JDK
- **Database setup**: Custom PostgreSQL image with initialization scripts in `docker/postgres/`
- **Development environment**: Docker Compose file orchestrating PostgreSQL and Redis services
- **Kubernetes manifests**: Basic deployment and service definitions for production deployment

### Database Migration Strategy
- Flyway migration framework with numbered SQL scripts
- Development schema initialization through Docker entrypoint scripts
- Separate migration directories for different environments

## Build Conventions and Constraints

### Version Management
- Centralized version control through Gradle version catalog
- Spring ecosystem versions aligned through Spring Boot BOM
- Consistent Kotlin version (2.3.0) across all modules
- Semantic versioning with SNAPSHOT suffix for development builds

### Repository Configuration
- Primary Maven Central repository
- Aliyun mirror for improved download speeds in China
- Local Maven repository support for development dependencies

### Testing Framework Integration
- JUnit 5 as the primary testing framework
- Kotest for property-based testing
- Mockito for mocking with Kotlin extensions
- Spring Boot Test for integration testing scenarios

The build system emphasizes consistency, maintainability, and developer experience through centralized configuration, standardized module structure, and comprehensive testing infrastructure.