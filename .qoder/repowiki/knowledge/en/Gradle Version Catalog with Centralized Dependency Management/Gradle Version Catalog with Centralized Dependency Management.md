---
kind: dependency_management
name: Gradle Version Catalog with Centralized Dependency Management
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - settings.gradle.kts
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
    - j-store-boot/build.gradle.kts
    - j-store-common-core/build.gradle.kts
---

The j-store project uses Gradle as its build system with a centralized dependency management strategy based on the Gradle Version Catalog (libs.versions.toml). All third-party library versions are declared in a single central file at gradle/libs.versions.toml, which defines both version numbers and named library references that modules consume via the `libs` alias.

**Centralized Version Management**: The root-level gradle/libs.versions.toml file serves as the single source of truth for all dependency versions across the multi-module project. It organizes dependencies into three sections: [versions] for version numbers, [libraries] for module coordinates with version references, and [plugins] for Gradle plugin versions. This ensures consistent versions across all modules and prevents version drift.

**Repository Configuration**: The root build.gradle.kts configures multiple Maven repositories including mavenCentral(), Aliyun mirror (https://maven.aliyun.com/repository/public), and mavenLocal(). Individual modules can override this configuration but most inherit from the root. The project uses Java 25 toolchain consistently across all modules.

**Module Dependencies**: Each module's build.gradle.kts file declares its dependencies using the `libs` namespace (e.g., `implementation(libs.kotlin.stdlib)`, `implementation(project(":j-store-order"))`). Internal module dependencies use project references while external dependencies use the centralized version catalog. The boot application aggregates all domain modules and their infrastructure layers.

**Dependency Categories**: The project organizes dependencies into clear categories: Spring Boot ecosystem (3.5.16), Kotlin (2.3.0), testing frameworks (Kotest 5.9.1, JUnit 5.11.4), database (PostgreSQL 42.7.4, Flyway), caching (Redisson 3.52.0), security (Spring Security 7.1.0-RC1, JWT 0.13.0), and AI SDKs (OpenAI, DashScope).

**Build Tool Versioning**: The project uses Gradle 9.4.0 via the wrapper system, ensuring consistent builds across development and CI environments. The settings.gradle.kts includes the Foojay toolchain resolver for automatic JDK provisioning.

**No Vendoring Strategy**: The project does not vendor dependencies or use private registries beyond the Aliyun mirror. Dependencies are downloaded directly from Maven Central and mirrors during build time.